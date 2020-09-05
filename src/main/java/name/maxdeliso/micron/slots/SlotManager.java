package name.maxdeliso.micron.slots;

public interface SlotManager {
  void incrementOccupants(int pos);

  void decrementOccupants(int pos);

  int nextNotSet(int pos);

  int size();
}
