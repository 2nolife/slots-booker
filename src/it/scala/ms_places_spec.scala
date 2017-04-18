package com.coldcore.slotsbooker
package test

import akka.http.scaladsl.model.headers.Authorization
import ms.{Timestamp => ts}
import org.apache.http.HttpStatus._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import ms.places.vo
import spray.json.{JsObject, JsString}

abstract class BaseMsPlacesSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

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

}

class MsPlacesSpec extends BaseMsPlacesSpec {

  "POST to /places" should "give 401 with invalid bearer token" in {
    val url = s"$placesBaseUrl/places"
    val json = """{"name": "My Place"}"""
    val headers = Seq((Authorization.name, s"Bearer ABCDEF"))
    When postTo url entity json withHeaders headers expect() code SC_UNAUTHORIZED
  }

  "POST to /places" should "create a new place" in {
    val url = s"$placesBaseUrl/places"
    val json = """{"name": "My Place Name"}"""
    val place = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Place]

    place.name.get shouldBe "My Place Name"
  }

  "PATCH to /places/{id}" should "give 401 with invalid bearer token" in {
    val url = s"$placesBaseUrl/places/$randomId"
    val json = """{"name": "My Place"}"""
    val headers = Seq((Authorization.name, s"Bearer ABCDEF"))
    When patchTo url entity json withHeaders headers expect() code SC_UNAUTHORIZED
  }

  "PATCH to /places/{id}" should "give 404 if a place does not exist" in {
    val url = s"$placesBaseUrl/places/$randomId"
    val json = """{"name": "My Place"}"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /places/{id}" should "update a place" in {
    val placeId = mongoCreatePlace()

    val url = s"$placesBaseUrl/places/$placeId"
    val jsonA = """{"name": "My New Name"}"""
    val placeA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeA.name.get shouldBe "My New Name"
    placeA.address shouldBe None

    val jsonB = """{"address": { "line1" : "Village road", "postcode" : "DH2 OSH", "town": "Denham", "country": "UK" }}"""
    val placeB = (When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeB.name.get shouldBe "My New Name"
    placeB.address should not be None
  }

  "PATCH to /places/{id}" should "update place local date and time" in {
    val placeId = mongoCreatePlace()

    val url = s"$placesBaseUrl/places/$placeId"
    val jsonA = """{"datetime": { "offset_minutes": 180 }}"""
    val placeA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    {
      placeA.datetime.map(dt => (dt.timezone, dt.offset_minutes.get)).get shouldBe (None, 180)
      val (date, time, utc_date, utc_time) = placeA.datetime.map(dt => (dt.date.get, dt.time.get, dt.utc_date.get, dt.utc_time.get)).get
      val (local, utc) = (ts.asCalendar(date, time), ts.asCalendar(utc_date, utc_time))
      local shouldBe ts.addMinutes(utc, 180)
    }

    val jsonB = """{"datetime": { "timezone": "CET", "offset_minutes": 180 }}""" // CET +1:00, offset_minutes not used
    val placeB = (When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    {
      placeB.datetime.map(dt => (dt.timezone.get, dt.offset_minutes.get)).get shouldBe ("CET", 60)
      val (date, time, utc_date, utc_time) = placeB.datetime.map(dt => (dt.date.get, dt.time.get, dt.utc_date.get, dt.utc_time.get)).get
      val (local, utc) = (ts.asCalendar(date, time), ts.asCalendar(utc_date, utc_time))
      local shouldBe ts.addMinutes(utc, 60)
    }

  }

  "PATCH to /places/{id}" should "give 403 if not place moderator" in {
    val placeId = mongoCreatePlace()

    val url = s"$placesBaseUrl/places/$placeId"
    val json = """{"name": "My New Name"}"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "GET to /places/{id}" should "return a place" in {
    val placeId = mongoCreatePlace()

    val url = s"$placesBaseUrl/places/$placeId"
    val headers = authHeaderSeq("testuser2")
    val place = (When getTo url withHeaders headers expect() code SC_OK).withBody[vo.Place]

    place.name.get shouldBe "My Place Name"
  }

  "GET to /places/{id}" should "return a place with selected fields" in {
    val placeId = mongoCreatePlace()
    val spaceIdA = mongoCreateSpace(placeId, name = "Space A")
    val spaceIdA1 = mongoCreateInnerSpace(placeId, spaceIdA, name = "Space A1")
    val spaceIdA2 = mongoCreateInnerSpace(placeId, spaceIdA, name = "Space A2")
    val priceIdA = mongoCreateSpacePrice(placeId, spaceIdA, name = "Price A")
    val priceIdA1 = mongoCreateSpacePrice(placeId, spaceIdA1, name = "Price A1")
    val priceIdA2 = mongoCreateSpacePrice(placeId, spaceIdA2, name = "Price A2")

    val baseurl = s"$placesBaseUrl/places/$placeId"
    val jsonA = """{"address": { "line1" : "Village road", "postcode" : "DH2 OSH", "town": "Denham", "country": "UK" }}"""
    val placeA = (When patchTo baseurl entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    val urlB = s"$baseurl?deep=false" // shallow
    val placeB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeB.name.get shouldBe "My Place Name"
    placeB.address.get.line1.get shouldBe "Village road"
    placeB.spaces shouldBe None

    val urlC = s"$baseurl?deep_prices=false" // all spaces with shallow prices
    val placeC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeC.name.get shouldBe "My Place Name"
    placeC.address.get.line1.get shouldBe "Village road"

    {
      val (spaces, space_A) = (placeC.spaces.get, placeC.spaces.get.head)
      (space_A.name.get, space_A.spaces.get.size, space_A.prices) shouldBe ("Space A", 2, None)
      val (space_A1, space_A2) = (space_A.spaces.get(0), space_A.spaces.get(1))
      (space_A1.name.get, space_A2.name.get) shouldBe ("Space A1", "Space A2")
      (space_A.prices, space_A1.prices, space_A2.prices) shouldBe (None, None, None)
    }
  }

  "PATCH to /places/{id}" should "update moderators if submitted by the place owner, otherwise give 403" in {
    val placeId = mongoCreatePlace()
    val anotherProfileId = mongoProfileId("testuser2")

    val url = s"$placesBaseUrl/places/$placeId"
    val jsonA = s"""{"moderators": ["$anotherProfileId"]}"""
    val place = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    place.moderators should not be None
    place.moderators.get.head shouldBe anotherProfileId

    val jsonB = """{"name": "My New Name"}"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity jsonB withHeaders headers expect() code SC_OK

    val jsonC = s"""{"moderators": ["$anotherProfileId"]}"""
    When patchTo url entity jsonC withHeaders headers expect() code SC_FORBIDDEN
  }

  "DELETE to /places/{id}" should "delete a place if submitted by the place owner, otherwise give 403" in {
    val placeId = mongoCreatePlace()
    val anotherProfileId = mongoProfileId("testuser2")

    val url = s"$placesBaseUrl/places/$placeId"
    val jsonA = s"""{"moderators": ["$anotherProfileId"]}"""

    val place = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    place.moderators should not be None
    place.moderators.get.head shouldBe anotherProfileId

    val jsonB = """{"name": "My New Name"}"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity jsonB withHeaders headers expect() code SC_OK

    When deleteTo url withHeaders headers expect() code SC_FORBIDDEN

    When deleteTo url withHeaders testuserTokenHeader expect() code SC_OK

    When deleteTo url withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /places/{id}" should "update writeable attributes if submitted by the place owner or moderators, otherwise give 403" in {
    val placeId = mongoCreatePlace()
    val moderatorProfileId = mongoProfileId("testuser2")
    val anotherProfileId = mongoProfileId("testuser3")
    mongoSetPlaceAttributes(placeId, "{}")
    mongoSetPlaceModerators(placeId, moderatorProfileId :: Nil)

    val url = s"$placesBaseUrl/places/$placeId"
    val jsonA = """{"attributes": {"key_rw": "value_a", "key_rwp": "value_b"} }"""
    val placeA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeA.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("value_a"), "key_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"key_rw": "value_c"} }"""
    val moderatorTokenHeader = authHeaderSeq("testuser2")
    val placeB = (When patchTo url entity jsonB withHeaders moderatorTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeB.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("value_c"), "key_rwp" -> JsString("value_b")))

    val jsonC = """{"attributes": {"key_r": "value_a"} }"""
    When patchTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_FORBIDDEN

    val anotherTokenHeader = authHeaderSeq("testuser3")
    When patchTo url entity jsonA withHeaders anotherTokenHeader expect() code SC_FORBIDDEN
  }

  "GET to /places/search?{attribute_name}={?}" should "list found places by attributes joined by 'and' condition" in {
    val placeIdA = mongoCreatePlace()
    val placeIdB = mongoCreatePlace()
    mongoSetPlaceAttributes(placeIdA, """{"key_rw": "value_c", "key": "value_d"}""")
    mongoSetPlaceAttributes(placeIdB, """{"key_rw": "value_a", "key": "value_e"}""")

    val baseUrl = s"$placesBaseUrl/places/search"
    val headers = authHeaderSeq("testuser2")

    val urlA = s"$baseUrl?key_rw=value_a"
    val placesA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesA.map(_.place_id) should contain only placeIdB

    val urlB = s"$baseUrl?key=value*"
    val placesB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesB.map(_.place_id) should contain allOf(placeIdA, placeIdB)

    val urlC = s"$baseUrl?key_rw=value_a&key=value_e"
    val placesC = (When getTo urlC withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesC.map(_.place_id) should contain only placeIdB

    val urlD = s"$baseUrl?key_rw=value_a&deep=false"
    val placesD = (When getTo urlD withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesD.map(_.place_id) should contain only placeIdB
  }

  "GET to /places/search?{attribute_name}={?}" should "list found places by attributes joined by 'or' condition" in {
    val placeIdA = mongoCreatePlace()
    val placeIdB = mongoCreatePlace()
    mongoSetPlaceAttributes(placeIdA, """{"key_rw": "value_c", "key": "value_d"}""")
    mongoSetPlaceAttributes(placeIdB, """{"key_rw": "value_a", "key": "value_e"}""")

    val baseUrl = s"$placesBaseUrl/places/search"
    val headers = authHeaderSeq("testuser2")

    val urlA = s"$baseUrl?key_rw=value_a&key_rw=value_c&or"
    val placesA = (When getTo urlA withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesA.map(_.place_id) should contain allOf(placeIdA, placeIdB)

    val urlB = s"$baseUrl?key_rw=value_a&key=value_d&or"
    val placesB = (When getTo urlB withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesB.map(_.place_id) should contain allOf(placeIdA, placeIdB)

    val urlC = s"$baseUrl?key_rw=value_a&key=value_e&or"
    val placesC = (When getTo urlC withHeaders headers expect() code SC_OK).withBody[Seq[vo.Place]]

    placesC.map(_.place_id) should contain only placeIdB
  }

  "GET to /places/{id}" should "return a place and space and price with attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceId)
    mongoSetPlaceAttributes(placeId, """ {"key_rw": "value_a", "key_rwp": "value_b"} """)
    mongoSetSpaceAttributes(spaceId, """ {"kez_rw": "value_a", "kez_rwp": "value_b"} """)
    mongoSetSpacePriceAttributes(priceId, """ {"kex_rw": "value_a", "kex_rwp": "value_b"} """)

    val url = s"$placesBaseUrl/places/$placeId"
    val place = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    val (placeAttrs, spaceAttrs, priceAttrs) = (place.attributes.get.value, place.spaces.get.head.attributes.get.value, place.spaces.get.head.prices.get.head.attributes.get.value)
    placeAttrs shouldBe JsObject(Map("key_rw" -> JsString("value_a"), "key_rwp" -> JsString("value_b")))
    spaceAttrs shouldBe JsObject(Map("kez_rw" -> JsString("value_a"), "kez_rwp" -> JsString("value_b")))
    priceAttrs shouldBe JsObject(Map("kex_rw" -> JsString("value_a"), "kex_rwp" -> JsString("value_b")))
  }

}

class MsPlaceSpacesSpec extends BaseMsPlacesSpec {

  "POST to /places/{id}/spaces" should "give 404 if a place does not exist" in {
    val url = s"$placesBaseUrl/places/$randomId/spaces"
    val json = """{"name": "Big Lake"}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /places/{id}/spaces" should "give 403 if not place moderator" in {
    val placeId = mongoCreatePlace()

    val url = s"$placesBaseUrl/places/$placeId/spaces"
    val json = """{"name": "Big Lake"}"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /places/{id}/spaces" should "create a new space within a place" in {
    val placeId = mongoCreatePlace()

    val url = s"$placesBaseUrl/places/$placeId/spaces"
    val jsonA = """{"name": "Big Lake"}"""
    val spaceA = (When postTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Space]

    val jsonB = """{"name": "Small Lake"}"""
    val spaceB = (When postTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Space]

    val url2 = s"$placesBaseUrl/places/$placeId"
    val place = (When getTo url2 withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    spaceA.name.get shouldBe "Big Lake"
    spaceB.name.get shouldBe "Small Lake"

    place.spaces should not be None
    val spaces = place.spaces.get
    spaces.size shouldBe 2
    spaces(0).name.get shouldBe "Big Lake"
    spaces(1).name.get shouldBe "Small Lake"
  }

  "PATCH to /places/{id}/spaces/{id}" should "update a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val json = """{"name": "Big Lake"}"""
    val space = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    space.name.get shouldBe "Big Lake"
    space.space_id shouldBe spaceId
  }

  "PATCH to /places/{id}/spaces/{id}" should "update arbitrary metadata" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val json = """{"metadata": {"title": "Spiderman", "pricing": "Flexible"} }"""
    val space = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    space.space_id shouldBe spaceId
    space.metadata should not be None
    space.metadata.get shouldBe JsObject(Map("title" -> JsString("Spiderman"), "pricing" -> JsString("Flexible")))
  }

  "DELETE to /places/{id}/spaces/{id}" should "delete a space" in {
    val placeId = mongoCreatePlace()
    val spaceId1 = mongoCreateSpace(placeId, name = "Space 1")
    val spaceId2 = mongoCreateSpace(placeId, name = "Space 2")
    val spaceId3 = mongoCreateSpace(placeId, name = "Space 3")

    val urlA = s"$placesBaseUrl/places/$placeId/spaces/$spaceId2"
    When deleteTo urlA withHeaders testuserTokenHeader expect() code SC_OK

    val urlB = s"$placesBaseUrl/places/$placeId"
    val placeB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Place]

    placeB.spaces.get.size shouldBe 2
    placeB.spaces.get.head.name.get shouldBe "Space 1"
  }

  "GET to /places/{id}/spaces" should "list spaces within a place" in {
    val placeId = mongoCreatePlace()
    val spaceIdA = mongoCreateSpace(placeId)
    val spaceIdB = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces"
    val spaces = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Space]]

    spaces.map(_.space_id) should contain only (spaceIdA, spaceIdB)
  }

  "GET to /places/{id}/spaces" should "list spaces within a place with selected fields" in {
    val placeId = mongoCreatePlace()
    val spaceIdA = mongoCreateSpace(placeId, name = "Space A")
    val spaceIdA1 = mongoCreateInnerSpace(placeId, spaceIdA, name = "Space A1")
    val spaceIdA2 = mongoCreateInnerSpace(placeId, spaceIdA, name = "Space A2")
    val priceIdA = mongoCreateSpacePrice(placeId, spaceIdA, name = "Price A")
    val priceIdA1 = mongoCreateSpacePrice(placeId, spaceIdA1, name = "Price A1")
    val priceIdA2 = mongoCreateSpacePrice(placeId, spaceIdA2, name = "Price A2")

    val baseurl = s"$placesBaseUrl/places/$placeId/spaces"

    val urlA = s"$baseurl?deep=false" // shallow
    val spacesA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Space]]

    spacesA.size shouldBe 1

    {
      val (space_A, inner_spaces, inner_prices) = (spacesA.head, spacesA.head.spaces, spacesA.head.prices)
      (space_A.name.get, inner_spaces, inner_prices) shouldBe ("Space A", None, None)
    }

    val urlB = s"$baseurl?deep_prices=false" // all spaces with shallow prices
    val spacesB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Space]]

    spacesB.size shouldBe 1

    {
      val (space_A, space_A1, space_A2) = (spacesB.head, spacesB.head.spaces.get(0), spacesB.head.spaces.get(1))
      (space_A.name.get, space_A1.name.get, space_A2.name.get) shouldBe ("Space A", "Space A1", "Space A2")
      val (prices_A, prices_A1, prices_A2) = (space_A.prices, space_A1.prices, space_A2.prices)
      (prices_A, prices_A1, prices_A2) shouldBe (None, None, None)
    }
  }

  "GET to /places/{id}/spaces/{id}/spaces" should "list spaces within a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val spaceIdA = mongoCreateInnerSpace(placeId, spaceId)
    val spaceIdB = mongoCreateInnerSpace(placeId, spaceId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/spaces"
    val spaces = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Space]]

    spaces.map(_.space_id) should contain only (spaceIdA, spaceIdB)
  }

  "GET to /places/{id}/spaces/{id}" should "return a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val space = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    space.name.get shouldBe "Small Lake"
  }

  "GET to /places/{id}/spaces/{id}" should "return a space with selected fields" in {
    val placeId = mongoCreatePlace()
    val spaceIdA = mongoCreateSpace(placeId, name = "Space A")
    val spaceIdA1 = mongoCreateInnerSpace(placeId, spaceIdA, name = "Space A1")
    val spaceIdA2 = mongoCreateInnerSpace(placeId, spaceIdA, name = "Space A2")
    val priceIdA = mongoCreateSpacePrice(placeId, spaceIdA, name = "Price A")
    val priceIdA1 = mongoCreateSpacePrice(placeId, spaceIdA1, name = "Price A1")
    val priceIdA2 = mongoCreateSpacePrice(placeId, spaceIdA2, name = "Price A2")

    val baseurl = s"$placesBaseUrl/places/$placeId/spaces/$spaceIdA"

    val urlB = s"$baseurl?deep=false" // shallow
    val spaceB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    (spaceB.name.get, spaceB.spaces, spaceB.prices) shouldBe ("Space A", None, None)

    val urlC = s"$baseurl?deep_prices=false" // all spaces with shallow prices
    val spaceC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    (spaceC.name.get, spaceC.spaces.get.size, spaceC.prices) shouldBe ("Space A", 2, None)

    {
      val (space_A1, space_A2) = (spaceC.spaces.get(0), spaceC.spaces.get(1))
      (space_A1.name.get, space_A2.name.get) shouldBe ("Space A1", "Space A2")
      val (prices_A, prices_A1, prices_A2) = (spaceC.prices, space_A1.prices, space_A2.prices)
      (prices_A, prices_A1, prices_A2) shouldBe (None, None, None)
    }
  }

  "POST to /places/{id}/spaces/{id}" should "create a new space within a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val jsonA = """{"name": "Parking A"}"""
    When postTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_CREATED

    val jsonB = """{"name": "Parking B"}"""
    When postTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_CREATED

    val space = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    space.name.get shouldBe "Small Lake"
    space.spaces should not be None
    val spaces = space.spaces.get
    spaces.size shouldBe 2
    spaces(0).name.get shouldBe "Parking A"
    spaces(1).name.get shouldBe "Parking B"
  }

  "DELETE to /places/{id}/spaces/{id}" should "delete a space within a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val spaceId1 = mongoCreateInnerSpace(placeId, spaceId, name = "Space 1")
    val spaceId2 = mongoCreateInnerSpace(placeId, spaceId, name = "Space 2")
    val spaceId3 = mongoCreateInnerSpace(placeId, spaceId, name = "Space 3")

    val urlA = s"$placesBaseUrl/places/$placeId/spaces/$spaceId2"
    When deleteTo urlA withHeaders testuserTokenHeader expect() code SC_OK

    val urlB = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val spaceB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    spaceB.spaces.get.size shouldBe 2
    spaceB.spaces.get.head.name.get shouldBe "Space 1"
  }

  "PATCH to /places/{id}/spaces/{id}" should "update writeable attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    mongoSetSpaceAttributes(spaceId, "{}")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val json = """{"attributes": {"kez_rw": "value_a", "kez_rwp": "value_b"} }"""
    val space = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    space.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("value_a"), "kez_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"kez_r": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "GET to /places/{id}/spaces/{id}" should "return a space and inner spaces with attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceId)
    val spaceIdA = mongoCreateInnerSpace(placeId, spaceId)
    val spaceIdB = mongoCreateInnerSpace(placeId, spaceId)
    mongoSetSpaceAttributes(spaceId, """ {"kez_rw": "value_a", "kez_rwp": "value_b"} """)
    mongoSetSpaceAttributes(spaceIdA, """ {"kez_rw": "value_c", "kez_rwp": "value_d"} """)
    mongoSetSpaceAttributes(spaceIdB, """ {"kez_rw": "value_e", "kez_rwp": "value_f"} """)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId"
    val space = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Space]

    val spaceAttrs = space.attributes.get.value
    spaceAttrs shouldBe JsObject(Map("kez_rw" -> JsString("value_a"), "kez_rwp" -> JsString("value_b")))

    val (spaceAttrsA, spaceAttrsB) = (space.spaces.get.head.attributes.get.value, space.spaces.get.last.attributes.get.value)
    spaceAttrsA shouldBe JsObject(Map("kez_rw" -> JsString("value_c"), "kez_rwp" -> JsString("value_d")))
    spaceAttrsB shouldBe JsObject(Map("kez_rw" -> JsString("value_e"), "kez_rwp" -> JsString("value_f")))
  }

}

class MsPlaceSpacePricesSpec extends BaseMsPlacesSpec {

  "POST to /places/{id}/spaces/{id}/prices" should "give 404 if a place does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$randomId/spaces/$spaceId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /places/{id}/spaces/{id}/prices" should "give 404 if a space does not exist" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$randomId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    When postTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /places/{id}/spaces/{id}/prices" should "give 403 if not place moderator" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    val headers = authHeaderSeq("testuser2")
    When postTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "POST to /places/{id}/spaces/{id}/prices" should "create a new price within a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    val price = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Price]

    price.name.get shouldBe "Slalom"
  }

  "POST then GET to /places/{id}/spaces/{id}/prices" should "create a new price within a space then return it" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices"
    val json = """{"name": "Slalom", "amount": 1700, "currency": "GBP"}"""
    val price = (When postTo url entity json withHeaders testuserTokenHeader expect() code SC_CREATED).withBody[vo.Price]

    price.name.get shouldBe "Slalom"

    val prices = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    prices.size shouldBe 1
    prices.head.name.get shouldBe "Slalom"
  }

  "PATCH to /places/{id}/spaces/{id}/prices/{id}" should "update a price" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceId, "Slalom")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices/$priceId"
    val json = """{"name": "Wakeboard"}"""
    val price = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Price]

    price.name.get shouldBe "Wakeboard"
  }

  "DELETE to /places/{id}/spaces/{id}/prices/{id}" should "delete a price" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceId, "Slalom")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices/$priceId"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_OK
  }

  "GET to /places/{id}/spaces/{id}/prices" should "list prices within a space" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceIdA = mongoCreateSpacePrice(placeId, spaceId, "Slalom")
    val priceIdB = mongoCreateSpacePrice(placeId, spaceId, "Wakeboard")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices"
    val prices = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Price]]

    prices.size shouldBe 2
    prices(0).name.get shouldBe "Slalom"
    prices(1).name.get shouldBe "Wakeboard"
  }

  "GET to /places/{id}/spaces/{id}/prices/{id}" should "return a price" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceId, "Slalom")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices/$priceId"
    val price = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Price]

    price.name.get shouldBe "Slalom"
  }

  "GET to /places/{id}/spaces/{id}/prices/{id}" should "give 404 is a price does not belong to a space" in {
    val placeId = mongoCreatePlace()
    val spaceIdA = mongoCreateSpace(placeId)
    val spaceIdB = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceIdA, "Slalom")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceIdB/prices/$priceId"
    When getTo url withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "PATCH to /places/{id}/spaces/{id}/prices/{id}" should "update writeable attributes" in {
    val placeId = mongoCreatePlace()
    val spaceId = mongoCreateSpace(placeId)
    val priceId = mongoCreateSpacePrice(placeId, spaceId)
    mongoSetSpacePriceAttributes(priceId, "{}")

    val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices/$priceId"
    val json = """{"attributes": {"kex_rw": "value_a", "kex_rwp": "value_b"} }"""
    val price = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Price]

    price.attributes.get.value shouldBe JsObject(Map("kex_rw" -> JsString("value_a"), "kex_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"kex_r": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

}
