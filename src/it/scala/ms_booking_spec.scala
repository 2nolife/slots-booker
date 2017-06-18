package com.coldcore.slotsbooker
package test

import ms.{Timestamp => ts}
import org.apache.http.HttpStatus._
import org.scalatest._
import ms.booking.vo
import spray.json.{JsObject, JsString}

abstract class BaseMsBookingSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupTestUser()
    mongoSetupUser("testuser2")
    mongoSetupUser("testuser3")
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoRemovePlace()
  }

  def setup2ActiveBookings(existingPlaceId: Option[String] = None, bookAsUsername: String = "testuser2"): Map[String,String] = {
    val placeId = existingPlaceId.getOrElse(mongoCreatePlace())
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA: String = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    val slotIdB: String = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    mongoCreateSpacePrice(placeId, spaceId)
    val bookingIdA = mongoCreateBooking(placeId, spaceId, slotIdA, "Booked slot A", bookAsUsername)
    val bookingIdB = mongoCreateBooking(placeId, spaceId, slotIdB, "Booked slot B", bookAsUsername)
    mongoSetBookingAttributes(bookingIdA, """{ "key_rw": "value_a" }""")
    val bookedId = mongoCreateBooked(placeId, Seq(slotIdA, slotIdB), Seq(bookingIdA, bookingIdB), bookAsUsername)
    mongoSetSlotBooked(slotIdA, bookedId)
    mongoSetSlotBooked(slotIdB, bookedId)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)

    Map(
      "placeId" -> placeId,
      "spaceId" -> spaceId,
      "slotIdA" -> slotIdA,
      "slotIdB" -> slotIdB,
      "bookedId" -> bookedId
    )
  }

  def updateSlotTime(slotId: String, fromOffsetMinutes: Int = 60, toOffsetMinutes: Int = 120) {
    val now = ts.asCalendar
    val (from, to) = (ts.addMinutes(ts.copy(now), fromOffsetMinutes), ts.addMinutes(ts.copy(now), toOffsetMinutes))
    mongoUpdateSlot(slotId,
      dateFrom = Some(ts.dateString(from).toInt), timeFrom = Some(ts.timeString(from).toInt/100),
      dateTo = Some(ts.dateString(to).toInt), timeTo = Some(ts.timeString(to).toInt/100))
  }
}

class MsBookingQuoteSpec extends BaseMsBookingSpec {

  "POST to /booking/quote" should "return a quote for selected slots" in {
    val placeId = mongoCreatePlace()
    val spaceId1 = mongoCreateSpace(placeId)
    val spaceId2 = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId1)
    val slotIdB = mongoCreateSlot(placeId, spaceId1)
    val slotIdC = mongoCreateSlot(placeId, spaceId2)
    val priceId1 = mongoCreateSpacePrice(placeId, spaceId1, "Default adult", amount = 1200)
    val priceId2 = mongoCreateSpacePrice(placeId, spaceId1, "Default child", amount = 800)
    val priceIdA1 = mongoCreateSlotPrice(placeId, spaceId1, slotIdA, "Price A adult", amount = 1600)
    val priceIdA2 = mongoCreateSlotPrice(placeId, spaceId1, slotIdA, "Price A child", amount = 1000)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)
    updateSlotTime(slotIdC)

    val url = s"$bookingBaseUrl/booking/quote"

    // single slot with price
    val jsonA = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA1" }] }"""
    val quoteA = (When postTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Quote]

    quoteA.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdA, 1600))
    quoteA.amount.get shouldBe 1600

    // multiple slots with prices
    val jsonB = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA1" }, { "slot_id": "$slotIdB", "price_id": "$priceId2" }] }"""
    val quoteB = (When postTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Quote]

    quoteB.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdA, 1600), (slotIdB, 800))
    quoteB.amount.get shouldBe 2400

    // single slot without price
    val jsonC = s"""{ "selected": [{ "slot_id": "$slotIdC" }] }"""
    val quoteC = (When postTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Quote]

    quoteC.prices.get.map(sp => sp.slot_id -> sp.amount) should contain only ((slotIdC, None))
    quoteC.amount shouldBe None

    // mixture of slots with and without prices
    val jsonD = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA1" }, { "slot_id": "$slotIdC" }] }"""
    val quoteD = (When postTo url entity jsonD withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Quote]

    quoteD.prices.get.map(sp => sp.slot_id -> sp.amount) should contain only ((slotIdA, Some(1600)), (slotIdC, None))
    quoteD.amount.get shouldBe 1600
  }

  "POST to /booking/quote" should "return a quote for selected slots with member prices" in {
    val placeId = mongoCreatePlace()
    val spaceId1 = mongoCreateSpace(placeId)
    val spaceId2 = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId1)
    val slotIdB = mongoCreateSlot(placeId, spaceId1)
    val priceId0 = mongoCreateSpacePrice(placeId, spaceId1, amount = 1200)
    val priceId1 = mongoCreateSpacePrice(placeId, spaceId1, amount = 800, member_level = 1)
    val priceIdA1 = mongoCreateSlotPrice(placeId, spaceId1, slotIdA, amount = 1600, member_level = 1)
    val priceIdA2 = mongoCreateSlotPrice(placeId, spaceId1, slotIdA, amount = 1000, member_level = 2)
    val priceIdA3 = mongoCreateSlotPrice(placeId, spaceId1, slotIdA, amount = 600, member_level = 3)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)
    mongoCreateMember(placeId, level = 2, username = "testuser2")

    val url = s"$bookingBaseUrl/booking/quote"

    val headers2 = authHeaderSeq("testuser2") // member with level 2
    val headers0 = authHeaderSeq("testuser3") // not a member

    // member, multiple slots with prices level 1 and 0
    val jsonA = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA1" }, { "slot_id": "$slotIdB", "price_id": "$priceId0" }] }"""
    val quoteA = (When postTo url entity jsonA withHeaders headers2 expect() code SC_CREATED).withBody[vo.Quote]

    quoteA.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdA, 1600), (slotIdB, 1200))

    // member, multiple slots with prices level 2 and 1
    val jsonB = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA2" }, { "slot_id": "$slotIdB", "price_id": "$priceId1" }] }"""
    val quoteB = (When postTo url entity jsonB withHeaders headers2 expect() code SC_CREATED).withBody[vo.Quote]

    quoteB.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdA, 1000), (slotIdB, 800))

    // member, multiple slots with prices level 3 and 1
    val jsonE = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA3" }, { "slot_id": "$slotIdB", "price_id": "$priceId1" }] }"""
    When postTo url entity jsonE withHeaders headers2 expect() code SC_CONFLICT withApiCode "ms-booking-16"

    // not a member, single slot with price level 0
    val jsonC = s"""{ "selected": [{ "slot_id": "$slotIdB", "price_id": "$priceId0" }] }"""
    val quoteC = (When postTo url entity jsonC withHeaders headers0 expect() code SC_CREATED).withBody[vo.Quote]

    quoteC.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdB, 1200))

    // not a member, multiple slots with prices level 1 and 0
    val jsonD = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdA1" }, { "slot_id": "$slotIdB", "price_id": "$priceId0" }] }"""
    When postTo url entity jsonD withHeaders headers0 expect() code SC_CONFLICT withApiCode "ms-booking-16"
  }

  "POST to /booking/quote" should "give 404 if any of the slots does not exist" in {
    val placeId = mongoCreatePlace()

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{ "selected": [{ "slot_id": "$randomId" }] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/quote" should "give 409 if no or invalid price selected for a priced slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    val priceId1 = mongoCreateSpacePrice(placeId, spaceId)
    val priceIdA = mongoCreateSlotPrice(placeId, spaceId, slotIdA)
    val priceIdB = mongoCreateSlotPrice(placeId, spaceId, slotIdB)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)

    val url = s"$bookingBaseUrl/booking/quote"

    val jsonA = s"""{ "selected": [{ "slot_id": "$slotIdA" }] }"""
    When postTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-11"

    val jsonB = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceIdB" }] }"""
    When postTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-11"

    val jsonC = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceId1" }] }"""
    When postTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-11"

  }

  "POST to /booking/quote" should "give 409 if any of the slots is booked" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{ "selected": [{ "slot_id": "$slotIdA" }, { "slot_id": "$slotIdB" }] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-10"
  }

  "POST to /booking/quote" should "give 409 if a slot time is in the past" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    updateSlotTime(slotId, -30, 30)

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{ "selected": [{ "slot_id": "$slotId" }] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-2"
  }

  "POST to /booking/quote" should "give 409 if a slot time is in the past compared to place local time" in {
    val offset = 120
    val placeId = mongoCreatePlace(offset_minutes = Some(offset))
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    updateSlotTime(slotId, -30+offset, 30+offset)

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{ "selected": [{ "slot_id": "$slotId" }] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-2"
  }

}

class MsBookingRefundSpec extends BaseMsBookingSpec {

  "POST to /booking/refund" should "return a refund for selected slots" in {
    val (placeId, slotIdA, slotIdB, bookedId1) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId1 = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId1), quoteId = Some(quoteId1), username = "testuser2")

    val (slotIdC, slotIdD, bookedId2) = {
      val ids = setup2ActiveBookings(existingPlaceId = Some(placeId))
      (ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId2 = mongoCreatePaidQuote(placeId, Seq((slotIdC, 1600), (slotIdD, 800)), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId2), quoteId = Some(quoteId2), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")

    // slots with no prices
    val jsonA = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    val refundA = (When postTo url entity jsonA withHeaders headers expect() code SC_CREATED).withBody[vo.Refund]

    refundA.prices.get.map(sp => sp.slot_id -> sp.amount) should contain only ((slotIdA, None), (slotIdB, None))
    refundA.amount shouldBe None

    // slots with prices
    val jsonB = s"""{ "slot_ids": ["$slotIdC","$slotIdD"] }"""
    val refundB = (When postTo url entity jsonB withHeaders headers expect() code SC_CREATED).withBody[vo.Refund]

    refundB.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdC, 1600), (slotIdD, 800))
    refundB.amount.get shouldBe 2400

    // mixture of slots with and without prices
    val jsonC = s"""{ "slot_ids": ["$slotIdA","$slotIdC"] }"""
    val refundC = (When postTo url entity jsonC withHeaders headers expect() code SC_CREATED).withBody[vo.Refund]

    refundC.prices.get.map(sp => sp.slot_id -> sp.amount) should contain only ((slotIdA, None), (slotIdC, Some(1600)))
    refundC.amount.get shouldBe 1600
  }

  "POST to /booking/refund" should "return a refund for selected slots with zero amount if quote payment is due" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((slotIdA, 1600), (slotIdB, 800)), status = 2, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    val refund = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[vo.Refund]

    refund.prices.get.map(sp => sp.slot_id -> sp.amount.get) should contain only ((slotIdA, 0), (slotIdB, 0))
    refund.amount.get shouldBe 0
  }

  "POST to /booking/refund" should "give 409 if quote payment is due but not all slots selected" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((slotIdA, 1600), (slotIdB, 800)), status = 2, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotIdA"] }"""
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-6"
  }

  "POST to /booking/refund" should "give 404 if any of the slots does not exist" in {
    val (placeId, slotId, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotId), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotId","$randomId"] }"""
    When postTo url entity json withHeaders headers expect() code SC_NOT_FOUND
  }

  "POST to /booking/refund" should "give 409 if any of the slots is not booked" in {
    val (placeId, slotIdA, bookedId, spaceId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("bookedId"), ids("spaceId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotIdA), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val slotIdB = mongoCreateSlot(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-1"
  }

  "POST to /booking/refund" should "give 409 if any of the slots is booked by another user" in {
    val (placeId, slotIdA, bookedIdA) = { // booked as testuser2
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("bookedId"))
    }
    val quoteIdA = mongoCreateFreeQuote(placeId, Seq(slotIdA), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedIdA), quoteId = Some(quoteIdA), username = "testuser2")

    val (slotIdB, bookedIdB) = { // booked as testuser
      val ids = setup2ActiveBookings(existingPlaceId = Some(placeId), bookAsUsername = "testuser")
      (ids("slotIdA"), ids("bookedId"))
    }
    val quoteIdB = mongoCreateFreeQuote(placeId, Seq(slotIdB), status = 1, username = "testuser")
    mongoCreateReference(placeId, Seq(bookedIdB), quoteId = Some(quoteIdB), username = "testuser")

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-5"
  }

  "POST to /booking/refund" should "give 409 if a slot time is in the past" in {
    val (placeId, slotId, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotId), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")
    updateSlotTime(slotId, -30, 30)

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotId"] }"""
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-2"
  }

  "POST to /booking/refund" should "give 409 if a slot time is in the past compared to place local time" in {
    val offset = 120
    val placeId = mongoCreatePlace(offset_minutes = Some(120))
    val (slotId, bookedId) = {
      val ids = setup2ActiveBookings(existingPlaceId = Some(placeId))
      (ids("slotIdA"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotId), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")
    updateSlotTime(slotId, -30+offset, 30+offset)

    val url = s"$bookingBaseUrl/booking/refund"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotId"] }"""
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-2"
  }

}

class MsBookingBookSpec extends BaseMsBookingSpec {

  "POST to /booking/book" should "book selected slots which require payment with quote" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    val priceId1 = mongoCreateSpacePrice(placeId, spaceId, "Default adult", amount = 1200)
    val priceId2 = mongoCreateSpacePrice(placeId, spaceId, "Default child", amount = 800)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)

    val headers = authHeaderSeq("testuser2")

    val urlQ = s"$bookingBaseUrl/booking/quote"
    val jsonQ = s"""{ "selected": [{ "slot_id": "$slotIdA", "price_id": "$priceId1" }, { "slot_id": "$slotIdB", "price_id": "$priceId2" }] }"""
    val quote = (When postTo urlQ entity jsonQ withHeaders headers expect() code SC_CREATED).withBody[vo.Quote]

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{"quote_id": "${quote.quote_id}", "attributes": { "kez_rw": "special instructions" }}"""
    val reference = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[vo.Reference]

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("special instructions")))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB"
    val slotB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotB.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("special instructions")))
  }

  "POST to /booking/book" should "book selected slots which do not require payment without quote" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_CREATED

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")
  }

  "POST to /booking/book" should "give 409 if any of the slots is already booked" in {
    val slotId = {
      val ids = setup2ActiveBookings()
      ids("slotIdA")
    }

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{ "slot_ids": ["$slotId"] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT withApiCode "ms-booking-10"
  }

  "POST to /booking/book" should "give 404 if any of the slots does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{ "slot_ids": ["$slotIdA","$randomId"] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/book" should "give 409 if slots require payment without quote" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId, "Default adult", amount = 1200)
    mongoCreateSpacePrice(placeId, spaceId, "Default child", amount = 800)
    updateSlotTime(slotIdA)
    updateSlotTime(slotIdB)

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-11"
  }

}

class MsBookingCancelSpec extends BaseMsBookingSpec {

  "POST to /booking/cancel" should "cancel selected slots which required payment with refund" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((slotIdA, 1600), (slotIdB, 800)), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val headers = authHeaderSeq("testuser2")

    val urlR = s"$bookingBaseUrl/booking/refund"
    val jsonR = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    val refund = (When postTo urlR entity jsonR withHeaders headers expect() code SC_CREATED).withBody[vo.Refund]

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{"refund_id": "${refund.refund_id}", "attributes": { "kez_rw": "reason" }}"""
    val reference = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[vo.Reference]

    mongoVerifyCancelledBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyCancelledBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("reason")))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB"
    val slotB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotB.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("reason")))
  }

  "POST to /booking/cancel" should "cancel a single slot which required payment with refund" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((slotIdA, 1600), (slotIdB, 800)), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val headers = authHeaderSeq("testuser2")

    val urlR = s"$bookingBaseUrl/booking/refund"
    val jsonR = s"""{ "slot_ids": ["$slotIdA"] }"""
    val refund = (When postTo urlR entity jsonR withHeaders headers expect() code SC_CREATED).withBody[vo.Refund]

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{"refund_id": "${refund.refund_id}"}"""
    val reference = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[vo.Reference]

    mongoVerifyCancelledBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")
  }

  "POST to /booking/cancel" should "cancel selected slots which did not require payment without refund" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{ "slot_ids": ["$slotIdA", "$slotIdB"] }"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_CREATED

    mongoVerifyCancelledBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyCancelledBookingIntegrity(slotIdB, username = "testuser2")
  }

  "POST to /booking/cancel" should "give 404 if any of the slots do not exist" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{ "slot_ids": ["$slotIdA","$randomId"] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/cancel" should "give 409 if any of the slots is booked by another user" in {
    val (placeId, slotIdA, bookedIdA) = { // booked as testuser2
    val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("bookedId"))
    }
    val quoteIdA = mongoCreateFreeQuote(placeId, Seq(slotIdA), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedIdA), quoteId = Some(quoteIdA), username = "testuser2")

    val (slotIdB, bookedIdB) = { // booked as testuser
    val ids = setup2ActiveBookings(existingPlaceId = Some(placeId), bookAsUsername = "testuser")
      (ids("slotIdA"), ids("bookedId"))
    }
    val quoteIdB = mongoCreateFreeQuote(placeId, Seq(slotIdB), status = 1, username = "testuser")
    mongoCreateReference(placeId, Seq(bookedIdB), quoteId = Some(quoteIdB), username = "testuser")

    val url = s"$bookingBaseUrl/booking/cancel"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "slot_ids": ["$slotIdA", "$slotIdB"] }"""
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-5"
  }

  "POST to /booking/cancel" should "give 409 if slots required payment without refund" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
    val ids = setup2ActiveBookings()
    (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
  }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((slotIdA, 1600), (slotIdB, 800)), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{ "slot_ids": ["$slotIdA", "$slotIdB"] }"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_CONFLICT withApiCode "ms-booking-8"
  }

}

class MsBookingUpdateSpec extends BaseMsBookingSpec {

  "POST to /booking/update" should "give 404 if any of the slots do not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotIdA","$randomId"] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/update" should "give 403 if any of the slots is not booked by that user" in {
    val (slotIdA, slotIdB) = {
      val ids = setup2ActiveBookings()
      (ids("slotIdA"), ids("slotIdB"))
    }

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"] }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "POST to /booking/update" should "update a single slot for a user" in {
    val (slotIdA, slotIdB) = {
      val ids = setup2ActiveBookings()
      (ids("slotIdA"), ids("slotIdB"))
    }

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotIdA"], "attributes": { "kez_rw": "reason" } }"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_OK

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("reason")))
  }

  "POST to /booking/update" should "update selected slots for a user" in {
    val (slotIdA, slotIdB) = {
      val ids = setup2ActiveBookings()
      (ids("slotIdA"), ids("slotIdB"))
    }

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"], "attributes": { "kez_rw": "reason" } }"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_OK

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("reason")))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB"
    val slotB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotB.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("reason")))
  }

}

class MsBookingQuoteModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/quote" should "give 403 if non moderator requests a quote for another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{ "selected": [{ "slot_id": "$slotId" }], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/quote" should "request a quote for another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")
    updateSlotTime(slotId)

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{ "selected": [{ "slot_id": "$slotId" }], "as_profile_id": "$profileId" }"""
    val quote = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Quote]

    quote.profile_id.get shouldBe profileId
  }
}

class MsBookingRefundModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/refund" should "give 403 if non moderator requests a refund for another user" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId1 = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId1), username = "testuser2")
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/refund"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/refund" should "request a refund for another user" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId1 = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId1), username = "testuser2")
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/refund"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"], "as_profile_id": "$profileId" }"""
    val refund = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Refund]

    refund.profile_id.get shouldBe profileId
  }
}

class MsBookingSlotsModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/book" should "give 403 if non moderator books a slot for another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/book" should "book a slot for another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")
    updateSlotTime(slotId)

    val url = s"$bookingBaseUrl/booking/book"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED

    mongoVerifyActiveBookingIntegrity(slotId, username = "testuser2")
  }

}

class MsBookingCancelModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/cancel" should "give 403 if non moderator cancels slots of another user" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s""" {"slot_ids": ["$slotIdA", "$slotIdB"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/cancel" should "cancel slots of another user" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s""" {"slot_ids": ["$slotIdA", "$slotIdB"], "as_profile_id": "$profileId" }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED

    mongoVerifyCancelledBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyCancelledBookingIntegrity(slotIdB, username = "testuser2")
  }

}

class MsBookingUpdateModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/update" should "give 403 if non moderator updates a slot of another user" in {
    val slotId = {
      val ids = setup2ActiveBookings()
      ids("slotIdA")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/update" should "update a slot of another user" in {
    val slotId = {
      val ids = setup2ActiveBookings()
      ids("slotIdA")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId", "attributes": { "kez_rw": "reason" }}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_OK

    mongoVerifyActiveBookingIntegrity(slotId, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotId"
    val slotA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("reason")))
  }

}

//todo refund and quote in case of multiple currencies should give 409
//todo bookings or refunds of the slots in the past should give 409

class MsBookingReferenceSpec extends BaseMsBookingSpec {

  "GET to /booking/reference?ref={?]&profile_id={?}" should "give 401 with non system bearer token" in {
    val url = s"$bookingBaseUrl/booking/reference?ref=$randomId&profile_id=$randomId"
    When getTo url withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "GET to /booking/reference?ref={?]&profile_id={?}" should "return a reference" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreateFreeQuote(placeId, Seq(slotIdA, slotIdB), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/reference?ref=Testuser2_1&profile_id=$profileId"
    val reference = (When getTo url withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Reference]

    reference.ref.get shouldBe "Testuser2_1"
  }

  "GET to /booking/reference/expired" should "give 401 with non system bearer token" in {
    val url = s"$bookingBaseUrl/booking/reference/expired"
    When getTo url withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "GET to /booking/reference/expired" should "return next expired reference which is not paid nor refunded" in {
    val (placeId, slotIdA, slotIdB, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("slotIdA"), ids("slotIdB"), ids("bookedId"))
    }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((slotIdA, 1600), (slotIdB, 800)), status = 2, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")

    def timeout(decMinutes: Int): Long = ts.asLong(ts.addMinutes(ts.asCalendar, -decMinutes))

    mongoEntryDates(quoteId, mongoQuotes, created = Some(timeout(6)))

    val urlA = s"$bookingBaseUrl/booking/reference/expired"
    val referenceA = (When getTo urlA withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Reference]

    val urlB = s"$bookingBaseUrl/booking/reference/expired" // in-flight set
    When getTo urlB withHeaders systemTokenHeader expect() code SC_NOT_FOUND

    mongoEntryDates(quoteId, mongoQuotes, locked = Some(timeout(2)))

    val urlC = s"$bookingBaseUrl/booking/reference/expired" // in-flight expired
    val referenceC = (When getTo urlC withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Reference]

    val urlD = s"$bookingBaseUrl/booking/reference/expired" // in-flight set
    When getTo urlD withHeaders systemTokenHeader expect() code SC_NOT_FOUND

    referenceA shouldBe referenceC

    // refunded

    mongoEntryDates(quoteId, mongoQuotes, locked = Some(timeout(2)))

    val refundId = mongoCreateFreeRefund(placeId, Seq(slotIdA, slotIdB), Seq(quoteId), status = 1, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), refundId = Some(refundId), username = "testuser2")

    val urlE = s"$bookingBaseUrl/booking/reference/expired"
    When getTo urlE withHeaders systemTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /booking/reference/paid" should "give 401 with non system bearer token" in {
    val url = s"$bookingBaseUrl/booking/reference/paid"
    When patchTo url entity "{}" withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "POST to /booking/reference/paid" should "mark quote or refund status to complete" in {
    val (placeId, bookedId) = {
      val ids = setup2ActiveBookings()
      (ids("placeId"), ids("bookedId"))
    }
    val quoteId = mongoCreatePaidQuote(placeId, Seq((randomId, 1600), (randomId, 800)), status = 2, username = "testuser2")
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = "testuser2")
    val profileId = mongoProfileId("testuser2")
    mongoCreateBalance(placeId, 2000, username = "testuser2")

    val url = s"$bookingBaseUrl/booking/reference/paid"
    val json = s"""{ "ref": "Testuser2_1", "profile_id": "$profileId" }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK

    val urlA = s"$bookingBaseUrl/booking/reference?ref=Testuser2_1&profile_id=$profileId"
    val referenceA = (When getTo urlA withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Reference]

    referenceA.quote.get.status.get shouldBe 1
  }

}
