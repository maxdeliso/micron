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
import java.nio.channels.ServerSocketChannel;

@RunWith(MockitoJUnitRunner.class)
public class SingleThreadedEventLooperTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedEventLooperTest.class);
    private static final int TEST_BUFFER_SIZE = 1;
    private static final int TEST_SELECT_TIMEOUT_SECONDS = 1;
    private static final int TEST_MESSAGE_TEST_CAP = 1;
    private static final String TEST_NO_NEW_DATA_MESSAGE = "\b";
    private SingleThreadedEventLooper singleThreadedEventLooper;
    @Mock
    private SocketAddress socketAddress;

    @Mock
    private PeerRegistry peerRegistry;

    @Mock
    private MessageStore messageStore;

    @Mock
    private ServerSocketChannel serverSocketChannel;

    @Mock
    private ServerSocketChannel boundServerSocketChannel;

    private TestSelectorProvider selectorProvider;

    @Before
    public void buildLooper() {
        selectorProvider = new TestSelectorProvider();

        singleThreadedEventLooper = new SingleThreadedEventLooper(
                socketAddress,
                TEST_BUFFER_SIZE,
                TEST_SELECT_TIMEOUT_SECONDS,
                TEST_MESSAGE_TEST_CAP,
                TEST_NO_NEW_DATA_MESSAGE,
                peerRegistry,
                messageStore,
                selectorProvider);
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

        startThread.join();
        stopThread.join();
    }
}
