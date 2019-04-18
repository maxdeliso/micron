package name.maxdeliso;

import java.nio.channels.SocketChannel;
import java.util.Objects;

public final class Peer {

    private final int peerIndex;
    private int peerOffset;

    private final SocketChannel socketChannel;

    public Peer(final int peerIndex, final SocketChannel socketChannel) {
        this.peerIndex = peerIndex;
        this.peerOffset = 0;

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

        Peer that = (Peer) obj;
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

    @Override
    public String toString() {
        return "Peer{" +
                "peerIndex=" + peerIndex +
                ", peerOffset=" + peerOffset +
                '}';
    }
}
