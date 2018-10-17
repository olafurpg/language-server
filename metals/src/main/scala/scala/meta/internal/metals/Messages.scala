package scala.meta.internal.metals

import java.util.Collections
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ShowMessageRequestParams
import scala.collection.JavaConverters._

object Messages {

  val BloopInstallProgress = MetalsSlowTaskParams("sbt bloopInstall")
  val ImportProjectFailed = new MessageParams(
    MessageType.Error,
    "Import project failed, no functionality will work. See the logs for more details"
  )
  val ImportProjectPartiallyFailed = new MessageParams(
    MessageType.Warning,
    "Import project partially failed, limited functionality may work in some parts of the workspace. " +
      "See the logs for more details. "
  )

  object ReimportSbtProject {
    def yes: MessageActionItem =
      new MessageActionItem("Import changes")
    def params: ShowMessageRequestParams = {
      val params = new ShowMessageRequestParams()
      params.setMessage("sbt projects need to be imported")
      params.setType(MessageType.Info)
      params.setActions(
        List(
          yes
        ).asJava
      )
      params
    }
  }

  object ImportProjectViaBloop {
    def yes = new MessageActionItem("Import project via bloop")
    def params: ShowMessageRequestParams = {
      val params = new ShowMessageRequestParams()
      params.setMessage(
        "sbt build detected, would you like to import the project via bloop?"
      )
      params.setType(MessageType.Info)
      params.setActions(Collections.singletonList(yes))
      params
    }

  }

}
