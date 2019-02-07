package scala.meta.pc;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.SignatureHelp;

import java.nio.file.Path;
import java.util.List;

public abstract class  PC {
    public abstract void shutdown();
    public abstract String symbol(String filename, String text, int offset);
    public abstract SignatureHelp signatureHelp(String filename, String text, int offset);
    public abstract Hover hover(String filename, String text, int offset);
    public abstract CompletionItems complete(String filename, String text, int offset);
    public abstract List<String> diagnostics();
    public abstract PC withIndexer(SymbolIndexer indexer);
    public abstract PC withWorkspace(Path workspace);
    public abstract PC newInstance(List<Path> classpath, List<String> options);
}