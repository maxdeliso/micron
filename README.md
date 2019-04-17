# Abstract

**micron** is a multiple input multiple output broadcasting program.

## Notes

* Implemented entirely using evented I/O using Java's Channel class.
* Data is stored in a central shared ring buffer [0, _M_-1], with shared cursor location _c_.
* Each peer has a position _p_ in the ring buffer.
* Events are processed about the writability and readablility of all peers as they occur.
* Toggles of read/write/accept interest flags are done using a delay queue and a separate thread.

## Building

``mvn compile``

## Testing

1. ``mvn clean compile exec:java -Dexec.args="-d 0 -b 128 -m 102400"``
2. ``yes a | nc localhost 1337``
3. ``yes b | nc localhost 1337``

Note: requires Java 14 or greater

You should see as and bs interleaved in the output, roughly equally,
and this will generalize to as many connected peers as there are.

You can tail the logs using: ``tail -f logs/metrics.log``

This will show some basic metrics and gauges about performance.

## References

* https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SocketChannel.html
* https://en.wikipedia.org/wiki/Circular_buffer

## Building and running in Docker

1. `docker build -t micron .`
1. `docker run -p 1337:1337 micron:latest`
