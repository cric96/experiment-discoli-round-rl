package it.unibo.casestudy.launch

import com.github.tototoshi.csv.CSVReader
import it.unibo.casestudy.launch.LaunchConstant._
import org.nspl._
import org.nspl.awtrenderer._
import scribe.Level
import it.unibo.casestudy.utils.UnsafeProduct._
import java.io.File
import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable

object Plot extends App {
  def tempFile = java.io.File.createTempFile("nspl", ".svg")
  val width = 400
  // line constans
  private val redLine = line(color = Color.red)
  private val greenLine = line(color = Color.green)
  private val blueLine = line(color = Color.blue)
  private val darkBlueLine = line(color = Color(0, 0, 139))
  private val darkGreenLine = line(color = Color(0, 139, 0))

  val toSample = 50 // one plot each 100 experiments
  val regex = raw"(.*)rl-(\d+)(.*)".r
  def sample(name: String): Boolean = if (args.length == 1 && args(0) == "sample") {
    name match {
      case regex(_, number, _) => number.toInt % toSample == 0
    }
  } else {
    true
  }
  // Prepare
  type PlainData = (Double, Double, Double)
  // Rl and Adhoc
  type GeneratedData = (Double, Double, Double, Double)
  val toSecondConversion = 1000.0
  val resultFolder = os.pwd / LaunchConstant.resFolder
  val imageFolder = os.pwd / LaunchConstant.imageFolder
  if (os.exists(imageFolder)) { os.remove.all(imageFolder) }
  os.makeDir.all(imageFolder)
  val allFiles = os.list(resultFolder).filter(os.isFile).filter(_.toString.contains(".csv"))

  // Load data
  val (_, fixed) = load(allFiles, fixedName, convertPlain).head
  val (_, adHoc) = load(allFiles, adhocName, convertOther).head

  if (os.list(resultFolder).size > 1) {
    scribe.Logger.root
      .clearHandlers()
      .clearModifiers()
      .withHandler(minimumLevel = Some(Level.Warn))
      .replace()
  }
  // One folder for each configuration
  allExperiment(resultFolder).par.foreach { rlFolder =>
    val experimentName = rlFolder.toIO.getName
    scribe.warn(s"Handle: $experimentName")
    val allFiles = os
      .list(rlFolder)
      .filter(os.isFile)
      .filter(_.toString.contains(".csv"))
    // One file foreach episode
    val rl = load(allFiles, rlName, convertOther, sample)
    val (_, error) = load(allFiles, errorName, convertSingle).head
    val (_, totalTicks) = load(allFiles, totalTicksName, convertSingle).head
    // Plots preparation
    val errorPlot = xyplot(
      (error, List(redLine), InLegend("Error"))
    )(
      par(xlab = "time", ylab = "Root Mean Squared Error")
    )
    val totalTickPlot = xyplot(
      (totalTicks, List(line(color = Color.apply(255, 255, 0))), InLegend("Average ticks per second"))
    )(
      par(xlab = "time", ylab = "Ticks per seconds")
    )
    os.makeDir(imageFolder / experimentName)
    // Plot storage
    rl.foreach { case (name, data) =>
      scribe.info(s"process: $name")
      plotRl(imageFolder / experimentName, data, fixed, adHoc, name)
    }
    store(svgToFile(tempFile, errorPlot, width), imageFolder / experimentName / s"error.svg")
    store(svgToFile(tempFile, totalTickPlot, width), imageFolder / experimentName / s"ticks.svg")
    scribe.warn(s"End: $experimentName")
  }

  // Utility functions
  def convertPlain(data: List[String]): PlainData =
    (data.head.toLong / toSecondConversion, data(1).toDouble, data(2).toDouble)

  // Utility functions
  def convertOther(data: List[String]): GeneratedData =
    (data.head.toLong / toSecondConversion, data(1).toDouble, data(2).toDouble, data(3).toDouble)

  def convertSingle(data: List[String]): Double = data.head.toDouble

  def allExperiment(resultFolder: os.Path): Seq[os.Path] = os.list(resultFolder).filter(os.isDir)
  def load[E](
      allFiles: Seq[os.Path],
      target: String,
      conversion: List[String] => E,
      filter: String => Boolean = _ => true
  ): (Seq[(String, Seq[E])]) =
    allFiles
      .filter(_.baseName.contains(target))
      .filter(f => filter(f.baseName))
      .map(f => f.baseName -> CSVReader.open(f.toIO))
      .map { case (name, reader) => (name, reader.all().tail, reader) }
      .tapEach(_._3.close())
      .map { case (name, dataL, _) => name -> dataL.map(conversion) }

  def plotRl(
      where: os.Path,
      rl: Seq[GeneratedData],
      fixed: Seq[PlainData],
      adhoc: Seq[GeneratedData],
      label: String = ""
  ): Unit = {
    def convert(data: Seq[Product], select: (Product) => Double) = data.map { t =>
      (t._1[Double], select(t))
    }

    def tickPerSeconds(trace: Seq[Product]): Seq[(Double, Double)] = {
      trace.dropRight(1).zip(trace.tail).map { case (first, second) =>
        (first._1[Double], second._2[Double] - first._2[Double])
      }
    }
    def percentage(ref: Seq[Product], current: Seq[Product], select: Product => Double): Seq[(Double, Double)] = {
      ref.zip(current).tail.map { case (l, r) =>
        (l._1, Math.abs((select(l) - select(r))) / select(l))
      }
    }
    val outputPlot = xyplot(
      (convert(fixed, _._3[Double]), List(redLine), InLegend("Periodic")),
      (convert(adhoc, _._3[Double]), List(greenLine), InLegend("Ad Hoc")),
      (convert(rl, _._3[Double]), List(blueLine), InLegend("Rl"))
    )(
      par(xlab = "time", ylab = "total output")
    )

    val totalTicksPlot = xyplot(
      (convert(fixed, _._2[Double]), List(redLine), InLegend("Periodic")),
      (convert(adhoc, _._2[Double]), List(greenLine), InLegend("Ad Hoc")),
      (convert(rl, _._2[Double]), List(blueLine), InLegend("Rl"))
    )(
      par(xlab = "time", ylab = "total ticks")
    )

    val frequencyPlot = xyplot(
      (tickPerSeconds(fixed), List(redLine), InLegend("Periodic")),
      (tickPerSeconds(adhoc), List(greenLine), InLegend("Ad Hoc")),
      (tickPerSeconds(rl), List(blueLine), InLegend("Rl"))
    )(
      par(xlab = "time", ylab = "ticks per second")
    )

    val errorPerSecond = xyplot(
      (convert(adhoc, _._4[Double]), List(greenLine), InLegend("Ad Hoc")),
      (convert(rl, _._4[Double]), List(blueLine), InLegend("Rl"))
    )(
      par(xlab = "time", ylab = "ticks per second")
    )
    val percentageOfRoundAndOutput = xyplot(
      (percentage(fixed, adhoc, _._3[Double]), List(greenLine), InLegend("Ad Hoc Error Percentage")),
      (percentage(fixed, rl, _._3[Double]), List(blueLine), InLegend("Rl Error Percentage")),
      (percentage(fixed, adhoc, _._2[Double]), List(darkGreenLine), InLegend("Ad Hoc Frequency Percentage")),
      (percentage(fixed, rl, _._2[Double]), List(darkBlueLine), InLegend("Rl Frequency Percentage"))
    )(
      par(xlab = "time", ylab = "Percentage")
    )
    val elements = sequence(
      List(outputPlot, totalTicksPlot, frequencyPlot, errorPerSecond, percentageOfRoundAndOutput),
      TableLayout(2)
    )
    store(
      svgToFile(tempFile, elements, width),
      where / s"image-$label.svg"
    )
  }

  def store(file: File, path: os.Path): Unit =
    os.copy.over(os.Path(file.toPath), path)
}
