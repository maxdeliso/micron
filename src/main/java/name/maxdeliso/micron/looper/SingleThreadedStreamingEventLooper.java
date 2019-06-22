package name.maxdeliso.micron.looper;

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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Slf4j
public final class SingleThreadedStreamingEventLooper implements
    EventLooper,
    PeerCountingReadWriteSelector,
    NonBlockingAcceptorSelector {

  private final SocketAddress socketAddress;
  private final Charset messageCharset;
  private final PeerRegistry peerRegistry;
  private final MessageStore messageStore;
  private final SelectorProvider selectorProvider;
  private final ByteBuffer incomingBuffer;
  private final SelectionKeyToggler selectionKeyToggler;

  private final CountDownLatch latch
      = new CountDownLatch(1);
  private final AtomicReference<ServerSocketChannel> serverSocketChannelRef
      = new AtomicReference<>();
  private final AtomicReference<Selector> selectorRef
      = new AtomicReference<>();

  public SingleThreadedStreamingEventLooper(final SocketAddress socketAddress,
                                            final Charset messageCharset,
                                            final PeerRegistry peerRegistry,
                                            final MessageStore messageStore,
                                            final SelectorProvider selectorProvider,
                                            final ByteBuffer incomingBuffer,
                                            final int asyncEnableTimeoutMs,
                                            final Random random) {
    this.socketAddress = socketAddress;
    this.messageCharset = messageCharset;
    this.peerRegistry = peerRegistry;
    this.messageStore = messageStore;
    this.selectorProvider = selectorProvider;
    this.incomingBuffer = incomingBuffer;
    this.selectionKeyToggler = new SelectionKeyToggler(random, asyncEnableTimeoutMs, selectorRef);
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

    final PeerReadResult peerReadResult = performRead(peer);
    final String incoming;

    if (peerReadResult.getBytesReadTotal() == 0) {
      incoming = null;

      log.trace("read no bytes from peer {}", peer);
    } else {
      log.trace("read {} bytes from peer {} in {} operations",
          peerReadResult.getBytesReadTotal(), peer, peerReadResult.getReadCalls());

      final var bytesReadTotal = peerReadResult.getBytesReadTotal();
      final var incomingBytes = new byte[bytesReadTotal];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesReadTotal);
      incomingBuffer.rewind();
      incoming = new String(incomingBytes, messageCharset);
    }

    selectionKeyToggler.toggleMaskAsync(key, SelectionKey.OP_READ);

    return Optional.ofNullable(incoming);
  }

  private PeerReadResult performRead(final Peer peer) {
    var readCalls = 0;
    var bytesReadTotal = 0;
    var evictPeer = false;

    try {
      final var socketChannel = peer.getSocketChannel();
      var doneReading = false;

      do {
        final var bytesRead = socketChannel.read(incomingBuffer);

        readCalls++;

        if (bytesRead == 0) {
          doneReading = true;
        } else if (bytesRead == -1) {
          doneReading = true;
          evictPeer = true;
        } else {
          bytesReadTotal += bytesRead;
        }
      } while (!doneReading);
    } catch (final IOException ioe) {
      log.warn("io error while reading from peer, so marking for eviction", ioe);

      evictPeer = true;
    } finally {
      if (evictPeer) {
        peerRegistry.evictPeer(peer);

        log.warn("received end of stream from peer {}", peer);
      }
    }

    return PeerReadResult
        .builder()
        .bytesReadTotal(bytesReadTotal)
        .readCalls(readCalls)
        .build();
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

      selectionKeyToggler.toggleMaskAsync(key, SelectionKey.OP_WRITE);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }
  }
}
