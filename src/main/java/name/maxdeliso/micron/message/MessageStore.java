package name.maxdeliso.micron.message;

import java.util.Optional;
import java.util.stream.Stream;

public interface MessageStore {
  boolean add(String received);

  Optional<String> get(long messageIndex);

  Stream<String> getFrom(long messageIndex);
}
