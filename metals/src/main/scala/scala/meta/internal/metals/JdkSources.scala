package scala.meta.internal.metals
import java.nio.file.Files
import java.nio.file.Paths
import scala.meta.io.AbsolutePath

object JdkSources {
  def apply(): Option[AbsolutePath] = {
    for {
      javaHome <- sys.props.get("java.home")
      jdkSources = Paths.get(javaHome).getParent.resolve("src.zip")
      if Files.isRegularFile(jdkSources)
    } yield AbsolutePath(jdkSources)
  }
}
