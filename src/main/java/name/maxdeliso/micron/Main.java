package name.maxdeliso.micron;

import com.beust.jcommander.JCommander;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import name.maxdeliso.micron.looper.EventLooper;
import name.maxdeliso.micron.looper.SynchronousEventStreamLooper;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.params.Arguments;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;
import name.maxdeliso.micron.slots.InMemorySlotManager;
import name.maxdeliso.micron.slots.SlotManager;
import name.maxdeliso.micron.toggle.DelayedToggle;
import name.maxdeliso.micron.toggle.DelayedToggler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(final String[] args) throws InterruptedException {
    final var arguments = new Arguments();

    final var jcommander = JCommander.newBuilder().addObject(arguments).build();

    jcommander.parse(args);

    if (arguments.isHelp()) {
      jcommander.usage();

      return;
    }

    if (arguments.isVerbose()) {
      Configurator.setRootLevel(Level.TRACE);
    }

    final SlotManager slotManager =
        new InMemorySlotManager(arguments.getMaxMessages());

    final RingBufferMessageStore messageStore =
        new InMemoryMessageStore(slotManager, arguments.getBufferSize());

    final var peerRegistry =
        new InMemoryPeerRegistry(slotManager, messageStore);

    final var incomingBuffer = ByteBuffer.allocateDirect(arguments.getBufferSize());

    final var toggleDelayQueue = new DelayQueue<DelayedToggle>();

    final var metrics = new MetricRegistry();

    final var reporter = Slf4jReporter
        .forRegistry(metrics)
        .outputTo(LoggerFactory.getLogger("name.maxdeliso.micron.metrics"))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.NANOSECONDS)
        .build();

    reporter.start(1, TimeUnit.SECONDS);

    final EventLooper looper =
        new SynchronousEventStreamLooper(
            new InetSocketAddress(arguments.getPort()),
            peerRegistry,
            messageStore,
            SelectorProvider.provider(),
            incomingBuffer,
            toggleDelayQueue,
            Duration.ofMillis(arguments.getBackoffDurationMillis()),
            metrics
        );

    final var toggleExecutor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("delay-queue-%d")
            .setUncaughtExceptionHandler(
                (thread, throwable) -> {
                  log.error("delay queue thread {} failed", thread, throwable);
                  looper.halt();
                }
            ).build());

    final var delayToggler = new DelayedToggler(toggleDelayQueue);

    toggleExecutor.execute(delayToggler);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.trace("sending halt to looper...");
      looper.halt();
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
}
