package name.maxdeliso.micron.selector;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;

public interface PeerCountingReadWriteSelector {
  default void associatePeer(final SocketChannel socketChannel,
                             final SelectionKey peerKey,
                             final PeerRegistry peerRegistry) {
    final var peer = peerRegistry.allocatePeer(socketChannel);
    peerKey.attach(peer.getIndex());
  }

  /**
   * Look up peer in registry using a selection event.
   *
   * @param selectionKey a key corresponding to a selection.
   * @param peerRegistry a registry to look up peer information.
   * @return optionally, the peer corresponding to the selection.
   */
  default Optional<Peer> lookupPeer(
      final SelectionKey selectionKey,
      final PeerRegistry peerRegistry) {
    return Optional.ofNullable(selectionKey)
        .map(key -> (Integer) key.attachment())
        .flatMap(peerRegistry::get);
  }

  default void handleReadableKey(
      final SelectionKey readSelectedKey,
      final PeerRegistry peerRegistry,
      final Consumer<Peer> peerConsumer) {
    lookupPeer(readSelectedKey, peerRegistry)
        .ifPresent(peerConsumer);
  }

  default void handleWritableKey(
      final SelectionKey writeSelectedKey,
      final PeerRegistry peerRegistry,
      final BiConsumer<SelectionKey, Peer> peerConsumer) {
    lookupPeer(writeSelectedKey, peerRegistry)
        .ifPresent(peer -> peerConsumer.accept(writeSelectedKey, peer));
  }
}
