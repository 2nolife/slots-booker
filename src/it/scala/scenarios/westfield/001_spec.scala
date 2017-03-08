package com.coldcore.slotsbooker
package scenarios.westfield

import ms.places.vo
import ms.slots.{vo => svo}
import org.apache.http.HttpStatus._
import org.scalatest.Ignore

@Ignore
class Westfield_001_Spec extends BaseSpec {

  val days = 20160502 :: 20160503 :: 20160504 :: 20160505 :: 20160506 :: 20160507 :: 20160508 :: Nil // 02 .. 08 May 2016

  "As a place owner I" should "ensure that my cinema is properly set" in {
    val url = s"$placesBaseUrl/places/$placeId"
    val place = (When getTo url withHeaders ownerTokenHeader expect() code SC_OK).withBody[vo.Place]

    place.spaces should not be None
    place.spaces.get.size shouldBe 4

    val seatsPerScreen = 30+6+0 :: 40+8+1 :: 50+10+2 :: 60+12+3 :: Nil
    place.spaces.get.sortBy(_.name).zip(seatsPerScreen).foreach { case (screen, seats) =>
      screen.spaces should not be None
      screen.spaces.get.size shouldBe seats
    }
  }

  "As a place owner I" should "setup movies for 7 consequent days in my cinema" in {
    days.foreach(createMovieSlots)

    val url = s"$placesBaseUrl/places/$placeId"
    val place = (When getTo url withHeaders ownerTokenHeader expect() code SC_OK).withBody[vo.Place]

    val screen1 = place.spaces.get.head
    screen1.name.get shouldBe "Screen 1"

    val urlA = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=${screen1.space_id}&from=20160503&to=20160503"
    val slotsA = (When getTo urlA withHeaders ownerTokenHeader expect() code SC_OK).withBody[Seq[svo.Slot]]

    slotsA.head should matchPattern { case svo.Slot(_, _, _, Some("Movie: Akira"), Some(20160503), Some(20160503), Some(800), Some(1000), _, _, _, _) => }
    slotsA.last should matchPattern { case svo.Slot(_, _, _, Some("Movie: Jason Bourne"), Some(20160503), Some(20160503), Some(2000), Some(2130), _, _, _, _) => }

    createDefaultScreenPrices_Screens123()

    val urlB = s"$placesBaseUrl/places/$placeId"
    val placeB = (When getTo urlB withHeaders ownerTokenHeader expect() code SC_OK).withBody[vo.Place]
    placeB.spaces.get.head.prices.get.size shouldBe 2
    placeB.spaces.get.head.prices.get.flatMap(_.name) should contain only ("Adult ticket", "Child ticket")

    days.foreach(createPeakOffpeakSeatSlotPrices_Screens12)

    val urlC = s"$slotsBaseUrl/slots/search?place_id=$placeId&space_id=${screen1.space_id}&from=20160503&to=20160503"
    val slotsC = (When getTo urlC withHeaders ownerTokenHeader expect() code SC_OK).withBody[Seq[svo.Slot]]

    slotsC.head.prices.get.size shouldBe 2
    slotsC.head.prices.get.flatMap(_.name) should contain only ("Adult peak", "Child peak", "Adult offpeak", "Child offpeak")
  }

}
