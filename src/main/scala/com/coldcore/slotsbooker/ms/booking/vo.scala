package com.coldcore.slotsbooker
package ms.booking.vo

import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import ms.vo.Attributes
import ms.{BoundsUtil => bu}

case class SlotPrice(slot_id: String, price_id: Option[String], name: Option[String], amount: Option[Int], currency: Option[String])
object SlotPrice extends DefaultJsonProtocol {
  implicit val format = jsonFormat5(apply)

  def apply(slotId: String): SlotPrice =
    SlotPrice(slotId, None, None, None, None)
}

case class Quote(quote_id: String, place_id: String, profile_id: Option[String],
                 amount: Option[Int], currency: Option[String],
                 status: Option[Int], prices: Option[Seq[SlotPrice]], deal: Option[Boolean])
object Quote extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

case class Refund(refund_id: String, place_id: String, profile_id: Option[String],
                  amount: Option[Int], currency: Option[String],
                  status: Option[Int], prices: Option[Seq[SlotPrice]],
                  quotes: Option[Seq[Quote]])
object Refund extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

case class Reference(reference_id: String, place_id: String, ref: Option[String], profile_id: Option[String],
                     booked_ids: Option[Seq[String]], quote: Option[Quote], refund: Option[Refund])
object Reference extends DefaultJsonProtocol { implicit val format = jsonFormat7(apply) }

case class SelectedPrice(slot_id: String, price_id: Option[String])
object SelectedPrice extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class QuoteSlots(selected: Seq[SelectedPrice], as_profile_id: Option[String])
object QuoteSlots extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class RefundSlots(slot_ids: Seq[String], as_profile_id: Option[String])
object RefundSlots extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class BookSlots(slot_ids: Option[Seq[String]], quote_id: Option[String], as_profile_id: Option[String], attributes: Option[Attributes])
object BookSlots extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

case class CancelSlots(slot_ids: Option[Seq[String]], refund_id: Option[String], as_profile_id: Option[String], attributes: Option[Attributes])
object CancelSlots extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

case class UpdateSlots(slot_ids: Seq[String], as_profile_id: Option[String], attributes: Option[Attributes])
object UpdateSlots extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class ReferencePaid(ref: String, profile_id: String)
object ReferencePaid extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

/** External JSON objects from other micro services. */
package ext {

  case class Price(price_id: String, place_id: String, space_id: String, slot_id: Option[String], name: Option[String],
                   amount: Option[Int], currency: Option[String], member_level: Option[Int])
  object Price extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

  case class Bound(date: Option[Int], time: Option[Int], before: Option[Int])
  object Bound extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

  case class Bounds(open: Option[Bound], close: Option[Bound])
  object Bounds extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

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
                    status: Option[Int],
                    slot_ids: Option[Seq[String]], booking_ids: Option[Seq[String]])
  object Booked extends DefaultJsonProtocol { implicit val format = jsonFormat6(apply) }

  case class Slot(slot_id: String, place_id: String, space_id: String,
                  name: Option[String],
                  date_from: Option[Int], date_to: Option[Int], time_from: Option[Int], time_to: Option[Int],
                  bookings: Option[Seq[Booking]], prices: Option[Seq[Price]],
                  book_status: Option[Int], booked: Option[Booked],
                  book_bounds: Option[Bounds], cancel_bounds: Option[Bounds])
  object Slot extends DefaultJsonProtocol {
    implicit val format = jsonFormat14(apply)

    def apply(slotId: String, placeId: String, spaceId: String): Slot =
      Slot(slotId, placeId, spaceId, None, None, None, None, None, None, None, None, None, None, None)
  }

  case class DateTime(timezone: Option[String], offset_minutes: Option[Int])
  object DateTime extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class Place(place_id: String, profile_id: String, moderators: Option[Seq[String]], datetime: Option[DateTime])
  object Place extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

  case class Member(profile_id: String, place_id: String, level: Option[Int])
  object Member extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

  case class CreateSlotBooking(as_profile_id: String, name: String)
  object CreateSlotBooking extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class UpdateSlotBooking(status: Option[Int], attributes: Option[Attributes], as_profile_id: Option[String])
  object UpdateSlotBooking extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

  case class CreateSlotBooked(as_profile_id: String, slot_ids: Seq[String])
  object CreateSlotBooked extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class UpdateSlotBooked(as_profile_id: String, status: Option[Int], booking_ids: Option[Seq[String]], paid: Option[Boolean])
  object UpdateSlotBooked extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

  case class UpdateSlotHold(booked_id: String, status: Int)
  object UpdateSlotHold extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

}

object Implicits {

  implicit class PlaceExt(obj: ext.Place) {

    def isModerator(profileId: String): Boolean =
      obj.moderators.getOrElse(Nil).exists(profileId==) || obj.profile_id == profileId

  }

  implicit class BoundExt(obj: ext.Bound) {

    def buBound: bu.Bound = {
      import obj._
      bu.Bound(date, time, before)
    }
  }

  implicit class SlotExt(obj: ext.Slot) {

    def buDates: bu.Dates = {
      import obj._
      bu.Dates(date_from.get, date_to.get, time_from.get, time_to.get)
    }
  }

}