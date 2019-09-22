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
import name.maxdeliso.micron.handler.read.ReadHandler;
import name.maxdeliso.micron.handler.read.SerialReadHandler;
import name.maxdeliso.micron.handler.write.SerialWriteHandler;
import name.maxdeliso.micron.handler.write.WriteHandler;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.selector.NonBlockingAcceptorSelector;
import name.maxdeliso.micron.selector.PeerCountingReadWriteSelector;
import name.maxdeliso.micron.toggle.DelayedToggle;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;

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
  private final ReadHandler readHandler;
  private final WriteHandler writeHandler;

  private final Meter eventsMeter;
  private final Meter acceptEventsMeter;
  private final Meter writeEventsMeter;
  private final Meter readEventsMeter;

  /**
   * Build a single threaded threaded streaming event looper.
   *
   * @param socketAddress       address to listen on.
   * @param peerRegistry        a peer registry instance.
   * @param messageStore        a message store instance.
   * @param selectorProvider    a provider of selectors.
   * @param incomingBuffer      a single shared buffer for incoming messages.
   * @param toggleDelayQueue    a delay queue of toggle events.
   * @param asyncEnableDuration the duration to re-enable i/o operations for.
   * @param metrics             capture metrics about performance.
   */
  public SingleThreadedStreamingEventLooper(final SocketAddress socketAddress,
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

    this.eventsMeter = metrics.meter("events");
    this.acceptEventsMeter = metrics.meter("accept_events");
    this.writeEventsMeter = metrics.meter("write_events");
    this.readEventsMeter = metrics.meter("read_events");

    metrics.register("peers", (Gauge<Long>) peerRegistry::size);

    this.selectionKeyToggleQueueAdder = new SelectionKeyToggleQueueAdder(
        asyncEnableDuration,
        selectorRef,
        toggleDelayQueue);

    this.readHandler = new SerialReadHandler(
        incomingBuffer,
        peerRegistry,
        messageStore,
        selectionKeyToggleQueueAdder);

    this.writeHandler = new SerialWriteHandler(
        messageStore,
        selectionKeyToggleQueueAdder,
        peerRegistry);
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
              && maskOpSet(selectedKey, SelectionKey.OP_ACCEPT)) {
            handleAccept(
                serverSocketChannel,
                selector,
                selectionKeyToggleQueueAdder,
                (channel, key) -> associatePeer(channel, key, peerRegistry));

            acceptEventsMeter.mark();
          }

          if (selectedKey.isValid()
              && selectedKey.isReadable()
              && maskOpSet(selectedKey, SelectionKey.OP_READ)) {
            handleReadableKey(
                selectedKey,
                peerRegistry,
                peer -> {
                  if (!readHandler.handleReadablePeer(selectedKey, peer)) {
                    log.trace("no message read from peer: {}", peer);
                  }
                });

            readEventsMeter.mark();
          }

          if (selectedKey.isValid()
              && selectedKey.isWritable()
              && maskOpSet(selectedKey, SelectionKey.OP_WRITE)) {
            handleWritableKey(
                selectedKey,
                peerRegistry,
                (selectionKey, peer) -> {
                  if (!writeHandler.handleWritablePeer(selectedKey, peer)) {
                    log.trace("no message written to peer: {}", peer);
                  }
                }
            );

            writeEventsMeter.mark();
          }
        } // for

        eventsMeter.mark();
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
        .ifPresentOrElse(Selector::wakeup, () -> log.warn("select ref was absent during halt..."));

    log.info("awaiting countdown latch");
    latch.await();
    log.info("halt completed");
  }

  private boolean maskOpSet(final SelectionKey selectionKey, final int mask) {
    return (selectionKey.interestOps() & mask) == mask;
  }
}

