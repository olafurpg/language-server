package scala.meta.pc;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.SignatureHelp;

import java.nio.file.Path;
import java.util.List;

public abstract class  PC {
    public abstract void shutdown();
    public abstract String symbol(OffsetParams params);
    public abstract SignatureHelp signatureHelp(OffsetParams params);
    public abstract Hover hover(OffsetParams params);
    public abstract CompletionItems complete(OffsetParams params);
    public abstract CompletionItem completionItemResolve(CompletionItem item, String symbol);
    public abstract List<String> diagnostics();
    public abstract PC withIndexer(SymbolIndexer indexer);
    public abstract PC withSearch(SymbolSearch search);
    public abstract PC newInstance(String buildTargetIdentifier,  List<Path> classpath, List<String> options);
}