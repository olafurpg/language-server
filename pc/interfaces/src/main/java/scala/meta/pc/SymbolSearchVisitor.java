package scala.meta.pc;

public abstract class SymbolSearchVisitor {
    abstract boolean preVisitPackage(String pkg);
    abstract boolean visitClassfile(String pkg, String filename);
}
