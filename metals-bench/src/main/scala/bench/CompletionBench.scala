package bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import scala.meta.internal.metals.ClasspathSearch
import scala.meta.internal.pc.ScalaPC
import scala.meta.pc.CompletionItems
import tests.Library
import tests.SimpleJavaSymbolIndexer

@State(Scope.Benchmark)
class CompletionBench {
  var libraries: List[Library] = _

  @Setup
  def setup(): Unit = {
    libraries = Library.jdk :: Library.all
  }

  @TearDown
  def teardown(): Unit = {}

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def run(): CompletionItems = {
    val classpath = libraries.flatMap(_.classpath.entries.map(_.toNIO))
    val sources = libraries.flatMap(_.sources.entries)
    val search = ClasspathSearch.fromClasspath(classpath, _ => 0)
    val indexer = new SimpleJavaSymbolIndexer(sources)
    val pc = new ScalaPC(classpath, Nil, indexer, search)
    val code = "import Java\n"
    pc.complete("A.scala", code, code.length - 2)
  }

}
