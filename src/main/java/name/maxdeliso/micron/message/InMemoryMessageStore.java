package name.maxdeliso.micron.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.slots.SlotManager;
import net.jcip.annotations.ThreadSafe;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@ThreadSafe
public final class InMemoryMessageStore implements RingBufferMessageStore {

  private final List<String> messages;

  private final AtomicInteger position = new AtomicInteger(0);

  private final SlotManager slotManager;

  public InMemoryMessageStore(final SlotManager slotManager) {
    this.messages = Arrays.asList(new String[slotManager.size()]);
    this.slotManager = slotManager;
  }

  @Override
  public boolean add(final String received) {
    synchronized (this.messages) {
      int currentPosition = position.get();
      int nextPosition = (currentPosition + 1) % this.messages.size();

      // if moving c would overwrite a peer's position, then data would be dropped, so fail
      if (slotManager.positionOccupied(nextPosition)) {
        log.warn("dropping message of length {} at position {} due to overflow",
            received.length(), nextPosition);
        return false;
      } else {
        this.messages.set(currentPosition, received);
        position.set(nextPosition); // update position of ring buffer message store
        return true;
      }
    }
  }

  @Override
  public Optional<String> get(final int messageIdx) {
    synchronized (this.messages) {
      return Optional.ofNullable(messages.get(messageIdx));
    }
  }

  @Override
  public int position() {
    return position.get();
  }

  @Override
  public int size() {
    return this.messages.size();
  }
}
