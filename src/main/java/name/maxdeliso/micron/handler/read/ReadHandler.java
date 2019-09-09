package name.maxdeliso.micron.handler.read;

import name.maxdeliso.micron.peer.Peer;

import java.nio.channels.SelectionKey;

public interface ReadHandler {
  boolean handleReadablePeer(final SelectionKey key, final Peer peer);
}
