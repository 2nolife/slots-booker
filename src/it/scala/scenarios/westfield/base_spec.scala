package com.coldcore.slotsbooker
package scenarios.westfield

import ms.Logger
import ms.places.vo
import ms.slots.{vo => svo}
import test.HelperObjects
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import org.apache.http.HttpStatus._

abstract class BaseSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects with Logger {

  initLoggingAdapter

  implicit val executionContext = system.dispatcher

  val resourcesPath = "/scenarios/westfield"

  lazy val placeId = createWestfiledCinema()

  val ownerTokenHeader = authHeaderSeq("testowner")
  val moderator1TokenHeader = authHeaderSeq("testmoderator1")
  val moderator2TokenHeader = authHeaderSeq("testmoderator2")
  val user1TokenHeader = authHeaderSeq("testuser1")
  val user2TokenHeader = authHeaderSeq("testuser2")
  val user3TokenHeader = authHeaderSeq("testuser3")
  val user4TokenHeader = authHeaderSeq("testuser4")

  override protected def beforeAll() {
    systemStart()
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoDropDatabase()

    ("testowner" :: "testmoderator1" :: "testmoderator2" ::
      "testuser1" :: "testuser2" :: "testuser3" :: "testuser4" :: Nil)
      .foreach(mongoSetupUser(_))

    placeId // create a place for "testowner"
  }

  def createWestfiledCinema(): String = {
    val placeId = {
      val urlA = s"$placesBaseUrl/places"
      val jsonA = readFileAsString(s"$resourcesPath/CreatePlace-westfield.json")
      val place = (When postTo urlA entity jsonA withHeaders ownerTokenHeader expect() code SC_CREATED).withBody[vo.Place]

      val urlB = s"$placesBaseUrl/places/${place.place_id}"
      val jsonB = readFileAsString(s"$resourcesPath/PatchPlace-westfield.json")
      When patchTo urlB entity jsonB withHeaders ownerTokenHeader expect() code SC_OK

      place.place_id
    }
    log.debug("Created place")

    (1 to 4).foreach { screenN => // screens added in order 1,2,3,4 to simplify testing
      val screenSpaceId = {
        val url = s"$placesBaseUrl/places/$placeId/spaces"
        val json = readFileAsString(s"$resourcesPath/CreateSpace-screen.json").replace("{name}", s"Screen $screenN")
        val space = (When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED).withBody[vo.Space]
        space.space_id
      }
      log.debug(s"Created screen $screenN")

      (1 to 10*(2+screenN)).foreach { standardSeatN => // 30, 40, 50, 60
        val url = s"$placesBaseUrl/places/$placeId/spaces/$screenSpaceId"
        val json = readFileAsString(s"$resourcesPath/CreateSpace-seat.json").replace("{name}", s"Standard seat $standardSeatN")
        When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED
      }

      (1 to 4+screenN*2).foreach { vipSeatN => // 6, 8, 10, 12
        val url = s"$placesBaseUrl/places/$placeId/spaces/$screenSpaceId"
        val json = readFileAsString(s"$resourcesPath/CreateSpace-seat.json").replace("{name}", s"VIP seat $vipSeatN")
        When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED
      }

      (1 until screenN).foreach { wheelchairSpaceN => // 0, 1, 2, 3
        val url = s"$placesBaseUrl/places/$placeId/spaces/$screenSpaceId"
        val json = readFileAsString(s"$resourcesPath/CreateSpace-seat.json").replace("{name}", s"Wheelchair space $wheelchairSpaceN")
        When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED
      }

      log.debug(s"Created seats for screen $screenN")
    }

    placeId
  }

  def createMovieSlots(date: Int) {
    val placeUrl = s"$placesBaseUrl/places/$placeId?deep=false&deep_spaces=true"
    val place = (When getTo placeUrl withHeaders ownerTokenHeader expect() code SC_OK).withBody[vo.Place]

    val createSlot = (spaceId: String, title: String, timeFrom: Int, timeTo: Int) => {
      val url = s"$slotsBaseUrl/slots"
      val json = readFileAsString(s"$resourcesPath/CreateSlot-movie.json")
        .replace("{space_id}", spaceId)
        .replace("{place_id}", placeId)
        .replace("{title}", title)
        .replace("{date}", date+"")
        .replace("{time_from}", timeFrom+"")
        .replace("{time_to}", timeTo+"")
      When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED
    }

    // screen 1
    val screen1 = place.spaces.get(0)
    screen1.spaces.get.zipWithIndex.par.foreach { case (seat, seatN) =>

      val movies = // title, time from, time to
        ("Akira", 800, 1000) :: // 2h
        ("Anthropoid", 1000, 1200) :: // 2h
        ("Julieta", 1200, 1300) :: // 1h
        ("Ben-Hur", 1300, 1500) :: // 2h, with 1h gap
        ("Blair Witch", 1600, 1700) :: // 1h
        ("Don't Breathe", 1800, 1900) :: // 1h
        ("Ghostbusters", 1900, 2000) :: // 1h
        ("Jason Bourne", 2000, 2130) :: // 1h 30min
        Nil

      movies.foreach { case (title, from, to) =>
        createSlot(seat.space_id, title, from, to)
      }

      log.debug(s"Created slots for screen 1, seat ${seatN+1}, date $date")
    }

    // screen 2
    val screen2 = place.spaces.get(1)
    screen2.spaces.get.zipWithIndex.par.foreach { case (seat, seatN) =>

      val movies = // title, time from, time to
        ("Ice Age: Collision Course", 800, 900) :: // 1h, with 1h gap
        ("Pete's Dragon", 1000, 1100) :: // 1h
        ("Julieta", 1100, 1200) :: // 1h
        ("Kubo and the Two Strings", 1200, 1400) :: // 2h
        ("Lights Out", 1400, 1530) :: // 1h 30min, with 30min gap
        ("Mechanic: Resurrection", 1600, 1800) :: // 2h
        ("Morgan", 1800, 2000) :: // 2h
        ("Nerve", 2000, 2115) :: // 2h 15min
        Nil

      movies.foreach { case (title, from, to) =>
        createSlot(seat.space_id, title, from, to)
      }

      log.debug(s"Created slots for screen 2, seat ${seatN+1}, date $date")
    }

    // screen 3
    val screen3 = place.spaces.get(2)
    screen3.spaces.get.zipWithIndex.par.foreach { case (seat, seatN) =>

      val movies = // title, time from, time to
          ("Mechanic: Resurrection", 800, 1000) :: // 2h
          ("Ben-Hur", 1000, 1200) :: // 2h
          ("Sausage Party", 1200, 1400) :: // 2h
          ("Star Trek Beyond", 1400, 1500) :: // 1h
          ("Suicide Squade", 1500, 1700) :: // 2h
          ("The BFG", 1700, 1900) :: // 2h
          ("The Infiltrator", 1900, 2000) :: // 1h
          ("The Legend of Tarzan", 2000, 2145) :: // 1h 45min
          Nil

      movies.foreach { case (title, from, to) =>
        createSlot(seat.space_id, title, from, to)
      }

      log.debug(s"Created slots for screen 3, seat ${seatN+1}, date $date")
    }

    // screen 4
    val screen4 = place.spaces.get(3)
    screen4.spaces.get.zipWithIndex.par.foreach { case (seat, seatN) =>

      val movies = // title, time from, time to
          ("The Legend of Tarzan", 800, 945) :: // 1h 45min, with 15min gap
          ("The Shallows", 1000, 1200) :: // 2h
          ("War Dogs", 1200, 1300) :: // 1h, with 1h gap
          ("The Secret Life of Pets", 1400, 1500) :: // 1h
          ("Ice Age: Collision Course", 1500, 1600) :: // 1h, with 1h gap
          ("Finding Dory", 1700, 1800) :: // 1h
          ("Hunt For the  Wilderpeople", 1800, 1900) :: // 1h, with 1h gap
          ("Hell or High Water", 2000, 2130) :: // 1h 30min
          Nil

      movies.foreach { case (title, from, to) =>
        createSlot(seat.space_id, title, from, to)
      }

      log.debug(s"Created slots for screen 4, seat ${seatN+1}, date $date")
    }

  }

  def createDefaultScreenPrices_Screens123() {
    val placeUrl = s"$placesBaseUrl/places/$placeId?deep=false"
    val place = (When getTo placeUrl withHeaders ownerTokenHeader expect() code SC_OK).withBody[vo.Place]

    val createPrice = (spaceId: String, name: String, amount: Int) => {
      val url = s"$placesBaseUrl/places/$placeId/spaces/$spaceId/prices"
      val json = readFileAsString(s"$resourcesPath/CreateSpacePrice-default.json")
        .replace("{space_id}", spaceId)
        .replace("{place_id}", placeId)
        .replace("{name}", name)
        .replace("{amount}", amount+"")
      When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED
    }

    // screen 1,2,3
    place.spaces.get.take(3).par.foreach { screen =>
      createPrice(screen.space_id, "Adult ticket", 1200)
      createPrice(screen.space_id, "Child ticket", 600)
    }

    log.debug("Created default prices for screens 1, 2 and 3")
  }

  def createPeakOffpeakSeatSlotPrices_Screens12(date: Int) {
    val placeUrl = s"$placesBaseUrl/places/$placeId?deep=false"
    val place = (When getTo placeUrl withHeaders ownerTokenHeader expect() code SC_OK).withBody[vo.Place]

    val createPrice = (slotId: String, name: String, amount: Int) => {
      val url = s"$slotsBaseUrl/slots/$slotId/prices"
      val json = readFileAsString(s"$resourcesPath/CreateSlotPrice-default.json")
        .replace("{name}", name)
        .replace("{amount}", amount+"")
      When postTo url entity json withHeaders ownerTokenHeader expect() code SC_CREATED
    }

    // screen 1,2
    place.spaces.get.take(2).zipWithIndex.par.foreach { case (screen, screenN) =>
      val screenId = screen.space_id
      val slotsUrl = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=$screenId&from=$date&to=$date&deep=false"
      val slots = (When getTo slotsUrl withHeaders ownerTokenHeader expect() code SC_OK).withBody[Seq[svo.Slot]]

      slots.foreach { slot =>
        val slotId = slot.slot_id
        val peak = slot.time_from.get >= 1700
        if (peak) {
          createPrice(slotId, "Adult peak", 1400)
          createPrice(slotId, "Child peak", 800)
        } else {
          createPrice(slotId, "Adult offpeak", 1000)
          createPrice(slotId, "Child offpeak", 400)
        }
      }

      log.debug(s"Created (off)peak prices for seat slots for screen ${screenN+1}, date $date")
    }

  }

}
