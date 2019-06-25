package name.maxdeliso.micron.looper.toggler;

import java.nio.channels.SelectionKey;
import java.util.concurrent.CompletableFuture;

public interface AsyncToggler {
  CompletableFuture<Void> asyncFlip(final SelectionKey key, final int mask);

  CompletableFuture<Void> asyncEnable(final SelectionKey key, final int mask);
}
