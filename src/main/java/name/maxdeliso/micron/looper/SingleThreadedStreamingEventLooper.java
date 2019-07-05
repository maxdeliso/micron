package name.maxdeliso.micron.looper;

import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.read.SerialReadHandler;
import name.maxdeliso.micron.looper.toggler.SelectionKeyToggler;
import name.maxdeliso.micron.looper.write.SerialBufferingWriteHandler;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.selector.NonBlockingAcceptorSelector;
import name.maxdeliso.micron.selector.PeerCountingReadWriteSelector;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class SingleThreadedStreamingEventLooper implements
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

  private final SelectionKeyToggler selectionKeyToggler;
  private final SerialReadHandler readHandler;
  private final SerialBufferingWriteHandler writeHandler;

  public SingleThreadedStreamingEventLooper(final SocketAddress socketAddress,
                                            final Charset messageCharset,
                                            final PeerRegistry peerRegistry,
                                            final RingBufferMessageStore messageStore,
                                            final SelectorProvider selectorProvider,
                                            final ByteBuffer incomingBuffer,
                                            final int asyncEnableTimeoutMs,
                                            final Random random) {
    this.socketAddress = socketAddress;
    this.peerRegistry = peerRegistry;
    this.selectorProvider = selectorProvider;

    this.selectionKeyToggler = new SelectionKeyToggler(
        random,
        asyncEnableTimeoutMs,
        selectorRef);

    this.readHandler = new SerialReadHandler(
        incomingBuffer,
        messageCharset,
        peerRegistry,
        messageStore,
        selectionKeyToggler);

    this.writeHandler = new SerialBufferingWriteHandler(
        messageStore,
        selectionKeyToggler,
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

      while (serverSocketChannel.isOpen()) {
        selector.select();

        for (final var selectedKey : selector.selectedKeys()) {
          if (selectedKey.isValid()
              && selectedKey.isAcceptable()
              && ((selectedKey.interestOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT)) {
            handleAccept(
                serverSocketChannel,
                selector,
                selectionKeyToggler,
                (channel, key) -> associatePeer(channel, key, peerRegistry));
          }

          if (selectedKey.isValid()
              && selectedKey.isWritable()
              && ((selectedKey.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)) {
            handleWritableKey(
                selectedKey,
                peerRegistry,
                (selectionKey, peer) -> {
                  if (!this.writeHandler.handleWritablePeer(selectedKey, peer)) {
                    log.trace("no message written to peer: {}", peer);
                  }
                }
            );
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
        } // for
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

    latch.await();
  }
}
