package name.maxdeliso.micron.looper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.DelayQueue;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.peer.InMemoryPeer;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.support.TestSelectorProvider;
import name.maxdeliso.micron.toggle.DelayedToggle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SynchronousEventStreamLooperTest {
  private static final int TEST_BUFFER_SIZE = 1;

  @Mock
  MetricRegistry metricRegistry;

  @Mock
  private SocketAddress socketAddress;

  @Mock
  private PeerRegistry<InMemoryPeer> peerRegistry;

  @Mock
  private RingBufferMessageStore messageStore;

  @Mock
  private DelayQueue<DelayedToggle> delayedToggles;

  @Mock
  private Meter meter;

  private SynchronousEventStreamLooper synchronousEventStreamLooper;

  @Before
  public void buildLooper() {
    Duration duration = Duration.ZERO;

    TestSelectorProvider selectorProvider = new TestSelectorProvider();

    when(metricRegistry.meter(anyString())).thenReturn(meter);

    synchronousEventStreamLooper = new SynchronousEventStreamLooper(
        socketAddress,
        peerRegistry,
        messageStore,
        selectorProvider,
        ByteBuffer.allocateDirect(TEST_BUFFER_SIZE),
        delayedToggles,
        duration,
        metricRegistry
    );
  }

  private Thread buildStarterThread(final SynchronousEventStreamLooper looper) {
    return new Thread(() -> {
      try {
        looper.loop();
      } catch (final IOException ioe) {
        log.warn("I/O exception while looping", ioe);
      }
    });
  }

  private Thread buildJoinerThread(final SynchronousEventStreamLooper looper) {
    return new Thread(() -> {
      log.trace("sending halt");
      while (!looper.halt()) {
        log.warn("sending additional halt");
      }
    });
  }

  private void joinSerially(final Thread... threads) {
    log.trace("attempting join of {} threads", threads.length);

    Arrays.stream(threads).forEach(thread -> {
      try {
        thread.join();
      } catch (final InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    });

    log.trace("joins completed normally");
  }

  @Test
  public void testLoopStartsAndStops() {
    final var starterThread = buildStarterThread(synchronousEventStreamLooper);
    final var joinerThread = buildJoinerThread(synchronousEventStreamLooper);
    starterThread.start();
    joinerThread.start();
    joinSerially(starterThread, joinerThread);
  }

  @Test
  public void testLoopStartsAndStopsInverted() {
    final var starterThread = buildStarterThread(synchronousEventStreamLooper);
    final var joinerThreadFirst = buildJoinerThread(synchronousEventStreamLooper);
    final var joinerThreadSecond = buildJoinerThread(synchronousEventStreamLooper);

    joinerThreadFirst.start();
    starterThread.start();
    joinerThreadSecond.start();
    joinSerially(joinerThreadFirst, starterThread, joinerThreadSecond);
  }
}
