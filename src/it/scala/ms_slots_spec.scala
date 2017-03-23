package com.coldcore.slotsbooker
package test

import akka.http.scaladsl.model.headers.Authorization
import org.apache.http.HttpStatus._
import org.scalatest._
import com.coldcore.slotsbooker.ms.slots.vo
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

    (slotA.name.get, slotA.bookings.get.size, slotA.prices.get.size) shouldBe("Slot A", 1, 1)

    {
      val (booking, price) = (slotA.bookings.get.head, slotA.prices.get.head)
      (booking.name, price.name) shouldBe (None, None)
    }

    val urlB = s"$baseurl?deep_prices=false" // shallow prices
    val slotB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Slot]

    (slotB.name.get, slotB.bookings.get.size, slotB.prices.get.size) shouldBe("Slot A", 1, 1)

    {
      val (booking, price) = (slotB.bookings.get.head, slotB.prices.get.head)
      (booking.name.get, price.name) shouldBe ("Booking A", None)
    }
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
    slots.head.bookings.get(0).name shouldBe None
  }

  "GET to /slots/search?place_id={?}&space_id={?}&from={?}&to={?}&booked" should "list found slots with which were booked by a user" in {
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
    val json = """{"attributes": {"key_rw": "value_a", "key_rwp": "value_b"} }"""
    val booking = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Booking]

    booking.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("value_a"), "key_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"key_r": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
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

}
