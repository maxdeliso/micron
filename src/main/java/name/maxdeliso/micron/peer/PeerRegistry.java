package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;
import java.util.Optional;

public interface PeerRegistry {
    Optional<name.maxdeliso.micron.peer.Peer> get(Long index);

    name.maxdeliso.micron.peer.Peer allocatePeer(SocketChannel clientChannel);

    Optional<Integer> minPosition();

    void resetPositions();

    void evictPeer(name.maxdeliso.micron.peer.Peer peer);
}
