package scala.meta.pc;

public interface SymbolSearch {
    Result search(String query, SymbolSearchVisitor visitor);
    enum Result {
        COMPLETE,
        INCOMPLETE
    }
}
