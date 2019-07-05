package name.maxdeliso.micron.message;

import name.maxdeliso.micron.peer.PeerRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryMessageStoreTest {

    private static final int TEST_MESSAGE_COUNT = 8;

    private static final String TEST_MESSAGE = "test-message";

    @Mock
    private PeerRegistry peerRegistry;

    private RingBufferMessageStore messageStore;

    @Before
    public void buildMessageStore() {
        messageStore = new InMemoryMessageStore(TEST_MESSAGE_COUNT, peerRegistry);
    }

    @Test
    public void testPutGet() {
        messageStore.add(TEST_MESSAGE);

        final String message = messageStore.get(0);

        assertEquals(message, TEST_MESSAGE);
    }

    @Test
    public void testEmptyGet() {
        final String message = messageStore.get(0);

        assertNull(message);
    }

    @Test
    public void testSingleProducerOverwrite() {
        // fill buffer with strings of the form 0, 1, 2 ..., one past its capacity
        for (int i = 0; i < TEST_MESSAGE_COUNT + 1; i++) {
            messageStore.add(String.valueOf(i));
        }

        // the last write should wrap around to the beginning
        assertEquals(messageStore.get(0), String.valueOf(TEST_MESSAGE_COUNT));
    }

    @Test
    public void testMultipleProducerOverflow() {
        when(peerRegistry.positionOccupied(1)).thenReturn(true);

        assertFalse(messageStore.add("not-added"));
    }
}
