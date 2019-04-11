package scala.meta.pc;

import java.util.Optional;
import java.nio.file.Path;

/**
 * Parameters for a presentation compiler request at a given offset in a single source file.
 */
public interface OffsetParams {

    /**
     * The name of the source file.
     */
    String filename();

    /**
     * The optional path to the source file.
     */
    Optional<Path> path();

    /**
     * The optional path to the source file.
     */
    Optional<Path> sourceDirectory();

    /**
     * The text contents of the source file.
     */
    String text();

    /**
     * The character offset of the request.
     */
    int offset();

    /**
     * The cancelation token for this request.
     */
    CancelToken token();

    default void checkCanceled() {
        token().checkCanceled();
    }
}
