package name.maxdeliso.micron.slots;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemorySlotManager implements SlotManager {
  private final List<AtomicInteger> occupationCounts;

  public InMemorySlotManager(int size) {
    occupationCounts = new ArrayList<>(size);

    for (int i = 0; i < size; i++) {
      occupationCounts.add(new AtomicInteger(0));
    }
  }

  @Override
  public boolean positionOccupied(int pos) {
    return occupationCounts.get(pos).get() > 0;
  }

  @Override
  public void incrementOccupants(int pos) {
    occupationCounts.get(pos).incrementAndGet();
  }

  @Override
  public void decrementOccupants(int pos) {
    occupationCounts.get(pos).decrementAndGet();
  }

  @Override
  public int size() {
    return occupationCounts.size();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    int matches = 0;

    for (int i = 0; i < occupationCounts.size(); i++) {
      final AtomicInteger occupationCount = occupationCounts.get(i);

      if (occupationCount.get() > 0) {
        if (matches > 0) {
          sb.append(", ");
        }

        sb.append(occupationCount.get());
        sb.append(" @ ");
        sb.append(i);
        matches++;
      }
    }

    return "InMemorySlotManager{occupationCounts=" + sb.toString() + '}';
  }
}
