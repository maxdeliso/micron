package name.maxdeliso.micron;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.SingleThreadedStreamingEventLooper;
import name.maxdeliso.micron.looper.toggle.DelayedToggle;
import name.maxdeliso.micron.looper.toggle.DelayedToggler;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Slf4j
final class Main {

  /**
   * Port to listen on.
   */
  private static final int SERVER_PORT = 1337;

  /**
   * Maximum size of a single read operation.
   */
  private static final int BUFFER_SIZE = 512;

  /**
   * Maximum number of messages to hold in memory.
   */
  private static final int MAX_MESSAGES = 1024;

  public static void main(final String[] args) {
    final var peerRegistry = new InMemoryPeerRegistry();

    final var messageStore = new InMemoryMessageStore(MAX_MESSAGES, peerRegistry);

    final var incomingBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    final var toggleDelayQueue = new DelayQueue<DelayedToggle>();

    final var asyncEnableDuration = Duration.ofNanos(1000);

    final var random = new Random();

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
            asyncEnableDuration,
            metrics,
            random
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
    }
  }

  private static void interruptFailure(final InterruptedException ie) {
    Thread.currentThread().interrupt();
    log.error("interrupted", ie);
    throw new RuntimeException(ie);
  }
}
