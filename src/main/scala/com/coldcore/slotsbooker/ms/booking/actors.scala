package com.coldcore.slotsbooker
package ms.booking.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.actors.Common.CodeEntityOUT
import ms.booking.service.{BookingService, BookingServiceImpl}
import ms.http.RestClient
import ms.vo.ProfileRemote
import ms.actors.MsgInterceptor
import ms.booking.vo
import ms.booking.vo.Implicits._
import org.apache.http.HttpStatus._

trait QuoteCommands {
  case class GetQuoteIN(obj: vo.GetQuote, profile: ProfileRemote)
  case class GetQuoteOUT(code: Int, quote: Option[vo.Quote])
}

trait SlotsCommands {
  case class BookSlotsIN(obj: vo.BookSlots, profile: ProfileRemote)
  case class CancelSlotsIN(obj: vo.CancelSlots, profile: ProfileRemote)
  case class UpdateSlotsIN(obj: vo.UpdateSlots, profile: ProfileRemote)
}

object BookingActor extends QuoteCommands with SlotsCommands {
  def props(placesBaseUrl: String, slotsBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new BookingActor(placesBaseUrl, slotsBaseUrl, systemToken, restClient))
}

class BookingActor(placesBaseUrl: String, slotsBaseUrl: String, systemToken: String,
                   restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor with QuoteReceive with SlotsReceive {

  val bookingService: BookingService = new BookingServiceImpl(placesBaseUrl, slotsBaseUrl, systemToken, restClient)

  def receive =
    quoteReceive orElse
    slotsReceive

  val placeModerator = (place: vo.ext.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)

}

trait QuoteReceive {
  self: BookingActor =>
  import BookingActor._

  val quoteReceive: Actor.Receive = {

    case GetQuoteIN(obj, profile) =>
      val (code, quote) = bookingService.quoteSlots(obj.slot_ids)
      reply ! CodeEntityOUT(code, quote)

  }

}

trait SlotsReceive {
  self: BookingActor =>
  import BookingActor._

  val slotsReceive: Actor.Receive = {

    case BookSlotsIN(obj, profile) =>
      val invalidEntity = obj.slot_ids.isEmpty

      lazy val (codeA, mySlot) = bookingService.slotById(obj.slot_ids.head)
      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = codeA != SC_OK
      lazy val placeNotFound = codeB != SC_OK
      lazy val canBook = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def book(): (Int, Option[vo.Reference]) = {
        val (codeA, booked) = bookingService.bookSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id))
        if (codeA != SC_CREATED) (codeA, booked)
        else {
          val (codeB, updated) = bookingService.updateSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id), obj.attributes)
          if (codeB == SC_OK) (codeA, booked) else (codeB, booked)
        }
      }

      val (code, reference) =
        if (invalidEntity) (SC_BAD_REQUEST, None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canBook) book()
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, reference)

    case CancelSlotsIN(obj, profile) =>
      val invalidEntity = obj.slot_ids.isEmpty

      lazy val (codeA, mySlot) = bookingService.slotById(obj.slot_ids.head)
      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = codeA != SC_OK
      lazy val placeNotFound = codeB != SC_OK
      lazy val canCancel = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def cancel(): (Int, Option[vo.Reference]) = {
        val (codeA, updated) = bookingService.updateSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id), obj.attributes)
        if (codeA != SC_OK) (codeA, None)
        else bookingService.cancelSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id))
      }

      val (code, reference) =
        if (invalidEntity) (SC_BAD_REQUEST, None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canCancel) cancel()
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, reference)

    case UpdateSlotsIN(obj, profile) =>
      val invalidEntity = obj.slot_ids.isEmpty

      lazy val (codeA, mySlot) = bookingService.slotById(obj.slot_ids.head)
      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = codeA != SC_OK
      lazy val placeNotFound = codeB != SC_OK
      lazy val canUpdate = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): (Int, Option[vo.Reference]) =
        bookingService.updateSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id), obj.attributes)

      val (code, reference) =
        if (invalidEntity) (SC_BAD_REQUEST, None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canUpdate) update()
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, reference)

  }

}
