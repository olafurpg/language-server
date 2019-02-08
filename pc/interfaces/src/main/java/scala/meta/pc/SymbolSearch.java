package scala.meta.pc;

public interface SymbolSearch {
    void search(String query, SymbolSearchVisitor visitor);
}
