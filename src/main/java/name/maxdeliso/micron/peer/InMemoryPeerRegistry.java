package name.maxdeliso.micron.peer;

import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.slots.SlotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryPeerRegistry implements PeerRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryPeerRegistry.class);

  private final AtomicInteger peerCounter;

  private final ConcurrentHashMap<Integer, InMemoryPeer> peerMap;

  private final RingBufferMessageStore ringBufferMessageStore;

  private final SlotManager slotManager;

  public InMemoryPeerRegistry(
      SlotManager slotManager,
      RingBufferMessageStore ringBufferMessageStore) {
    this.peerCounter = new AtomicInteger();
    this.peerMap = new ConcurrentHashMap<>();
    this.slotManager = slotManager;
    this.ringBufferMessageStore = ringBufferMessageStore;
  }

  @Override
  public Optional<Peer> get(final int index) {
    return Optional.ofNullable(peerMap.get(index));
  }

  @Override
  public Peer allocatePeer(final SocketChannel socketChannel) {
    final var newPeerNumber = peerCounter.get();
    final var initialPosition = slotManager.nextNotSet(ringBufferMessageStore.position());
    final var newPeer = new InMemoryPeer(newPeerNumber, initialPosition, socketChannel, slotManager);
    peerMap.put(newPeerNumber, newPeer);
    peerCounter.incrementAndGet();
    return newPeer;
  }

  /**
   * Evicts a peer from the peer map using its id.
   *
   * @param peer peer to be evicted.
   */
  @Override
  public void evictPeer(final Peer peer) {
    try {
      peer.socketChannel().close();
    } catch (final IOException ioe) {
      LOG.warn("failed to close channel during peer eviction of {}", peer, ioe);
    } finally {
      peerMap.remove(peer.index());
      slotManager.decrementOccupants(peer.position());
    }
  }

  @Override
  public long size() {
    return peerMap.size();
  }

  @Override
  public int getReadOrder(final Peer peer) {
    return peerMap
        .values()
        .stream()
        .sorted(Comparator.comparingLong(InMemoryPeer::netBytesRX))
        .toList()
        .indexOf(peer);
  }
}
