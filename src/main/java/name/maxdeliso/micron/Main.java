package name.maxdeliso.micron;

import name.maxdeliso.micron.looper.SingleThreadedEventLooper;
import name.maxdeliso.micron.message.InMemoryMessageStore;
import name.maxdeliso.micron.message.MessageStore;
import name.maxdeliso.micron.peer.InMemoryPeerRegistry;
import name.maxdeliso.micron.peer.PeerRegistry;
import name.maxdeliso.micron.selector.EventLooper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static java.lang.Runtime.getRuntime;

final class Main {

    private static final int SERVER_PORT = 1337;

    private static final int BUFFER_SIZE = 512;

    private static final int MAX_MESSAGES = 8192;

    private static final int SELECT_TIMEOUT_SECONDS = 1;

    private static final String NO_NEW_DATA_MESSAGE = "\b";

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args) {
        final PeerRegistry peerRegistry = new InMemoryPeerRegistry();

        final MessageStore messageStore = new InMemoryMessageStore(MAX_MESSAGES, peerRegistry);

        final EventLooper looper =
                new SingleThreadedEventLooper(
                        SERVER_PORT,
                        BUFFER_SIZE,
                        SELECT_TIMEOUT_SECONDS,
                        MAX_MESSAGES,
                        NO_NEW_DATA_MESSAGE,
                        peerRegistry,
                        messageStore);

        getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOGGER.trace("sending halt to looper...");

                looper.halt();
            } catch (final InterruptedException | IOException exc) {
                LOGGER.warn("exception while halting looper", exc);
            }
        }));

        try {
            looper.loop();
        } catch (final IOException ioe) {
            LOGGER.error("terminated exceptionally", ioe);
        }
    }
}
