package name.maxdeliso.micron.looper.write;

import java.nio.channels.SelectionKey;
import name.maxdeliso.micron.peer.Peer;

public interface WriteHandler {
  boolean handleWritablePeer(final SelectionKey key, final Peer peer);
}
