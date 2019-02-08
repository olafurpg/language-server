package scala.meta.pc;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.nio.file.Path;

public abstract class SymbolSearchVisitor {
    abstract public boolean preVisitPackage(String pkg);
    abstract public boolean preVisitPath(Path path);
    abstract public int visitClassfile(String pkg, String filename);
    abstract public int visitWorkspaceSymbol(Path path, String symbol, SymbolKind kind, Range range);
    abstract public boolean isCancelled();
}
