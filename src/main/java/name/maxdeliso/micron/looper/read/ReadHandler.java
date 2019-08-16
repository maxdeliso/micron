package name.maxdeliso.micron.looper.read;

import java.nio.channels.SelectionKey;
import name.maxdeliso.micron.peer.Peer;

public interface ReadHandler {
  boolean handleReadablePeer(final SelectionKey key, final Peer peer);
}
