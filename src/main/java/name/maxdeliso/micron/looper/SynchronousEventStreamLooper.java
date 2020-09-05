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
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.handler.read.ReadHandler;
import name.maxdeliso.micron.handler.read.SerialReadHandler;
import name.maxdeliso.micron.handler.write.SerialWriteHandler;
import name.maxdeliso.micron.handler.write.WriteHandler;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.toggle.DelayedToggle;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;


@Slf4j
public class SynchronousEventStreamLooper implements EventLooper {

  private final SocketAddress socketAddress;
  private final PeerRegistry<InMemoryPeer> peerRegistry;
  private final SelectorProvider selectorProvider;

  private final AtomicReference<ServerSocketChannel> serverSocketChannelRef
      = new AtomicReference<>();
  private final AtomicReference<Selector> selectorRef
      = new AtomicReference<>();
  private final AtomicBoolean looping
      = new AtomicBoolean(false);
  private final AtomicBoolean starting
      = new AtomicBoolean(true);

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
  public SynchronousEventStreamLooper(final SocketAddress socketAddress,
                                      final PeerRegistry<InMemoryPeer> peerRegistry,
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
      looping.set(true);
      starting.set(false);
      while (looping.get() && serverSocketChannel.isOpen()) {
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
      looping.set(false);
      log.info("looper exiting");
    }
  }

  @Override
  public boolean halt() {
    if (starting.get()) {
      log.trace("looper is still starting");
      return false;
    }

    if (looping.get()) {
      log.trace("disabling looping flag");
      looping.set(false);
    } else {
      log.info("looper is already done looping");
      return true;
    }

    var closeSucceeded = Optional
        .ofNullable(serverSocketChannelRef.get())
        .filter(AbstractInterruptibleChannel::isOpen)
        .map(ss -> {
          try {
            log.trace("closing server socket channel during halt...");
            ss.close();
            return true;
          } catch (final IOException ioe) {
            log.trace("warn failed to close server socket channel during halt...", ioe);
            return false;
          }
        })
        .orElse(false);

    if (!closeSucceeded) {
      return false;
    }

    Optional
        .ofNullable(selectorRef.get())
        .ifPresentOrElse(Selector::wakeup, () -> log.warn("select ref was absent during halt..."));

    return true;
  }

  private boolean maskOpSet(final SelectionKey selectionKey, final int mask) {
    return (selectionKey.interestOps() & mask) == mask;
  }

  private void handleAccept(
      ServerSocketChannel serverSocketChannel,
      Selector selector,
      SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder,
      BiConsumer<SocketChannel, SelectionKey> peerConsumer) throws IOException {

    final SelectionKey acceptSelectionKey = serverSocketChannel.register(selector, 0);

    selectionKeyToggleQueueAdder.enqueueEnableInterest(acceptSelectionKey, SelectionKey.OP_ACCEPT);

    final SocketChannel socketChannel = serverSocketChannel.accept();

    if (socketChannel == null) {
      return;
    }

    socketChannel.configureBlocking(false);

    final var peerKey = socketChannel
        .register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

    peerConsumer.accept(socketChannel, peerKey);
  }

  private void associatePeer(
      SocketChannel socketChannel,
      SelectionKey peerKey,
      PeerRegistry<InMemoryPeer> peerRegistry) {
    final var peer = peerRegistry.allocatePeer(socketChannel);
    peerKey.attach(peer.getIndex());
  }

  public Optional<InMemoryPeer> lookupPeer(
      SelectionKey selectionKey,
      PeerRegistry<InMemoryPeer> peerRegistry) {
    return Optional.ofNullable(selectionKey)
        .map(key -> (Integer) key.attachment())
        .flatMap(peerRegistry::get);
  }

  private void handleReadableKey(
      SelectionKey readSelectedKey,
      PeerRegistry<InMemoryPeer> peerRegistry,
      Consumer<InMemoryPeer> peerConsumer) {
    lookupPeer(readSelectedKey, peerRegistry)
        .ifPresent(peerConsumer);
  }

  private void handleWritableKey(
      SelectionKey writeSelectedKey,
      PeerRegistry<InMemoryPeer> peerRegistry,
      BiConsumer<SelectionKey, InMemoryPeer> peerConsumer) {
    lookupPeer(writeSelectedKey, peerRegistry)
        .ifPresent(peer -> peerConsumer.accept(writeSelectedKey, peer));
  }
}

