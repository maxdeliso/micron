package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;
import java.util.Optional;

public interface PeerRegistry {
  Optional<Peer> get(int index);

  Peer allocatePeer(SocketChannel clientChannel);

  void evictPeer(Peer peer);

  long size();

  int getReadOrder(Peer peer);
}
