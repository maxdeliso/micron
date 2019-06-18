package name.maxdeliso.micron.looper;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.MessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.selector.NonBlockingAcceptorSelector;
import name.maxdeliso.micron.selector.PeerCountingReadWriteSelector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Slf4j
@Builder
public final class SingleThreadedEventLooper implements
    EventLooper,
    PeerCountingReadWriteSelector,
    NonBlockingAcceptorSelector {

  private final SocketAddress socketAddress;
  private final Charset messageCharset;
  private final PeerRegistry peerRegistry;
  private final MessageStore messageStore;
  private final SelectorProvider selectorProvider;
  private final ByteBuffer incomingBuffer;
  private final int asyncEnableTimeoutMs;

  private final CountDownLatch latch
      = new CountDownLatch(1);
  private final AtomicReference<ServerSocketChannel> serverSocketChannelRef
      = new AtomicReference<>();
  private final AtomicReference<Selector> selectorRef
      = new AtomicReference<>();

  @Override
  public void loop() throws IOException {
    log.info("entering event loop");

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
  public void halt() throws InterruptedException {
    Optional
        .ofNullable(serverSocketChannelRef.get())
        .filter(AbstractInterruptibleChannel::isOpen)
        .ifPresentOrElse(ss -> {
          try {
            log.trace("closing server socket channel during halt...");
            ss.close();
            log.trace("closed server socket channel during halt...");
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

  private Optional<String> handleReadablePeer(
      final SelectionKey key,
      final Peer peer) {

    int readCalls = 0;
    int bytesReadTotal = 0;
    boolean endOfFile = false;

    try {
      final var socketChannel = peer.getSocketChannel();
      boolean doneReading = false;

      do {
        final var bytesRead = socketChannel.read(incomingBuffer);
        readCalls++;

        if (bytesRead == 0) {
          doneReading = true;
        } else if (bytesRead == -1) {
          doneReading = true;
          endOfFile = true;
        } else {
          bytesReadTotal += bytesRead;
        }
      } while (!doneReading);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to read from peer {}, so evicted", peer, ioe);
    }

    if (endOfFile) {
      peerRegistry.evictPeer(peer);

      log.warn("received end of stream from peer {}", peer);
    }

    final String incoming;

    if (bytesReadTotal == 0) {
      incoming = null;

      log.trace("read no bytes from peer {}", peer);
    } else {
      log.trace("read {} bytes from peer {} in {} operations",
          bytesReadTotal, peer, readCalls);

      final var incomingBytes = new byte[bytesReadTotal];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesReadTotal);
      incomingBuffer.rewind();
      incoming = new String(incomingBytes, messageCharset);
    }

    toggleMaskAsync(key, SelectionKey.OP_READ);

    return Optional.ofNullable(incoming);
  }

  private void handleWritablePeer(final SelectionKey key, final Peer peer) {
    try {
      final Stream<String> messageStream = messageStore.getFrom(peer.getPosition());
      final AtomicInteger messageCount = new AtomicInteger(0);
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      messageStream
          .map(String::getBytes)
          .forEach(bytes -> {
            messageCount.incrementAndGet();
            byteArrayOutputStream.writeBytes(bytes);
          });

      if (messageCount.get() > 0) {
        final var bufferToWrite = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
        final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);
        final var newPosition = peer.advancePosition(messageCount.get());

        log.trace("wrote {} bytes to peer {} to advance to {}", bytesWritten, peer, newPosition);
      }

      toggleMaskAsync(key, SelectionKey.OP_WRITE);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }
  }

  private void toggleMaskAsync(final SelectionKey key, final int mask) {
    try {
      if ((key.interestOpsAnd(~mask) & mask) == mask) {
        asyncEnable(key, mask);
      } else {
        log.trace("clearing interest ops had no effect on key {}", key);
      }
    } catch (final CancelledKeyException cke) {
      log.warn("key was cancelled", cke);
    }
  }

  private void asyncEnable(final SelectionKey key, final int mask) {
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(asyncEnableTimeoutMs);
        key.interestOpsOr(mask);
        selectorRef.get().wakeup();
      } catch (final CancelledKeyException cke) {
        log.trace("key was cancelled while toggling async interest ops", cke);
      } catch (final InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    });
  }
}
