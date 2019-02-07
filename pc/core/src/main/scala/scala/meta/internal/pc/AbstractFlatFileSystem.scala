package scala.meta.internal.pc

class AbstractFlatFileSystem(ffs: FlatFileSystem) {
  val roots: Map[String, AbstractFlatJar] =
    ffs.jars.map(jar => jar.uri -> new AbstractFlatJar(jar, ffs)).toMap
}
