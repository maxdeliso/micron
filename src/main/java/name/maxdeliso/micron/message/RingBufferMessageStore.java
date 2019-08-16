package name.maxdeliso.micron.message;

import java.util.Optional;

public interface RingBufferMessageStore {
  boolean add(String received);

  Optional<String> get(int messageIdx);

  int position();

  int size();
}
