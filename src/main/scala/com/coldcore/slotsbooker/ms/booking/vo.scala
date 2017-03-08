package com.coldcore.slotsbooker
package ms.booking.vo

import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import ms.vo.Attributes

case class QuotedPrice(place_id: Option[String], space_id: Option[String], slot_id: Option[String], price_id: Option[String],
                       name: Option[String], amount: Option[Int], currency: Option[String])
object QuotedPrice extends DefaultJsonProtocol { implicit val format = jsonFormat7(apply) }

case class Quote(prices: Option[Seq[QuotedPrice]])
object Quote extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }

case class Reference(ref: Option[String])
object Reference extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }

case class GetQuote(slot_ids: Seq[String])
object GetQuote extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }

case class BookSlots(slot_ids: Seq[String], as_profile_id: Option[String], attributes: Option[Attributes])
object BookSlots extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class CancelSlots(slot_ids: Seq[String], as_profile_id: Option[String], attributes: Option[Attributes])
object CancelSlots extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class UpdateSlots(slot_ids: Seq[String], as_profile_id: Option[String], attributes: Option[Attributes])
object UpdateSlots extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

/** External JSON objects from other micro services. */
package ext {

  case class Price(price_id: String, place_id: String, space_id: String, slot_id: Option[String], name: Option[String],
                   amount: Option[Int], currency: Option[String], roles: Option[Seq[String]])
  object Price extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

  case class Booking(booking_id: String, place_id: String, space_id: String, slot_id: String, profile_id: Option[String],
                     name: Option[String], status: Option[Int], attributes: Option[Attributes])
  object Booking extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

  case class Space(space_id: String, place_id: String, parent_space_id: Option[String],
                   name: Option[String], spaces: Option[Seq[Space]], prices: Option[Seq[Price]])
  object Space extends DefaultJsonProtocol {
    implicit val format: RootJsonFormat[Space] =
      rootFormat(lazyFormat(jsonFormat(Space.apply, "space_id", "place_id", "parent_space_id", "name", "spaces", "prices")))
  }

  case class Booked(booked_id: String, place_id: String, profile_id: Option[String],
                    status: Option[Int], deal: Option[Boolean],
                    slot_ids: Option[Seq[String]], booking_ids: Option[Seq[String]])
  object Booked extends DefaultJsonProtocol { implicit val format = jsonFormat7(apply) }

  case class Slot(slot_id: String, place_id: String, space_id: String,
                  name: Option[String],
                  date_from: Option[Int], date_to: Option[Int], time_from: Option[Int], time_to: Option[Int],
                  bookings: Option[Seq[Booking]], prices: Option[Seq[Price]],
                  book_status: Option[Int], booked: Option[Booked])
  object Slot extends DefaultJsonProtocol {
    implicit val format = jsonFormat12(apply)

    def apply(slotId: String, placeId: String, spaceId: String): Slot =
      Slot(slotId, placeId, spaceId, None, None, None, None, None, None, None, None, None)
  }

  case class Place(place_id: String, profile_id: String, moderators: Option[Seq[String]])
  object Place extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

  case class CreateSlotBooking(as_profile_id: String, name: String)
  object CreateSlotBooking extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class UpdateSlotBooking(status: Option[Int], attributes: Option[Attributes], as_profile_id: Option[String])
  object UpdateSlotBooking extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

  case class CreateSlotBooked(as_profile_id: String, slot_ids: Seq[String])
  object CreateSlotBooked extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class UpdateSlotBooked(as_profile_id: String, status: Option[Int], deal: Option[Boolean], booking_ids: Option[Seq[String]])
  object UpdateSlotBooked extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

  case class UpdateSlotHold(booked_id: String, status: Int)
  object UpdateSlotHold extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

}

object Implicits {

  implicit class PlaceExt(obj: ext.Place) {

    def isModerator(profileId: String): Boolean =
      obj.moderators.getOrElse(Nil).exists(profileId==) || obj.profile_id == profileId

  }

}