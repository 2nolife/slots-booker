package com.coldcore.slotsbooker
package test

import akka.http.scaladsl.model.headers.Authorization
import ms.{Timestamp => ts}
import org.apache.http.HttpStatus._
import org.scalatest._
import ms.slots.vo
import spray.json.{JsObject, JsString}

abstract class BaseMsSlotsSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupTestUser()
    mongoSetupUser("testuser2")
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoRemovePlace()
  }

}

class MsSlotsSpec extends BaseMsSlotsSpec {

  "POST to /slots" should "give 401 with invalid bearer token" in {
    val url = s"$slotsBaseUrl/slots"
    val json = s"""{"space_id": "$randomId", "place_id": "$randomId", "name": "Slot A", "date_from": 20160508, "date_to": 20160508, "time_from": 800, "time_to": 815}"""
    val headers = Seq((Authorization.name, s"Bearer ABCDEF"))
    When postTo url entity json withHeaders headers expect() code SC_UNAUTHORIZED
  }

  "POST to /slots" should "give 404 if a space does not exist" in {
    val url = s"$slotsBaseUrl/slots"
    val json = s"""{"space_id": "$randomId", "place_id": "$randomId", "name": "Slot A", "date_from": 20160508, "date_to": 20160508, "time_from": 800, "time_to": 815}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /slots" should "give 403 if not place moderator" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$slotsBaseUrl/slots"
    val json = s"""{"space_id": "$spaceId", "place_id": "$placeId", "name": "Slot A", "date_from": 20160508, "date_to": 20160508, "time_from": 800, "time_to": 815}"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /slots" should "create a new slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$slotsBaseUrl/slots"
    val json = s"""{"space_id": "$spaceId", "place_id": "$placeId", "name": "Slot A", "date_from": 20160508, "date_to": 20160508, "time_from": 800, "time_to": 815}"""
    val slot = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Slot]

    slot.name.get shouldBe "Slot A"
  }

  "DELETE to /slots/{id}" should "delete a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$slotId"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_OK
  }

  "PATCH to /slots/{id}" should "update a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$slotId"
    val json = """{"name": "Red Parking"}"""
    val slot = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    slot.name.get shouldBe "Red Parking"
  }

  "GET to /slots/{id}" should "not give 401 for an anonymous read" in {
    val url = s"$slotsBaseUrl/slots/$randomId"
    When getTo url expect() code SC_NOT_FOUND
  }

  "GET to /slots/{id}" should "return a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$slotId"
    val slot = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    slot.name.get shouldBe "Slot A"
  }

  "GET to /slots/{id}" should "return a slot with selected fields" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    mongoCreateBooking(placeId, spaceId, slotId)
    mongoCreateSlotPrice(placeId, spaceId, slotId)

    val baseurl = s"$slotsBaseUrl/slots/$slotId"

    val urlA = s"$baseurl?deep=false" // shallow
    val slotA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    (slotA.name.get, slotA.bookings, slotA.prices) shouldBe("Slot A", None, None)

    val urlB = s"$baseurl?deep_prices=false" // shallow prices
    val slotB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    (slotB.name.get, slotB.bookings.get.size, slotB.prices) shouldBe("Slot A", 1, None)
    slotB.bookings.get.head.name.get shouldBe "Booking A"
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}&inner=false" should "list found slots by date for that space" in {
    val placeId = mongoCreatePlace()
    val parentSpaceId = mongoCreateSpace(placeId)
    val spaceIdA = mongoCreateInnerSpace(placeId, parentSpaceId)
    val (slotIdA1, slotIdA2) = (
      mongoCreateSlot(placeId, spaceIdA, dateFrom = 20160112, dateTo = 20160115), // 4 days (12,13,14,15)
      mongoCreateSlot(placeId, spaceIdA, dateFrom = 20160113, dateTo = 20160116)) // 4 days (13,14,15,16)

    val urlA = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceIdA&from=20160112&to=20160115&inner=false"
    val slotsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsA.size shouldBe 2
    slotsA.map(_.slot_id) should contain allOf (slotIdA1, slotIdA2)

    val urlD = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$parentSpaceId&from=20160112&to=20160115&inner=false"
    val slotsD = (When getTo urlD withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsD.size shouldBe 0
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}" should "list found slots by date for that space or its inner spaces" in {
    val placeId = mongoCreatePlace()
    val parentSpaceId = mongoCreateSpace(placeId)
    val (spaceIdA, spaceIdB) = (
      mongoCreateInnerSpace(placeId, parentSpaceId),
      mongoCreateInnerSpace(placeId, parentSpaceId))
    val (slotIdA1, slotIdA2, slotIdB1, slotIdB2) = (
      mongoCreateSlot(placeId, spaceIdA, dateFrom = 20160112, dateTo = 20160115), // 4 days (12,13,14,15)
      mongoCreateSlot(placeId, spaceIdA, dateFrom = 20160113, dateTo = 20160116), // 4 days (13,14,15,16)
      mongoCreateSlot(placeId, spaceIdB, dateFrom = 20160111, dateTo = 20160112), // 2 days (11,12)
      mongoCreateSlot(placeId, spaceIdB, dateFrom = 20160109, dateTo = 20160111)) // 3 days (09,10,11)

    val urlA = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceIdA&from=20160112&to=20160115"
    val slotsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsA.size shouldBe 2
    slotsA.map(_.slot_id) should contain allOf (slotIdA1, slotIdA2)

    val urlB = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceIdB&from=20160112&to=20160115"
    val slotsB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsB.size shouldBe 1
    slotsB.map(_.slot_id) should contain (slotIdB1)

    val urlC = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$parentSpaceId&from=20160112&to=20160115"
    val slotsC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsC.size shouldBe 3
    slotsC.map(_.slot_id) should contain allOf (slotIdA1, slotIdA2, slotIdB1)

    val urlD = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$parentSpaceId&from=20160105&to=20160118"
    val slotsD = (When getTo urlD withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsD.size shouldBe 4
    slotsD.map(_.slot_id) should contain allOf (slotIdA1, slotIdA2, slotIdB1, slotIdB2)
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}" should "list found slots by date and time for that space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val (slotIdA, slotIdB, slotIdC) = (
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160112, dateTo = 20160115, timeFrom = 1200, timeTo = 1100), // 4 days (12,13,14,15)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160113, dateTo = 20160116, timeFrom = 1200, timeTo = 1100), // 4 days (13,14,15,16)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160111, dateTo = 20160112, timeFrom = 1200, timeTo = 1100)) // 2 days (11,12)

    val urlA = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=201601121200&to=201601151100" // 12 12:00 - 15 11:00
    val slotsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsA.size shouldBe 2
    slotsA.map(_.slot_id) should contain allOf (slotIdA, slotIdB)

    val urlB = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=201601121200&to=201601131200" // 12 12:00 - 13 12:00
    val slotsB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsB.size shouldBe 2
    slotsB.map(_.slot_id) should contain allOf (slotIdA, slotIdB)

    val urlC = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=201601121200&to=201601131100" // 12 12:00 - 13 11:00
    val slotsC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsC.size shouldBe 1
    slotsC.map(_.slot_id) should contain (slotIdA)

    val urlD = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=201601151200&to=201601151400" // 15 12:00 - 15 14:00
    val slotsD = (When getTo urlD withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsD.size shouldBe 1
    slotsD.map(_.slot_id) should contain (slotIdB)
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}" should "list found slots with the selected fields" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val (slotIdA, slotIdB) = (
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160112, dateTo = 20160115, timeFrom = 1200, timeTo = 1100, name = "Slot A"), // 4 days (12,13,14,15)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160113, dateTo = 20160116, timeFrom = 1200, timeTo = 1100, name = "Slot B")) // 4 days (13,14,15,16)
    mongoCreateBooking(placeId, spaceId, slotIdA)


    val url = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=20160112&to=201601131000&deep=false" // shallow
    val slots = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slots.size shouldBe 1
    slots.head.name.get shouldBe "Slot A"
    slots.head.bookings shouldBe None
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}&booked" should "list found slots which were booked by a user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val (slotIdA, slotIdB, slotIdC, slotIdD) = (
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160112, dateTo = 20160115), // 4 days (12,13,14,15)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160113, dateTo = 20160116), // 4 days (13,14,15,16)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160111, dateTo = 20160112), // 2 days (11,12)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160109, dateTo = 20160111)) // 3 days (09,10,11)
    val (bookingIdA, bookingIdB, bookingIdD) = (
      mongoCreateBooking(placeId, spaceId, slotIdA, name = "Booking A"),
      mongoCreateBooking(placeId, spaceId, slotIdB, name = "Booking B", username = "testuser2"), // booked by another user
      mongoCreateBooking(placeId, spaceId, slotIdD, name = "Booking D"))
    val (bookedIdA, bookedIdB, bookedIdD) = (
      mongoCreateBooked(placeId, Seq(slotIdA), Seq(bookingIdA), paid = Some(true)),
      mongoCreateBooked(placeId, Seq(slotIdB), Seq(bookingIdB), username = "testuser2"), // booked by another user
      mongoCreateBooked(placeId, Seq(slotIdD), Seq(bookingIdD)))
    mongoSetSlotBooked(slotIdA, bookedIdA)
    mongoSetSlotBooked(slotIdB, bookedIdB)
    mongoSetSlotBooked(slotIdD, bookedIdD)

    val baseUrl = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=20160112&to=20160115"

    val urlA = baseUrl+"&booked"
    val slotsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]] // my bookings (testuser)

    slotsA.map(_.slot_id) should contain only slotIdA

    val urlB = baseUrl+"&booked="+randomId
    val headers = authHeaderSeq("testuser2")
    val slotsB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[Seq[vo.Slot]] // my bookings (testuser2)

    slotsB.map(_.slot_id) should contain only slotIdB

    val urlC = baseUrl+"&booked="+mongoProfileId("testuser2")
    val slotsC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]] // moderator: bookings of testuser2

    slotsC.map(_.slot_id) should contain only slotIdB

    val urlD = baseUrl+"&booked=*"
    val slotsD = (When getTo urlD withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]] // moderator: all booked

    slotsD.map(_.slot_id) should contain only (slotIdA, slotIdB)
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}&booked&paid={?]" should "list found slots which were booked by a user and paid" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val (slotIdA, slotIdB, slotIdC, slotIdD) = (
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160112, dateTo = 20160115), // 4 days (12,13,14,15)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160113, dateTo = 20160116), // 4 days (13,14,15,16)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160111, dateTo = 20160112), // 2 days (11,12)
      mongoCreateSlot(placeId, spaceId, dateFrom = 20160109, dateTo = 20160111)) // 3 days (09,10,11)
    val (bookingIdA, bookingIdB, bookingIdD) = (
      mongoCreateBooking(placeId, spaceId, slotIdA, name = "Booking A"),
      mongoCreateBooking(placeId, spaceId, slotIdB, name = "Booking B", username = "testuser2"), // booked by another user
      mongoCreateBooking(placeId, spaceId, slotIdD, name = "Booking D"))
    val (bookedIdA, bookedIdB, bookedIdD) = (
      mongoCreateBooked(placeId, Seq(slotIdA), Seq(bookingIdA), paid = Some(true)),
      mongoCreateBooked(placeId, Seq(slotIdB), Seq(bookingIdB), username = "testuser2"), // booked by another user
      mongoCreateBooked(placeId, Seq(slotIdD), Seq(bookingIdD), paid = Some(false)))
    mongoSetSlotBooked(slotIdA, bookedIdA)
    mongoSetSlotBooked(slotIdB, bookedIdB)
    mongoSetSlotBooked(slotIdD, bookedIdD)

    val baseUrl = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$spaceId&from=20160109&to=20160115&booked"

    val urlA = baseUrl+"&paid=true"
    val slotsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsA.map(_.slot_id) should contain only slotIdA

    val urlD = baseUrl+"&paid=false"
    val slotsD = (When getTo urlD withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Slot]]

    slotsD.map(_.slot_id) should contain only slotIdD
  }

  "PATCH to /slots/{id}" should "update writeable attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    mongoSetSlotAttributes(slotId, "{}")

    val url = s"$slotsBaseUrl/slots/$slotId"
    val json = """{"attributes": {"key_rw": "value_a", "key_rwp": "value_b"} }"""
    val slot = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    slot.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("value_a"), "key_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"key_r": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "GET to /slots/{id}" should "return a slot and booking and price with attributes" in { 
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotId)
    mongoSetSlotAttributes(slotId, """ {"key_rw": "value_a", "key_rwp": "value_b"} """)
    mongoSetBookingAttributes(bookingId, """ {"kez_rw": "value_a", "kez_rwp": "value_b"} """)
    mongoSetSlotPriceAttributes(priceId, """ {"kex_rw": "value_a", "kex_rwp": "value_b"} """)

    val url = s"$slotsBaseUrl/slots/$slotId"
    val slot = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    val (slotAttrs, bookingAttrs, priceAttrs) = (slot.attributes.get.value, slot.bookings.get.head.attributes.get.value, slot.prices.get.head.attributes.get.value)
    slotAttrs shouldBe JsObject(Map("key_rw" -> JsString("value_a"), "key_rwp" -> JsString("value_b")))
    bookingAttrs shouldBe JsObject(Map("kez_rw" -> JsString("value_a"), "kez_rwp" -> JsString("value_b")))
    priceAttrs shouldBe JsObject(Map("kex_rw" -> JsString("value_a"), "kex_rwp" -> JsString("value_b")))
  }

}

class MsSlotsBookedSpec extends BaseMsSlotsSpec {

  "POST to /slots/booked" should "give 401 with non system bearer token" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/booked"
    val json = s"""{ "slot_ids": ["$randomId","$randomId"], "as_profile_id": "$profileId" }"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "POST to /slots/booked" should "create a booked object for that user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/booked"
    val json = s"""{ "slot_ids": ["$slotIdA","$slotIdB"], "as_profile_id": "$profileId" }"""
    val booked = (When postTo url entity json withHeaders systemTokenHeader expect() code SC_CREATED).withBody[vo.Booked]

    booked.profile_id.get shouldBe profileId
    booked.status.get shouldBe 1
    booked.slot_ids.get should contain allOf (slotIdA, slotIdB)
    booked.booking_ids shouldBe None
  }

  "PATCH to /slots/booked/{id}" should "give 401 with non system bearer token" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/booked/$randomId"
    val json = s"""{ "status": 2, "as_profile_id": "$profileId" }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "PATCH to /slots/booked/{id}" should "update a booked object" in {
    val placeId = mongoCreatePlace()
    val bookedId = mongoCreateBooked(placeId, username = "testuser2")
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/booked/$bookedId"
    val json = s"""{ "status": 2, "as_profile_id": "$profileId" }"""
    val booked = (When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Booked]

    booked.status.get shouldBe 2
  }

  "PATCH to /slots/booked/{id}" should "give 404 if a booked object does not exist" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/booked/$randomId"
    val json = s"""{ "status": 2, "as_profile_id": "$profileId" }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /slots/booked/{id}" should "give 403 if a booked object does not belong to that user" in {
    val placeId = mongoCreatePlace()
    val bookedId = mongoCreateBooked(placeId, username = "testuser2")
    val profileId = mongoProfileId("testuser")

    val url = s"$slotsBaseUrl/slots/booked/$bookedId"
    val json = s"""{ "status": 2, "as_profile_id": "$profileId" }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_FORBIDDEN
  }

}

class MsSlotsHoldSpec extends BaseMsSlotsSpec {

  "PATCH to /slots/{id}/hold" should "give 401 with non system bearer token" in {
    val url = s"$slotsBaseUrl/slots/$randomId/hold"
    val json = s"""{ "booked_id": "$randomId", "status": 2 }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "PATCH to /slots/{id}/hold" should "acquire a slot for that user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookedId = mongoCreateBooked(placeId)

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 2 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK
  }

  "PATCH to /slots/{id}/hold" should "give 409 on acquire if a slot is already booked" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    val bookedId = mongoCreateBooked(placeId)

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 2 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_CONFLICT
  }

  "PATCH to /slots/{id}/hold" should "confirm an acquired slot for that user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val bookedId = mongoCreateBooked(placeId, username = "testuser2")
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 2, bookedId = Some(bookedId))

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 1 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK
  }

  "PATCH to /slots/{id}/hold" should "give 409 on confirm if a slot is not acquired by that user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val bookedId = mongoCreateBooked(placeId, username = "testuser2")
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 2, bookedId = Some(randomId))

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 1 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_CONFLICT
  }

  "PATCH to /slots/{id}/hold" should "release an acquired slot for that user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val bookedId = mongoCreateBooked(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 2, bookedId = Some(bookedId))

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 3 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK
  }

  "PATCH to /slots/{id}/hold" should "release a booked slot for that user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val bookedId = mongoCreateBooked(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 1, bookedId = Some(bookedId))

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 3 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK
  }

  "PATCH to /slots/{id}/hold" should "give 409 on release if a slot is booked by another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val bookedId = mongoCreateBooked(placeId, username = "testuser2")
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 1, bookedId = Some(randomId))

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 3 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_CONFLICT
  }

  "PATCH to /slots/{id}/hold" should "give 409 on release if a slot is not booked" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookedId = mongoCreateBooked(placeId)

    val url = s"$slotsBaseUrl/slots/$slotId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 3 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_CONFLICT
  }

  "PATCH to /slots/{id}/hold" should "give 404 if a slot does not exist" in {
    val placeId = mongoCreatePlace()
    val bookedId = mongoCreateBooked(placeId)

    val url = s"$slotsBaseUrl/slots/$randomId/hold"
    val json = s"""{ "booked_id": "$bookedId", "status": 2 }"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_NOT_FOUND
  }

}

class MsSlotsBookingsSpec extends BaseMsSlotsSpec {

  "POST to /slots/{id}/bookings" should "give 401 with non system bearer token" in {
    val profileId = mongoProfileId("testuser")
    val url = s"$slotsBaseUrl/slots/$randomId/bookings"
    val json = s"""{"name": "Booking A", "as_profile_id": "$profileId"}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "POST to /slots/{id}/bookings" should "give 404 if a slot does not exist" in {
    val profileId = mongoProfileId("testuser2")
    val url = s"$slotsBaseUrl/slots/$randomId/bookings"
    val json = s"""{"name": "Booking A", "as_profile_id": "$profileId"}"""
    When postTo url entity json withHeaders systemTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /slots/{id}/bookings" should "create a booking for an acquired slot for the specified user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId: String = mongoCreateSlot(placeId, spaceId, bookStatus = 2)
    val bookedId = mongoCreateBooked(placeId, Seq(slotId), status = 1, username = "testuser2")
    mongoSetSlotBooked(slotId, bookedId)
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings"
    val json = s"""{"name": "Booking A", "as_profile_id": "$profileId"}"""
    val booking = (When postTo url entity json withHeaders systemTokenHeader expect() code SC_CREATED).withBody[vo.Booking]

    booking.place_id shouldBe placeId
    booking.space_id shouldBe spaceId
    booking.slot_id shouldBe slotId
    booking.profile_id.get shouldBe profileId
    booking.name.get shouldBe "Booking A"
    booking.status.get shouldBe 2 // is being booked

    val url2 = s"$slotsBaseUrl/slots/$slotId"
    val headers = authHeaderSeq("testuser2")
    val slot = (When getTo url2 withHeaders headers expect() code SC_OK).withBody[vo.Slot]

    slot.bookings.size shouldBe 1
    slot.bookings.get.head.name.get shouldBe "Booking A"
    slot.bookings.get.head.profile_id.get shouldBe profileId
    slot.bookings.get.head.status.get shouldBe 2 // is being booked

    slot.book_status.get shouldBe 2 // is being booked
    slot.booked.get.booked_id shouldBe bookedId
  }

  "POST to /slots/{id}/bookings" should "give 409 if a slot is being booked by another user" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId: String = mongoCreateSlot(placeId, spaceId, bookStatus = 2)
    val bookedId = mongoCreateBooked(placeId, Seq(slotId), username = "testuser2")
    mongoSetSlotBooked(slotId, bookedId)
    mongoCreateBooked(placeId, Seq(slotId), username = "testuser")
    val profileId = mongoProfileId("testuser")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings"
    val json = s"""{"name": "Booking A", "as_profile_id": "$profileId"}"""
    When postTo url entity json withHeaders systemTokenHeader expect() code SC_CONFLICT
  }

  "POST to /slots/{id}/bookings" should "give 409 if a slot is already booked" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId, bookStatus = 1)
    val profileId = mongoProfileId("testuser2")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings"
    val json = s"""{"name": "Booking B", "as_profile_id": "$profileId"}"""
    When postTo url entity json withHeaders systemTokenHeader expect() code SC_CONFLICT
  }

  "GET to /slots/{id}/bookings" should "list bookings within a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingIdA = mongoCreateBooking(placeId, spaceId, slotId, status = 1)
    val bookingIdB = mongoCreateBooking(placeId, spaceId, slotId, status = 0)

    val url = s"$slotsBaseUrl/slots/$slotId/bookings"
    val bookings = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Booking]]

    bookings.map(_.booking_id) should contain only (bookingIdA, bookingIdB)
  }

  "GET to /slots/{id}/bookings?active" should "list active bookings within a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingIdA = mongoCreateBooking(placeId, spaceId, slotId, status = 1)
    val bookingIdB = mongoCreateBooking(placeId, spaceId, slotId, status = 0)

    val url = s"$slotsBaseUrl/slots/$slotId/bookings?active"
    val bookings = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Booking]]

    bookings.map(_.booking_id) should contain only bookingIdA
  }

  "GET to /slots/{id}/bookings/{id}" should "return a booking" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId)

    val url = s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId"
    val booking = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Booking]

    booking.name.get shouldBe "Booking A"
  }

  "GET to /slots/{id}/bookings/{id}" should "give 404 if a booking does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId)

    val url = s"$slotsBaseUrl/slots/$slotId/bookings/$randomId"
    When getTo url withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "GET to /slots/{id}/bookings/{id}" should "give 404 if a booking does not belong to a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotIdA)

    val url = s"$slotsBaseUrl/slots/$slotIdB/bookings/$bookingId"
    When getTo url withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /slots/{id}/bookings/{id}" should "give 403 for non permitted users, including the booking owner" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId, username = "testuser2")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId"
    val json = """{"name": "My Booking" }"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "PATCH to /slots/{id}/bookings/{id}" should "update only limited set of fields when submitted with non system bearer token" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId, username = "testuser2")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId"
    val jsonA = """{"name": "My Booking" }"""
    val bookingA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Booking]

    bookingA.name.get shouldBe "My Booking"

    val jsonB = """{"name": "My Booking", "status": 0 }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "PATCH to /slots/{id}/bookings/{id}" should "update a booking when submitted with system bearer token" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId, username = "testuser2")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId"
    val json = """{"name": "My Booking", "status": 0 }"""
    val booking = (When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Booking]

    booking.name.get shouldBe "My Booking"
    booking.status.get shouldBe 0
  }

  "PATCH to /slots/{id}/bookings/{id}" should "update writeable attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val bookingId = mongoCreateBooking(placeId, spaceId, slotId, username = "testuser2")
    mongoSetBookingAttributes(bookingId, "{}")

    val url = s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId"
    val json = """{"attributes": {"kez_rw": "value_a", "kez_rwp": "value_b"} }"""
    val booking = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Booking]

    booking.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("value_a"), "kez_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"kez_r": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN

    // write once
    val jsonC = """{"attributes": {"kez_op": "value_a"} }"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity jsonC withHeaders headers expect() code SC_OK
    When patchTo url entity jsonC withHeaders headers expect() code SC_FORBIDDEN
    When patchTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_OK
  }

}

class MsSlotsPricesSpec extends BaseMsSlotsSpec {

  "POST to /slots/{id}/prices" should "give 404 if a slot does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$randomId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /slots/{id}/prices" should "give 403 if not place moderator" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$slotId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /slots/{id}/prices" should "create a new price within a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$slotId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    val price = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Price]

    price.name.get shouldBe "Slalom"
  }

  "POST then GET to /slots/{id}/prices" should "create a new price within a slot then return it" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)

    val url = s"$slotsBaseUrl/slots/$slotId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    val price = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Price]

    price.name.get shouldBe "Slalom"

    val prices = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    prices.size shouldBe 1
    prices.head.name.get shouldBe "Slalom"
  }

  "PATCH to /slots/{id}/prices/{id}" should "update a price" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotId, "Slalom")

    val url = s"$slotsBaseUrl/slots/$slotId/prices/$priceId"
    val json = """{"name": "Wakeboard"}"""
    val price = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Price]

    price.name.get shouldBe "Wakeboard"
  }

  "DELETE to /slots/{id}/prices/{id}" should "delete a price" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotId, "Slalom")

    val url = s"$slotsBaseUrl/slots/$slotId/prices/$priceId"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_OK
  }

  "GET to /slots/{id}/prices" should "list prices within a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val priceIdA = mongoCreateSlotPrice(placeId, spaceId, slotId, "Slalom")
    val priceIdB = mongoCreateSlotPrice(placeId, spaceId, slotId, "Wakeboard")

    val url = s"$slotsBaseUrl/slots/$slotId/prices"
    val prices = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    prices.size shouldBe 2
    prices(0).name.get shouldBe "Slalom"
    prices(1).name.get shouldBe "Wakeboard"
  }

  "GET to /slots/{id}/effective/prices" should "list prices within a slot or parent spaces" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    val priceId1 = mongoCreateSpacePrice(placeId, spaceId)
    val priceId2 = mongoCreateSpacePrice(placeId, spaceId)
    val priceIdA = mongoCreateSlotPrice(placeId, spaceId, slotIdA)

    val urlA = s"$slotsBaseUrl/slots/$slotIdA/effective/prices"
    val pricesA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    pricesA.map(_.price_id) should contain only priceIdA

    val urlB = s"$slotsBaseUrl/slots/$slotIdB/effective/prices"
    val pricesB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    pricesB.map(_.price_id) should contain only (priceId1, priceId2)

    val urlC = s"$slotsBaseUrl/slots/$slotIdB/prices"
    val pricesC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    pricesC.size shouldBe 0
  }

  "GET to /slots/{id}/prices/{id}" should "return a price" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotId, "Slalom")

    val url = s"$slotsBaseUrl/slots/$slotId/prices/$priceId"
    val price = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Price]

    price.name.get shouldBe "Slalom"
  }

  "GET to /slots/{id}/prices/{id}" should "give 404 if a price does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotId, "Slalom")

    val url = s"$slotsBaseUrl/slots/$slotId/prices/$randomId"
    When getTo url withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "GET to /slots/{id}/prices/{id}" should "give 404 if a price does not belong to a slot" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotIdA = mongoCreateSlot(placeId, spaceId)
    val slotIdB = mongoCreateSlot(placeId, spaceId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotIdA, "Slalom")

    val url = s"$slotsBaseUrl/slots/$slotIdB/prices/$priceId"
    When getTo url withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /slots/{id}/prices/{id}" should "update writeable attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId)
    val priceId = mongoCreateSlotPrice(placeId, spaceId, slotId)
    mongoSetSlotPriceAttributes(priceId, "{}")

    val url = s"$slotsBaseUrl/slots/$slotId/prices/$priceId"
    val json = """{"attributes": {"kex_rw": "value_a", "kex_rwp": "value_b"} }"""
    val price = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Price]

    price.attributes.get.value shouldBe JsObject(Map("kex_rw" -> JsString("value_a"), "kex_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"kex_r": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }
}

class MsSlotsBoundsSpec extends BaseMsSlotsSpec {   

  "GET to /slots/{id}/effective/bounds?book" should "return booking bounds for a slot inherited from parent spaces" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId,
      bookBounds = Some(ms.places.vo.Bounds(Some(ms.places.vo.Bound(date = None, time = Some(800), before = Some(14*1440))), None))) // open: 2 weeks before at 8:00, close: ?
    val slotIdA = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170324, dateTo = 20170324, timeFrom = 1200, timeTo = 1215, // 24/03/2017 12:00 to 12:15
      bookBounds = Some(vo.Bounds(None, Some(vo.Bound(date = Some(20170322), time = Some(1000), None))))) // open: ?, close: 22/03/2017 at 10:00
    val slotIdB = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170324, dateTo = 20170324, timeFrom = 1215, timeTo = 1230) // 24/03/2017 12:15 to 12:30

    val urlA = s"$slotsBaseUrl/slots/$slotIdA/effective/bounds?book"
    val boundsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (boundsA.open, boundsA.close.get) shouldBe (None, vo.Bound(Some(20170322), Some(1000), None))
    Some(boundsA).map(b => (b.date_from, b.date_to, b.time_from, b.time_to)).get shouldBe (None, Some(20170322), None, Some(1000))

    val urlB = s"$slotsBaseUrl/slots/$slotIdB/effective/bounds?book"
    val boundsB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (boundsB.open.get, boundsB.close) shouldBe (vo.Bound(None, Some(800), Some(14*1440)), None)
    Some(boundsB).map(b => (b.date_from, b.date_to, b.time_from, b.time_to)).get shouldBe (Some(20170310), None, Some(800), None)
  }

  "GET to /slots/{id}/effective/bounds?book" should "calculate book bounds date and time" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170318, dateTo = 20170318, timeFrom = 1200, timeTo = 1230,
      bookBounds = Some(vo.Bounds( // open: 2 weeks before at 8:00, close: 2 days before at 24:00
        Some(vo.Bound(date = None, time = Some(800), before = Some(14*1440))),
        Some(vo.Bound(date = None, time = Some(2400), before = Some(2*1440))))))

    val urlA = s"$slotsBaseUrl/slots/$slotId/effective/bounds?book"
    val bounds = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (bounds.date_from.get, bounds.time_from.get) shouldBe (20170304, 800)
    (bounds.date_to.get, bounds.time_to.get) shouldBe (20170316, 2400)
  }

  "GET to /slots/{id}/effective/bounds?cancel" should "return cancel bounds for a slot inherited from parent spaces" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId,
      cancelBounds = Some(ms.places.vo.Bounds(None, Some(ms.places.vo.Bound(date = None, time = Some(2400), before = Some(2*1440)))))) // open: ?, close: 2 days before at 24:00
    val slotIdA = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170324, dateTo = 20170324, timeFrom = 1200, timeTo = 1215, // 24/03/2017 12:00 to 12:15
      cancelBounds = Some(vo.Bounds(Some(vo.Bound(date = Some(20170323), time = Some(2000), None)), None))) // open: 23/03/2017 at 20:00, close: ?
    val slotIdB = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170324, dateTo = 20170324, timeFrom = 1215, timeTo = 1230) // 24/03/2017 12:15 to 12:30

    val urlA = s"$slotsBaseUrl/slots/$slotIdA/effective/bounds?cancel"
    val boundsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (boundsA.open.get, boundsA.close) shouldBe (vo.Bound(Some(20170323), Some(2000), None), None)
    Some(boundsA).map(b => (b.date_from, b.date_to, b.time_from, b.time_to)).get shouldBe (Some(20170323), None, Some(2000), None)

    val urlB = s"$slotsBaseUrl/slots/$slotIdB/effective/bounds?cancel"
    val boundsB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (boundsB.open, boundsB.close.get) shouldBe (None, vo.Bound(None, Some(2400), Some(2*1440)))
    Some(boundsB).map(b => (b.date_from, b.date_to, b.time_from, b.time_to)).get shouldBe (None, Some(20170322), None, Some(2400))
  }

  "GET to /slots/{id}/effective/bounds?cancel" should "return booking bounds if cancel bounds not set for a slot inherited from parent spaces" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId,
      bookBounds = Some(ms.places.vo.Bounds(Some(ms.places.vo.Bound(date = None, time = Some(800), before = Some(14*1440))), None))) // open: 2 weeks before at 8:00, close: ?
    val slotIdA = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170324, dateTo = 20170324, timeFrom = 1200, timeTo = 1215, // 24/03/2017 12:00 to 12:15
      cancelBounds = Some(vo.Bounds(Some(vo.Bound(date = Some(20170323), time = Some(2000), None)), None))) // open: 23/03/2017 at 20:00, close: ?
    val slotIdB = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170324, dateTo = 20170324, timeFrom = 1215, timeTo = 1230) // 24/03/2017 12:15 to 12:30

    val urlA = s"$slotsBaseUrl/slots/$slotIdA/effective/bounds?cancel"
    val boundsA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (boundsA.open.get, boundsA.close) shouldBe (vo.Bound(Some(20170323), Some(2000), None), None)
    Some(boundsA).map(b => (b.date_from, b.date_to, b.time_from, b.time_to)).get shouldBe (Some(20170323), None, Some(2000), None)

    val urlB = s"$slotsBaseUrl/slots/$slotIdB/effective/bounds?cancel"
    val boundsB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (boundsB.open.get, boundsB.close) shouldBe (vo.Bound(None, Some(800), Some(14*1440)), None)
    Some(boundsB).map(b => (b.date_from, b.date_to, b.time_from, b.time_to)).get shouldBe (Some(20170310), None, Some(800), None)
  }

  "GET to /slots/{id}/effective/bounds?cancel" should "calculate cancel bounds date and time" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val slotId = mongoCreateSlot(placeId, spaceId,
      dateFrom = 20170318, dateTo = 20170318, timeFrom = 1200, timeTo = 1230,
      cancelBounds = Some(vo.Bounds( // open: none, close: 2 days before at 24:00
        None,
        Some(vo.Bound(date = None, time = Some(2400), before = Some(2*1440))))))

    val urlA = s"$slotsBaseUrl/slots/$slotId/effective/bounds?cancel"
    val bounds = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Bounds]

    (bounds.date_from, bounds.time_from) shouldBe (None, None)
    (bounds.date_to.get, bounds.time_to.get) shouldBe (20170316, 2400)
  }

}
