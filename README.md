# Abstract

**micron** is a multiple input multiple output broadcasting program.

## Notes

* Implemented using event driven I/O.
* Data is stored in a central shared ring buffer [0, _M_-1], with shared cursor location _c_.
* Each peer has a position _p_ in the ring buffer.
* Toggles of read/write/accept interest flags are done using a delay queue and a separate thread.

## Building

You can build directly on your local machine using maven, or use the provided Dockerfile.

``docker build -t micron .``

## Testing

1. ``docker run -p 1337:1337 micron``
2. ``yes a | nc localhost 1337``
3. ``yes b | nc localhost 1337``

### Notes

* requires Java 17 or greater
* you should see as and bs interleaved in the output, roughly equally,
and this will generalize to as many connected peers as there are.
* you can tail the metrics using: ``tail -f logs/metrics.log``; this will show some basic metrics and gauges about performance.

## References

* https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html
* https://en.wikipedia.org/wiki/Circular_buffer
