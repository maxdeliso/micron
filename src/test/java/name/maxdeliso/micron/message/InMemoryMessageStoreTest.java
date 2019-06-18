package name.maxdeliso.micron.message;

import name.maxdeliso.micron.peer.PeerRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

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

    private MessageStore messageStore;

    @Before
    public void buildMessageStore() {
        messageStore = new InMemoryMessageStore(TEST_MESSAGE_COUNT, peerRegistry);
    }

    @Test
    public void testPutGet() {
        messageStore.add(TEST_MESSAGE);

        final Optional<String> messageOpt = messageStore.get(0);

        assertTrue(messageOpt.isPresent());
        assertEquals(messageOpt.get(), TEST_MESSAGE);
    }

    @Test
    public void testEmptyGet() {
        final Optional<String> messageOpt = messageStore.get(0);

        assertFalse(messageOpt.isPresent());
    }

    @Test
    public void testSingleProducerOverflowRotation() {
        // min position is after all messages -> min reader is caught up at the end of the buffer
        when(peerRegistry.minPosition()).thenReturn(Optional.of(TEST_MESSAGE_COUNT));

        // fill buffer with strings of the form 0, 1, 2 ..., one past its capacity
        for (int i = 0; i < TEST_MESSAGE_COUNT + 1; i++) {
            messageStore.add(String.valueOf(i));
        }

        assertTrue(messageStore.get(0).isPresent());
        assertEquals(messageStore.get(0).get(), String.valueOf(TEST_MESSAGE_COUNT));
        assertFalse(messageStore.get(1).isPresent());
    }

    @Test
    public void testMultipleProducerOverflow() {
        // min position is zero -> min reader is at beginning of buffer
        when(peerRegistry.minPosition()).thenReturn(Optional.of(0));
        when(peerRegistry.maxPosition()).thenReturn(Optional.of(TEST_MESSAGE_COUNT)); // after last

        // fill buffer with n strings of the form 0, 1, 2 ... (n - 1)
        for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
            final boolean added = messageStore.add(String.valueOf(i));
            assertTrue(added);
        }

        // attempt to insert n + 1th message, observe failure
        assertFalse(messageStore.add("next"));

        // check first message is present and equal to "0"
        assertTrue(messageStore.get(0).isPresent());
        assertEquals(messageStore.get(0).get(), "0");

        // check last message is present and equal to (n - 1) as a string
        assertTrue(messageStore.get(TEST_MESSAGE_COUNT - 1).isPresent());
        assertEquals(messageStore.get(TEST_MESSAGE_COUNT - 1).get(),
                String.valueOf(TEST_MESSAGE_COUNT - 1));
    }
}
