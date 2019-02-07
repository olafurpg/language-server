package bench

object MainBench {
  def main(args: Array[String]): Unit = {
    val bench = new CompletionBench
    bench.setup()
    pprint.log(bench.run())
  }
}
