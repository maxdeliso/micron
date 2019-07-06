package name.maxdeliso.micron.looper.toggle;

import lombok.extern.slf4j.Slf4j;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class DelayedToggle implements Delayed {

  private final AtomicReference<Selector> selectorAtomicReference;

  private final long fireTime;

  private final SelectionKey selectionKey;

  private final int mask;

  public DelayedToggle(final AtomicReference<Selector> selectorAtomicReference,
                       final long delta,
                       final TimeUnit deltaUnit,
                       final SelectionKey selectionKey,
                       final int mask) {
    this.selectorAtomicReference = selectorAtomicReference;
    final long deltaNanos = TimeUnit.NANOSECONDS.convert(delta, deltaUnit);
    this.fireTime = System.nanoTime() + deltaNanos;
    this.selectionKey = selectionKey;
    this.mask = mask;
  }

  public long fireTime() {
    return this.fireTime;
  }

  @Override
  public long getDelay(final TimeUnit unit) {
    final long nanosRemaining = fireTime - System.nanoTime();
    return unit.convert(nanosRemaining, unit);
  }

  @Override
  public int compareTo(final Delayed other) {
    final long fireDifference;

    if (other instanceof DelayedToggle) {
      fireDifference = fireTime - ((DelayedToggle) other).fireTime;
    } else {
      fireDifference = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
    }

    return Math.toIntExact(fireDifference);
  }

  public void toggle() {
    try {
      selectionKey.interestOpsOr(mask);
      selectorAtomicReference.get().wakeup();
    } catch (final CancelledKeyException cke) {
      log.trace("detected cancelled key while toggling async interest ops", cke);
    }
  }
}
