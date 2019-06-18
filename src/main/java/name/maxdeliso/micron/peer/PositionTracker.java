package name.maxdeliso.micron.peer;

public interface PositionTracker {
  int advancePosition();

  int advancePosition(int delta);

  void resetPosition();

  int getPosition();
}
