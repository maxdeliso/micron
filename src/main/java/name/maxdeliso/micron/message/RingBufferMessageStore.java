package name.maxdeliso.micron.message;

public interface RingBufferMessageStore {
  boolean add(String received);

  String get(int messageIdx);

  int position();

  int size();
}
