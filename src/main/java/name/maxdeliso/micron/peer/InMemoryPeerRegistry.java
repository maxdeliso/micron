package name.maxdeliso.micron.peer;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
    final var initialPosition = slotManager.nextNotSet(ringBufferMessageStore.position());
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

  @Override
  public int getReadOrder(final Peer peer) {
    return peerMap
        .values()
        .stream()
        .sorted(Comparator.comparingLong(Peer::getNetBytesRX))
        .collect(Collectors.toList())
        .indexOf(peer);
  }

}
