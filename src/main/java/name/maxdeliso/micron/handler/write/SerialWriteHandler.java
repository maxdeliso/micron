package name.maxdeliso.micron.handler.write;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;

@Slf4j
@RequiredArgsConstructor
public class SerialWriteHandler implements WriteHandler {

  private final RingBufferMessageStore messageStore;
  private final SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder;
  private final PeerRegistry<InMemoryPeer> peerRegistry;

  @Override
  public boolean handleWritablePeer(final SelectionKey key, final InMemoryPeer peer) {
    final int writeOrder = peerRegistry.getReadOrder(peer);

    selectionKeyToggleQueueAdder
        .disableAndEnqueueEnableInterest(key, SelectionKey.OP_WRITE, 1 + writeOrder);

    if (peer.position() == messageStore.position()) {
      log.trace("nothing to write, peer {} is caught up", peer);
      return false;
    }

    try {
      final var messageToWrite = messageStore.get(peer.position());
      final var newPosition = peer.advancePosition();

      log.trace("updated the position of peer {} position to {}", peer, newPosition);

      if (messageToWrite.length == 0) {
        log.trace("nothing to write, message store is empty at position {}", peer.position());
        return false;
      }

      final var bufferToWrite = ByteBuffer.wrap(messageToWrite);
      final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);
      peer.countBytesTx(bytesWritten);

      log.trace("wrote {} bytes to peer {} to advance to {}", bytesWritten, peer, newPosition);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }

    return true;
  }
}
