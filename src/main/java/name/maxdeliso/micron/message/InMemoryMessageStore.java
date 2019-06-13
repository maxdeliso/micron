package name.maxdeliso.micron.message;


import lombok.RequiredArgsConstructor;
import name.maxdeliso.micron.peer.PeerRegistry;
import net.jcip.annotations.ThreadSafe;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
  public boolean add(final String received) {
    synchronized (this.messages) {
      if (messages.size() == maxMessages) {
        rotateBuffer();
      }

      if (messages.size() == maxMessages) {
        // after rotation, still no space, so return false for failed add
        return false;
      } else {
        messages.add(received);
        // add succeeded, so return true
        return true;
      }
    }
  }

  @Override
  public Optional<String> get(final long messageIndex) {
    synchronized (this.messages) {
      if (messageIndex < messages.size()) {
        final String message;

        message = messages.get(Math.toIntExact(messageIndex));

        return Optional.of(message);
      } else {
        return Optional.empty();
      }
    }
  }

  @Override
  public Stream<String> getFrom(final long messageIndex) {
    synchronized (this.messages) {
      final List<String> messageBuffer =
          new LinkedList<>(messages.subList(Math.toIntExact(messageIndex), messages.size()));

      return messageBuffer.stream();
    }
  }

  private void rotateBuffer() {
    final var minimumRightExtent =
        Math.min(peerRegistry.minPosition().orElse(maxMessages), maxMessages);

    final var maximumRightExtent =
        Math.max(peerRegistry.maxPosition().orElse(0), minimumRightExtent);

    final var leftOver = new ArrayList<>(messages.subList(minimumRightExtent, maximumRightExtent));

    messages.clear();
    messages.addAll(leftOver);
    peerRegistry.resetPositions();
  }
}
