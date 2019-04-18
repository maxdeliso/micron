package name.maxdeliso.looper;

import java.io.IOException;

public interface EventLooper {
    void loop() throws IOException;
    void halt() throws InterruptedException, IOException;
}
