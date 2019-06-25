package name.maxdeliso.micron.looper.read;

import name.maxdeliso.micron.peer.Peer;

import java.nio.channels.SelectionKey;

public interface ReadHandler {
  boolean handleReadablePeer(final SelectionKey key, final Peer peer);
}
