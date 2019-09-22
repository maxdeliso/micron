package name.maxdeliso.micron.handler.read;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;

@Slf4j
@RequiredArgsConstructor
public class SerialReadHandler implements ReadHandler {

  private final ByteBuffer incomingBuffer;
  private final PeerRegistry peerRegistry;
  private final RingBufferMessageStore messageStore;
  private final SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder;

  @Override
  public boolean handleReadablePeer(final SelectionKey key, final Peer peer) {
    selectionKeyToggleQueueAdder.disableAndEnqueueEnableInterest(key, SelectionKey.OP_READ);

    final int bytesRead = performRead(peer);
    final byte[] incomingBytes;

    if (bytesRead == 0) {
      log.trace("read no bytes from peer {}", peer);

      incomingBytes = null;
    } else {
      log.trace("read {} bytes from peer {}", bytesRead, peer);

      incomingBytes = new byte[bytesRead];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesRead);
      incomingBuffer.rewind();
    }

    return Optional.ofNullable(incomingBytes).map(messageStore::add).orElse(false);
  }

  private int performRead(final Peer peer) {
    var evictPeer = false;
    final int finalBytesRead;

    try {
      final var socketChannel = peer.getSocketChannel();

      final var bytesRead = socketChannel.read(incomingBuffer);

      if (bytesRead == -1) {
        evictPeer = true;
        finalBytesRead = 0;
      } else {
        finalBytesRead = bytesRead;
      }
    } catch (final IOException ioe) {
      log.warn("io error while reading from peer, so marking for eviction", ioe);
      evictPeer = true;
      return 0;
    } finally {
      if (evictPeer) {
        peerRegistry.evictPeer(peer);
      }
    }

    return finalBytesRead;
  }
}
