package name.maxdeliso.micron.looper.toggle;

import java.nio.channels.SelectionKey;

public interface ToggleQueueAdder {
  void disableAndEnqueueEnable(final SelectionKey key, final int mask);

  void enqueueEnable(final SelectionKey key, final int mask);
}
