package name.maxdeliso.micron.message;

import java.util.Optional;
import java.util.stream.Stream;

public interface MessageStore {
  boolean add(String received);

  Optional<String> get(int messageIndex);

  Stream<String> getFrom(int messageIndex);
}
