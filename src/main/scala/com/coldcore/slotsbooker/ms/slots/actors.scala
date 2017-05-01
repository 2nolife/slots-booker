package com.coldcore.slotsbooker
package ms.slots.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.actors.MsgInterceptor
import ms.vo.ProfileRemote
import ms.http.{ApiCode, RestClient, SystemRestCalls}
import ms.slots.db.SlotsDb
import ms.slots.db.SlotsDb._
import ms.slots.vo
import ms.slots.vo.Implicits._
import ms.vo.Implicits._
import ms.attributes.Types._
import ms.attributes.{OncePermission, WritePermission, Permission => ap, Util => au}
import org.apache.http.HttpStatus._

trait SlotsCommands {
  case class CreateSlotIN(obj: vo.CreateSlot, profile: ProfileRemote) extends RequestInfo
  case class UpdateSlotIN(slotId: String, obj: vo.UpdateSlot, profile: ProfileRemote) extends RequestInfo
  case class GetSlotIN(slotId: String, profile: ProfileRemote,
                       deepBookings: Boolean, deepPrices: Boolean, deepBooked: Boolean) extends RequestInfo
  case class DeleteSlotIN(slotId: String, profile: ProfileRemote) extends RequestInfo
  case class UpdateHoldIN(slotId: String, obj: vo.UpdateHold, profile: ProfileRemote) extends RequestInfo
}

trait BookedCommands {
  case class CreateBookedIN(obj: vo.CreateBooked, profile: ProfileRemote) extends RequestInfo
  case class UpdateBookedIN(bookedId: String, obj: vo.UpdateBooked, profile: ProfileRemote) extends RequestInfo
}

trait BookingsCommands {
  case class CreateBookingIN(slotId: String, obj: vo.CreateBooking, profile: ProfileRemote) extends RequestInfo
  case class UpdateBookingIN(slotId: String, bookingId: String, obj: vo.UpdateBooking, profile: ProfileRemote) extends RequestInfo
  case class GetBookingIN(slotId: String, bookingId: String, profile: ProfileRemote) extends RequestInfo
  case class GetBookingsIN(slotId: String, active: Option[String], profile: ProfileRemote) extends RequestInfo
}

trait PricesCommands {
  case class CreatePriceIN(slotId: String, obj: vo.CreatePrice, profile: ProfileRemote) extends RequestInfo
  case class UpdatePriceIN(slotId: String, priceId: String, obj: vo.UpdatePrice, profile: ProfileRemote) extends RequestInfo
  case class GetPriceIN(slotId: String, priceId: String, profile: ProfileRemote) extends RequestInfo
  case class GetPricesIN(slotId: String, profile: ProfileRemote) extends RequestInfo
  case class DeletePriceIN(slotId: String, priceId: String, profile: ProfileRemote) extends RequestInfo
}

trait SearchCommands {
  case class SearchSlotsIN(placeId: String, spaceId: String, profile: ProfileRemote,
                           dateFrom: Int, dateTo: Int, timeFrom: Int, timeTo: Int,
                           searchInnerSpaces: Boolean, bookedBy: Option[String], groupBy: Option[String],
                           deepBookings: Boolean, deepPrices: Boolean)
}

trait PlacesMsRestCalls extends SystemRestCalls {
  self: {
    val placesBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def placeFromMsPlaces(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    restGet[vo.ext.Place](s"$placesBaseUrl/places/$placeId?deep=false")

  def spaceFromMsPlaces(placeId: String, spaceId: String, withInnerSpaces: Boolean = false): (ApiCode, Option[vo.ext.Space]) =
    restGet[vo.ext.Space](s"$placesBaseUrl/places/$placeId/spaces/$spaceId?deep=false&deep_spaces=$withInnerSpaces")
}

object SlotsActor extends SlotsCommands with PricesCommands with BookedCommands with BookingsCommands with SearchCommands {
  def props(slotsDb: SlotsDb, placesBaseUrl: String, systemToken: String, restClient: RestClient, voAttributes: VoAttributes): Props =
    Props(new SlotsActor(slotsDb, placesBaseUrl, systemToken, restClient, voAttributes))
}

class SlotsActor(val slotsDb: SlotsDb, val placesBaseUrl: String, val systemToken: String,
                 val restClient: RestClient, val voAttributes: VoAttributes) extends Actor with ActorLogging with MsgInterceptor with VoExpose
  with PlacesMsRestCalls with AmendSlot with GetSlot with AmendPrice with GetPrice with AmendBooking with GetBooking
  with AmendBooked with SearchSlots {

  def receive =
    amendSlotReceive orElse getSlotReceive orElse
    amendPriceReceive orElse getPriceReceive orElse
    amendBookingReceive orElse getBookingReceive orElse
    amendBookedReceive orElse
    searchSlotsReceive

  val bookingOwner = (p: vo.Booking, profile: ProfileRemote) => p.profile_id.contains(profile.profile_id)
  val bookedOwner = (p: vo.Booked, profile: ProfileRemote) => p.profile_id.contains(profile.profile_id)
  val placeModerator = (place: vo.ext.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)

  def permitAttributes(obj: vo.UpdateSlot, place: vo.ext.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("slot"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

  def permitAttributes(obj: vo.UpdatePrice, place: vo.ext.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("price"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

  def permitAttributes(obj: vo.UpdateBooking, booking: vo.Booking, place: vo.ext.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("booking"), ap.defaultWrite(profile, attr =>
      placeModerator(place, profile) ||
      bookingOwner(booking, profile) && attr.permissions.contains(WritePermission) ||
      bookingOwner(booking, profile) && attr.permissions.contains(OncePermission) && !au.definedFields(booking, voAttributes("booking")).contains(attr.name)
    ))._1

}

trait AmendSlot {
  self: SlotsActor =>
  import SlotsActor._

  val amendSlotReceive: Actor.Receive = {

    case CreateSlotIN(obj, profile) =>
      import obj._
      lazy val (codeA, myPlace) = placeFromMsPlaces(place_id)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(place_id, space_id)
      lazy val spaceNotFound = mySpace.isEmpty
      lazy val canCreate = placeModerator(myPlace.get, profile) || profile.isSuper

      def create(): Option[vo.Slot] = Some(slotsDb.createSlot(place_id, space_id, obj))

      val (code, slot) =
        if (spaceNotFound) (codeB, None)
        else if (canCreate) (ApiCode.CREATED, create())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, expose(slot, myPlace, profile))

    case DeleteSlotIN(slotId, profile) => //todo bookings may exist
      lazy val mySlot = slotsDb.slotById(slotId)
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(mySlot.get.place_id, mySlot.get.space_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val spaceNotFound = mySpace.isEmpty
      lazy val canDelete = placeModerator(myPlace.get, profile) || profile.isSuper

      def delete() = slotsDb.deleteSlot(slotId)

      val code: ApiCode =
        if (slotNotFound) SC_NOT_FOUND
        else if (spaceNotFound) codeB
        else if (canDelete) {
          delete()
          SC_OK
        } else SC_FORBIDDEN

      reply ! CodeOUT(code)

    case UpdateSlotIN(slotId, obj, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId)
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(mySlot.get.place_id, mySlot.get.space_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val spaceNotFound = mySpace.isEmpty
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canUpdate = placeModerator(myPlace.get, profile) || profile.isSuper

      def update(): Option[vo.Slot] = slotsDb.updateSlot(slotId, obj)

      val (code, slot) =
        if (slotNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (spaceNotFound) (codeB, None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, expose(slot, mySlot.flatMap(_ => myPlace), profile))

    case UpdateHoldIN(slotId, obj, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val canUpdate = profile.isSuper

      def acquire(): Boolean = slotsDb.acquireHold(slotId, obj.booked_id)
      def confirm(): Boolean = slotsDb.confirmHold(slotId, obj.booked_id)
      def release(): Boolean = slotsDb.releaseHold(slotId, obj.booked_id)

      val code: ApiCode =
        if (slotNotFound) SC_NOT_FOUND
        else if (canUpdate) {
          if (obj.status == 2 && acquire()) SC_OK
          else if (obj.status == 1 && confirm()) SC_OK
          else if (obj.status == 3 && release()) SC_OK
          else SC_CONFLICT
        } else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

trait GetSlot {
  self: SlotsActor =>
  import SlotsActor._

  val getSlotReceive: Actor.Receive = {

    case GetSlotIN(slotId, profile, deepBookings, deepPrices, deepBooked) =>
      val fields = customSlotFields(deepBookings, deepPrices, deepBooked)

      lazy val mySlot = slotsDb.slotById(slotId, fields)
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(mySlot.get.place_id, mySlot.get.space_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val spaceNotFound = mySpace.isEmpty

      val (code, slot) =
        if (slotNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (spaceNotFound) (codeB, None)
        else (ApiCode.OK, mySlot)

      reply ! CodeEntityOUT(code, expose(slot, mySlot.flatMap(_ => myPlace), profile))

  }

}

trait AmendPrice {
  self: SlotsActor =>
  import SlotsActor._

  val amendPriceReceive: Actor.Receive = {

    case CreatePriceIN(slotId, obj, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId)
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(mySlot.get.place_id, mySlot.get.space_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val spaceNotFound = mySpace.isEmpty
      lazy val canCreate = placeModerator(myPlace.get, profile) || profile.isSuper

      def create(): Option[vo.Price] = Some(slotsDb.createPrice(mySlot.get.slot_id, obj))

      val (code, price) =
        if (slotNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (spaceNotFound) (codeB, None)
        else if (canCreate) (ApiCode.CREATED, create())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposePrice(price, mySlot.flatMap(_ => myPlace), profile))

    case UpdatePriceIN(slotId, priceId, obj, profile) =>
      lazy val myPrice = slotsDb.priceById(priceId)
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = false, deep_prices = true, deep_booked = false))
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val priceNotFound = myPrice.isEmpty || mySlot.isEmpty || !mySlot.get.hasPriceId(priceId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canUpdate = placeModerator(myPlace.get, profile) || profile.isSuper

      def update(): Option[vo.Price] = slotsDb.updatePrice(priceId, obj)

      val (code, price) =
        if (priceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (placeNotFound) (codeA, None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposePrice(price, mySlot.flatMap(_ => myPlace), profile))

    case DeletePriceIN(slotId, priceId, profile) =>
      lazy val myPrice = slotsDb.priceById(priceId)
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = false, deep_prices = true, deep_booked = false))
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val slotNotFound = myPrice.isEmpty || mySlot.isEmpty || !mySlot.get.hasPriceId(priceId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canDelete = placeModerator(myPlace.get, profile) || profile.isSuper

      def delete() = slotsDb.deletePrice(priceId)

      val code: ApiCode =
        if (slotNotFound) SC_NOT_FOUND
        else if (placeNotFound) codeA
        else if (canDelete) {
          delete()
          SC_OK
        } else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

trait GetPrice {
  self: SlotsActor =>
  import SlotsActor._

  val getPriceReceive: Actor.Receive = {

    case GetPriceIN(slotId, priceId, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = false, deep_prices = true, deep_booked = false))
      lazy val myPrice = slotsDb.priceById(priceId)
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val priceNotFound = myPrice.isEmpty || mySlot.isEmpty || !mySlot.get.hasPriceId(priceId)
      lazy val placeNotFound = myPlace.isEmpty

      val (code, price) =
        if (priceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (placeNotFound) (codeA, None)
        else (ApiCode.OK, myPrice)

      reply ! CodeEntityOUT(code, exposePrice(price, mySlot.flatMap(_ => myPlace), profile))

    case GetPricesIN(slotId, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = false, deep_prices = true, deep_booked = false))
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty

      def read: Option[Seq[vo.Price]] = Some(mySlot.get.prices.getOrElse(Nil))

      val (code, prices) =
        if (slotNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (placeNotFound) (codeA, None)
        else (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposePrices(prices, mySlot.flatMap(_ => myPlace), profile))

  }

}

trait AmendBooking {
  self: SlotsActor =>
  import SlotsActor._

  val amendBookingReceive: Actor.Receive = {

    case CreateBookingIN(slotId, obj, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId)
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(mySlot.get.place_id, mySlot.get.space_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val spaceNotFound = mySpace.isEmpty

      @throws[BookingConflictException]
      def create(): Option[vo.Booking] = Some(slotsDb.createBooking(mySlot.get.slot_id, profile.profile_id, obj))

      val (code, booking) =
        if (slotNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (spaceNotFound) (codeB, None)
        else
          try {
            (ApiCode.CREATED, create())
          } catch {
            case _: BookingConflictException => (ApiCode(SC_CONFLICT), None)
          }

      reply ! CodeEntityOUT(code, exposeBooking(booking, mySlot.flatMap(_ => myPlace), profile))

    case UpdateBookingIN(slotId, bookingId, obj, profile) =>
      val forbiddenUpdateFields = obj.status.isDefined
      val forbiddenNonModeratorUpdateFields = obj.name.isDefined

      lazy val myBooking = slotsDb.bookingById(bookingId)
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = true, deep_prices = false, deep_booked = false))
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val bookingNotFound = myBooking.isEmpty || mySlot.isEmpty || !mySlot.get.hasBookingId(bookingId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val forbidFields = forbiddenUpdateFields && !profile.isSuper ||
                              forbiddenNonModeratorUpdateFields && !(placeModerator(myPlace.get, profile) || profile.isSuper)
      lazy val forbidAttributes = !permitAttributes(obj, myBooking.get, myPlace.get, profile)
      lazy val canUpdate = profile.isSuper || bookingOwner(myBooking.get, profile) || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Booking] = slotsDb.updateBooking(bookingId, obj)
                             
      val (code, booking) =
        if (bookingNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (placeNotFound) (codeA, None)
        else if (forbidFields) (ApiCode(SC_FORBIDDEN), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeBooking(booking, mySlot.flatMap(_ => myPlace), profile))

  }

}

trait GetBooking {
  self: SlotsActor =>
  import SlotsActor._

  val getBookingReceive: Actor.Receive = {

    case GetBookingIN(slotId, bookingId, profile) =>
      lazy val myBooking = slotsDb.bookingById(bookingId)
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = true, deep_prices = false, deep_booked = false))
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val bookingNotFound = myBooking.isEmpty || mySlot.isEmpty || !mySlot.get.hasBookingId(bookingId)
      lazy val placeNotFound = myPlace.isEmpty

      val (code, booking) =
        if (bookingNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (placeNotFound) (codeA, None)
        else (ApiCode.OK, myBooking)

      reply ! CodeEntityOUT(code, exposeBooking(booking, mySlot.flatMap(_ => myPlace), profile))

    case GetBookingsIN(slotId, active, profile) =>
      lazy val mySlot = slotsDb.slotById(slotId, customSlotFields(deep_bookings = true, deep_prices = false, deep_booked = false))
      lazy val (codeA, myPlace) = placeFromMsPlaces(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty

      def read: Option[Seq[vo.Booking]] = Some(mySlot.get.bookings.getOrElse(Nil).filter(_.status.get == 1 || active.isEmpty))

      val (code, bookings) =
        if (slotNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (placeNotFound) (codeA, None)
        else (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposeBookings(bookings, mySlot.flatMap(_ => myPlace), profile))

  }

}

trait AmendBooked {
  self: SlotsActor =>
  import SlotsActor._

  val amendBookedReceive: Actor.Receive = {

    case CreateBookedIN(obj, profile) =>
      val invalidEntity = obj.slot_ids.isEmpty

      def create(): Option[vo.Booked] = Some(slotsDb.createBooked(profile.profile_id, obj))

      val (code, booked) =
        if (invalidEntity) (ApiCode(SC_BAD_REQUEST), None)
        else (ApiCode.CREATED, create())

      reply ! CodeEntityOUT(code, booked)

    case UpdateBookedIN(bookedId, obj, profile) =>
      lazy val myBooked = slotsDb.bookedById(bookedId)
      lazy val bookedNotFound = myBooked.isEmpty
      lazy val canUpdate = bookedOwner(myBooked.get, profile)

      def update(): Option[vo.Booked] = slotsDb.updateBooked(bookedId, obj)

      val (code, booked) =
        if (bookedNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, booked)

  }

}

trait SearchSlots {
  self: SlotsActor =>
  import SlotsActor._

  val searchSlotsReceive: Actor.Receive = {

    case SearchSlotsIN(placeId, spaceId, profile,
                       dateFrom, dateTo, timeFrom, timeTo,
                       searchInnerSpaces, bookedBy, groupBy,
                       deepBookings, deepPrices) => //todo check dates range or enforce limit, otherwise could return a lot of slots
      val fields = customSlotFields(deepBookings, deepPrices, deep_booked = false)

      lazy val (codeA, myPlace) = placeFromMsPlaces(placeId)
      lazy val (codeB, mySpace) = spaceFromMsPlaces(placeId, spaceId, withInnerSpaces = searchInnerSpaces)
      lazy val spaceNotFound = mySpace.isEmpty
      lazy val bookedByProfileId =
        bookedBy.map { byProfileId =>
           val special = profile.isAdmin || placeModerator(myPlace.get, profile)
           if (special && byProfileId.nonEmpty) byProfileId else profile.profile_id
        }

      def read: Option[Seq[vo.Slot]] = {
        val spaceIds = spaceId +: (if (searchInnerSpaces) mySpace.get.flatSpaces.map(_.space_id) else Nil)
        val criteria = SearchCriteria(placeId, spaceIds,
                                      dateFrom, dateTo, timeFrom, timeTo,
                                      bookedByProfileId, groupBy)
        Some(slotsDb.searchSlots(criteria, fields))
      }

      val (code, slots) =
        if (spaceNotFound) (codeB, None)
        else (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposeSeq(slots, myPlace, profile))

  }

}

trait VoExpose {
  self: {
    val voAttributes: VoAttributes
  } =>

  private val bookingOwner = (p: vo.Booking, profile: ProfileRemote) => p.profile_id.contains(profile.profile_id)
  private val placeModerator = (place: vo.ext.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)

  def expose(obj: vo.Slot, place: vo.ext.Place, profile: ProfileRemote): vo.Slot =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("slot"), ap.defaultRead(profile, _ => placeModerator(place, profile))))
      .map { p => exposePrices(p.prices, Some(place), profile); p }
      .map { p => exposeBookings(p.bookings, Some(place), profile); p }
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def expose(obj: Option[vo.Slot], place: Option[vo.ext.Place], profile: ProfileRemote): Option[vo.Slot] =
    obj.map(expose(_, place.get, profile))

  def exposeSeq(obj: Option[Seq[vo.Slot]], place: Option[vo.ext.Place], profile: ProfileRemote): Option[Seq[vo.Slot]] =
    obj.map(_.map(expose(_, place.get, profile)).toList)

  def expose(obj: vo.Booking, place: vo.ext.Place, profile: ProfileRemote): vo.Booking =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("booking"), ap.defaultRead(profile, _ => bookingOwner(p, profile) || placeModerator(place, profile))))
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def exposeBooking(obj: Option[vo.Booking], place: Option[vo.ext.Place], profile: ProfileRemote): Option[vo.Booking] =
    obj.map(expose(_, place.get, profile))

  def exposeBookings(obj: Option[Seq[vo.Booking]], place: Option[vo.ext.Place], profile: ProfileRemote): Option[Seq[vo.Booking]] =
    obj.map(_.map(expose(_, place.get, profile)).toList)

  def expose(obj: vo.Price, place: vo.ext.Place, profile: ProfileRemote): vo.Price =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("price"), ap.defaultRead(profile, _ => placeModerator(place, profile))))
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def exposePrice(obj: Option[vo.Price], place: Option[vo.ext.Place], profile: ProfileRemote): Option[vo.Price] =
    obj.map(expose(_, place.get, profile))

  def exposePrices(obj: Option[Seq[vo.Price]], place: Option[vo.ext.Place], profile: ProfileRemote): Option[Seq[vo.Price]] =
    obj.map(_.map(expose(_, place.get, profile)).toList)

}
