package scala.meta.internal.pantsbuild

import java.nio.file.Path
import ujson.Obj
import java.nio.file.Paths

case class PantsRoots(
    roots: List[Path]
)
object PantsRoots {

  def fromJson(target: Obj): PantsRoots = {
    target.value.get(PantsKeys.roots) match {
      case Some(roots: Obj) =>
        PantsRoots(roots.arr.iterator.flatMap {
          case obj: Obj =>
            obj.value
              .get(PantsKeys.sourceRoot)
              .map(root => Paths.get(root.str))
              .toList
          case _ => Nil
        }.toList)
      case _ =>
        PantsRoots(Nil)
    }
  }
}
