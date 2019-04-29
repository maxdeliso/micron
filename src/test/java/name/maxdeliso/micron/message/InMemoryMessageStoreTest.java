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
}
