package name.maxdeliso.micron.looper.toggle;

import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.EventLooper;

import java.io.IOException;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DelayedToggler implements Runnable {

  private final DelayQueue<DelayedToggle> toggles;

  public DelayedToggler(final DelayQueue<DelayedToggle> toggles) {
    this.toggles = toggles;
  }

  @Override
  public void run() {
    while (true) {
      try {
        final DelayedToggle delayedToggle = toggles.take();
        delayedToggle.toggle();
      } catch (final InterruptedException ie) {
        log.warn("interrupted while waiting for a delayed toggle", ie);
        break;
      }
    }
  }
}
