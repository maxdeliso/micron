package name.maxdeliso.micron.toggle;

import lombok.extern.slf4j.Slf4j;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class DelayedToggle implements Delayed {

  private final AtomicReference<Selector> selectorAtomicReference;

  private final long fireTime;

  private final SelectionKey selectionKey;

  private final int mask;

  /**
   * Build a delay toggle.
   *
   * @param selectorAtomicReference reference to a selector.
   * @param toggleDuration          how long to wait to perform the toggle.
   * @param selectionKey            the key to toggle.
   * @param mask                    the mask to toggle with.
   */
  public DelayedToggle(final AtomicReference<Selector> selectorAtomicReference,
                       final Duration toggleDuration,
                       final SelectionKey selectionKey,
                       final int mask) {
    this.selectorAtomicReference = selectorAtomicReference;
    this.selectionKey = selectionKey;
    this.mask = mask;
    final long deltaNanos = toggleDuration.toNanos();
    this.fireTime = System.nanoTime() + deltaNanos;
  }

  @Override
  public long getDelay(final TimeUnit unit) {
    final long nanosRemaining = fireTime - System.nanoTime();
    return unit.convert(nanosRemaining, unit);
  }

  @Override
  public int compareTo(final Delayed other) {
    if (other instanceof DelayedToggle) {
      return Long.compare(fireTime, ((DelayedToggle) other).fireTime);
    } else {
      return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }
  }

  /**
   * Perform the toggle operation, to flip the interest ops, after a delay.
   */
  public void toggle() {
    try {
      selectionKey.interestOpsOr(mask);
      selectorAtomicReference.get().wakeup();
    } catch (final CancelledKeyException cke) {
      log.trace("detected cancelled key while toggling async interest ops", cke);
    }
  }
}
