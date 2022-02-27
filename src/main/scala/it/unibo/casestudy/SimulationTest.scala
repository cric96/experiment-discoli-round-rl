package it.unibo.casestudy
import it.unibo.casestudy.DesIncarnation._
import it.unibo.casestudy.event.{AdjustableEvaluation, RoundAtEach}
import it.unibo.casestudy.utils.ExperimentTrace

import java.time.Instant
import scala.concurrent.duration._
import scala.language.postfixOps
import org.nspl._
import org.nspl.awtrenderer._

object SimulationTest extends App {
  val count = 10
  val range = 10
  val delta = 100 milliseconds
  val totalTime = 80 seconds
  val switchTime = totalTime / 2
  val leftmost = 1
  val rightmost = 100

  def newSimulator(fireLogic: ID => RoundEvent): (ExperimentTrace[Int], ExperimentTrace[Double]) = {
    val world = StandardWorld.withRange(count, count, range, Set(leftmost))
    val des = new DesSimulator(world)
    val fireEvents = des.network.ids.map(fireLogic(_))
    val roundCount = Exports.NumericValueExport.fromSensor[Int](des.now, 1 seconds, ExperimentConstant.RoundCount)
    val totalGradient = Exports.NumericValueExport.`export`[Double](des.now, 1 seconds)
    val turnOffLeft = ChangeSourceAt(des.now.plusMillis(switchTime.toMillis), leftmost, value = false)
    val turnOnRight = ChangeSourceAt(des.now.plusMillis(switchTime.toMillis), rightmost, value = true)
    des.schedule(roundCount)
    des.schedule(totalGradient)
    des.schedule(turnOffLeft)
    des.schedule(turnOnRight)
    fireEvents.foreach(des.schedule)
    des.stopWhen(des.now.plusMillis(totalTime.toMillis))
    DesUtils.consume(des)
    (roundCount.trace, totalGradient.trace)
  }

  val (standardFrequency, gradientStandard) =
    newSimulator(id => RoundAtEach(id, new GradientProgram, Instant.ofEpochMilli(0), delta))
  val (adjustableFrequency, adjustableGradient) =
    newSimulator(id => AdjustableEvaluation(id, new GradientProgram, Instant.ofEpochMilli(0), delta, 2 seconds, delta))

  val fixedOutputPlot = gradientStandard.values.map { case (time, data) => time.getEpochSecond.toDouble -> data }
  val adjustableOutputPlot = adjustableGradient.values.map { case (time, data) =>
    time.getEpochSecond.toDouble -> data
  }

  val fixedFrequencyPlot = standardFrequency.values.map { case (time, data) =>
    time.getEpochSecond.toDouble -> data.toDouble
  }
  val adjustableFrequencyPlot = adjustableFrequency.values.map { case (time, data) =>
    time.getEpochSecond.toDouble -> data.toDouble
  }
  val outputPlot = xyplot(
    (fixedOutputPlot.toList, List(line(color = Color.red)), InLegend("Periodic")),
    (adjustableOutputPlot.toList, List(line(color = Color.blue)), InLegend("Adjustable"))
  )(
    par(xlab = "time", ylab = "total output")
  )

  val frequencyPlot = xyplot(
    //(fixedFrequencyPlot.toList, List(line(color = Color.red)), InLegend("Periodic")),
    (adjustableFrequencyPlot.toList, List(line(color = Color.blue)), InLegend("Adjustable"))
  )(
    par(xlab = "time", ylab = "total frequency")
  )

  show(sequence(List(outputPlot, frequencyPlot), TableLayout(2)))
}