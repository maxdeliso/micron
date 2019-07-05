package name.maxdeliso.micron.peer;

public interface RingPositionTracker {
  int advancePosition(final int max);

  int position();
}
