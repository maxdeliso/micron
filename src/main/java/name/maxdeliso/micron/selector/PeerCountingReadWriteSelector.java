package name.maxdeliso.micron.selector;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.PeerRegistry;

public interface PeerCountingReadWriteSelector {
  void associatePeer(final SocketChannel socketChannel,
                     final SelectionKey peerKey,
                     final PeerRegistry peerRegistry);

  Optional<InMemoryPeer> lookupPeer(
      final SelectionKey selectionKey,
      final PeerRegistry peerRegistry);

  void handleReadableKey(
      final SelectionKey readSelectedKey,
      final PeerRegistry peerRegistry,
      final Consumer<InMemoryPeer> peerConsumer);

  void handleWritableKey(
      final SelectionKey writeSelectedKey,
      final PeerRegistry peerRegistry,
      final BiConsumer<SelectionKey, InMemoryPeer> peerConsumer);
}
