package name.maxdeliso.micron.message;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.slots.SlotManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryMessageStoreTest {

  private static final int TEST_MESSAGE_COUNT = 8;

  private static final byte[] TEST_MESSAGE = "test-message".getBytes();

  private static final Integer TEST_SIZE = 8;

  private static final Integer TEST_MESSAGE_SIZE = 128;

  @Mock
  private PeerRegistry peerRegistry;

  @Mock
  private SlotManager slotManager;

  private RingBufferMessageStore messageStore;

  @Before
  public void setup() {
    when(slotManager.size()).thenReturn(TEST_SIZE);
    when(slotManager.positionOccupied(anyInt())).thenReturn(false);

    messageStore = new InMemoryMessageStore(slotManager, TEST_MESSAGE_SIZE);
  }

  @Test
  public void testPutGet() {
    messageStore.add(TEST_MESSAGE);

    final byte[] message = messageStore.get(0);

    assertArrayEquals(message, TEST_MESSAGE);
  }

  @Test
  public void testEmptyGet() {
    final byte[] message = messageStore.get(0);

    assertTrue(message.length == TEST_MESSAGE_SIZE);

    for (int i = 0; i < TEST_MESSAGE_SIZE; i++) {
      assertEquals(message[i], 0);
    }
  }

  @Test
  public void testSingleProducerOverwrite() {
    // fill buffer with strings of the form 0, 1, 2 ..., one past its capacity
    for (int i = 0; i < TEST_MESSAGE_COUNT + 1; i++) {
      messageStore.add(String.valueOf(i).getBytes());
    }

    // the last write should wrap around to the beginning
    assertArrayEquals(messageStore.get(0), String.valueOf(TEST_MESSAGE_COUNT).getBytes());
  }

  @Test
  public void testMultipleProducerOverflow() {
    when(slotManager.positionOccupied(1)).thenReturn(true);

    assertFalse(messageStore.add("not-added".getBytes()));
  }
}
