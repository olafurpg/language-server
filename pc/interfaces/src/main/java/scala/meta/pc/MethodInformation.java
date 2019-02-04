package scala.meta.pc;

import java.util.List;

public interface MethodInformation {
    String symbol();
    String name();
    String docstring();
    List<ParameterInformation> typeParameters();
    List<ParameterInformation> parameters();
}
