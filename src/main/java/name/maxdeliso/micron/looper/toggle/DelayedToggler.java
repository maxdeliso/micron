package name.maxdeliso.micron.looper.toggle;

import java.util.concurrent.DelayQueue;
import lombok.extern.slf4j.Slf4j;

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
