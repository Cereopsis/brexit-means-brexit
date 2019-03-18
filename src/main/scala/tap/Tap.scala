package tap

import scalaz.zio.{UIO,ZIO,Ref}

trait FlowControl {
  def rejects: UIO[Boolean]
  def fail: UIO[Unit]
  def tick: UIO[Unit]
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
    UIO {
      new Tap[E1,E2] {
        def apply[R, E >: E2 <: E1, A](effect: ZIO[R, E, A]): ZIO[R, E, A] =
          for {
            _ <- (errBound.tick *> ZIO.fail(rejected)).whenM(errBound.rejects)
            result <- effect.foldM(
                      e => (if (qualified(e)) errBound.fail else errBound.tick) *> ZIO.fail(e),
                      a => errBound.tick *> UIO.succeed(a)
                    )
          } yield result
      }
    }
}
