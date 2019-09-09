package name.maxdeliso.micron.handler.write;

import name.maxdeliso.micron.peer.Peer;

import java.nio.channels.SelectionKey;

public interface WriteHandler {
  boolean handleWritablePeer(final SelectionKey key, final Peer peer);
}
