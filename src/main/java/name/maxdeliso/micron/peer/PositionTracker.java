package name.maxdeliso.micron.peer;

public interface PositionTracker {
  long advancePosition();

  long advancePosition(long delta);

  void resetPosition();

  long getPosition();
}
