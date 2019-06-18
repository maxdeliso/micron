package name.maxdeliso.micron.peer;

import lombok.Value;
import net.jcip.annotations.ThreadSafe;

import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

@ThreadSafe
@Value
public final class Peer implements PositionTracker {

  private final int index;

  private final AtomicInteger position;

  private final SocketChannel socketChannel;

  /**
   * Construct a peer, which identifies a connected user.
   *
   * @param index         the numeric index of the peer.
   * @param socketChannel a selectable channel with which to communicate with them.
   */
  public Peer(final int index, final SocketChannel socketChannel) {
    this.index = index;
    this.position = new AtomicInteger(0);
    this.socketChannel = socketChannel;
  }

  @Override
  public int advancePosition() {
    return this.position.incrementAndGet();
  }

  @Override
  public int advancePosition(final int delta) {
    return this.position.addAndGet(delta);
  }

  @Override
  public void resetPosition() {
    this.position.set(0);
  }

  @Override
  public int getPosition() {
    return this.position.get();
  }
}
