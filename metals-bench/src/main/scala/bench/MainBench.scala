package bench

object MainBench {
  def main(args: Array[String]): Unit = {
    val bench = new ClasspathFuzzBench
    bench.setup()
    val symbols = bench.symbols
    pprint.log(symbols.search("SparkS"))
  }
}
