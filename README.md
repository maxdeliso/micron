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

## References

* https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/nio/channels/SocketChannel.html
* https://en.wikipedia.org/wiki/Circular_buffer

## Building and running in Docker

1. `docker build -t micron .`
1. `docker run -p 1337:1337 micron:latest`
