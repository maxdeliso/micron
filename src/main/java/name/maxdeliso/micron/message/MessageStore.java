package name.maxdeliso.micron.message;

import java.util.Optional;

public interface MessageStore {
    void add(String received);

    Optional<String> get(long messageIndex);
}
