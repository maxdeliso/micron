package name.maxdeliso.micron.looper;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class PeerReadResult {
  private final int readCalls;
  private final int bytesReadTotal;
}
