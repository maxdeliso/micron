package name.maxdeliso.micron.handler.read;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Optional;

import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.toggle.SelectionKeyToggleQueueAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SerialReadHandler(ByteBuffer incomingBuffer,
                                PeerRegistry<InMemoryPeer> peerRegistry,
                                RingBufferMessageStore messageStore,
                                SelectionKeyToggleQueueAdder selectionKeyToggleQueueAdder) implements ReadHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SerialReadHandler.class);

  @Override
  public void handleReadablePeer(final SelectionKey key, final InMemoryPeer peer) {
    final int readOrder = peerRegistry.getReadOrder(peer);

    selectionKeyToggleQueueAdder
        .disableAndEnqueueEnableInterest(key, SelectionKey.OP_READ, 1 + readOrder);

    LOG.trace("handling read for peer {} with order {}", peer, readOrder);

    final int bytesRead = performRead(peer);
    final byte[] incomingBytes;

    if (bytesRead == 0) {
      LOG.trace("read no bytes from peer {}", peer);

      incomingBytes = null;
    } else {
      LOG.trace("read {} bytes from peer {}", bytesRead, peer);

      incomingBytes = new byte[bytesRead];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesRead);
      incomingBuffer.rewind();
      peer.countBytesRx(incomingBytes.length);
    }

    Optional.ofNullable(incomingBytes).map(messageStore::add);
  }

  private int performRead(final InMemoryPeer peer) {
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
      LOG.warn("io error while reading from peer, so marking for eviction", ioe);
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
