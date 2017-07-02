package com.coldcore.slotsbooker
package ms.places

import ms.Timestamp._

object DateTimeUtil {

  private def datetime(timezone: Option[String], offsetMinutes: Option[Int]): vo.DateTime = {
    val utc = asCalendar
    val local = timezone.map(copyTz(utc, _)) orElse offsetMinutes.map(addMinutes(copy(utc), _)) getOrElse utc
    val offset = timezone.map(_ => offsetInMinutes(local, utc)) orElse offsetMinutes getOrElse 0

    vo.DateTime(
      timezone = timezone,
      offset_minutes = Some(offset),
      date = Some(dateString(local).toInt),
      time = Some(timeString(local).toInt),
      utc_date = Some(dateString(utc).toInt),
      utc_time = Some(timeString(utc).toInt)
    )
  }

  def datetime(dt: vo.DateTime): vo.DateTime = datetime(dt.timezone, dt.offset_minutes)

  def datetime: vo.DateTime = datetime(None, Some(0))

}