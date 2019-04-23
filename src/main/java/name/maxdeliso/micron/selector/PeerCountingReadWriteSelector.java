package name.maxdeliso.micron.selector;

import static java.util.Optional.ofNullable;

import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.function.Consumer;

public interface PeerCountingReadWriteSelector {
  default void associatePeer(final SocketChannel socketChannel,
                             final SelectionKey peerKey,
                             final PeerRegistry peerRegistry) {
    final var peer = peerRegistry.allocatePeer(socketChannel);
    peerKey.attach(peer.getIndex());
  }

  default Optional<Peer> lookupPeer(
      final SelectionKey selectionKey,
      final PeerRegistry peerRegistry) {
    return ofNullable(selectionKey).map(key -> (Long) key.attachment()).flatMap(peerRegistry::get);
  }

  default void handleReadableKey(
      final SelectionKey readSelectedKey,
      final PeerRegistry peerRegistry,
      final Consumer<Peer> peerConsumer) {
    lookupPeer(readSelectedKey, peerRegistry).ifPresent(peerConsumer);
  }

  default void handleWritableKey(
      final SelectionKey writeSelectedKey,
      final PeerRegistry peerRegistry,
      final Consumer<Peer> peerConsumer) {
    lookupPeer(writeSelectedKey, peerRegistry).ifPresent(peerConsumer);
  }
}
