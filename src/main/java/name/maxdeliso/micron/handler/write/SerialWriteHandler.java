package name.maxdeliso.micron.handler.write;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SerialWriteHandler(RingBufferMessageStore messageStore,
                                 SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder,
                                 PeerRegistry<InMemoryPeer> peerRegistry) implements WriteHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SerialWriteHandler.class);

  @Override
  public void handleWritablePeer(final SelectionKey key, final InMemoryPeer peer) {
    final int writeOrder = peerRegistry.getReadOrder(peer);

    selectionKeyToggleQueueAdder
        .disableAndEnqueueEnableInterest(key, SelectionKey.OP_WRITE, 1 + writeOrder);

    if (peer.position() == messageStore.position()) {
      LOG.trace("nothing to write, peer {} is caught up", peer);
    }

    try {
      final var messageToWrite = messageStore.get(peer.position());
      final var newPosition = peer.advancePosition();

      LOG.trace("updated the position of peer {} position to {}", peer, newPosition);

      if (messageToWrite.length == 0) {
        LOG.trace("nothing to write, message store is empty at position {}", peer.position());
      }

      final var bufferToWrite = ByteBuffer.wrap(messageToWrite);
      final var bytesWritten = peer.socketChannel().write(bufferToWrite);
      peer.countBytesTx(bytesWritten);

      LOG.trace("wrote {} bytes to peer {} to advance to {}", bytesWritten, peer, newPosition);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      LOG.warn("failed to write to peer {}", peer, ioe);
    }
  }
}
