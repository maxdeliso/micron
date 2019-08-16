package name.maxdeliso.micron.peer;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import name.maxdeliso.micron.message.RingBufferMessageStore;
import name.maxdeliso.micron.slots.SlotManager;
import net.jcip.annotations.ThreadSafe;

@Slf4j
@RequiredArgsConstructor
@ThreadSafe
public final class InMemoryPeerRegistry implements PeerRegistry {

  private final AtomicInteger peerCounter;

  private final ConcurrentHashMap<Integer, Peer> peerMap;

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

    /*
     * the "caught-up" logic states that a peer is caught up when it has reached the current
     * position, so by setting the initial position to the current one plus one, the new peer may
     * be able to read the entire ring buffer by reading new messages, so long as reading events
     * are prioritized over writes
     */
    final var initialPosition = (ringBufferMessageStore.position() + 1)
        % ringBufferMessageStore.size();
    final var newPeer = new Peer(newPeerNumber, initialPosition, socketChannel, slotManager);

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
      peer.getSocketChannel().close();
    } catch (final IOException ioe) {
      log.warn("failed to close channel during peer eviction of {}", peer, ioe);
    } finally {
      peerMap.remove(peer.getIndex());
      slotManager.decrementOccupants(peer.getPosition().get());
    }
  }

  @Override
  public long size() {
    return peerMap.size();
  }
}
