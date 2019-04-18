package name.maxdeliso;

import name.maxdeliso.looper.EventLooper;
import name.maxdeliso.looper.SingleThreadedEventLooper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static java.lang.Runtime.getRuntime;

final class Main {

    private static final int SERVER_PORT = 1234;

    private static final int BUFFER_SIZE = 512;

    private static final int MAX_MESSAGES = 32;

    private static final int SELECT_TIMEOUT_SECONDS = 1;

    private static final String NO_NEW_DATA_MESSAGE = "\b";

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args) {
        final EventLooper looper =
                new SingleThreadedEventLooper(
                        SERVER_PORT,
                        BUFFER_SIZE,
                        SELECT_TIMEOUT_SECONDS,
                        MAX_MESSAGES,
                        NO_NEW_DATA_MESSAGE);

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
