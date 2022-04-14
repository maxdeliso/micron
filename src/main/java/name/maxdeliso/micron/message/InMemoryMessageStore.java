package name.maxdeliso.micron.message;

import name.maxdeliso.micron.slots.SlotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryMessageStore implements RingBufferMessageStore {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryMessageStore.class);

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
      LOG.warn("message received ({}) was larger than maximum of {}", received.length, messageSize);
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

}
