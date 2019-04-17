package name.maxdeliso.micron.message;

public interface RingBufferMessageStore {
  boolean add(byte[] received);

  byte[] get(int messageIdx);

  int position();

  int size();
}
