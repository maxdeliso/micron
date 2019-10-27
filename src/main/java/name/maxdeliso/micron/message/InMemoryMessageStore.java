package name.maxdeliso.micron.message;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.slots.SlotManager;
import net.jcip.annotations.ThreadSafe;

@Slf4j
@RequiredArgsConstructor
@ThreadSafe
public final class InMemoryMessageStore implements RingBufferMessageStore {

  private final List<byte[]> messages;

  private final AtomicInteger position = new AtomicInteger(0);

  private final int messageSize;

  public InMemoryMessageStore(final SlotManager slotManager, final int messageSize) {
    this.messages = Arrays.asList(new byte[slotManager.size()][messageSize]);
    this.messageSize = messageSize;
  }

  @Override
  public boolean add(final byte[] received) {
    if (received.length > messageSize) {
      log.warn("message received ({}) was larger than maximum of {}", received.length, messageSize);
      return false;
    }

    synchronized (this.messages) {
      final int currentPosition = position.get();
      final int nextPosition = (currentPosition + 1) % this.messages.size();
      this.messages.set(currentPosition, received);
      position.set(nextPosition); // update position of ring buffer message store
      return true;
    }
  }

  @Override
  public byte[] get(final int messageIdx) {
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
    return this.messages.size();
  }

}
