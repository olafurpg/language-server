package scala.meta.pc;

import java.util.Optional;

public interface ParameterInformation {
    String name();
    Optional<String> docstring();
}
