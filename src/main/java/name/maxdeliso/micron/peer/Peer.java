package name.maxdeliso.micron.peer;

import net.jcip.annotations.ThreadSafe;

import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@ThreadSafe
public final class Peer {

  private final long index;

  private final AtomicLong position;

  private final SocketChannel socketChannel;

  /**
   * Construct a peer, which identifies a connected user.
   *
   * @param index         the numeric index of the peer.
   * @param socketChannel a socket channel with which to communicate with them.
   */
  public Peer(final long index, final SocketChannel socketChannel) {
    this.index = index;
    this.position = new AtomicLong(0);
    this.socketChannel = socketChannel;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    final Peer that = (Peer) obj;
    return this.index == that.index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(index);
  }

  public SocketChannel getSocketChannel() {
    return socketChannel;
  }

  public long getPosition() {
    return position.get();
  }

  public void advancePosition() {
    this.position.incrementAndGet();
  }

  public long getIndex() {
    return index;
  }

  public void resetPosition() {
    this.position.set(0);
  }

  @Override
  public String toString() {
    return "Peer{index=" + index + ", position=" + position + '}';
  }
}
