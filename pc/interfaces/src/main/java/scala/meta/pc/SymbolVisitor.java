package scala.meta.pc;

public abstract class SymbolVisitor {
    public abstract void visitMethod(MethodInformation method);
    public abstract void visitInput(String filename, String text);
}
