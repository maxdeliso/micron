package name.maxdeliso.micron.looper;

import static java.util.concurrent.TimeUnit.SECONDS;

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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Builder
public final class SingleThreadedEventLooper implements
    EventLooper,
    PeerCountingReadWriteSelector,
    NonBlockingAcceptorSelector {

  private final SocketAddress socketAddress;

  private final long selectTimeoutSeconds;

  private final String noNewDataMessage;

  private final Charset messageCharset;

  private final PeerRegistry peerRegistry;

  private final MessageStore messageStore;

  private final SelectorProvider selectorProvider;

  private final ByteBuffer incomingBuffer;

  ServerSocketChannel serverSocketChannel;

  private final CountDownLatch latch = new CountDownLatch(1);

  @Override
  public void loop() throws IOException {
    log.info("entering event loop");

    try (final var serverSocketChannel = selectorProvider.openServerSocketChannel();
         final var socketChannel = serverSocketChannel.bind(socketAddress);
         final var selector = selectorProvider.openSelector()) {

      this.serverSocketChannel = serverSocketChannel;
      serverSocketChannel.configureBlocking(false);
      socketChannel.register(selector, SelectionKey.OP_ACCEPT);

      while (serverSocketChannel.isOpen()) {
        selector.select(SECONDS.toMillis(this.selectTimeoutSeconds));

        for (final var selectedKey : selector.selectedKeys()) {
          if (!selectedKey.isValid()) {
            log.warn("selected invalid key");

            continue;
          }

          if (selectedKey.isAcceptable()) {
            handleAccept(serverSocketChannel, selector,
                (channel, key) -> associatePeer(channel, key, peerRegistry));
          }

          if (selectedKey.isValid() && selectedKey.isWritable()) {
            handleWritableKey(selectedKey, peerRegistry, this::handleWritablePeer);
          }

          if (selectedKey.isValid() && selectedKey.isReadable()) {
            handleReadableKey(selectedKey, peerRegistry,
                peer -> handleReadablePeer(peer).ifPresent(messageStore::add));
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

  private void handleWritablePeer(final Peer peer) {
    try {
      final var bytesToWriteOpt = messageStore
          .get(peer.getPosition()).map(String::getBytes);
      final var bufferToWrite = ByteBuffer
          .wrap(bytesToWriteOpt.orElse(noNewDataMessage.getBytes(messageCharset)));
      final var bytesWritten = peer
          .getSocketChannel().write(bufferToWrite);

      bytesToWriteOpt.ifPresent(bytes -> peer.advancePosition());

      log.trace("wrote {} bytes to peer {}", bytesWritten, peer);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }
  }
}
