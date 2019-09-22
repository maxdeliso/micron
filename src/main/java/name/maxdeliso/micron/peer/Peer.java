package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Value;
import name.maxdeliso.micron.slots.SlotManager;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
@Value
public final class Peer implements RingPositionTracker {

  private final int index;

  private final AtomicInteger position;

  private final SocketChannel socketChannel;

  private final SlotManager slotManager;

  private final AtomicLong netBytesRX;

  private final AtomicLong netBytesTX;

  /**
   * Allocate a peer.
   *
   * @param index           the peer index.
   * @param initialPosition the initial position of the peer.
   * @param socketChannel   a socket channel corresponding to the peer.
   * @param slotManager     a manager of slots, to keep track of what slots the peer occupies.
   */
  public Peer(final int index,
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
  public int position() {
    return this.position.get();
  }

  public void countBytesRx(long bytes) {
    this.netBytesRX.addAndGet(bytes);
  }

  public void countBytesTx(long bytes) {
    this.netBytesTX.addAndGet(bytes);
  }

  public long getNetBytesRX() {
    return netBytesRX.get();
  }

  public long getNetBytesTX() {
    return netBytesTX.get();
  }
}
