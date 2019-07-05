package name.maxdeliso.micron.message;

import lombok.RequiredArgsConstructor;
import name.maxdeliso.micron.peer.PeerRegistry;
import net.jcip.annotations.ThreadSafe;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@ThreadSafe
public final class InMemoryMessageStore implements RingBufferMessageStore {

  private final int maxMessages;

  private final List<String> messages;

  private final PeerRegistry peerRegistry;

  private final AtomicInteger position = new AtomicInteger(0);

  /**
   * Construct an in memory message store.
   *
   * @param maxMessages  the total number of events to hold in memory at once.
   * @param peerRegistry a registry of peers.
   */
  public InMemoryMessageStore(final int maxMessages, final PeerRegistry peerRegistry) {
    this.maxMessages = maxMessages;
    this.messages = Arrays.asList(new String[maxMessages]);
    this.peerRegistry = peerRegistry;
  }

  @Override
  public boolean add(final String received) {
    synchronized (this.messages) {
      int currentPosition = position.get();
      int nextPosition = (currentPosition + 1) % maxMessages;

      // if moving c would overwrite a peer's position, then data would be dropped, so fail
      if (peerRegistry.positionOccupied(nextPosition)) {
        return false;
      } else {
        this.messages.set(currentPosition, received);
        position.set(nextPosition);
        return true;
      }
    }
  }

  @Override
  public String get(final int messageIdx) {
    synchronized (this.messages) {
      return messages.get(messageIdx);
    }
  }

  @Override
  public int position() {
    return position.get();
  }

  @Override
  public int size() {
    return this.maxMessages;
  }
}
