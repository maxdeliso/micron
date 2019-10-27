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

## Running Locally

``mvn clean compile exec:java``

Note: requires Java 11 or greater

## Testing

1. run main
2. run `yes a | nc localhost 1337`
3. run `yes b | nc localhost 1337`

you should see as and bs interleaved in the output, roughly equally,
and this will generalize to as many connected peers as there are

## References

* https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SocketChannel.html
* https://en.wikipedia.org/wiki/Circular_buffer

## Building and running in Docker

1. `docker build -t micron .`
1. `docker run -p 1337:1337 micron:latest`
