package name.maxdeliso.micron.looper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.MessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class ReadHandler {

  private final ByteBuffer incomingBuffer;
  private final Charset messageCharset;
  private final PeerRegistry peerRegistry;
  private final MessageStore messageStore;
  private final SelectionKeyToggler selectionKeyToggler;

  public boolean handleReadablePeer(final SelectionKey key, final Peer peer) {
    final PeerReadResult peerReadResult = performRead(peer);
    final String incoming;

    if (peerReadResult.getBytesReadTotal() == 0) {
      incoming = null;

      log.trace("read no bytes from peer {}", peer);
    } else {
      log.trace("read {} bytes from peer {} in {} operations",
          peerReadResult.getBytesReadTotal(), peer, peerReadResult.getReadCalls());

      final var bytesReadTotal = peerReadResult.getBytesReadTotal();
      final var incomingBytes = new byte[bytesReadTotal];
      incomingBuffer.flip();
      incomingBuffer.get(incomingBytes, 0, bytesReadTotal);
      incomingBuffer.rewind();
      incoming = new String(incomingBytes, messageCharset);
    }

    selectionKeyToggler.toggleMaskAsync(key, SelectionKey.OP_READ);

    return Optional
        .ofNullable(incoming)
        .map(messageStore::add)
        .orElse(false);
  }

  private PeerReadResult performRead(final Peer peer) {
    var readCalls = 0;
    var bytesReadTotal = 0;
    var evictPeer = false;

    try {
      final var socketChannel = peer.getSocketChannel();
      var doneReading = false;

      do {
        final var bytesRead = socketChannel.read(incomingBuffer);

        readCalls++;

        if (bytesRead == 0) {
          doneReading = true;
        } else if (bytesRead == -1) {
          doneReading = true;
          evictPeer = true;
        } else {
          bytesReadTotal += bytesRead;
        }
      } while (!doneReading);
    } catch (final IOException ioe) {
      log.warn("io error while reading from peer, so marking for eviction", ioe);

      evictPeer = true;
    } finally {
      if (evictPeer) {
        peerRegistry.evictPeer(peer);

        log.warn("received end of stream from peer {}", peer);
      }
    }

    return PeerReadResult
        .builder()
        .bytesReadTotal(bytesReadTotal)
        .readCalls(readCalls)
        .build();
  }
}
