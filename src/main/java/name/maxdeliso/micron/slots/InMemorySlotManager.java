package name.maxdeliso.micron.slots;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    final String ocs =
        IntStream
            .range(0, occupationCounts.length())
            .boxed()
            .filter(idx -> occupationCounts.get(idx) > 0)
            .map(idx -> String.format("%d @ %d", occupationCounts.get(idx), idx))
            .collect(Collectors.joining(","));

    return "InMemorySlotManager{occupationCounts=[" + ocs + "]}";
  }
}
