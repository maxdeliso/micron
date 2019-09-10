package name.maxdeliso.micron.slots;

import java.util.concurrent.atomic.AtomicIntegerArray;

public class InMemorySlotManager implements SlotManager {
  private final AtomicIntegerArray occupationCounts;

  public InMemorySlotManager(int size) {
    occupationCounts = new AtomicIntegerArray(size);
  }

  @Override
  public boolean positionOccupied(int pos) {
    return occupationCounts.get(pos) > 0;
  }

  @Override
  public void incrementOccupants(int pos) {
    occupationCounts.incrementAndGet(pos);
  }

  @Override
  public void decrementOccupants(int pos) {
    occupationCounts.decrementAndGet(pos);
  }

  @Override
  public int nextNotSet(int pos) {
    int i = pos;

    do {
      i = (i + 1) % size();
    } while (i != pos && occupationCounts.get(i) == 0);

    return i;
  }

  @Override
  public int size() {
    return occupationCounts.length();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    int matches = 0;

    for (int i = 0; i < occupationCounts.length(); i++) {
      final int count = occupationCounts.get(i);

      if (count > 0) {
        if (matches > 0) {
          sb.append(", ");
        }

        sb.append(count);
        sb.append(" @ ");
        sb.append(i);
        matches++;
      }
    }

    return "InMemorySlotManager{occupationCounts=" + sb.toString() + '}';
  }
}
