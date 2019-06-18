package name.maxdeliso.micron.peer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jcip.annotations.ThreadSafe;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
@ThreadSafe
public final class InMemoryPeerRegistry implements PeerRegistry {

  private final AtomicInteger peerCounter;

  private final ConcurrentHashMap<Integer, Peer> peerMap;

  public InMemoryPeerRegistry() {
    this.peerCounter = new AtomicInteger();
    this.peerMap = new ConcurrentHashMap<>();
  }

  @Override
  public Optional<Peer> get(final int index) {
    return Optional.ofNullable(peerMap.get(index));
  }

  @Override
  public Peer allocatePeer(final SocketChannel socketChannel) {
    final var newPeerNumber = peerCounter.get();
    final var newPeer = new Peer(newPeerNumber, socketChannel);

    peerMap.put(newPeerNumber, newPeer);
    peerCounter.incrementAndGet();
    return newPeer;
  }

  @Override
  public Optional<Integer> minPosition() {
    return peerMap
        .values()
        .parallelStream()
        .map(Peer::getPosition)
        .min(Integer::compare);
  }

  @Override
  public Optional<Integer> maxPosition() {
    return peerMap
        .values()
        .parallelStream()
        .map(Peer::getPosition)
        .max(Integer::compare);
  }

  @Override
  public void resetPositions() {
    peerMap.values()
        .parallelStream()
        .forEach(Peer::resetPosition);
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
      log.warn("failed to close channel during peer eviction", ioe);
    } finally {
      peerMap.remove(peer.getIndex());
    }
  }
}
