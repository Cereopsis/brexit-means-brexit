package tap

import scalaz.zio.{UIO,ZIO,Ref}
import scala.util.{Either,Left,Right}
import scala.collection.mutable.Queue

/**
 * A Tap adjusts the flow of requests continuously.
 *
 * The flow is adjusted based on observed failures
 * that qualify (i.e. match some user-defined predicate).
 *
 * The tap must continuously adjust the percentage of tasks it
 * lets through until it finds the maximum flow rate that satisfies 
 * the user-defined maximum error bound.
 *
 * As the service is recovering, the failure rate will drop
 * below 1%, which will cause the tap to increase flow and 
 * let more tasks through.
 * 
 */

trait FlowControl {
  def rejects: UIO[Boolean]
  def fail: UIO[Unit]
  def tick: UIO[Unit]
}

final case class SimpleValve(
  window: Ref[Queue[Int]],
  threshold: Int) extends FlowControl {

  def rejects: UIO[Boolean] =
    window.get.map(_.sum > threshold)

  def tick: UIO[Unit] = update(0)

  def fail: UIO[Unit] = update(1)

  private [this] def update(i: Int): UIO[Unit] = 
    window.modify { q =>
      q.enqueue(i)
      q.dequeue() // maintain window size
      ((), q)
    }
}

trait Tap[-E1, +E2] {
  def apply[R, E >: E2 <: E1, A](
    effect: ZIO[R, E, A]): ZIO[R, E, A]
}

object Tap {
  
  type Percentage = FlowControl

  def make[E1, E2](
    errBound  : Percentage,
    qualified : E1 => Boolean, 
    rejected  : => E2
  ): UIO[Tap[E1, E2]] =
    UIO(new TapImpl(errBound, qualified, rejected))

  private [Tap] class TapImpl[E1,E2](
    flow: Percentage,
    qualifies: E1 => Boolean,
    rejects: => E2
  ) extends Tap[E1,E2] {
    def apply[R, E >: E2 <: E1, A](effect: ZIO[R, E, A]): ZIO[R, E, A] =
    for {
      _ <- (flow.tick *> ZIO.fail(rejects)).whenM(flow.rejects)
      result <- effect.foldM(
                e => (if (qualifies(e)) flow.fail else flow.tick) *> ZIO.fail(e),
                a => flow.tick *> UIO.succeed(a)
              )
    } yield result
  }
}
