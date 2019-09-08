package name.maxdeliso.micron.toggle;

import java.nio.channels.SelectionKey;

public interface ToggleQueueAdder {
  void disableAndEnqueueEnableInterest(final SelectionKey key, final int mask);

  void enqueueEnableInterest(final SelectionKey key, final int mask);
}
