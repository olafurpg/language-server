package scala.meta.pc;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public interface OffsetParams {
    String filename();
    String text();
    int offset();
    CancelChecker token();
}
