package scala.meta.internal.builds

import scala.meta.io.AbsolutePath
import java.security.MessageDigest
import scala.sys.process._

import scala.meta.internal.metals.UserConfiguration

object PantsDigest extends Digestable {
  override protected def digestWorkspace(
      workspace: AbsolutePath,
      digest: MessageDigest,
      userConfig: UserConfiguration
  ): Boolean = {

    val userConfig = new UserConfiguration()
    val args = List(
      workspace.resolve("pants").toString(),
      "filedeps",
      userConfig.pantsTargets.getOrElse("::/")
    )
    pprint.log(args)
    val pantsFileDeps = Process(
      args,
      None,
      "OS_PANTS_SRC" -> "/Users/srohankar/workspace/pants"
    ).!!.trim
    pprint.log(pantsFileDeps)
    pantsFileDeps.linesIterator
      .map { file =>
        java.nio.file.Paths.get(file).toAbsolutePath.normalize
      }
      .filter(_.endsWith("BUILD"))
      .forall(file => Digest.digestFile(AbsolutePath(file), digest))
  }
}
