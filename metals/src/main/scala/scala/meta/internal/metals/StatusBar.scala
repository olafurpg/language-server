package scala.meta.internal.metals

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._

final class StatusBar(client: MetalsLanguageClient, time: Time)
    extends Cancelable {
  private var future: ScheduledFuture[_] = _
  private var activeItem: Option[Item] = None
  sealed abstract class Item {
    val timer = new Timer(time)
    private val shownCounter = new AtomicInteger(0)
    def show(): Unit = shownCounter.incrementAndGet()
    def priority: Long = timer.elapsedNanos
    def isRecent: Boolean = timer.elapsedSeconds < 3
    def formattedMessage: String = this match {
      case Message(value) => value.text
      case Progress(message, _, maxTicks) =>
        val ticks = timer.elapsedSeconds.toInt
        if (ticks > maxTicks) {
          val seconds = Timer.readableSeconds(ticks)
          s"$message $seconds"
        } else {
          val i = ticks % 4
          // pad so length==3, keeps the message position stable
          val dots = ("." * i).padTo(3, ' ')
          message + dots
        }
    }
    def isOutdated: Boolean = timer.elapsedSeconds > 10
    def isShown: Boolean = shownCounter.get() > 0
    def isStale: Boolean = this match {
      case Message(_) =>
        isShown || isOutdated
      case Progress(_, job, _) => job.isCompleted
    }
  }
  private case class Message(params: MetalsStatusParams) extends Item
  private case class Progress(message: String, job: Future[_], maxTicks: Int)
      extends Item

  def addFuture(
      message: String,
      value: Future[_],
      maxDots: Int = Int.MaxValue
  ): Unit = {
    items.add(Progress(message, value, maxDots))
    tickIfHidden()
  }

  def addMessage(params: MetalsStatusParams): Unit = {
    items.add(Message(params))
    tickIfHidden()
  }
  def addMessage(message: String): Unit = {
    addMessage(MetalsStatusParams(message))
  }

  def start(
      sh: ScheduledExecutorService,
      initialDelay: Long,
      period: Long,
      unit: TimeUnit
  ): Unit = {
    cancel()
    future = sh.scheduleAtFixedRate(() => tick(), initialDelay, period, unit)
  }

  def tickIfHidden(): Boolean = {
    if (isHidden) tick()
    else false
  }
  private def isActiveMessage: Boolean = activeItem.exists {
    case m: Message => !m.isOutdated
    case _ => false
  }
  def tick(): Boolean = {
    garbageCollect()
    mostRelevant() match {
      case Some(value) =>
        activeItem = Some(value)
        val show: java.lang.Boolean = if (isHidden) true else null
        val params = value match {
          case Message(p) =>
            p.copy(show = show)
          case _ =>
            MetalsStatusParams(value.formattedMessage, show = show)
        }
        value.show()
        client.metalsStatus(params)
        isHidden = false
        true
      case None =>
        if (!isHidden) {
          if (isActiveMessage) {
            false
          } else {
            client.metalsStatus(MetalsStatusParams("", hide = true))
            isHidden = true
            activeItem = None
            true
          }
        } else {
          false
        }
    }
  }

  private var isHidden: Boolean = true
  private val items = new ConcurrentLinkedQueue[Item]()
  private def garbageCollect(): Unit = {
    items.removeIf(_.isStale)
  }
  private def mostRelevant(): Option[Item] = {
    if (items.isEmpty) None
    else {
      Some(items.asScala.maxBy { item =>
        (item.isRecent, item.priority)
      })
    }
  }

  override def cancel(): Unit = {
    if (future != null) {
      future.cancel(true)
    }
  }
}
