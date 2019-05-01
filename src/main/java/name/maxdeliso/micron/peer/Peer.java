package name.maxdeliso.micron.peer;

import lombok.Value;
import net.jcip.annotations.ThreadSafe;

import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

@ThreadSafe
@Value
public final class Peer {

  private final long index;

  private final AtomicLong position;

  private final SocketChannel socketChannel;

  /**
   * Construct a peer, which identifies a connected user.
   *
   * @param index         the numeric index of the peer.
   * @param socketChannel a selectable channel with which to communicate with them.
   */
  public Peer(final long index, final SocketChannel socketChannel) {
    this.index = index;
    this.position = new AtomicLong(0);
    this.socketChannel = socketChannel;
  }

  public void advancePosition() {
    this.position.incrementAndGet();
  }

  public void resetPosition() {
    this.position.set(0);
  }

  public long getPosition() {
    return this.position.get();
  }
}
