package name.maxdeliso.micron.handler.write;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;

@Slf4j
@RequiredArgsConstructor
public class SerialWriteHandler implements WriteHandler {

  private final RingBufferMessageStore messageStore;
  private final SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder;
  private final PeerRegistry peerRegistry;
  private final Charset charset;

  @Override
  public boolean handleWritablePeer(final SelectionKey key, final Peer peer) {
    selectionKeyToggleQueueAdder.disableAndEnqueueEnableInterest(key, SelectionKey.OP_WRITE);

    if (peer.position() == messageStore.position()) {
      log.trace("nothing to write, peer {} is caught up", peer);
      return false;
    }

    try {
      final var messageToWriteOpt = messageStore.get(peer.position());
      final var newPosition = peer.advancePosition();

      log.trace("updated the position of peer {} position to {}", peer, newPosition);

      if (messageToWriteOpt.isEmpty()) {
        log.trace("nothing to write, message store is empty at position {}", peer.position());
        return false;
      }

      final var messageToWrite = messageToWriteOpt.get();
      final var bytesToWrite = messageToWrite.getBytes(charset);
      final var bufferToWrite = ByteBuffer.wrap(bytesToWrite);
      final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);

      log.trace("wrote {} bytes to peer {} to advance to {}", bytesWritten, peer, newPosition);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }

    return true;
  }
}
