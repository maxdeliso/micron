package name.maxdeliso.micron.handler.read;

import java.nio.channels.SelectionKey;
import name.maxdeliso.micron.peer.InMemoryPeer;

public interface ReadHandler {
  boolean handleReadablePeer(final SelectionKey key, final InMemoryPeer peer);
}
