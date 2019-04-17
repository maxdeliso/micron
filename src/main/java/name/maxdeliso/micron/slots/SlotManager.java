package name.maxdeliso.micron.slots;

public interface SlotManager {
  boolean positionOccupied(int pos);

  void incrementOccupants(int pos);

  void decrementOccupants(int pos);

  int nextNotSet(int pos);

  int size();
}
