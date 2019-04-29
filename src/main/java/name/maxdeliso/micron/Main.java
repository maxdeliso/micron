package name.maxdeliso.micron;

import static java.lang.Runtime.getRuntime;

import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.looper.SingleThreadedEventLooper;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;

@Slf4j
final class Main {

  private static final int SERVER_PORT = 1337;

  private static final int BUFFER_SIZE = 512;

  private static final int MAX_MESSAGES = 8192;

  private static final int SELECT_TIMEOUT_SECONDS = 1;

  private static final String NO_NEW_DATA_MESSAGE = "\b";

  public static void main(final String[] args) {
    final var peerRegistry = new InMemoryPeerRegistry();

    final var messageStore = new InMemoryMessageStore(MAX_MESSAGES, peerRegistry);

    final var incomingBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    final var looper =
        SingleThreadedEventLooper.builder()
            .incomingBuffer(incomingBuffer)
            .messageCharset(StandardCharsets.UTF_8)
            .messageStore(messageStore)
            .noNewDataMessage(NO_NEW_DATA_MESSAGE)
            .peerRegistry(peerRegistry)
            .selectorProvider(SelectorProvider.provider())
            .selectTimeoutSeconds(SELECT_TIMEOUT_SECONDS)
            .socketAddress(new InetSocketAddress(SERVER_PORT))
            .build();

    getRuntime().addShutdownHook(new Thread(() -> {
      try {
        log.trace("sending halt to looper...");

        looper.halt();
      } catch (final InterruptedException | IOException exc) {
        log.warn("exception while halting looper", exc);
      }
    }));

    try {
      looper.loop();
    } catch (final IOException ioe) {
      log.error("terminated exceptionally", ioe);
    }
  }
}
