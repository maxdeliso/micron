package name.maxdeliso.micron.support;


import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.Set;

public class TestSelector extends AbstractSelector {
    TestSelector(SelectorProvider provider) {
        super(provider);
    }

    @Override
    protected void implCloseSelector() {

    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        return null;
    }

    @Override
    public Set<SelectionKey> keys() {
        return Collections.emptySet();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return Collections.emptySet();
    }

    @Override
    public int selectNow() {
        return 0;
    }

    @Override
    public int select(long timeout) {
        return 0;
    }

    @Override
    public int select() {
        return 0;
    }

    @Override
    public Selector wakeup() {
        return null;
    }
}