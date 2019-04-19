package name.maxdeliso.micron.peer;

import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ThreadSafe
public final class InMemoryPeerRegistry implements PeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPeerRegistry.class);

    private final AtomicLong peerCounter;

    private final ConcurrentHashMap<Long, Peer> peerMap;

    public InMemoryPeerRegistry() {
        this.peerCounter = new AtomicLong();
        this.peerMap = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<name.maxdeliso.micron.peer.Peer> get(final Long index) {
        return Optional.ofNullable(peerMap.get(index));
    }

    @Override
    public name.maxdeliso.micron.peer.Peer allocatePeer(final SocketChannel clientChannel) {
        final var newPeerNumber = peerCounter.get();
        final var newPeer = new name.maxdeliso.micron.peer.Peer(newPeerNumber, clientChannel);

        peerMap.put(newPeerNumber, newPeer);
        peerCounter.incrementAndGet();
        return newPeer;
    }

    @Override
    public Optional<Integer> minPosition() {
        return peerMap
                .values()
                .parallelStream()
                .map(name.maxdeliso.micron.peer.Peer::getPosition)
                .map(Math::toIntExact)
                .min(Integer::compare);
    }

    @Override
    public void resetPositions() {
        peerMap.values().parallelStream().forEach(name.maxdeliso.micron.peer.Peer::resetPosition);
    }

    @Override
    public void evictPeer(final name.maxdeliso.micron.peer.Peer peer) {
        try {
            peer.getSocketChannel().close();
        } catch (final IOException ioe) {
            LOGGER.warn("failed to close channel on peer eviction", ioe);
        } finally {
            peerMap.remove(peer.getIndex());
        }
    }
}
