package name.maxdeliso.micron;

import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.SingleThreadedStreamingEventLooper;
import name.maxdeliso.micron.looper.toggle.DelayedToggle;
import name.maxdeliso.micron.looper.toggle.DelayedToggler;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;

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

    final var asyncEnableDuration = Duration.ofMillis(1);

    final var random = new Random();

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
            random
        );

    final var toggleExecutor = Executors.newSingleThreadExecutor();
    final var delayToggler = new DelayedToggler(toggleDelayQueue);

    toggleExecutor.execute(delayToggler);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        log.trace("sending halt to looper...");

        looper.halt();
      } catch (final InterruptedException ie) {
        log.warn("exception while halting looper", ie);
      }
    }));

    try {
      looper.loop();
    } catch (final IOException ioe) {
      log.error("terminated exceptionally", ioe);
    }
  }
}
