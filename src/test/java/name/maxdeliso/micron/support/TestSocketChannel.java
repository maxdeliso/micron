package name.maxdeliso.micron.support;

import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class TestSocketChannel extends SocketChannel {

  public TestSocketChannel(SelectorProvider provider) {
    super(provider);
  }

  @Override
  public SocketChannel bind(SocketAddress local) {
    return null;
  }

  @Override
  public <T> SocketChannel setOption(SocketOption<T> name, T value) {
    return null;
  }

  @Override
  public <T> T getOption(SocketOption<T> name) {
    return null;
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
    return null;
  }

  @Override
  public SocketChannel shutdownInput() {
    return null;
  }

  @Override
  public SocketChannel shutdownOutput() {
    return null;
  }

  @Override
  public Socket socket() {
    return null;
  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public boolean isConnectionPending() {
    return false;
  }

  @Override
  public boolean connect(SocketAddress remote) {
    return false;
  }

  @Override
  public boolean finishConnect() {
    return false;
  }

  @Override
  public SocketAddress getRemoteAddress() {
    return null;
  }

  @Override
  public int read(ByteBuffer dst) {
    return 0;
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) {
    return 0;
  }

  @Override
  public int write(ByteBuffer src) {
    return 0;
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) {
    return 0;
  }

  @Override
  public SocketAddress getLocalAddress() {
    return null;
  }

  @Override
  protected void implCloseSelectableChannel() {

  }

  @Override
  protected void implConfigureBlocking(boolean block) {

  }
}
