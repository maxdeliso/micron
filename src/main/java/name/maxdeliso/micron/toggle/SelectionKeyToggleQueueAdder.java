package name.maxdeliso.micron.toggle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
public class SelectionKeyToggleQueueAdder implements ToggleQueueAdder {

  private final Duration enableDuration;
  private final AtomicReference<Selector> selectorAtomicReference;
  private final DelayQueue<DelayedToggle> toggleDelayQueue;

  @Override
  public void disableAndEnqueueEnableInterest(final SelectionKey key, final int mask) {
    try {
      if ((key.interestOpsAnd(~mask) & mask) == mask) {
        enqueueEnableInterest(key, mask);
      } else {
        log.trace("clearing interest ops had no effect on key {}", key);
      }
    } catch (final CancelledKeyException cke) {
      log.warn("key was cancelled", cke);
    }
  }

  @Override
  public void enqueueEnableInterest(final SelectionKey key, final int mask) {
    var delayedEnableToggle = new DelayedToggle(
        selectorAtomicReference,
        enableDuration,
        key,
        mask);

    toggleDelayQueue.add(delayedEnableToggle);
  }
}
