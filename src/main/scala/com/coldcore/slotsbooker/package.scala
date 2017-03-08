package com.coldcore

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

package object slotsbooker {

  type JCollection[A] = java.util.Collection[A]
  type JList[A] = java.util.List[A]
  type JMap[A,B] = java.util.Map[A,B]
  type JEnumeration[A] = java.util.Enumeration[A]

  implicit def javaCollection2Scala[T](o: JCollection[T]) = o.asScala
  implicit def javaList2Scala[T](o: JList[T]) = o.asScala
  implicit def javaMap2Scala[T,U](o: JMap[T,U]) = o.asScala
  implicit def javaEnumeration2Scala[T](o: JEnumeration[T]) = o.asScala

  def withOpen[R <: { def close(): Unit }, T](r: R)(f: R => T) =
    try { f(r) } finally { r.close }

  /**
   * new File("/") |> { f => new File(f, "Users") } |> { case f if f.isDirectory => "dir"; case _ => "file" }
   * val x = new BasicDataSource |< { ds =>  ds.setUsername(getProperty("User")); ds.setPassword(getProperty("Password")) }
   */
  implicit class Piper[A](a: A) {
    def |>[B](f: A => B): B = f(a)
    def |<(f: A => Any): A = { f(a); a }
  }

  // http://loicdescotte.github.io/posts/scala-compose-option-future/
  case class FutureO[+A](future: Future[Option[A]]) extends AnyVal {
    def flatMap[B](f: A => FutureO[B])(implicit ec: ExecutionContext): FutureO[B] = {
      val newFuture = future.flatMap{
        case Some(a) => f(a).future
        case None => Future.successful(None)
      }
      FutureO(newFuture)
    }

    def map[B](f: A => B)(implicit ec: ExecutionContext): FutureO[B] = {
      FutureO(future.map(option => option map f))
    }
  }

  implicit class EmptySeq[A](xs: Option[Seq[A]]) {
    def noneIfEmpty =
      xs match {
        case Some(Seq()) => None
        case x => x
      }
  }

  implicit class EmptyString[A](x: Option[String]) {
    def noneIfEmpty =
      x match {
        case Some("") | Some(null) => None
        case _ => x
      }
  }

}
