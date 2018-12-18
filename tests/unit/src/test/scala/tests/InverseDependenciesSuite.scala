package tests

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.meta.internal.metals.BuildTargets

object InverseDependenciesSuite extends BaseSuite {
  class Build(val root: String) {
    val inverseDependencies = mutable.Map.empty[String, ListBuffer[String]]
    def dependsOn(key: String, value: String): this.type = {
      val lst = inverseDependencies.getOrElseUpdate(value, ListBuffer.empty)
      lst += key
      this
    }
  }
  def root(x: String): Build = new Build(x)
  def check(
      name: String,
      original: Build,
      expected: String
  ): Unit = {
    test(name) {
      val obtained = BuildTargets.inverseDependencies(
        new BuildTargetIdentifier(original.root), { key =>
          original.inverseDependencies
            .get(key.getUri)
            .map(_.map(new BuildTargetIdentifier(_)))
        }
      )
      assertNoDiff(
        obtained.toSeq.map(_.getUri).sorted.mkString("\n"),
        expected
      )
    }
  }

  check(
    "basic",
    root("a")
      .dependsOn("b", "a"),
    "b"
  )

  check(
    "transitive",
    root("a")
      .dependsOn("b", "a")
      .dependsOn("c", "b"),
    "c"
  )

  check(
    "branch",
    root("a")
      .dependsOn("b", "a")
      .dependsOn("d", "a")
      .dependsOn("c", "b"),
    """
      |c
      |d
      |""".stripMargin
  )

  check(
    "alone",
    root("a"),
    "a"
  )

  check(
    "diamond",
    root("a")
      .dependsOn("b", "a")
      .dependsOn("c", "a")
      .dependsOn("d", "b")
      .dependsOn("d", "c"),
    "d"
  )

}
