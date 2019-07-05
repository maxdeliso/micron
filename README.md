# Abstract

**micron** is a multiple input multiple output broadcasting program.

## Notes

* Implemented entirely using evented I/O using Java's Channel class.
* Data is stored in a central shared ring buffer [0, _M_-1], with shared cursor location _c_.
* Each peer has a position _p_ in the ring buffer.
* Events are processed about the writability and readablility of all peers as they occur.
* Typically, a readable peer causes a new message to be made available to all other peers when a successful read follows a readable event.
* If, when receiving an event about a readable peer, the ring buffer's next cursor location would clobber **any** _p_, then the receive is not done.
* Typically, a writable peer causes a new message to be written to that peer that it has not yet seen.
* If, when receiving an event about a writable peer, writing to the peer would cause it to pass the cursor _c_, then the write is not done.
* The executability of fork join tasks on the common fork join pool is used to selectively toggle interest in events per connected peer (this is subtle and needs a more thorough explanation).

## Building

``mvn compile``

## Testing

1. run main
2. run `yes a | nc localhost 1337`
3. run `yes b | nc localhost 1337`

you should see as and bs interleaved in the output, roughly equally, and this will generalize to as many connected peers as there are

## Next steps

* automated runtime performance analysis
* test over multicast
* write in different languages, compare perf

## References

* https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SocketChannel.html
* https://en.wikipedia.org/wiki/Circular_buffer

## Running in Docker

1. `docker build -t micron .`
1. `docker run -p 1337:1337 micron:latest`