package scala.meta.internal.metals

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import scribe.Level
import scribe.LogRecord
import scribe.writer.Writer

/**
 * Scribe logging handler that forwards logging messages to the LSP editor client.
 */
object LanguageClientLogger extends Writer {
  var languageClient: Option[MetalsLanguageClient] = None
  override def write[M](record: LogRecord[M], output: String): Unit = {
    languageClient.foreach { client =>
      val messageType = record.level match {
        case Level.Error => MessageType.Error
        case Level.Warn => MessageType.Warning
        case Level.Info => MessageType.Info
        case _ => MessageType.Log
      }
      client.logMessage(new MessageParams(messageType, record.message))
    }
  }
}
