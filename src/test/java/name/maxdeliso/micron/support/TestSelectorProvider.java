package name.maxdeliso.micron.support;

import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

public final class TestSelectorProvider extends SelectorProvider {

  private final ServerSocketChannel serverSocketChannel;

  private final AbstractSelector selector;

  public TestSelectorProvider() {
    this.serverSocketChannel = new TestServerSocketChannel(this);
    this.selector = new TestSelector(this);
  }

  @Override
  public DatagramChannel openDatagramChannel() {
    return null;
  }

  @Override
  public DatagramChannel openDatagramChannel(ProtocolFamily family) {
    return null;
  }

  @Override
  public Pipe openPipe() {
    return null;
  }

  @Override
  public AbstractSelector openSelector() {
    return selector;
  }

  @Override
  public ServerSocketChannel openServerSocketChannel() {
    return serverSocketChannel;
  }

  @Override
  public SocketChannel openSocketChannel() {
    return null;
  }
}
