package com.coldcore.slotsbooker.ms

import java.util.Calendar

import org.scalatest._

class TimestampSpec extends FlatSpec with Matchers with BeforeAndAfter {

  val c = Timestamp.asCalendar

  before {
    c.set(Calendar.YEAR, 2017)
    c.set(Calendar.MONTH, 2)
    c.set(Calendar.DAY_OF_MONTH, 25)
    c.set(Calendar.HOUR_OF_DAY, 21)
    c.set(Calendar.MINUTE, 26)
    c.set(Calendar.SECOND, 59)
    c.set(Calendar.MILLISECOND, 987)
  }

  "asString" should "format a calendar as yyyyMMddHHmmssSSS" in {
    Timestamp.asString(c) shouldBe "20170325212659987"

    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    Timestamp.asString(c) shouldBe "20170325000000000"

    Timestamp.asString(Timestamp.asCalendar("20170325"+"200146"))       shouldBe "20170325"+"200146"+"000"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"200146"+"000")) shouldBe "20170325"+"200146"+"000"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"200146"+"001")) shouldBe "20170325"+"200146"+"001"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"200146"+"021")) shouldBe "20170325"+"200146"+"021"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"200146"+"321")) shouldBe "20170325"+"200146"+"321"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"200146"+"320")) shouldBe "20170325"+"200146"+"320"

    Timestamp.asString(Timestamp.asCalendar(20170325, 200146))      shouldBe "20170325"+"200146"+"000"
    Timestamp.asString(Timestamp.asCalendar(20170325, 200146,   0)) shouldBe "20170325"+"200146"+"000"
    Timestamp.asString(Timestamp.asCalendar(20170325, 200146,   1)) shouldBe "20170325"+"200146"+"001"
    Timestamp.asString(Timestamp.asCalendar(20170325, 200146,  21)) shouldBe "20170325"+"200146"+"021"
    Timestamp.asString(Timestamp.asCalendar(20170325, 200146, 321)) shouldBe "20170325"+"200146"+"321"
    Timestamp.asString(Timestamp.asCalendar(20170325, 200146, 320)) shouldBe "20170325"+"200146"+"320"

    Timestamp.asString(Timestamp.asCalendar("20170325"+"000046")) shouldBe "20170325"+"000046"+"000"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"000000")) shouldBe "20170325"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar("10010101"+"000000")) shouldBe "10010101"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar("00010101"+"000000")) shouldBe "00010101"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar("99990101"+"000000")) shouldBe "99990101"+"000000"+"000"

    Timestamp.asString(Timestamp.asCalendar(20170325, 46)) shouldBe "20170325"+"000046"+"000"
    Timestamp.asString(Timestamp.asCalendar(20170325,  0)) shouldBe "20170325"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar(10010101,  0)) shouldBe "10010101"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar(   10101,  0)) shouldBe "00010101"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar(99990101,  0)) shouldBe "99990101"+"000000"+"000"

    Timestamp.asString(Timestamp.min) shouldBe "00010101"+"000000"+"000"
    Timestamp.asString(Timestamp.max) shouldBe "99990101"+"000000"+"000"
  }

  "asString" should "roll over on hours excess" in {
    Timestamp.asString(Timestamp.asCalendar("20170325"+"240000"+"000")) shouldBe "20170326"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"250000"+"000")) shouldBe "20170326"+"010000"+"000"
    Timestamp.asString(Timestamp.asCalendar("20170325"+"251234"+"567")) shouldBe "20170326"+"011234"+"567"

    Timestamp.asString(Timestamp.asCalendar(20170325, 240000,   0)) shouldBe "20170326"+"000000"+"000"
    Timestamp.asString(Timestamp.asCalendar(20170325, 250000,   0)) shouldBe "20170326"+"010000"+"000"
    Timestamp.asString(Timestamp.asCalendar(20170325, 251234, 567)) shouldBe "20170326"+"011234"+"567"
  }

  "dateString" should "return yyyyMMdd" in {
    Timestamp.dateString(c) shouldBe "20170325"
  }

  "timeString" should "return HHmmss" in {
    Timestamp.timeString(c) shouldBe "212659"
  }

  "asCalendar" should "construct a calendar from string" in {
    Timestamp.asCalendar("20170325212659987") shouldBe c

    c.set(Calendar.MILLISECOND, 0)
    Timestamp.asCalendar("20170325212659") shouldBe c

    c.set(Calendar.MILLISECOND, 77)
    Timestamp.asCalendar("20170325212659077") shouldBe c

    c.set(Calendar.MILLISECOND, 7)
    Timestamp.asCalendar("20170325212659007") shouldBe c

    c.set(Calendar.MILLISECOND, 0)
    c.set(Calendar.HOUR_OF_DAY, 2)
    c.set(Calendar.MINUTE, 4)
    c.set(Calendar.SECOND, 6)
    Timestamp.asCalendar("20170325020406") shouldBe c

    c.set(Calendar.YEAR, 1)
    c.set(Calendar.MONTH, 1)
    c.set(Calendar.DAY_OF_MONTH, 3)
    Timestamp.asCalendar("00010203020406") shouldBe c
  }

  "asCalendar" should "construct a calendar from date and time" in {
    Timestamp.asCalendar(20170325, 212659, 987) shouldBe c

    c.set(Calendar.MILLISECOND, 0)
    Timestamp.asCalendar(20170325, 212659) shouldBe c

    c.set(Calendar.MILLISECOND, 77)
    Timestamp.asCalendar(20170325, 212659, 77) shouldBe c

    c.set(Calendar.MILLISECOND, 7)
    Timestamp.asCalendar(20170325, 212659, 7) shouldBe c

    c.set(Calendar.MILLISECOND, 0)
    c.set(Calendar.HOUR_OF_DAY, 2)
    c.set(Calendar.MINUTE, 4)
    c.set(Calendar.SECOND, 6)
    Timestamp.asCalendar(20170325, 20406) shouldBe c

    c.set(Calendar.YEAR, 1)
    c.set(Calendar.MONTH, 1)
    c.set(Calendar.DAY_OF_MONTH, 3)
    Timestamp.asCalendar(10203, 20406) shouldBe c
  }

  "copyTz" should "convert to timezone" in {
    val cet = Timestamp.copyTz(c, "CET")

    cet shouldBe Timestamp.addHours(c, 1)
  }

  "offsetInMinutes" should "return difference in minutes" in {
    val cet = Timestamp.copyTz(c, "CET")
    Timestamp.offsetInMinutes(cet, c) shouldBe 60
  }

}
