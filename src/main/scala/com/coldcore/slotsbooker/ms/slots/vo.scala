package com.coldcore.slotsbooker
package ms.slots.vo

import ms.vo.Attributes
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import ms.{BoundsUtil => bu}

case class Price(price_id: String, place_id: String, space_id: String, slot_id: String, name: Option[String],
                 amount: Option[Int], currency: Option[String], attributes: Option[Attributes], member_level: Option[Int])
object Price extends DefaultJsonProtocol {
  implicit val format = jsonFormat9(apply)

  def apply(p: ext.Price): Price = {
    import p._
    Price(
      price_id, place_id, space_id, slot_id = "?", name,
      amount, currency, attributes, member_level)
  }
}

case class Bound(date: Option[Int], time: Option[Int], before: Option[Int])
object Bound extends DefaultJsonProtocol {
  implicit val format = jsonFormat3(apply)

  def apply(p: ext.Bound): Bound = {
    import p._
    Bound(date, time, before)
  }
}

case class Bounds(date_from: Option[Int], date_to: Option[Int], time_from: Option[Int], time_to: Option[Int], // calculated
                  open: Option[Bound], close: Option[Bound])
object Bounds extends DefaultJsonProtocol {
  implicit val format = jsonFormat6(apply)

  def apply(p: ext.Bounds): Bounds = {
    import p._
    Bounds(
      None, None, None, None,
      open.map(Bound(_)), close.map(Bound(_)))
  }

  def apply(open: Option[Bound], close: Option[Bound]): Bounds =
    Bounds(
      None, None, None, None,
      open, close)
}

case class Booking(booking_id: String, place_id: String, space_id: String, slot_id: String, profile_id: Option[String],
                   name: Option[String], status: Option[Int], attributes: Option[Attributes])
object Booking extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

case class CreateBooking(as_profile_id: String, name: String)
object CreateBooking extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class UpdateBooking(name: Option[String], status: Option[Int], attributes: Option[Attributes], as_profile_id: Option[String])
object UpdateBooking extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

case class CreateBooked(as_profile_id: String, slot_ids: Seq[String])
object CreateBooked extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class UpdateBooked(as_profile_id: String, status: Option[Int], booking_ids: Option[Seq[String]], paid: Option[Boolean])
object UpdateBooked extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

case class CreatePrice(name: String, amount: Int, currency: String,
                       attributes: Option[Attributes], member_level: Option[Int])
object CreatePrice extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

case class UpdatePrice(name: Option[String], amount: Option[Int], currency: Option[String],
                       attributes: Option[Attributes], member_level: Option[Int])
object UpdatePrice extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

case class UpdateHold(booked_id: String, status: Int)
object UpdateHold extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class Booked(booked_id: String, place_id: String, profile_id: Option[String],
                  status: Option[Int],
                  slot_ids: Option[Seq[String]], booking_ids: Option[Seq[String]],
                  paid: Option[Boolean])
object Booked extends DefaultJsonProtocol { implicit val format = jsonFormat7(apply) }

case class Slot(slot_id: String, place_id: String, space_id: String,
                name: Option[String],
                date_from: Option[Int], date_to: Option[Int], time_from: Option[Int], time_to: Option[Int],
                bookings: Option[Seq[Booking]], prices: Option[Seq[Price]],
                book_status: Option[Int], booked: Option[Booked], attributes: Option[Attributes],
                book_bounds: Option[Bounds], cancel_bounds: Option[Bounds])
object Slot extends DefaultJsonProtocol { implicit val format = jsonFormat15(apply) }

case class CreateSlot(place_id: String, space_id: String, name: String,
                      date_from: Int, date_to: Int, time_from: Int, time_to: Int,
                      attributes: Option[Attributes])
object CreateSlot extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

case class UpdateSlot(name: Option[String],
                      date_from: Option[Int], date_to: Option[Int], time_from: Option[Int], time_to: Option[Int],
                      attributes: Option[Attributes],
                      book_bounds: Option[Bounds], cancel_bounds: Option[Bounds])
object UpdateSlot extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

/** External JSON objects from other micro services. */
package ext {

  case class Bound(date: Option[Int], time: Option[Int], before: Option[Int])
  object Bound extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

  case class Bounds(open: Option[Bound], close: Option[Bound])
  object Bounds extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class Price(price_id: String, place_id: String, space_id: String, name: Option[String],
                   amount: Option[Int], currency: Option[String], attributes: Option[Attributes], member_level: Option[Int])
  object Price extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

  case class Space(space_id: String, place_id: String, parent_space_id: Option[String],
                      name: Option[String], spaces: Option[Seq[Space]], prices: Option[Seq[Price]])
  object Space extends DefaultJsonProtocol {
    implicit val format: RootJsonFormat[Space] =
      rootFormat(lazyFormat(jsonFormat(Space.apply, "space_id", "place_id", "parent_space_id", "name", "spaces", "prices")))
  }

  case class Place(place_id: String, profile_id: String,
                      name: Option[String], spaces: Option[Seq[Space]], moderators: Option[Seq[String]])
  object Place extends DefaultJsonProtocol {
    implicit val placeFormat: RootJsonFormat[Place] =
      jsonFormat(Place.apply, "place_id", "profile_id", "name", "spaces", "moderators")
  }

}

object Implicits {

  implicit class PlaceExt(obj: ext.Place) {

    def isModerator(profileId: String): Boolean =
      obj.moderators.getOrElse(Nil).exists(profileId==) || obj.profile_id == profileId

    def flatSpaces: Seq[ext.Space] =
      obj.spaces.getOrElse(Nil).flatMap(_.flatSpaces)

    def flatPrices: Seq[ext.Price] =
      flatSpaces.flatMap(_.prices).flatten

    def hasSpaceId(spaceId: String): Boolean =
      flatSpaces.exists(_.space_id == spaceId)

    def hasPriceId(priceId: String): Boolean =
      flatPrices.exists(_.price_id == priceId)
  }

  implicit class SpaceExt(obj: ext.Space) {

    private def flatSpaces(space: ext.Space): Seq[ext.Space] =
      space +: space.spaces.getOrElse(Nil).flatMap(flatSpaces)

    def flatSpaces: Seq[ext.Space] =
      flatSpaces(obj)

    def flatPrices: Seq[ext.Price] =
      flatSpaces.flatMap(_.prices).flatten

    def hasSpaceId(spaceId: String): Boolean =
      flatSpaces.exists(_.space_id == spaceId)

    def hasPriceId_flat(priceId: String): Boolean =
      flatPrices.exists(_.price_id == priceId)
  }

  implicit class SlotExt(obj: Slot) {

    def hasBookingId(bookingId: String): Boolean =
      obj.bookings.getOrElse(Nil).exists(_.booking_id == bookingId)

    def hasPriceId(priceId: String): Boolean =
      obj.prices.getOrElse(Nil).exists(_.price_id == priceId)
  }

  implicit class BoundExt(obj: Bound) {

    def buBound: bu.Bound = {
      import obj._
      bu.Bound(date, time, before)
    }
  }

}
