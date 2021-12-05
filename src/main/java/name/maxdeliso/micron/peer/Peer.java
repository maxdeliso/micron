package name.maxdeliso.micron.peer;

import java.nio.channels.SocketChannel;

public interface Peer {
  int advancePosition();

  int index();

  int position();

  void countBytesRx(long bytes);

  void countBytesTx(long bytes);

  long netBytesRX();

  SocketChannel socketChannel();
}
