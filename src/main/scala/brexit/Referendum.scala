package brexit

import scalaz.zio._
import scala.collection.mutable.{Queue => MQ}
import scala.annotation.switch
import tap._

sealed trait Vote
final case class Leave(i: Int) extends Vote
case object Remain extends Vote

sealed trait Brexit
case object CustomsUnion extends Brexit
case object NorwayPlus extends Brexit
case object WTOTerms extends Brexit
case object NoDeal extends Brexit

sealed trait ExitStatus extends Brexit
case object Rejected extends ExitStatus
case object Soft extends ExitStatus


class Ballot(queue: Queue[Vote], monitor: Tap[Brexit,ExitStatus]) {

  def cast(vote: Vote): UIO[Boolean] =
    queue.offer(vote)

  def open = {
    (for {
      vote <- queue.take
      io <- monitor(Ballot.process(vote)).either
    } yield io).forever.fork
  }

  def close: UIO[Unit] = queue.shutdown
  
  def process(vote: Vote): ZIO[Any,Brexit,Vote] =
    vote match {
      case Remain   => UIO.succeed(vote)
      case Leave(i) =>
        (i: @switch) match {
          case 1 => ZIO.fail(WTOTerms)
          case 2 => ZIO.fail(NoDeal)
          case _ => UIO.succeed(vote)
        }
    }
}

object Ballot {

  def make(capacity: Int, errors: Int): UIO[Ballot] =
    for {
      queue <- Queue.bounded[Vote](capacity)
      ref <- Ref.make(MQ.fill(capacity)(0))
      monitor <- Tap.make[Brexit,ExitStatus](SimpleValve(ref, errors), _ == NoDeal, Rejected)
      box = new Ballot(queue, monitor)
    } yield box

  def process(vote: Vote): ZIO[Any,Brexit,Vote] =
    vote match {
      case Remain   => UIO.succeed(vote)
      case Leave(i) =>
        (i: @switch) match {
          case 1 => ZIO.fail(WTOTerms)
          case 2 => ZIO.fail(NoDeal)
          case _ => UIO.succeed(vote)
        }
    }
}
