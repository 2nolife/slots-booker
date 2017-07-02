package com.coldcore.slotsbooker.ms

import java.util.{Calendar, TimeZone}

object Timestamp {

  def asCalendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

  def asString: String = asString(asCalendar)
  def asLong: Long = asString.toLong
  def asLong(c: Calendar): Long = asString(c).toLong
  def dateString(c: Calendar): String = asString(c).take(8)
  def timeString(c: Calendar): String = asString(c).slice(8, 14)

  def min: Calendar = asCalendar("00010101000000")
  def max: Calendar = asCalendar("99990101000000")

  def asString(c: Calendar): String = { // format: yyyyMMddHHmmssSSS
    val prepend = (i: Int, n: Int) => i.toString.reverse.padTo(n, "0").reverse.mkString
    prepend(c.get(Calendar.YEAR), 4) +
      (c.get(Calendar.MONTH)+1 :: c.get(Calendar.DAY_OF_MONTH) ::
       c.get(Calendar.HOUR_OF_DAY) :: c.get(Calendar.MINUTE) :: c.get(Calendar.SECOND) :: Nil)
        .map(prepend(_, 2)).mkString +
      prepend(c.get(Calendar.MILLISECOND), 3)
  }

  def asCalendar(s: String): Calendar = {
    assert(s.length == 14 || s.length == 17, s"String size is ${s.length} but should be either 14 or 17")
    val xs = s.take(4).toInt :: s.grouped(2).drop(2).map(_.toInt).toList
    val c = asCalendar
    c.set(Calendar.YEAR, xs.head)
    c.set(Calendar.MONTH, xs(1)-1)
    c.set(Calendar.DAY_OF_MONTH, xs(2))
    c.set(Calendar.HOUR_OF_DAY, xs(3))
    c.set(Calendar.MINUTE, xs(4))
    c.set(Calendar.SECOND, xs(5))
    c.set(Calendar.MILLISECOND, if (s.length == 17) s.takeRight(3).toInt else 0)
    c
  }

  def asCalendar(date: Int, time: Int, ms: Int = 0): Calendar = {
    val prepend = (i: Int, n: Int) => i.toString.reverse.padTo(n, "0").reverse.mkString
    asCalendar(prepend(date, 8)+prepend(time, 6)+prepend(ms, 3))
  }

  def copyTz(c: Calendar, timezone: String): Calendar = {
    assert(c.getTimeZone.getID == "UTC", s"Calendar timezone is ${c.getTimeZone.getID} but should be UTC")
    val nc = copy(c)
    nc.add(Calendar.MILLISECOND, TimeZone.getTimeZone(timezone).getRawOffset)
    nc
  }

  def copy(c: Calendar): Calendar = asCalendar(asString(c))

  def offsetInMinutes(c1: Calendar, c2: Calendar): Int =
    ((c1.getTimeInMillis-c2.getTimeInMillis)/1000L/60L).toInt

  def add(c: Calendar, field: Int, value: Int): Calendar = {
    c.add(field, value)
    c
  }

  def addSeconds(c: Calendar, value: Int): Calendar =
    add(c, Calendar.SECOND, value)

  def addMinutes(c: Calendar, value: Int): Calendar =
    add(c, Calendar.MINUTE, value)

  def addHours(c: Calendar, value: Int): Calendar =
    add(c, Calendar.HOUR_OF_DAY, value)

  def addDays(c: Calendar, value: Int): Calendar =
    add(c, Calendar.DAY_OF_MONTH, value)

}