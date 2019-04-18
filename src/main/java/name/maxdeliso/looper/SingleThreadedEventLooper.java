package name.maxdeliso.looper;

import name.maxdeliso.message.MessageStore;
import name.maxdeliso.peer.Peer;
import name.maxdeliso.peer.PeerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SingleThreadedEventLooper implements EventLooper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedEventLooper.class);

    private final int serverPort;

    private final String noNewDataMessage;

    private final long selectTimeoutSeconds;

    private final ByteBuffer incomingBuffer;

    private final PeerRegistry peerRegistry;

    private final MessageStore messageStore;

    private final CountDownLatch latch;

    private ServerSocketChannel serverSocketChannel;

    public SingleThreadedEventLooper(int serverPort,
                                     int bufferSize,
                                     int selectTimeoutSeconds,
                                     int messageListCap,
                                     String noNewDataMessage) {
        this.serverPort = serverPort;
        this.incomingBuffer = ByteBuffer.allocateDirect(bufferSize);
        this.selectTimeoutSeconds = selectTimeoutSeconds;
        this.noNewDataMessage = noNewDataMessage;
        this.peerRegistry = new PeerRegistry();
        this.messageStore = new MessageStore(messageListCap, peerRegistry);
        this.latch = new CountDownLatch(1);
    }

    @Override
    public void loop() throws IOException {
        LOGGER.trace("entering event loop");

        try (final var serverSocketChannel = ServerSocketChannel.open();
             final var socketChannel = serverSocketChannel.bind(new InetSocketAddress(serverPort));
             final var selector = Selector.open()) {

            this.serverSocketChannel = serverSocketChannel;
            serverSocketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (serverSocketChannel.isOpen()) {
                selector.select(TimeUnit.SECONDS.toMillis(this.selectTimeoutSeconds));

                for (final var selectedKey : selector.selectedKeys()) {
                    if (!selectedKey.isValid()) {
                        LOGGER.warn("selected invalid key");

                        continue;
                    }

                    if (selectedKey.isAcceptable()) {
                        handleAccept(serverSocketChannel, selector);
                    }

                    if (selectedKey.isValid() && selectedKey.isWritable()) {
                        handleWritableKey(selectedKey);
                    }

                    if (selectedKey.isValid() && selectedKey.isReadable()) {
                        handleReadableKey(selectedKey);
                    }
                }
            }
        } finally {
            latch.countDown();

            LOGGER.trace("exiting event loop");
        }
    }

    @Override
    public void halt() throws InterruptedException, IOException {
        if(serverSocketChannel != null && serverSocketChannel.isOpen()) {
            serverSocketChannel.close();
        }

        latch.await();
    }

    private Optional<Peer> lookupPeerDescriptor(final SelectionKey selectionKey) {
        return Optional.ofNullable(selectionKey).map(key -> (Long) key.attachment()).flatMap(peerRegistry::get);
    }

    private void handleAccept(final ServerSocketChannel serverSocketChannel, final Selector selector)
            throws IOException {
        Optional.ofNullable(serverSocketChannel.accept())
                .ifPresent(socketChannel -> handleAccept(socketChannel, selector));
    }

    private void handleAccept(final SocketChannel socketChannel, final Selector selector) {
        try {
            socketChannel.configureBlocking(false);

            final var peerKey = socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            final var peer = peerRegistry.allocatePeer(socketChannel);

            peerKey.attach(peer.getIndex());
        } catch (final IOException ioe) {
            LOGGER.warn("not allocating peer for socket channel that failed to be registered", ioe);
        }
    }

    private void handleReadableKey(final SelectionKey readSelectedKey) {
        lookupPeerDescriptor(readSelectedKey).flatMap(this::handleReadablePeer).ifPresent(messageStore::add);
    }

    private Optional<String> handleReadablePeer(final Peer peer) {
        final int bytesRead;

        try {
            bytesRead = peer.getSocketChannel().read(incomingBuffer);
        } catch (final IOException ioe) {
            peerRegistry.evictPeer(peer);

            LOGGER.warn("failed to read from peer {}, so evicted", peer, ioe);

            return Optional.empty();
        }

        final String incoming;

        if (bytesRead == 0) {
            incoming = null;

            LOGGER.trace("read zero bytes from peer {}", peer);
        } else if (bytesRead == -1) {
            incoming = null;

            peerRegistry.evictPeer(peer);

            LOGGER.warn("received end of stream from peer {}", peer);
        } else {
            final var incomingBytes = new byte[bytesRead];
            incomingBuffer.flip();
            incomingBuffer.get(incomingBytes, 0, bytesRead);
            incomingBuffer.rewind();
            incoming = new String(incomingBytes);
        }

        return Optional.ofNullable(incoming);
    }

    private void handleWritableKey(final SelectionKey writeSelectedKey) {
        lookupPeerDescriptor(writeSelectedKey).ifPresent(this::handleWritablePeer);
    }

    private void handleWritablePeer(final Peer peer) {
        try {
            final var bytesToWriteOpt = messageStore.get(peer.getPosition()).map(String::getBytes);
            final var bufferToWrite = ByteBuffer.wrap(bytesToWriteOpt.orElse(noNewDataMessage.getBytes()));
            final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);

            bytesToWriteOpt.ifPresent(_bytes -> peer.advancePosition());

            LOGGER.info("wrote {} bytes to peer {}", bytesWritten, peer);
        } catch (final IOException ioe) {
            peerRegistry.evictPeer(peer);

            LOGGER.warn("failed to write to peer {}", peer, ioe);
        }
    }
}
