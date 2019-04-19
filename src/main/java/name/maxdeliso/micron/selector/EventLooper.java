package name.maxdeliso.micron.selector;

import java.io.IOException;

public interface EventLooper {
    void loop() throws IOException;
    void halt() throws InterruptedException, IOException;
}
