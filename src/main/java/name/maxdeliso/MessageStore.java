package name.maxdeliso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class MessageStore {

    private final int messageListCap;

    private final List<String> messages;

    private final PeerRegistry peerRegistry;

    private final Logger LOGGER = LoggerFactory.getLogger(MessageStore.class);

    public MessageStore(int messageListCap, final PeerRegistry peerRegistry) {
        this.messageListCap = messageListCap;
        this.messages = new ArrayList<>(messageListCap);
        this.peerRegistry = peerRegistry;
    }

    public void add(final String receivedMsg) {
        if (messages.size() >= messageListCap) {
            rotateBuffer();
        }

        assert messages.size() <= messageListCap;

        messages.add(receivedMsg);
    }

    private void rotateBuffer() {
        final var minimumRightExtent = peerRegistry.findMinExtent().orElse(messageListCap);
        final var leftOver = new ArrayList<>(messages.subList(minimumRightExtent, messageListCap));

        messages.clear();
        messages.addAll(leftOver);

        peerRegistry.resetPositions();

        LOGGER.debug("maximum of {} was hit, copying {} left over messages to the beginning",
                messageListCap, leftOver.size());
    }

    public Optional<String> get(final long messageIndex) {
        final Long messageIndexBoxed = messageIndex;

        if(messageIndex < messages.size()) {
            return Optional.of(messages.get(messageIndexBoxed.intValue()));
        } else {
            return Optional.empty();
        }
    }
}
