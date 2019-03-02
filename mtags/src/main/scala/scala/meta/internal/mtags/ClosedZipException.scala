package scala.meta.internal.mtags

import scala.meta.io.AbsolutePath
import scala.util.control.NoStackTrace

class ClosedZipException(path: AbsolutePath, e: Throwable)
    extends Exception(path.toURI.toString, e)
    with NoStackTrace
