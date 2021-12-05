package name.maxdeliso.micron.handler.write;

import java.nio.channels.SelectionKey;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.Peer;

public interface WriteHandler {
  void handleWritablePeer(final SelectionKey key, final Peer peer);
}
