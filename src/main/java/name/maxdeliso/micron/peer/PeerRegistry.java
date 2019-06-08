package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;
import java.util.Optional;

public interface PeerRegistry {
  Optional<Peer> get(Long index);

  Peer allocatePeer(SocketChannel clientChannel);

  Optional<Integer> minPosition();

  Optional<Integer> maxPosition();

  void resetPositions();

  void evictPeer(Peer peer);
}
