package name.maxdeliso.micron.toggle;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SelectionKeyToggleQueueAdder {

  private final Duration enableDuration;
  private final AtomicReference<Selector> selectorAtomicReference;
  private final DelayQueue<DelayedToggle> toggleDelayQueue;

  public void disableAndEnqueueEnableInterest(final SelectionKey key, final int mask, int weight) {
    try {
      if ((key.interestOpsAnd(~mask) & mask) == mask) {
        enqueueEnableInterest(key, mask, weight);
      } else {
        log.trace("clearing interest ops had no effect on key {}", key);
      }
    } catch (final CancelledKeyException cke) {
      log.warn("key was cancelled", cke);
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
