package name.maxdeliso.micron.support;

import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.Set;

public class TestServerSocketChannel extends ServerSocketChannel {
    TestServerSocketChannel(final SelectorProvider provider) {
        super(provider);
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) {
        return this;
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) {
        return null;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) {
        return null;
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public ServerSocket socket() {
        return null;
    }

    @Override
    public SocketChannel accept() {
        return null;
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
