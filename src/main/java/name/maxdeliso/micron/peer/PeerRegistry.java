package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;
import java.util.Optional;

public interface PeerRegistry<P> {
  Optional<P> get(int index);

  P allocatePeer(SocketChannel clientChannel);

  void evictPeer(P peer);

  long size();

  int getReadOrder(P peer);
}
