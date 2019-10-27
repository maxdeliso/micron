package name.maxdeliso.micron.looper;

import java.io.IOException;

public interface EventLooper {
  void loop() throws IOException;

  boolean alive();

  void halt() throws InterruptedException;
}
