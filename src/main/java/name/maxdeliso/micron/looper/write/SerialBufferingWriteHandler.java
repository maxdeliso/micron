package name.maxdeliso.micron.looper.write;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.toggle.SelectionKeyToggleQueueAdder;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;

@Slf4j
@RequiredArgsConstructor
public class SerialBufferingWriteHandler implements WriteHandler {

  private final RingBufferMessageStore messageStore;
  private final SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder;
  private final PeerRegistry peerRegistry;
  private final Charset charset;

  @Override
  public boolean handleWritablePeer(final SelectionKey key, final Peer peer) {
    if (peer.position() == messageStore.position()) {
      log.trace("nothing to write, peer is caught up");
      return false;
    }

    try {
      final var messageToWrite = messageStore.get(peer.position());
      final var bytesToWrite = messageToWrite.getBytes(charset);
      final var bufferToWrite = ByteBuffer.wrap(bytesToWrite);
      final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);
      final var newPosition = peer.advancePosition(messageStore.size());

      log.trace("wrote {} bytes to peer {} to advance to {}", bytesWritten, peer, newPosition);

      selectionKeyToggleQueueAdder.disableAndEnqueueEnable(key, SelectionKey.OP_WRITE);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }

    return true;
  }
}
