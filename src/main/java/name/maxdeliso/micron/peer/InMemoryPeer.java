package name.maxdeliso.micron.peer;

import com.google.common.base.MoreObjects;
import name.maxdeliso.micron.slots.SlotManager;

import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryPeer implements Peer {

  int index;

  AtomicInteger position;

  SocketChannel socketChannel;

  SlotManager slotManager;

  AtomicLong netBytesRX;

  AtomicLong netBytesTX;

  /**
   * Allocate a peer.
   *
   * @param index           the peer index.
   * @param initialPosition the initial position of the peer.
   * @param socketChannel   a socket channel corresponding to the peer.
   * @param slotManager     a manager of slots, to keep track of what slots the peer occupies.
   */
  public InMemoryPeer(final int index,
                      final int initialPosition,
                      final SocketChannel socketChannel,
                      final SlotManager slotManager) {
    this.index = index;
    this.position = new AtomicInteger(initialPosition);
    this.socketChannel = socketChannel;
    this.slotManager = slotManager;
    this.netBytesRX = new AtomicLong(0);
    this.netBytesTX = new AtomicLong(0);

    slotManager.incrementOccupants(initialPosition);
  }

  @Override
  public int advancePosition() {
    int prevPosition = position.get();
    int newPosition = position.updateAndGet(pos -> (pos + 1) % slotManager.size());
    slotManager.decrementOccupants(prevPosition);
    slotManager.incrementOccupants(newPosition);
    return newPosition;
  }

  @Override
  public int index() { return index; }

  @Override
  public int position() {
    return this.position.get();
  }

  @Override
  public void countBytesRx(long bytes) {
    this.netBytesRX.addAndGet(bytes);
  }

  @Override
  public void countBytesTx(long bytes) {
    this.netBytesTX.addAndGet(bytes);
  }

  @Override
  public long netBytesRX() {
    return netBytesRX.get();
  }

  @Override
  public SocketChannel socketChannel() { return socketChannel; }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("index", index)
        .add("position", position)
        .add("socketChannel", socketChannel)
        .add("slotManager", slotManager)
        .add("netBytesRX", netBytesRX)
        .add("netBytesTX", netBytesTX)
        .toString();
  }
}
