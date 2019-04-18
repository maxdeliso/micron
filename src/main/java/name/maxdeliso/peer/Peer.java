package name.maxdeliso.peer;

import java.nio.channels.SocketChannel;
import java.util.Objects;

public final class Peer {

    private final long index;

    private long position;

    private final SocketChannel socketChannel;

    public Peer(final long index, final SocketChannel socketChannel) {
        this.index = index;
        this.position = 0;
        this.socketChannel = socketChannel;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()){
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
        return position;
    }

    public void advancePosition() {
        this.position++;
    }

    public long getIndex() {
        return index;
    }

    public void resetPosition() {
        this.position = 0;
    }

    @Override
    public String toString() {
        return "Peer{index=" + index + ", position=" + position + '}';
    }
}
