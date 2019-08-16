package name.maxdeliso.micron.message;

import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.slots.SlotManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryMessageStoreTest {

  private static final int TEST_MESSAGE_COUNT = 8;

  private static final String TEST_MESSAGE = "test-message";

  private static final Integer TEST_SIZE = 8;

  @Mock
  private PeerRegistry peerRegistry;

  @Mock
  private SlotManager slotManager;

  private RingBufferMessageStore messageStore;

  @Before
  public void setup() {
    when(slotManager.size()).thenReturn(TEST_SIZE);
    when(slotManager.positionOccupied(anyInt())).thenReturn(false);

    messageStore = new InMemoryMessageStore(slotManager);
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

    assertTrue(messageOpt.isEmpty());
  }

  @Test
  public void testSingleProducerOverwrite() {
    // fill buffer with strings of the form 0, 1, 2 ..., one past its capacity
    for (int i = 0; i < TEST_MESSAGE_COUNT + 1; i++) {
      messageStore.add(String.valueOf(i));
    }

    // the last write should wrap around to the beginning
    assertEquals(messageStore.get(0).get(), String.valueOf(TEST_MESSAGE_COUNT));
  }

  @Test
  public void testMultipleProducerOverflow() {
    when(slotManager.positionOccupied(1)).thenReturn(true);

    assertFalse(messageStore.add("not-added"));
  }
}
