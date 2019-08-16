package name.maxdeliso.micron.looper;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.read.SerialReadHandler;
import name.maxdeliso.micron.looper.toggle.DelayedToggle;
import name.maxdeliso.micron.looper.toggle.SelectionKeyToggleQueueAdder;
import name.maxdeliso.micron.looper.write.SerialBufferingWriteHandler;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.selector.NonBlockingAcceptorSelector;
import name.maxdeliso.micron.selector.PeerCountingReadWriteSelector;

@Slf4j
public class SingleThreadedStreamingEventLooper implements
    EventLooper,
    PeerCountingReadWriteSelector,
    NonBlockingAcceptorSelector {

  private final SocketAddress socketAddress;
  private final PeerRegistry peerRegistry;
  private final SelectorProvider selectorProvider;

  private final CountDownLatch latch
      = new CountDownLatch(1);
  private final AtomicReference<ServerSocketChannel> serverSocketChannelRef
      = new AtomicReference<>();
  private final AtomicReference<Selector> selectorRef
      = new AtomicReference<>();

  private final SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder;
  private final SerialReadHandler readHandler;
  private final SerialBufferingWriteHandler writeHandler;

  private final Meter events;

  /**
   * Build a single threaded threaded streaming event looper.
   *
   * @param socketAddress       address to listen on.
   * @param messageCharset      message charset to use.
   * @param peerRegistry        a peer registry instance.
   * @param messageStore        a message store instance.
   * @param selectorProvider    a provider of selectors.
   * @param incomingBuffer      a single shared buffer for incoming messages.
   * @param toggleDelayQueue    a delay queue of toggle events.
   * @param asyncEnableDuration the duration to re-enable i/o operations for.
   * @param metrics             capture metrics about performance.
   */
  public SingleThreadedStreamingEventLooper(final SocketAddress socketAddress,
                                            final Charset messageCharset,
                                            final PeerRegistry peerRegistry,
                                            final RingBufferMessageStore messageStore,
                                            final SelectorProvider selectorProvider,
                                            final ByteBuffer incomingBuffer,
                                            final DelayQueue<DelayedToggle> toggleDelayQueue,
                                            final Duration asyncEnableDuration,
                                            final MetricRegistry metrics) {
    this.socketAddress = socketAddress;
    this.peerRegistry = peerRegistry;
    this.selectorProvider = selectorProvider;

    this.events = metrics.meter("events");

    metrics.register("peers", (Gauge<Long>) peerRegistry::size);

    this.selectionKeyToggleQueueAdder = new SelectionKeyToggleQueueAdder(
        asyncEnableDuration,
        selectorRef,
        toggleDelayQueue);

    this.readHandler = new SerialReadHandler(
        incomingBuffer,
        messageCharset,
        peerRegistry,
        messageStore,
        selectionKeyToggleQueueAdder);

    this.writeHandler = new SerialBufferingWriteHandler(
        messageStore,
        selectionKeyToggleQueueAdder,
        peerRegistry,
        messageCharset);
  }

  @Override
  public void loop() throws IOException {
    try (final var serverSocketChannel = selectorProvider.openServerSocketChannel();
         final var socketChannel = serverSocketChannel.bind(socketAddress);
         final var selector = selectorProvider.openSelector()) {
      selectorRef.set(selector);
      serverSocketChannelRef.set(serverSocketChannel);
      serverSocketChannel.configureBlocking(false);
      socketChannel.register(selector, SelectionKey.OP_ACCEPT);

      log.info("bound to {}, entering event loop", socketAddress);

      while (serverSocketChannel.isOpen()) {
        selector.select();

        for (final var selectedKey : selector.selectedKeys()) {
          if (selectedKey.isValid()
              && selectedKey.isAcceptable()
              && ((selectedKey.interestOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT)) {
            handleAccept(
                serverSocketChannel,
                selector,
                selectionKeyToggleQueueAdder,
                (channel, key) -> associatePeer(channel, key, peerRegistry));
          }

          if (selectedKey.isValid()
              && selectedKey.isReadable()
              && ((selectedKey.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ)) {
            handleReadableKey(
                selectedKey,
                peerRegistry,
                peer -> {
                  if (!readHandler.handleReadablePeer(selectedKey, peer)) {
                    log.trace("no message read from peer: {}", peer);
                  }
                });
          }

          if (selectedKey.isValid()
              && selectedKey.isWritable()
              && ((selectedKey.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)) {
            handleWritableKey(
                selectedKey,
                peerRegistry,
                (selectionKey, peer) -> {
                  if (!writeHandler.handleWritablePeer(selectedKey, peer)) {
                    log.trace("no message written to peer: {}", peer);
                  }
                }
            );
          }
        } // for

        events.mark();
      } // while
    } finally {
      latch.countDown();

      log.info("exiting event loop");
    }
  }

  @Override
  public void halt() throws InterruptedException {
    Optional
        .ofNullable(serverSocketChannelRef.get())
        .filter(AbstractInterruptibleChannel::isOpen)
        .ifPresentOrElse(ss -> {
          try {
            log.trace("closing server socket channel during halt...");
            ss.close();
          } catch (final IOException ioe) {
            log.trace("warn failed to close server socket channel during halt...", ioe);
          }
        }, () -> log.warn("halting prior to server socket channel ref becoming available"));

    Optional
        .ofNullable(selectorRef.get())
        .ifPresentOrElse(Selector::wakeup, () -> {
          log.warn("select ref was absent during halt...");
        });

    log.info("awaiting countdown latch");
    latch.await();
    log.info("halt completed");
  }
}
