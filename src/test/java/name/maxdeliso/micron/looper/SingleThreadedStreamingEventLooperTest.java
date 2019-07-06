package name.maxdeliso.micron.looper;

import name.maxdeliso.micron.looper.toggle.DelayedToggle;
import name.maxdeliso.micron.message.RingBufferMessageStore;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.DelayQueue;

@RunWith(MockitoJUnitRunner.class)
public class SingleThreadedStreamingEventLooperTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedStreamingEventLooperTest.class);

  private static final int TEST_BUFFER_SIZE = 1;

  private static final int TEST_ASYNC_ENABLE_MS = 1;

  private static final Random random = new Random();

  private SingleThreadedStreamingEventLooper singleThreadedStreamingEventLooper;

  @Mock
  private SocketAddress socketAddress;

  @Mock
  private PeerRegistry peerRegistry;

  @Mock
  private RingBufferMessageStore messageStore;

  @Mock
  private DelayQueue<DelayedToggle> delayedToggles;

  @Mock
  private Duration duration;

  private TestSelectorProvider selectorProvider;

  @Before
  public void buildLooper() {
    selectorProvider = new TestSelectorProvider();

    new SingleThreadedStreamingEventLooper(
        socketAddress,
        StandardCharsets.UTF_8,
        peerRegistry,
        messageStore,
        selectorProvider,
        ByteBuffer.allocateDirect(TEST_BUFFER_SIZE),
        delayedToggles,
        duration
    );
  }

  private Thread buildStarterThread(final SingleThreadedStreamingEventLooper looper) {
    return new Thread(() -> {
      try {
        looper.loop();
      } catch (final IOException ioe) {
        LOGGER.warn("I/O exception while looping", ioe);
      }
    });
  }

  private Thread buildJoinerThread(final SingleThreadedStreamingEventLooper looper) {
    return new Thread(() -> {
      try {
        looper.halt();
      } catch (final InterruptedException ie) {
        LOGGER.warn("exception while halting", ie);
      }
    });
  }

  private void joinSerially(final Thread... threads) {
    LOGGER.trace("attempting join of {} threads", threads.length);

    Arrays.stream(threads).forEach(thread -> {
      try {
        thread.join();
      } catch (final InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    });

    LOGGER.trace("joins completed normally");
  }

  @Test
  public void testLoopStartsAndStops() {
    final var starterThread = buildStarterThread(singleThreadedStreamingEventLooper);
    final var joinerThread = buildJoinerThread(singleThreadedStreamingEventLooper);

    joinSerially(starterThread, joinerThread);
  }
}
