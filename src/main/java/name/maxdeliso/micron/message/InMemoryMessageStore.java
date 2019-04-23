package name.maxdeliso.micron.message;


import lombok.RequiredArgsConstructor;

import name.maxdeliso.micron.peer.PeerRegistry;

import net.jcip.annotations.ThreadSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@ThreadSafe
public final class InMemoryMessageStore implements MessageStore {

  private final int maxMessages;

  private final List<String> messages;

  private final PeerRegistry peerRegistry;

  /**
   * Construct an in memory message store.
   *
   * @param maxMessages  the total number of events to hold in memory at once.
   * @param peerRegistry a registry of peers.
   */
  public InMemoryMessageStore(final int maxMessages, final PeerRegistry peerRegistry) {
    this.maxMessages = maxMessages;
    this.messages = new ArrayList<>(maxMessages);
    this.peerRegistry = peerRegistry;
  }

  @Override
  public void add(final String received) {
    synchronized (this.messages) {
      if (messages.size() >= maxMessages) {
        rotateBuffer();
      }

      messages.add(received);
    }
  }

  @Override
  public Optional<String> get(final long messageIndex) {
    final Long messageIndexBoxed = messageIndex;

    synchronized (this.messages) {
      if (messageIndex < messages.size()) {
        final String message;

        message = messages.get(messageIndexBoxed.intValue());

        return Optional.of(message);
      } else {
        return Optional.empty();
      }
    }
  }

  private void rotateBuffer() {
    synchronized (this.messages) {
      final var minimumRightExtent = peerRegistry.minPosition().orElse(maxMessages);
      final var leftOver = new ArrayList<>(messages.subList(minimumRightExtent, maxMessages));

      messages.clear();
      messages.addAll(leftOver);
      peerRegistry.resetPositions();
    }
  }
}
