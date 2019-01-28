package scala.meta.pc;

import org.eclipse.lsp4j.CompletionList;

public class CompletionItems extends CompletionList {

    public LookupKind lookupKind;

    public CompletionItems(LookupKind lookupKind) {
        super();
        this.lookupKind = lookupKind;
    }

    public enum LookupKind {
        None,
        Scope,
        Type
    }
}
