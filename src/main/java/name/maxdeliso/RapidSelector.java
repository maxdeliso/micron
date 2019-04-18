package name.maxdeliso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class RapidSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(RapidSelector.class);

    private final ByteBuffer incomingBuffer;

    private final int messageListCap;

    private final int serverPort;

    private final String noNewDataMessage;

    private final long selectTimeoutSeconds;

    private final List<String> messages;

    private final AtomicLong peerCounter = new AtomicLong();

    private final Map<Long, Peer> peerMap = new HashMap<>();

    public RapidSelector(int serverPort,
                         int bufferSize,
                         int selectTimeoutSeconds,
                         int messageListCap,
                         String noNewDataMessage) {
        this.serverPort = serverPort;
        this.incomingBuffer = ByteBuffer.allocateDirect(bufferSize);
        this.selectTimeoutSeconds = selectTimeoutSeconds;
        this.messageListCap = messageListCap;
        this.noNewDataMessage = noNewDataMessage;
        this.messages = new ArrayList<>(messageListCap);
    }

    public void eventLoop() throws IOException {
        try (final var serverSocketChannel = ServerSocketChannel.open();
             final var socketChannel = serverSocketChannel.bind(new InetSocketAddress(serverPort));
             final var selector = Selector.open()) {

            serverSocketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (serverSocketChannel.isOpen()) {
                selector.select(TimeUnit.SECONDS.toMillis(this.selectTimeoutSeconds));

                for (final var selectedKey : selector.selectedKeys()) {
                    if (!selectedKey.isValid()) {
                        LOGGER.warn("selected invalid key!");
                        continue;
                    }

                    if (selectedKey.isAcceptable()) {
                        handleAcceptableKey(serverSocketChannel, selector);
                    }

                    if (selectedKey.isValid() && selectedKey.isWritable()) {
                        handleWritableKey(selectedKey);
                    }

                    if (selectedKey.isValid() && selectedKey.isReadable()) {
                        handleReadableKey(selectedKey);
                    }
                }
            }
        }
    }

    private Optional<Peer> lookupPeerDescriptor(final SelectionKey selectionKey) {
        return Optional.ofNullable(selectionKey)
                .map(key -> (Long) key.attachment())
                .map(peerMap::get);
    }

    private void handleAcceptableKey(final ServerSocketChannel serverSocketChannel, final Selector selector)
            throws IOException {
        Optional.ofNullable(serverSocketChannel.accept())
                .ifPresent(clientChannel -> {
                    try {
                        clientChannel.configureBlocking(false);

                        final var peerKey = clientChannel.register(selector,
                                SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        final var newPeerNumber = peerCounter.get();
                        final var newPeerDescriptor = new Peer(newPeerNumber, clientChannel);

                        peerMap.put(newPeerNumber, newPeerDescriptor);
                        peerKey.attach(newPeerNumber);
                        peerCounter.incrementAndGet();

                        LOGGER.info("new connection, peer counter is now {}", peerCounter.get());
                    } catch (final IOException ioe) {
                        throw new IllegalStateException("failed to initialize peer socket", ioe);
                    }
                });
    }


    private void handleReadableKey(final SelectionKey readSelectedKey) {
        lookupPeerDescriptor(readSelectedKey)
                .flatMap(this::receiveMessage)
                .ifPresent(receivedMsg -> {
                    handleOverflow();
                    messages.add(receivedMsg);
                });
    }

    private void handleOverflow() {
        if (messages.size() >= messageListCap) {
            final var minimumRightExtent = peerMap
                    .values()
                    .parallelStream()
                    .map(Peer::getPosition)
                    .map(Math::toIntExact)
                    .min(Integer::compare)
                    .orElse(messageListCap);

            final var leftOver = new ArrayList<>(messages.subList(minimumRightExtent, messageListCap));

            LOGGER.debug("maximum of {} was hit, copying {} left over messages to the beginning",
                    messageListCap, leftOver.size());

            messages.clear();
            messages.addAll(leftOver);
            peerMap.values().parallelStream().forEach(Peer::resetPosition);
        }
    }

    private Optional<String> receiveMessage(final Peer peer) {
        final int bytesRead;

        try {
            bytesRead = peer.getSocketChannel().read(incomingBuffer);

            if (bytesRead == 0) {
                return Optional.empty();
            } else if (bytesRead == -1) {
                evictPeer(peer);
                return Optional.empty();
            }
        } catch (final IOException ioe) {
            evictPeer(peer);
            return Optional.empty();
        }

        final var incomingBytes = new byte[bytesRead];
        incomingBuffer.flip();
        incomingBuffer.get(incomingBytes, 0, bytesRead);
        incomingBuffer.rewind();
        final var incoming = new String(incomingBytes);
        return Optional.of(incoming);
    }

    private void handleWritableKey(final SelectionKey writeSelectedKey) {
        lookupPeerDescriptor(writeSelectedKey)
                .ifPresent(peer -> {
                    try {
                        final Long peerPos = peer.getPosition();

                        if (peerPos < messages.size()) {
                            final var nextMessageBytes = messages
                                    .get(peerPos.intValue()) // java.util.List uses primitive int
                                    .getBytes();

                            peer.getSocketChannel().write(ByteBuffer.wrap(nextMessageBytes));
                            peer.advancePosition();
                        } else {
                            peer.getSocketChannel().write(ByteBuffer.wrap(noNewDataMessage.getBytes()));
                        }
                    } catch (final IOException ioe) {
                        LOGGER.warn("failed to write to peer with index {}", peer.getIndex());

                        evictPeer(peer);
                    }
                });
    }

    private void evictPeer(final Peer peer) {
        try {
            peer.getSocketChannel().close();
        } catch (final IOException ioe) {
            LOGGER.warn("failed to close channel on peer eviction", ioe);
        } finally {
            peerMap.remove(peer.getIndex());
        }
    }
}
