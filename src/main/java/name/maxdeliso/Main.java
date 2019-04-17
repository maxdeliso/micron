package name.maxdeliso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

final class Main {

    private static final int SERVER_PORT = 1234;

    private static final int BUFFER_SIZE = 512;

    private static final int MAX_MESSAGES = 32;

    private static final int SELECT_TIMEOUT_SECONDS = 1;

    private static final String NO_NEW_DATA_MESSAGE = "\b";

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args) {
        try {
            new RapidSelector(SERVER_PORT, BUFFER_SIZE, SELECT_TIMEOUT_SECONDS, MAX_MESSAGES, NO_NEW_DATA_MESSAGE)
                    .eventLoop();
        } catch (final IOException ioe) {
            LOGGER.error("terminated exceptionally", ioe);
        }
    }
}
