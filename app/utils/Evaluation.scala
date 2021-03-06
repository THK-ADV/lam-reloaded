package utils

import scalaz.Monoid
import scalaz.syntax.monoid._

import scala.language.reflectiveCalls

object Evaluation {
  def lift2[E, V](v: V, e: List[E]): Evaluation[E, V] = Evaluation(e, v)

  def withValue[E, V](v: V): Evaluation[E, V] = Evaluation(List.empty[E], v)

  def withError[E, V: Monoid](e: List[E]): Evaluation[E, V] = Evaluation(e, mzero[V])

  def empty[E, V: Monoid]: Evaluation[E, V] = Evaluation(List.empty[E], mzero[V])
}

sealed case class Evaluation[+E, +V](err: List[E], value: V) {

  import _root_.utils.Evaluation._

  def add[E2 >: E](e: E2): Evaluation[E2, V] = mapErrWhole(_.+:(e))

  def map[B](f: V => B): Evaluation[E, B] = lift2(f(value), err)

  def mapErrWhole[E2](f: List[E] => List[E2]): Evaluation[E2, V] = lift2(value, f(err))

  def fold[B](f: (List[E], V) => B): B = f(err, value)

  def existsV(p: V => Boolean): Boolean = p(value)
}