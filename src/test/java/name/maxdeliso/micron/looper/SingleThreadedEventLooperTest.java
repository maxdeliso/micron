package name.maxdeliso.micron.looper;


import name.maxdeliso.micron.message.MessageStore;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.support.TestSelectorProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@RunWith(MockitoJUnitRunner.class)
public class SingleThreadedEventLooperTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedEventLooperTest.class);

  private static final int TEST_BUFFER_SIZE = 1;

  private static final int TEST_SELECT_TIMEOUT_SECONDS = 1;

  private static final String TEST_NO_NEW_DATA_MESSAGE = "\b";

  private SingleThreadedEventLooper singleThreadedEventLooper;

  @Mock
  private SocketAddress socketAddress;

  @Mock
  private PeerRegistry peerRegistry;

  @Mock
  private MessageStore messageStore;

  private TestSelectorProvider selectorProvider;

  @Before
  public void buildLooper() {
    selectorProvider = new TestSelectorProvider();

    singleThreadedEventLooper = SingleThreadedEventLooper
        .builder()
        .socketAddress(socketAddress)
        .incomingBuffer(ByteBuffer.allocateDirect(TEST_BUFFER_SIZE))
        .selectTimeoutSeconds(TEST_SELECT_TIMEOUT_SECONDS)
        .noNewDataMessage(TEST_NO_NEW_DATA_MESSAGE)
        .messageCharset(StandardCharsets.UTF_8)
        .peerRegistry(peerRegistry)
        .messageStore(messageStore)
        .selectorProvider(selectorProvider)
        .build();
  }

  @Test
  public void testLoopStartsAndStops() throws InterruptedException {
    final var startThread = new Thread(() -> {
      try {
        singleThreadedEventLooper.loop();
      } catch (final IOException ioe) {
        LOGGER.warn("I/O exception while looping", ioe);
      }
    });

    final var stopThread = new Thread(() -> {
      try {
        singleThreadedEventLooper.halt();
      } catch (final InterruptedException | IOException exc) {
        LOGGER.warn("exception while halting", exc);
      }
    });

    LOGGER.trace("attempting joins...");
    startThread.join();
    stopThread.join();
    LOGGER.trace("... joins completed");
  }
}
