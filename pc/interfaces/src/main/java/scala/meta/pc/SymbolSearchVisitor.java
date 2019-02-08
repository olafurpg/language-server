package scala.meta.pc;

public abstract class SymbolSearchVisitor {
    abstract public boolean preVisitPackage(String pkg);
    abstract public void visitClassfile(String pkg, String filename);
    abstract public boolean isCancelled();
}
