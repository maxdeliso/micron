package name.maxdeliso.message;

import name.maxdeliso.peer.PeerRegistry;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ThreadSafe
public final class MessageStore {

    private final int messageListCap;

    private final List<String> messages;

    private final PeerRegistry peerRegistry;

    private final Logger LOGGER = LoggerFactory.getLogger(MessageStore.class);

    public MessageStore(final int maxMessages, final PeerRegistry peerRegistry) {
        this.messageListCap = maxMessages;
        this.messages = new ArrayList<>(maxMessages);
        this.peerRegistry = peerRegistry;
    }

    public void add(final String received) {
        if (messages.size() >= messageListCap) {
            rotateBuffer();
        }

        assert messages.size() <= messageListCap;

        messages.add(received);
    }

    private void rotateBuffer() {
        final var minimumRightExtent = peerRegistry.minPosition().orElse(messageListCap);
        final var leftOver = new ArrayList<>(messages.subList(minimumRightExtent, messageListCap));

        synchronized (messages) {
            messages.clear();
            messages.addAll(leftOver);
            peerRegistry.resetPositions();
        }

        LOGGER.debug("maximum of {} was hit, copying {} left over messages to the beginning",
                messageListCap, leftOver.size());
    }

    public Optional<String> get(final long messageIndex) {
        assert messageIndex >= 0;

        final Long messageIndexBoxed = messageIndex;

        if(messageIndex < messages.size()) {
            final String message;

            synchronized (this.messages) {
                message = messages.get(messageIndexBoxed.intValue());
            }

            return Optional.of(message);
        } else {
            return Optional.empty();
        }
    }
}
