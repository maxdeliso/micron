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

  private static final int PER_PEER_WRITE_TIMEOUT_MS = 1;
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
          if (!selectedKey.isValid()) {
            log.warn("selected invalid key");

            continue;
          }

          if (selectedKey.isAcceptable()) {
            handleAccept(serverSocketChannel, selector,
                (channel, key) -> associatePeer(channel, key, peerRegistry));
          }

          if (selectedKey.isValid()
              && selectedKey.isWritable()
              && ((selectedKey.interestOps() & SelectionKey.OP_WRITE) != 0)) {
            handleWritableKey(selectedKey, peerRegistry, this::handleWritablePeer);
          }

          if (selectedKey.isValid() && selectedKey.isReadable()) {
            handleReadableKey(selectedKey, peerRegistry,
                peer -> handleReadablePeer(peer)
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

  private Optional<String> handleReadablePeer(final Peer peer) {
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

      log.trace("read zero bytes from peer {}", peer);
    } else if (bytesRead == -1) {
      incoming = null;

      peerRegistry.evictPeer(peer);

      log.warn("received end of stream from peer {}", peer);
    } else {
      final var incomingBytes = new byte[bytesRead];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesRead);
      incomingBuffer.rewind();
      incoming = new String(incomingBytes, messageCharset);
    }

    return Optional.ofNullable(incoming);
  }

  private void handleWritablePeer(final SelectionKey selectionKey,
                                  final Peer peer) {
    try {
      final var bytesToWriteOpt = messageStore.get(peer.getPosition()).map(String::getBytes);

      if (bytesToWriteOpt.isEmpty()) {
        return;
      }

      final var bufferToWrite = ByteBuffer.wrap(bytesToWriteOpt.get());
      final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);

      bytesToWriteOpt.ifPresent(bytes -> peer.advancePosition());

      log.trace("wrote {} bytes to peer {}", bytesWritten, peer);

      final var interestBefore = selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);

      if ((interestBefore & SelectionKey.OP_WRITE) != 0) {
        log.info("disabled write interest due to write for peer {}", peer);

        CompletableFuture.runAsync(() -> {
          reenableWriteSelection(selectionKey);
        });
      }
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }
  }

  private void reenableWriteSelection(final SelectionKey selectionKey) {
    try {
      Thread.sleep(PER_PEER_WRITE_TIMEOUT_MS);
      selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
      selector.wakeup();
    } catch (final InterruptedException ie) {
      log.error("interrupted while waiting to re-enable write interest", ie);
    }
  }
}
