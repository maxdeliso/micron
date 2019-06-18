package name.maxdeliso.micron;

import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.SingleThreadedEventLooper;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;

import static java.lang.Runtime.getRuntime;

@Slf4j
final class Main {

  private static final int SERVER_PORT = 1337;

  /**
   * Maximum size of a single read operation.
   * Messages larger than this size will be truncated.
   */
  private static final int BUFFER_SIZE = 512;

  /**
   * Maximum number of messages to hold in memory.
   * When the threshold is hit, the minimum position
   * of all currently connected peers is used to determine
   * the subsection of messages that it's safe to discard.
   */
  private static final int MAX_MESSAGES = 8192;

  private static final String NO_NEW_DATA_MESSAGE = "\0";

  public static void main(final String[] args) {
    final var peerRegistry = new InMemoryPeerRegistry();

    final var messageStore = new InMemoryMessageStore(MAX_MESSAGES, peerRegistry);

    final var incomingBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    final var looper =
        SingleThreadedEventLooper.builder()
            .incomingBuffer(incomingBuffer)
            .messageCharset(StandardCharsets.UTF_8)
            .messageStore(messageStore)
            .peerRegistry(peerRegistry)
            .selectorProvider(SelectorProvider.provider())
            .socketAddress(new InetSocketAddress(SERVER_PORT))
            .build();

    getRuntime().addShutdownHook(new Thread(() -> {
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
