package name.maxdeliso.micron.toggle;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public record SelectionKeyToggleQueueAdder(Duration enableDuration,
                                           AtomicReference<Selector> selectorAtomicReference,
                                           DelayQueue<DelayedToggle> toggleDelayQueue) {

  private static final Logger LOG = LoggerFactory.getLogger(SelectionKeyToggleQueueAdder.class);

  public void disableAndEnqueueEnableInterest(final SelectionKey key, final int mask, int weight) {
    try {
      if ((key.interestOpsAnd(~mask) & mask) == mask) {
        enqueueEnableInterest(key, mask, weight);
      } else {
        LOG.trace("clearing interest ops had no effect on key {}", key);
      }
    } catch (final CancelledKeyException cke) {
      LOG.warn("key was cancelled", cke);
    }
  }

  private void enqueueEnableInterest(final SelectionKey key, final int mask, int weight) {
    var delayedEnableToggle = new DelayedToggle(
        selectorAtomicReference,
        enableDuration.multipliedBy(weight),
        key,
        mask);

    toggleDelayQueue.add(delayedEnableToggle);
  }

  public void enqueueEnableInterest(SelectionKey acceptSelectionKey, int opAccept) {
    enqueueEnableInterest(acceptSelectionKey, opAccept, 1);
  }
}
