package com.coldcore.slotsbooker
package ms

import BoundsUtil._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest._
import ms.{Timestamp => ts}

class BoundsUtilSpec extends FlatSpec with MockitoSugar with Matchers with BeforeAndAfter {

  val noBound = Bound(date = None, time = None, before = None)
  val mday = 24*60 //minutes in a day

  val (oneWeekBeforeAt8, twoDaysBeforeAt24, oneDayBefore) =(
    noBound.copy(before = Some(7*mday), time = Some(800)),
    noBound.copy(before = Some(2*mday), time = Some(2400)),
    noBound.copy(before = Some(1*mday)))

  val datesA = Dates(dateFrom = 20170324, timeFrom = 1200, dateTo = 20170324, timeTo = 1215)

  "offset" should "offset a date by a bound" in {
    offset(20170324, 1200, noBound) shouldBe (20170324, 1200)

    offset(20170324, 1200, noBound.copy(date = Some(20170320)))                    shouldBe (20170320, 1200)
    offset(20170324, 1200, noBound.copy(date = Some(20170320), time = Some(1100))) shouldBe (20170320, 1100)
    offset(20170324, 1200, noBound.copy(                       time = Some(1100))) shouldBe (20170324, 1100)
    offset(20170324, 1200, noBound.copy(                       time = Some(2400))) shouldBe (20170325,    0)
    offset(20170324, 1200, noBound.copy(date = Some(20170320), time = Some(2400))) shouldBe (20170321,    0)

    offset(20170324, 1200, noBound.copy(before = Some(0*mday)))                    shouldBe (20170324, 1200)
    offset(20170324, 1200, noBound.copy(before = Some(1*mday)))                    shouldBe (20170323, 1200)
    offset(20170324, 1200, noBound.copy(before = Some(2*mday), time = Some(800)))  shouldBe (20170322,  800)
    offset(20170324, 1200, noBound.copy(before = Some(2*mday), time = Some(2400))) shouldBe (20170323,    0)
    offset(20170324, 1200, noBound.copy(before = Some(14*mday)))                   shouldBe (20170310, 1200)

    offset(20170324, 1200, noBound.copy(before = Some( 30)))       shouldBe (20170324, 1130)
    offset(20170324, 1200, noBound.copy(before = Some(-30)))       shouldBe (20170324, 1230)
    offset(20170324, 1200, noBound.copy(before = Some(1*mday+30))) shouldBe (20170323, 1130)
    offset(20170324, 1200, noBound.copy(before = Some(1*mday-30))) shouldBe (20170323, 1230)
    offset(20170324,    0, noBound.copy(before = Some( 30)))       shouldBe (20170323, 2330)
    offset(20170324, 2400, noBound.copy(before = Some(-30)))       shouldBe (20170325,   30)

    // ignore date
    offset(20170324, 1200, noBound.copy(date = Some(20170320), time = Some(800), before = Some(30)))     shouldBe (20170324, 800)
    offset(20170324, 1200, noBound.copy(date = Some(20170320), time = Some(800), before = Some(1*mday))) shouldBe (20170323, 800)

    // month roll over back anf forth
    offset(20170301, 1200, noBound.copy(before = Some( mday))) shouldBe (20170228, 1200)
    offset(20160301, 1200, noBound.copy(before = Some( mday))) shouldBe (20160229, 1200)
    offset(20170228, 1200, noBound.copy(before = Some(-mday))) shouldBe (20170301, 1200)
    offset(20160229, 1200, noBound.copy(before = Some(-mday))) shouldBe (20160301, 1200)

    // exclusive 24:00
    offset(20170324, 1200, noBound.copy(date = Some(20170320), time = Some(2400))) shouldBe (20170321,    0)
    offset(20170324, 1200, noBound.copy(before = Some(2*mday), time = Some(2400))) shouldBe (20170323,    0)
    offset(20170324, 1200, noBound.copy(before = Some(mday),   time = Some(2400))) shouldBe (20170324,    0)
    offset(20170324, 2400, noBound.copy(before = Some(mday)))                      shouldBe (20170324,    0)
    offset(20170324, 2400, noBound.copy(before = Some(30)))                        shouldBe (20170324, 2330)
    offset(20170324, 2400, noBound)                                                shouldBe (20170325,    0)
    offset(20170324,    0, noBound)                                                shouldBe (20170324,    0)

    // inclusive 24:00
    offset(20170324, 1200, noBound.copy(date = Some(20170320), time = Some(2400)), inclusive = true) shouldBe (20170320, 2400)
    offset(20170324, 1200, noBound.copy(before = Some(2*mday), time = Some(2400)), inclusive = true) shouldBe (20170322, 2400)
    offset(20170324, 1200, noBound.copy(before = Some(mday),   time = Some(2400)), inclusive = true) shouldBe (20170323, 2400)
    offset(20170324, 2400, noBound.copy(before = Some(mday)),                      inclusive = true) shouldBe (20170323, 2400)
    offset(20170324, 2400, noBound.copy(before = Some(30)),                        inclusive = true) shouldBe (20170324, 2330)
    offset(20170324, 2400, noBound,                                                inclusive = true) shouldBe (20170324, 2400)
    offset(20170324,    0, noBound,                                                inclusive = true) shouldBe (20170323, 2400)
  }

  "compare" should "calculate if a date interval with bounds applied contains a point of time" in {
    val (zeroBound, oneBound, twoBound) = ( // no offset, one day, two days
      noBound.copy(before = Some(0)),
      noBound.copy(before = Some(mday)),
      noBound.copy(before = Some(2*mday)))
    val (pW, pB, pA) = ( // points: within, before, after
      ts.asCalendar(20170324, 120800),
      ts.asCalendar(20170324, 115800),
      ts.asCalendar(20170324, 123200))

    compare(pW, datesA, None, None) shouldBe 0
    compare(pB, datesA, None, None) shouldBe 0
    compare(pA, datesA, None, None) shouldBe 0

    compare(pW, datesA, Some(zeroBound), Some(zeroBound)) shouldBe 0
    compare(pB, datesA, Some(zeroBound), Some(zeroBound)) shouldBe -1
    compare(pA, datesA, Some(zeroBound), Some(zeroBound)) shouldBe 1

    compare(pW, datesA, Some(zeroBound), None) shouldBe 0
    compare(pB, datesA, Some(zeroBound), None) shouldBe -1
    compare(pA, datesA, Some(zeroBound), None) shouldBe 0

    compare(pW, datesA, None, Some(zeroBound)) shouldBe 0
    compare(pB, datesA, None, Some(zeroBound)) shouldBe 0
    compare(pA, datesA, None, Some(zeroBound)) shouldBe 1

    compare(pW, datesA, Some(oneBound), Some(zeroBound)) shouldBe 0
    compare(pB, datesA, Some(oneBound), Some(zeroBound)) shouldBe 0
    compare(pA, datesA, Some(oneBound), Some(zeroBound)) shouldBe 1

    compare(pW, datesA, Some(oneBound), Some(oneBound)) shouldBe 1
    compare(pB, datesA, Some(oneBound), Some(oneBound)) shouldBe 1
    compare(pA, datesA, Some(oneBound), Some(oneBound)) shouldBe 1

    compare(ts.asCalendar(20170323, 120800), datesA, Some(oneBound), Some(oneBound)) shouldBe 0
    compare(ts.asCalendar(20170323, 115800), datesA, Some(oneBound), Some(oneBound)) shouldBe -1
    compare(ts.asCalendar(20170323, 123200), datesA, Some(oneBound), Some(oneBound)) shouldBe 1

    // edge case: 20170323 12:00 to 20170323 12:30
    compare(pW, datesA, Some(zeroBound), Some(oneBound)) shouldBe 1
    compare(pB, datesA, Some(zeroBound), Some(oneBound)) shouldBe 1
    compare(pA, datesA, Some(zeroBound), Some(oneBound)) shouldBe 1
    compare(ts.asCalendar(20170323, 120800), datesA, Some(zeroBound), Some(oneBound)) shouldBe 0
    compare(ts.asCalendar(20170323, 115800), datesA, Some(zeroBound), Some(oneBound)) shouldBe -1
    compare(ts.asCalendar(20170323, 123200), datesA, Some(zeroBound), Some(oneBound)) shouldBe 1

    compare(ts.asCalendar(20170323, 120800), datesA, Some(twoBound), Some(oneBound)) shouldBe 0
    compare(ts.asCalendar(20170323, 115800), datesA, Some(twoBound), Some(oneBound)) shouldBe 0
    compare(ts.asCalendar(20170323, 123200), datesA, Some(twoBound), Some(oneBound)) shouldBe 1

    compare(ts.asCalendar(20170322, 120800), datesA, Some(twoBound), Some(oneBound)) shouldBe 0
    compare(ts.asCalendar(20170322, 115800), datesA, Some(twoBound), Some(oneBound)) shouldBe -1
    compare(ts.asCalendar(20170322, 123200), datesA, Some(twoBound), Some(oneBound)) shouldBe 0

    compare(ts.asCalendar(20170324, 115959), datesA, Some(zeroBound), Some(zeroBound)) shouldBe -1
    compare(ts.asCalendar(20170324, 120000), datesA, Some(zeroBound), Some(zeroBound)) shouldBe 0
    compare(ts.asCalendar(20170324, 121459), datesA, Some(zeroBound), Some(zeroBound)) shouldBe 0
    compare(ts.asCalendar(20170324, 121500), datesA, Some(zeroBound), Some(zeroBound)) shouldBe 1
  }

}
