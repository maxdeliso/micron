package name.maxdeliso.micron.looper;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.MessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.selector.NonBlockingAcceptorSelector;
import name.maxdeliso.micron.selector.PeerCountingReadWriteSelector;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Builder
public final class SingleThreadedEventLooper implements
    EventLooper,
    PeerCountingReadWriteSelector,
    NonBlockingAcceptorSelector {

  private static final int SELECTION_KEY_TIMEOUT_MS = 1000;
  private final SocketAddress socketAddress;
  private final long selectTimeoutSeconds;
  private final String noNewDataMessage;
  private final Charset messageCharset;
  private final PeerRegistry peerRegistry;
  private final MessageStore messageStore;
  private final SelectorProvider selectorProvider;
  private final ByteBuffer incomingBuffer;
  private final CountDownLatch latch = new CountDownLatch(1);
  private ServerSocketChannel serverSocketChannel;
  private Selector selector;

  @Override
  public void loop() throws IOException {
    log.info("entering event loop");

    try (final var serverSocketChannel = selectorProvider.openServerSocketChannel();
         final var socketChannel = serverSocketChannel.bind(socketAddress);
         final var selector = selectorProvider.openSelector()) {

      this.selector = selector;
      this.serverSocketChannel = serverSocketChannel;
      serverSocketChannel.configureBlocking(false);
      socketChannel.register(selector, SelectionKey.OP_ACCEPT);

      while (serverSocketChannel.isOpen()) {
        selector.select();

        for (final var selectedKey : selector.selectedKeys()) {
          if (selectedKey.isValid()
              && selectedKey.isAcceptable()) {
            handleAccept(serverSocketChannel, selector,
                (channel, key) -> associatePeer(channel, key, peerRegistry));
          }

          if (selectedKey.isValid()
              && selectedKey.isWritable()
              && ((selectedKey.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)) {
            handleWritableKey(selectedKey, peerRegistry, this::handleWritablePeer);
          }

          if (selectedKey.isValid()
              && selectedKey.isReadable()
              && ((selectedKey.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ)) {
            handleReadableKey(selectedKey, peerRegistry,
                peer -> handleReadablePeer(selectedKey, peer)
                    .ifPresent(message -> {
                      if (!messageStore.add(message)) {
                        log.warn("discarded message due to overflow");
                      }
                    }));
          }
        }
      }
    } finally {
      latch.countDown();

      log.info("exiting event loop");
    }
  }

  @Override
  public void halt() throws InterruptedException, IOException {
    if (this.serverSocketChannel != null && serverSocketChannel.isOpen()) {
      this.serverSocketChannel.close();
    }

    if (this.selector != null) {
      selector.wakeup();
    }

    latch.await();
  }

  private Optional<String> handleReadablePeer(
      final SelectionKey key,
      final Peer peer) {
    final int bytesRead;

    try {
      bytesRead = peer.getSocketChannel().read(incomingBuffer);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to read from peer {}, so evicted", peer, ioe);

      return Optional.empty();
    }

    final String incoming;

    if (bytesRead == 0) {
      incoming = null;

      log.trace("read no bytes from peer {}", peer);
    } else if (bytesRead == -1) {
      incoming = null;

      peerRegistry.evictPeer(peer);

      log.warn("received end of stream from peer {}", peer);
    } else {
      log.trace("read {} bytes from peer {}", bytesRead, peer);

      final var incomingBytes = new byte[bytesRead];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesRead);
      incomingBuffer.rewind();
      incoming = new String(incomingBytes, messageCharset);
    }

    toggleAndReenableAsync(key, SelectionKey.OP_READ);

    return Optional.ofNullable(incoming);
  }

  private void handleWritablePeer(final SelectionKey key,
                                  final Peer peer) {
    try {
      final var bytesToWriteOpt = messageStore.get(peer.getPosition()).map(String::getBytes);

      if (bytesToWriteOpt.isPresent()) {
        final var bufferToWrite = ByteBuffer.wrap(bytesToWriteOpt.get());
        final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);
        peer.advancePosition();

        log.trace("wrote {} bytes to peer {}", bytesWritten, peer);
      }

      toggleAndReenableAsync(key, SelectionKey.OP_WRITE);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }
  }

  private void toggleAndReenableAsync(final SelectionKey key, final int mask) {
    try {
      if ((key.interestOpsAnd(~mask) & mask) == mask) {
        reEnableAsync(key, mask);
      } else {
        log.trace("clearing interest ops had no effect on key {}", key);
      }
    } catch (final CancelledKeyException cke) {
      log.warn("key was cancelled", cke);
    }
  }


  private void reEnableAsync(final SelectionKey key,
                             final int mask) {
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(SELECTION_KEY_TIMEOUT_MS);
        key.interestOpsOr(mask);
        selector.wakeup();
      } catch (final CancelledKeyException cke) {
        log.trace("key was cancelled while toggling async interest ops", cke);
      } catch (final InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    });
  }
}
