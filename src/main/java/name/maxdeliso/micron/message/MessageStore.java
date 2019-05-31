package name.maxdeliso.micron.message;

import java.util.Optional;

public interface MessageStore {
  boolean add(String received);

  Optional<String> get(long messageIndex);
}
