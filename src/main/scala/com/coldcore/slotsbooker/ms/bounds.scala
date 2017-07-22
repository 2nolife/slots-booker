package com.coldcore.slotsbooker
package ms

import java.util.Calendar
import ms.{Timestamp => ts}

object BoundsUtil {

  case class Bound(date: Option[Int], time: Option[Int], before: Option[Int]) // "time" w/o seconds, "before" in minutes
  object Bound {
    def apply(c: Calendar): Bound = {
      val t = ts.asString(c)
      Bound(Some(t.take(8).toInt), Some(t.slice(8, 12).toInt), None)
    }
    def apply(date: Int, time: Int): Bound = apply(ts.asCalendar(date, time*100))
  }

  case class Dates(dateFrom: Int, dateTo: Int, timeFrom: Int, timeTo: Int) // time w/o seconds

  def offsetPoint(point: Calendar, bound: Bound, inclusive: Boolean = false): (Int,Int) = {
    val t = ts.asString(point)
    offset(t.take(8).toInt, t.slice(8, 12).toInt, bound, inclusive)
  }

  def offset(date: Int, time: Int, bound: Bound, inclusive: Boolean = false): (Int,Int) = {
    val (days, mins) =
      if (bound.before.isEmpty) (0, 0)
      else {
        val m = bound.before.get
        if (m < 1440) (0, m) //less than a day
        else { // one day or more
          val d = m%1440
          (d, m-d*1440)
        }
      }
    val c0 = ts.asCalendar(date, time*100)
    val c1 = ts.addDays(c0, -days)
    val c2 = ts.addMinutes(c1, -mins)
    val c = ts.asString(c2)

    val ndate =
      if (bound.before.isDefined) c.take(8).toInt
      else if (bound.date.isDefined) bound.date.get
      else date
    val ntime =
      if (bound.time.isDefined) bound.time.get
      else if (bound.before.isDefined) c.slice(8, 12).toInt
      else time

    val t0 = ts.asCalendar(ndate, ntime*100)
    val is2400 =
      if (inclusive && (ntime == 2400 || ntime == 0)) {
        ts.addDays(t0, -1)
        true
      } else false
    val t = ts.asString(t0)
    (t.take(8).toInt, if (is2400) 2400 else t.slice(8, 12).toInt)
  }

  def compare(point: Calendar, dates: Dates, open: Option[Bound], close: Option[Bound]): Int = {
    val (boundA, boundB) = (open.getOrElse(Bound(ts.min)), close.getOrElse(Bound(ts.max))) // [A .. B)
    import dates._

    val f = (of: (Int,Int)) => of match { case (date, time) => (date.toLong*10000+time)*100*1000 }
    val (p, from, to, from2) = ( // yyyyMMddHHmmssSSS
      ts.asLong(point),
      f(offset(dateFrom, timeFrom, boundA)),
      f(offset(dateTo, timeTo, boundB)),
      f(offset(dateFrom, timeFrom, boundB)))

    val (a, b) =
      if (from > to) (from2, to) 
      else (from, to)

    if (p < a) -1
    else if (p >= b) 1
    else 0
  }
}

