package scala.meta.internal.pc

import java.nio.file.Path
import scala.meta.pc.SymbolSearchVisitor
import org.eclipse.{lsp4j => l}

class CompilerSearchVisitor(query: String, containsPackage: String => Boolean)
    extends SymbolSearchVisitor {
  val candidates = new java.util.PriorityQueue[WorkspaceCandidate](
    new WorkspaceCandidate.Comparator(query)
  )
  def visitClassfile(pkg: String, filename: String): Unit = {
    candidates.add(WorkspaceCandidate.Classfile(pkg, filename))
  }
  def visitWorkspaceSymbol(
      path: Path,
      symbol: String,
      kind: l.SymbolKind,
      range: l.Range
  ): Unit = {
    candidates.add(WorkspaceCandidate.Workspace(symbol))
  }

  override def preVisitPath(path: Path): Boolean = {
    // TODO: filter out paths that are guaranteed on a source dependency
    true
  }
  def preVisitPackage(pkg: String): Boolean = {
    containsPackage(pkg)
  }

  override def isCancelled: Boolean = {
    // TODO(olafur) integrate CancelChecker
    false
  }
}
