package name.maxdeliso.micron.params;

import com.beust.jcommander.Parameter;

public class Arguments {
  private static final int DEFAULT_SERVER_PORT = 1337;

  private static final int DEFAULT_BUFFER_SIZE = 128;

  private static final int DEFAULT_MAX_MESSAGES = 4096;

  private static final int DEFAULT_BACKOFF_DURATION_MILLIS = 100;

  @Parameter(names = {"--help", "-h"})
  private boolean help = false;

  @Parameter(names = {"--port", "-p"})
  private int port = DEFAULT_SERVER_PORT;

  @Parameter(names = {"--buffer-size", "-b"})
  private int bufferSize = DEFAULT_BUFFER_SIZE;

  @Parameter(names = {"--max-messages", "-m"})
  private int maxMessages = DEFAULT_MAX_MESSAGES;

  @Parameter(names = {"--backoff-duration", "-d"})
  private int backoffDurationMillis = DEFAULT_BACKOFF_DURATION_MILLIS;

  public boolean isHelp() {
    return help;
  }

  public int getPort() {
    return port;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public int getMaxMessages() {
    return maxMessages;
  }

  public int getBackoffDurationMillis() {
    return backoffDurationMillis;
  }
}
