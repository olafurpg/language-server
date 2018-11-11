package tests

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Promise
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.MetalsStatusParams
import scala.meta.internal.metals.StatusBar

object StatusBarSuite extends BaseSuite {
  class FakeClient extends SilentClient {
    def clear(): Unit = statusParams.clear()
    def history: String = {
      statusParams.asScala
        .map { params =>
          if (params.show) {
            s"<show> - ${params.text}".trim
          } else if (params.hide) {
            "<hide>"
          } else {
            params.text.trim
          }
        }
        .mkString("\n")
    }
    private val statusParams = new ConcurrentLinkedQueue[MetalsStatusParams]()
    override def metalsStatus(params: MetalsStatusParams): Unit = {
      statusParams.add(params)
    }
  }
  val time = new FakeTime
  val client = new FakeClient
  var status: StatusBar = new StatusBar(client, time)
  override def utestBeforeEach(path: Seq[String]): Unit = {
    client.clear()
    status.cancel()
  }

  def tickSecond(): Unit = {
    time.elapseSeconds(1)
    status.tick()
  }

  test("message") {
    status.addMessage("tick 1")
    time.elapseSeconds(5)
    status.addMessage("tick 2")
    status.tick()
    time.elapseSeconds(11)
    status.tick()
    assertNoDiff(
      client.history,
      """|
         |<show> - tick 1
         |tick 2
         |<hide>
         |""".stripMargin
    )
  }

  test("future") {
    val promise = Promise[Unit]()
    status.addFuture("tick", promise.future, 5)
    1.to(7).foreach { _ =>
      tickSecond()
    }
    promise.success(())
    status.tick()
    assertNoDiff(
      client.history,
      """|
         |<show> - tick
         |tick.
         |tick..
         |tick...
         |tick
         |tick.
         |tick 6s
         |tick 7s
         |<hide>
         |""".stripMargin
    )
  }

  test("race") {
    val promise1 = Promise[Unit]()
    val promise2 = Promise[Unit]()
    status.addFuture("a", promise1.future, -1)
    tickSecond()
    status.addFuture("b", promise2.future, -1)
    1.to(2).foreach { _ =>
      tickSecond()
    }
    promise1.success(())
    1.to(2).foreach { _ =>
      tickSecond()
    }
    promise2.success(())
    status.tick()
    assertNoDiff(
      client.history,
      """|
         |<show> - a 0s
         |a 1s
         |a 2s
         |b 2s
         |b 3s
         |b 4s
         |<hide>
         |""".stripMargin
    )
  }
}
