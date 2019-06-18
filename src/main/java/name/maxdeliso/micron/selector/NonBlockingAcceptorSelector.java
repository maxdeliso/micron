package name.maxdeliso.micron.selector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;

public interface NonBlockingAcceptorSelector {
  /**
   * Handles an accept event when detected on the server socket channel.
   *
   * @param serverSocketChannel the server socket channel.
   * @param selector            a selectorRef to register the newly accepted channel.
   * @param peerConsumer        a callback to execute after the new channel is registered.
   * @throws IOException if an issue occurs while handling the acceptance.
   */
  default void handleAccept(final ServerSocketChannel serverSocketChannel,
                            final Selector selector,
                            final BiConsumer<SocketChannel, SelectionKey> peerConsumer)
      throws IOException {
    final SocketChannel socketChannel = serverSocketChannel.accept();

    if (socketChannel == null) {
      return;
    }

    socketChannel.configureBlocking(false);

    final var peerKey = socketChannel
        .register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

    peerConsumer.accept(socketChannel, peerKey);
  }
}
