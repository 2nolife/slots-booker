package com.coldcore.slotsbooker
package test

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

  def setup2ActiveBookings(bookAsUsername: String = "testuser2"): (String, String) = {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    val slotIdB = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    mongoCreateSpacePrice(placeId, spaceId)
    val bookingIdA = mongoCreateBooking(placeId, spaceId, slotIdA, "Booked slot A", bookAsUsername)
    val bookingIdB = mongoCreateBooking(placeId, spaceId, slotIdB, "Booked slot B", bookAsUsername)
    mongoSetBookingAttributes(bookingIdA, """{ "key_rw": "value_a" }""")
    val bookedIdA = mongoCreateBooked(placeId, Seq(slotIdA), Seq(bookingIdA), bookAsUsername)
    val bookedIdB = mongoCreateBooked(placeId, Seq(slotIdB), Seq(bookingIdB), bookAsUsername)
    mongoSetSlotBooked(slotIdA, bookedIdA)
    mongoSetSlotBooked(slotIdB, bookedIdB)
    (slotIdA, slotIdB)
  }
}

class MsBookingQuoteSpec extends BaseMsBookingSpec {

  "POST to /booking/quote" should "return a quote for selected slots" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId, "Default adult", amount = 1200)
    mongoCreateSpacePrice(placeId, spaceId, "Default child", amount = 800)
    mongoCreateSlotPrice(placeId, spaceId, slotIdA, "Price A adult", amount = 1600)
    mongoCreateSlotPrice(placeId, spaceId, slotIdA, "Price A child", amount = 1000)

    val url = s"$bookingBaseUrl/booking/quote"

    val jsonA = s"""{"slot_ids": ["$slotIdA"]}"""
    val quoteA = (When postTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Quote]

    quoteA.prices.get.size shouldBe 2
    quoteA.prices.get.flatMap(_.amount) should contain allOf (1600, 1000)

    val jsonB = s"""{"slot_ids": ["$slotIdA","$slotIdB"]}"""
    val quoteB = (When postTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Quote]

    quoteB.prices.get.size shouldBe 4
    quoteB.prices.get.flatMap(_.amount) should contain allOf (1600, 1000, 1200, 800)
  }

  "POST to /booking/quote" should "give 404 if any of the slots does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{"slot_ids": ["$slotIdA","$randomId"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/quote" should "give 404 if any of the slots is not priced" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    mongoCreateSlotPrice(placeId, spaceId, slotIdA)

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{"slot_ids": ["$slotIdA","$slotIdB"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/quote" should "return a quote for selected slots even if any of the slots is already booked" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)
    mongoCreateBooking(placeId, spaceId, slotIdA, "Booked slot", username = "testuser2")

    val url = s"$bookingBaseUrl/booking/quote"
    val json = s"""{"slot_ids": ["$slotIdA","$slotIdB"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_OK
  }

}

class MsBookingSlotsSpec extends BaseMsBookingSpec {

  "POST to /booking/slots" should "book a single slot for a user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId, "Default adult", amount = 1200)
    mongoCreateSpacePrice(placeId, spaceId, "Default child", amount = 800)
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/slots"
    val json = s"""{"slot_ids": ["$slotIdA"], "attributes": { "key_rw": "special instructions" }}"""
    val headers = authHeaderSeq("testuser2")
    val reference = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[vo.Reference]

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("special instructions")))
  }

  "POST to /booking/slots" should "book selected slots for a user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId, "Default adult", amount = 1200)
    mongoCreateSpacePrice(placeId, spaceId, "Default child", amount = 800)
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/slots"
    val json = s"""{"slot_ids": ["$slotIdA","$slotIdB"], "attributes": { "key_rw": "special instructions" }}"""
    val headers = authHeaderSeq("testuser2")
    val reference = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[vo.Reference]

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("special instructions")))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB"
    val slotB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotB.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("special instructions")))
  }

  "POST to /booking/slots" should "give 409 if any of the slots is already booked" in {
    val (slotId, _) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/slots"
    val json = s"""{"slot_ids": ["$slotId"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT
  }

  "POST to /booking/slots" should "give 404 if any of the slots does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/slots"
    val json = s"""{"slot_ids": ["$slotIdA","$randomId"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  //todo change special instructions into write just once
  //todo test write just once attribute after booking created
}

class MsBookingCancelSpec extends BaseMsBookingSpec {

  "POST to /booking/cancel" should "give 404 if any of the slots do not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{"slot_ids": ["$slotIdA","$randomId"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/cancel" should "give 403 if any of the slots is not booked by that user" in {
    val (slotIdA, slotIdB) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{"slot_ids": ["$slotIdA","$slotIdB"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "POST to /booking/cancel" should "cancel a single slot for a user" in {
    val (slotIdA, slotIdB) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{"slot_ids": ["$slotIdA"], "attributes": { "key_rw": "reason" }}"""
    val headers = authHeaderSeq("testuser2")
    val reference = (When postTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Reference]

    mongoVerifyCancelledBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))
  }

  "POST to /booking/cancel" should "cancel selected slots for a user" in {
    val (slotIdA, slotIdB) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{"slot_ids": ["$slotIdA", "$slotIdB"], "attributes": { "key_rw": "reason" }}"""
    val headers = authHeaderSeq("testuser2")
    val reference = (When postTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Reference]

    mongoVerifyCancelledBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyCancelledBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB"
    val slotB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotB.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))
  }

}

class MsBookingUpdateSpec extends BaseMsBookingSpec {

  "POST to /booking/update" should "give 404 if any of the slots do not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{"slot_ids": ["$slotIdA","$randomId"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /booking/update" should "give 403 if any of the slots is not booked by that user" in {
    val (slotIdA, slotIdB) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{"slot_ids": ["$slotIdA","$slotIdB"]}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "POST to /booking/update" should "update a single slot for a user" in {
    val (slotIdA, slotIdB) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{"slot_ids": ["$slotIdA"], "attributes": { "key_rw": "reason" }}"""
    val headers = authHeaderSeq("testuser2")
    val reference = (When postTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Reference]

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))
  }

  "POST to /booking/update" should "update selected slots for a user" in {
    val (slotIdA, slotIdB) = setup2ActiveBookings()

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{"slot_ids": ["$slotIdA","$slotIdB"], "attributes": { "key_rw": "reason" }}"""
    val headers = authHeaderSeq("testuser2")
    val reference = (When postTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Reference]

    mongoVerifyActiveBookingIntegrity(slotIdA, username = "testuser2")
    mongoVerifyActiveBookingIntegrity(slotIdB, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotIdA"
    val slotA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB"
    val slotB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[vo.ext.Slot]

    slotB.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))
  }

}

class MsBookingSlotsModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/slots" should "give 403 if non moderator is trying to book a slot for another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/slots"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/slots" should "book a single slot for another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    mongoCreateSpacePrice(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/slots"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED

    mongoVerifyActiveBookingIntegrity(slotId, username = "testuser2")
  }

}

class MsBookingCancelModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/cancel" should "give 403 if non moderator is trying to cancel a slot of another user" in {
    val (slotId, _) = setup2ActiveBookings()
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/cancel" should "cancel a single slot of another user" in {
    val (slotId, _) = setup2ActiveBookings()
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/cancel"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_OK

    mongoVerifyCancelledBookingIntegrity(slotId, username = "testuser2")
  }

}

class MsBookingUpdateModeratorSpec extends BaseMsBookingSpec {

  "POST to /booking/update" should "give 403 if non modertor is trying to update a slot of another user" in {
    val (slotId, _) = setup2ActiveBookings()
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId" }"""
    val headers = authHeaderSeq("testuser3")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /booking/update" should "update a single slot of another user" in {
    val (slotId, _) = setup2ActiveBookings()
    val profileId = mongoProfileId("testuser2")

    val url = s"$bookingBaseUrl/booking/update"
    val json = s"""{ "slot_ids": ["$slotId"], "as_profile_id": "$profileId", "attributes": { "key_rw": "reason" }}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_OK

    mongoVerifyActiveBookingIntegrity(slotId, username = "testuser2")

    val urlA = s"$slotsBaseUrl/slots/$slotId"
    val slotA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.ext.Slot]

    slotA.bookings.get.head.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("reason")))
  }

}
