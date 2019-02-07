package myexample

import scala.collection.JavaConverters._

object App {
  import scala.collection.concurrent.TrieMap
  val x = 1
  val n = scala.collection.concurrent.TrieMap.empty[Int, String].get(1)
  TrieMap.empty
  println(n)
  import myexample.CoolioSync.Inner
}

object CoolioSync {
  class Inner
}
