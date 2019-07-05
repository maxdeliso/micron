package name.maxdeliso.micron.peer;

import name.maxdeliso.micron.support.TestSelectorProvider;
import name.maxdeliso.micron.support.TestSocketChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryPeerRegistryTest {

    private final int MAX_MESSAGES = 8;

    private SelectorProvider selectorProvider;

    private SocketChannel socketChannel;

    private PeerRegistry peerRegistry;

    @Before
    public void setup() {
        selectorProvider = new TestSelectorProvider();
        socketChannel = new TestSocketChannel(selectorProvider);
        peerRegistry = new InMemoryPeerRegistry();
    }

    @Test
    public void testPeerRegistryAssociatesSinglePeer() {
        peerRegistry.allocatePeer(socketChannel);

        final Optional<Peer> peerOpt = peerRegistry.get(0);

        assertTrue(peerOpt.isPresent());
        assertEquals(socketChannel, peerOpt.get().getSocketChannel());
    }

    @Test
    public void testSinglePeerPositionIsReturned() {
        peerRegistry.allocatePeer(socketChannel);

        final Optional<Peer> peerOpt = peerRegistry.get(0);

        assertTrue(peerOpt.isPresent());
    }

    @Test
    public void testSinglePeerAllocationAndEviction() {
        final Peer peer = peerRegistry.allocatePeer(socketChannel);

        peerRegistry.evictPeer(peer);

        assertFalse(peerRegistry.get(0).isPresent());
    }

    @Test
    public void testTwoPeersAreSequentiallyNumbered() {
        final var firstPeer = peerRegistry.allocatePeer(socketChannel);
        final var secondPeer = peerRegistry.allocatePeer(socketChannel);

        firstPeer.advancePosition(MAX_MESSAGES);
        secondPeer.advancePosition(MAX_MESSAGES);

        final Optional<Peer> firstPeerOpt = peerRegistry.get(0);
        final Optional<Peer> secondPeerOpt = peerRegistry.get(1);

        assertTrue(firstPeerOpt.isPresent());
        assertEquals(firstPeerOpt.get(), firstPeer);
        assertTrue(secondPeerOpt.isPresent());
        assertEquals(secondPeerOpt.get(), secondPeer);
    }
}
