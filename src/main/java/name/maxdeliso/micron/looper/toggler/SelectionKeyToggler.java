package name.maxdeliso.micron.looper.toggler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
public class SelectionKeyToggler implements AsyncToggler {

  private final Random random;
  private final int asyncEnableTimeoutMs;
  private final AtomicReference<Selector> selectorAtomicReference;

  @Override
  public CompletableFuture<Void> asyncFlip(final SelectionKey key, final int mask) {
    try {
      if ((key.interestOpsAnd(~mask) & mask) == mask) {
        return asyncEnable(key, mask);
      } else {
        log.trace("clearing interest ops had no effect on key {}", key);
      }
    } catch (final CancelledKeyException cke) {
      log.warn("key was cancelled", cke);
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> asyncEnable(final SelectionKey key, final int mask) {
    return CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep((asyncEnableTimeoutMs + random.nextInt(asyncEnableTimeoutMs) / 2));
        key.interestOpsOr(mask);
        selectorAtomicReference.get().wakeup();
      } catch (final CancelledKeyException cke) {
        log.trace("key was cancelled while toggling async interest ops", cke);
      } catch (final InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    });
  }
}
