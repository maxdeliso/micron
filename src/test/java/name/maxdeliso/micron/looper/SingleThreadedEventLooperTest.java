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
import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class SingleThreadedEventLooperTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedEventLooperTest.class);

    private static final int TEST_BUFFER_SIZE = 1;

    private static final int TEST_SELECT_TIMEOUT_SECONDS = 1;

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
                .messageCharset(StandardCharsets.UTF_8)
                .peerRegistry(peerRegistry)
                .messageStore(messageStore)
                .selectorProvider(selectorProvider)
                .build();
    }

    private Thread buildStarterThread(final SingleThreadedEventLooper looper) {
        return new Thread(() -> {
            try {
                looper.loop();
            } catch (final IOException ioe) {
                LOGGER.warn("I/O exception while looping", ioe);
            }
        });
    }

    private Thread buildJoinerThread(final SingleThreadedEventLooper looper) {
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
        final var starterThread = buildStarterThread(singleThreadedEventLooper);
        final var joinerThread = buildJoinerThread(singleThreadedEventLooper);

        joinSerially(starterThread, joinerThread);
    }
}
