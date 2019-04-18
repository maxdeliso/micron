package name.maxdeliso.peer;

import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ThreadSafe
public final class PeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRegistry.class);

    private final AtomicLong peerCounter;

    private final ConcurrentHashMap<Long, Peer> peerMap;

    public PeerRegistry() {
        this.peerCounter = new AtomicLong();
        this.peerMap = new ConcurrentHashMap<>();
    }

    public Optional<Peer> get(final Long index) {
        return Optional.ofNullable(peerMap.get(index));
    }

    public Peer allocatePeer(final SocketChannel clientChannel) {
        final var newPeerNumber = peerCounter.get();
        final var newPeer = new Peer(newPeerNumber, clientChannel);

        peerMap.put(newPeerNumber, newPeer);
        peerCounter.incrementAndGet();
        return newPeer;
    }

    public Optional<Integer> minPosition() {
        return peerMap
                .values()
                .parallelStream()
                .map(Peer::getPosition)
                .map(Math::toIntExact)
                .min(Integer::compare);
    }

    public void resetPositions() {
        peerMap.values().parallelStream().forEach(Peer::resetPosition);
    }

    public void evictPeer(final Peer peer) {
        try {
            peer.getSocketChannel().close();
        } catch (final IOException ioe) {
            LOGGER.warn("failed to close channel on peer eviction", ioe);
        } finally {
            peerMap.remove(peer.getIndex());
        }
    }
}
