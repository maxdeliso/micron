package name.maxdeliso;

import java.nio.channels.SocketChannel;
import java.util.Objects;

public final class PeerDescriptor {

    private final int peerIndex;
    private final SocketChannel socketChannel;
    private int peerOffset;

    public PeerDescriptor(final int peerIndex, final SocketChannel socketChannel) {
        this.peerIndex = peerIndex;
        this.socketChannel = socketChannel;
        this.peerOffset = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerDescriptor that = (PeerDescriptor) o;
        return peerIndex == that.peerIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerIndex);
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public int getPeerOffset() {
        return peerOffset;
    }

    public void advancePeerOffset() {
        this.peerOffset++;
    }

    public void resetPeerOffset() {
        this.peerOffset = 0;
    }

    public int getPeerIndex() {
        return peerIndex;
    }
}
