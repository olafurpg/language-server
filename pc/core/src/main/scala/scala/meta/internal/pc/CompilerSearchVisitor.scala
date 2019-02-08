package scala.meta.internal.pc

import java.nio.file.Path
import scala.meta.pc.SymbolSearchVisitor
import org.eclipse.{lsp4j => l}

class CompilerSearchVisitor(
    query: String,
    containsPackage: String => Boolean,
    visit: WorkspaceCandidate => Int
) extends SymbolSearchVisitor {
  def visitClassfile(pkg: String, filename: String): Int = {
    visit(WorkspaceCandidate.Classfile(pkg, filename))
  }
  def visitWorkspaceSymbol(
      path: Path,
      symbol: String,
      kind: l.SymbolKind,
      range: l.Range
  ): Int = {
    visit(WorkspaceCandidate.Workspace(symbol))
  }

  override def preVisitPath(path: Path): Boolean = {
    // TODO: filter out paths that are guaranteed on a source dependency
    true
  }
  def preVisitPackage(pkg: String): Boolean = {
    // TODO come up with less hacky check, maybe staticPackageSymbol(..)
    containsPackage(pkg.stripSuffix("/").replace('/', '.'))
  }

  override def isCancelled: Boolean = {
    // TODO(olafur) integrate CancelChecker
    false
  }
}
