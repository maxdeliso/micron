package name.maxdeliso.micron;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.SingleThreadedStreamingEventLooper;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;
import name.maxdeliso.micron.slots.InMemorySlotManager;
import name.maxdeliso.micron.toggle.DelayedToggle;
import name.maxdeliso.micron.toggle.DelayedToggler;
import org.slf4j.LoggerFactory;

@Slf4j
final class Main {

  /**
   * Port to listen on.
   */
  private static final int SERVER_PORT = 1337;

  /**
   * Maximum size of a single read operation.
   */
  private static final int BUFFER_SIZE = 128;

  /**
   * Maximum number of messages to hold in memory.
   */
  private static final int MAX_MESSAGES = 4096;

  /**
   * How long to wait to handle another event after handling one for the same selection key.
   */
  private static final Duration ASYNC_ENABLE_DURATION = Duration.ofMillis(1);

  public static void main(final String[] args) throws InterruptedException {
    final var slotManager = new InMemorySlotManager(MAX_MESSAGES);

    // needs a way to check if a peer is in a position
    final var messageStore = new InMemoryMessageStore(slotManager);

    // needs to check what the current message store position is
    // needs message store to initialize new peers at the right point in the ring buffer
    final var peerRegistry = new InMemoryPeerRegistry(slotManager, messageStore);

    final var incomingBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    final var toggleDelayQueue = new DelayQueue<DelayedToggle>();

    final var metrics = new MetricRegistry();

    final var reporter = Slf4jReporter.forRegistry(metrics)
        .outputTo(LoggerFactory.getLogger("name.maxdeliso.micron.metrics"))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.NANOSECONDS)
        .build();

    reporter.start(1, TimeUnit.SECONDS);

    final var looper =
        new SingleThreadedStreamingEventLooper(
            new InetSocketAddress(SERVER_PORT),
            StandardCharsets.UTF_8,
            peerRegistry,
            messageStore,
            SelectorProvider.provider(),
            incomingBuffer,
            toggleDelayQueue,
            ASYNC_ENABLE_DURATION,
            metrics
        );

    final var toggleExecutor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("delay-queue-%d")
            .setUncaughtExceptionHandler(
                (thread, throwable) -> {
                  log.error("delay queue thread {} failed", thread, throwable);

                  try {
                    looper.halt();
                  } catch (InterruptedException ie) {
                    interruptFailure(ie);
                  }
                }
            ).build());

    final var delayToggler = new DelayedToggler(toggleDelayQueue);

    toggleExecutor.execute(delayToggler);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        log.trace("sending halt to looper...");

        looper.halt();
      } catch (final InterruptedException ie) {
        interruptFailure(ie);
      }
    }));

    try {
      looper.loop();
    } catch (final IOException ioe) {
      log.error("terminated exceptionally", ioe);
    } finally {
      if (!toggleExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
        log.warn("toggle executor not shut down within time interval");
        toggleExecutor.shutdownNow();
      }
    }
  }

  private static void interruptFailure(final InterruptedException ie) {
    Thread.currentThread().interrupt();
    log.error("interrupted", ie);
    throw new RuntimeException(ie);
  }
}
