package scala.meta.internal.builds
import java.nio.file.Files
import java.security.MessageDigest
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.UserConfiguration

object MavenDigest extends Digestable {
  override protected def digestWorkspace(
      workspace: AbsolutePath,
      digest: MessageDigest,
      userConfig: UserConfiguration
  ): Boolean = {
    Digest.digestFile(workspace.resolve("pom.xml"), digest)
    Files.walk(workspace.toNIO).allMatch { file =>
      if (file.getFileName.toString == "pom.xml") {
        Digest.digestFile(AbsolutePath(file), digest)
      } else {
        true
      }
    }
  }
}
