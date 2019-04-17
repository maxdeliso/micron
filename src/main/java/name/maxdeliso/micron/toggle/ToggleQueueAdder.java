package name.maxdeliso.micron.toggle;

import java.nio.channels.SelectionKey;

public interface ToggleQueueAdder {
  void disableAndEnqueueEnableInterest(SelectionKey key, int mask, int weight);

  void enqueueEnableInterest(SelectionKey key, int mask, int weight);
}
