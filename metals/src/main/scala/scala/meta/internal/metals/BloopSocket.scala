package scala.meta.internal.metals

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import org.scalasbt.ipcsocket.UnixDomainSocket

/**
 * We communicate with Bloop via one of.
 *
 * - unix domain sockets (macos/linux)
 * - tcp (current default on Windows)
 * - named pipes (ideal default on Windows)
 *
 * This class abstracts over each of these.
 */
sealed trait BloopSocket extends Cancelable {
  import BloopSocket._
  def socket: Socket
  def input: InputStream = this match {
    case Unix(socket) => socket.getInputStream
    case NamedPipe(socket) => socket.getInputStream
    case Tcp(socket) =>
      new QuietInputStream(
        socket.getInputStream,
        "bloop tcp input socket"
      )
  }
  def output: OutputStream = this match {
    case Unix(socket) => socket.getOutputStream
    case NamedPipe(socket) => socket.getOutputStream
    case Tcp(socket) =>
      new QuietOutputStream(
        socket.getOutputStream,
        "bloop tcp output socket"
      )
  }
  override def cancel(): Unit = {
    if (!socket.isClosed && socket.isConnected) {
      pprint.log("close")
      socket.close()
    }
  }
}

object BloopSocket {
  case class Unix(socket: UnixDomainSocket) extends BloopSocket
  case class Tcp(socket: Socket) extends BloopSocket
  case class NamedPipe(socket: Socket) extends BloopSocket
}
