package scala.meta.pc;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;

import java.util.List;

public class CompletionItems extends CompletionList {

    public Kind kind;

    public CompletionItems(Kind kind, List<CompletionItem> items) {
        super();
        this.kind = kind;
        super.setItems(items);
    }

    public enum Kind {
        None,
        Scope,
        Type,
        Override
    }
}
