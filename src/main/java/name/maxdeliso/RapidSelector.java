package name.maxdeliso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class RapidSelector implements EventLooper {

    private static final Logger LOGGER = LoggerFactory.getLogger(RapidSelector.class);

    private final Map<Integer, SocketChannel> peerIdxToChannel = new HashMap<>();

    private final ByteBuffer incomingBuffer;

    private final AtomicInteger peerCounter = new AtomicInteger();

    private final int messageListCap;

    private final List<String> messageList;

    private final Map<Integer, Integer> peerIdxToPosition = new HashMap<>();

    private final int serverPort;

    private final String noNewDataMessage;

    private final long selectTimeoutSeconds;

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

        this.messageList = new ArrayList<>(messageListCap);
    }

    @Override
    public void eventLoop() throws IOException {
        try (final var serverSocketChannel = ServerSocketChannel.open();
             final var socketChannel = serverSocketChannel.bind(new InetSocketAddress(serverPort));
             final var selector = Selector.open()) {

            serverSocketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (serverSocketChannel.isOpen()) {
                var eventCount = selector.select(TimeUnit.SECONDS.toMillis(this.selectTimeoutSeconds));

                LOGGER.trace("selected for {} events", eventCount);

                for (var selectedKey : selector.selectedKeys()) {
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

    private void handleReadableKey(final SelectionKey readSelectedKey) {
        var peerIndex = (Integer) readSelectedKey.attachment();

        LOGGER.trace("peer {} is readable", peerIndex);

        var clientChannel = peerIdxToChannel.get(peerIndex);

        if (clientChannel == null) {
            LOGGER.warn("null channel returned from readable key selection, updating peer idx to channel map");
            evictPeer(readSelectedKey);
            return;
        }

        try {
            var bytesRead = clientChannel.read(incomingBuffer);

            if (bytesRead == -1) {
                LOGGER.warn("detected end of stream while reading from peer {}", peerIndex);

                evictPeer(readSelectedKey);
            } else if (bytesRead > 0) {
                LOGGER.debug("received message from peer {} with {} bytes", peerIndex, bytesRead);

                var incomingBytes = new byte[bytesRead];
                incomingBuffer.flip();
                incomingBuffer.get(incomingBytes, 0, bytesRead);
                incomingBuffer.rewind();
                var incoming = new String(incomingBytes);

                if (messageList.size() >= messageListCap) {
                    final var rightExtent = peerIdxToPosition
                            .values()
                            .stream()
                            .min(Integer::compare)
                            .orElse(messageListCap);

                    final var leftOver = new ArrayList<>(messageList.subList(rightExtent, messageListCap));

                    LOGGER.debug("maximum of {} was hit, copying {} left over messages to the beginning",
                            messageListCap, leftOver.size());

                    messageList.clear();
                    messageList.addAll(leftOver);
                    peerIdxToPosition.keySet().forEach(peer -> peerIdxToPosition.put(peer, 0));
                }

                messageList.add(incoming);
            } else if (bytesRead == 0) {
                LOGGER.trace("received read event but read returned 0");
            }
        } catch (final IOException ioe) {
            LOGGER.warn("failed to read from peer {}", peerIndex, ioe);

            evictPeer(readSelectedKey);
        }
    }

    private void handleAcceptableKey(final ServerSocketChannel serverSocketChannel, final Selector selector)
            throws IOException {

        var clientChannel = serverSocketChannel.accept();

        if (clientChannel == null) {
            LOGGER.trace("ignoring null returned from acceptable key selection");
            return;
        }

        clientChannel.configureBlocking(false);

        final var peerKey = clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        peerIdxToChannel.put(peerCounter.get(), clientChannel);

        peerKey.attach(peerCounter.getAndIncrement());

        LOGGER.info("new connection, peer counter is now {}", peerCounter);
    }


    private void handleWritableKey(final SelectionKey writeSelectedKey) {
        var peerIndex = (Integer) writeSelectedKey.attachment();

        LOGGER.trace("peer {} is writable", peerIndex);

        final SocketChannel clientChannel = peerIdxToChannel.get(peerIndex);

        if (clientChannel == null) {
            LOGGER.warn("null channel retrieved from writable event with index {}, evicting peer", peerIndex);

            evictPeer(writeSelectedKey);

            return;
        }

        try {
            var currentPeerPosition = peerIdxToPosition.get(peerIndex);

            if (currentPeerPosition == null) {
                currentPeerPosition = 0;
                peerIdxToPosition.put(peerIndex, 0);
            }

            if (currentPeerPosition < messageList.size()) {
                var nextMessageBytes = messageList.get(currentPeerPosition).getBytes();
                var written = clientChannel.write(ByteBuffer.wrap(nextMessageBytes));

                LOGGER.debug("wrote {} to peer {} with position {}", written, peerIndex, currentPeerPosition);

                peerIdxToPosition.put(peerIndex, currentPeerPosition + 1);
            } else {
                var bytesWritten = clientChannel.write(ByteBuffer.wrap(noNewDataMessage.getBytes()));

                LOGGER.trace("wrote {} to caught-up peer", bytesWritten);
            }
        } catch (final IOException ioe) {
            LOGGER.warn("failed to write to peer with index {}", peerIndex);

            evictPeer(writeSelectedKey);
        }
    }

    private void evictPeer(final SelectionKey selectedKey) {
        var peerIndex = (Integer) selectedKey.attachment();

        selectedKey.cancel();

        if (peerIndex == null) {
            LOGGER.warn("null index key attachment");

            return;
        }

        var removedChannel = peerIdxToChannel.remove(peerIndex);

        var removedPosition = peerIdxToPosition.remove(peerIndex);

        LOGGER.warn("evicted peer with channel {} and position {}", removedChannel, removedPosition);

        if (removedChannel.isConnected()) {
            try {
                removedChannel.close();
            } catch (final IOException ioe) {
                LOGGER.warn("failed to close channel while evicting peer with index {}", peerIndex, ioe);
            }
        }
    }
}
