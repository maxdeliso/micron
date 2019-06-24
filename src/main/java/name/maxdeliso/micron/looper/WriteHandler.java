package name.maxdeliso.micron.looper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.MessageStore;
import name.maxdeliso.micron.peer.Peer;
import name.maxdeliso.micron.peer.PeerRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class WriteHandler {

  private final MessageStore messageStore;
  private final SelectionKeyToggler selectionKeyToggler;
  private final PeerRegistry peerRegistry;

  public void handleWritablePeer(final SelectionKey key, final Peer peer) {
    try {
      final Stream<String> messageStream = messageStore.getFrom(peer.getPosition());
      final AtomicInteger messageCount = new AtomicInteger(0);
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      messageStream
          .map(String::getBytes)
          .forEach(bytes -> {
            messageCount.incrementAndGet();
            byteArrayOutputStream.writeBytes(bytes);
          });

      if (messageCount.get() > 0) {
        final var bufferToWrite = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
        final var bytesWritten = peer.getSocketChannel().write(bufferToWrite);
        final var newPosition = peer.advancePosition(messageCount.get());

        log.trace("wrote {} bytes to peer {} to advance to {}", bytesWritten, peer, newPosition);
      }

      selectionKeyToggler.toggleMaskAsync(key, SelectionKey.OP_WRITE);
    } catch (final IOException ioe) {
      peerRegistry.evictPeer(peer);

      log.warn("failed to write to peer {}", peer, ioe);
    }
  }
}
