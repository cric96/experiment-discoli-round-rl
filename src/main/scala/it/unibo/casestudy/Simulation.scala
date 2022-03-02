package it.unibo.casestudy

trait Simulation[E] {
  def perform(): E
  final def repeat(times: Int)(progressEvaluation: (E, Int) => Unit): Seq[E] =
    LazyList
      .continually(perform())
      .zipWithIndex
      .tapEach { case (index, elem) => progressEvaluation(index, elem) }
      .tapEach(_ => updateAfter())
      .map(_._1)
      .take(times)
  def updateAfter(): Unit
}