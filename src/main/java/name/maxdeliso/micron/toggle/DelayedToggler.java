package name.maxdeliso.micron.toggle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.DelayQueue;

public record DelayedToggler(
    DelayQueue<DelayedToggle> delayedToggles) implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(DelayedToggler.class);

  @Override
  public void run() {
    while (true) {
      try {
        final DelayedToggle delayedToggle = delayedToggles.take();
        delayedToggle.toggle();
      } catch (final InterruptedException ie) {
        LOG.warn("interrupted while waiting for a delayed toggle", ie);
        break;
      }
    }
  }
}
