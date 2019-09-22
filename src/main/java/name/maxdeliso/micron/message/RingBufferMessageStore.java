package name.maxdeliso.micron.message;

import java.util.Optional;

public interface RingBufferMessageStore {
  boolean add(byte[] received);

  byte[] get(int messageIdx);

  int position();

  int size();

  int messageSize();
}
