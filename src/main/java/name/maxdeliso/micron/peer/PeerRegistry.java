package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;
import java.util.Optional;

public interface PeerRegistry {
  Optional<InMemoryPeer> get(int index);

  InMemoryPeer allocatePeer(SocketChannel clientChannel);

  void evictPeer(InMemoryPeer peer);

  long size();

  int getReadOrder(InMemoryPeer peer);
}
