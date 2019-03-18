package tap

import org.scalatest.{FlatSpec,Matchers}
import scalaz.zio._
import scala.collection.mutable.{Queue => MQ}
import scala.annotation.switch
import brexit._

class TapSpec extends FlatSpec with Matchers with DefaultRuntime {
  
  import Ballot.process

  def ballot(votes: List[Vote]) =
    unsafeRun(for {
      ref <- Ref.make(MQ.fill(5)(0))
      monitor <- Tap.make[Brexit,ExitStatus](FraudMeter(ref, 1), _ == NoDeal, Rejected)
      io <- ZIO.foreach(votes)(vote => monitor(process(vote)).either)
    } yield io).map {
      case Right(x) => x
      case Left(y)  => y
    }

  "Tap" should "work" in {
    val result = ballot(List.fill(5)(3).map(Leave(_)))
    assert(result.forall(_ == Leave(3)))
  }
  
  it should "ignore unqualified errors" in {
    val result = ballot(List.fill(5)(1).map(Leave(_)))
    result should be (List(WTOTerms, WTOTerms, WTOTerms, WTOTerms, WTOTerms))
  }

  it should "not reject when qualified errors =< 1" in {
    val result = ballot(List(Remain,Remain,Remain,Remain,Leave(2),Remain))
    result should be (List(Remain, Remain, Remain, Remain, NoDeal, Remain))
  }
  
  it should "reject when qualified errors > 1" in {
    val result = ballot(List(Remain,Leave(2),Leave(2),Remain,Remain,Remain))
    result should be (List(Remain, NoDeal, NoDeal, Rejected, Rejected, Rejected))
  }

  it should "stop rejecting when qualified errors drop below threshold" in {
    val result = ballot(List(Leave(2),Leave(2),Remain,Remain,Remain,Remain,Remain))
    result should be (List(NoDeal, NoDeal, Rejected, Rejected, Rejected, Rejected, Remain))
  }

}

