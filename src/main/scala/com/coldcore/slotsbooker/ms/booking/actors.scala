package com.coldcore.slotsbooker
package ms.booking.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.booking.db.BookingDb
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.booking.service.{BookingService, BookingServiceImpl}
import ms.http.{ApiCode, RestClient}
import ms.vo.ProfileRemote
import ms.actors.MsgInterceptor
import ms.booking.vo
import ms.booking.vo.Implicits._
import org.apache.http.HttpStatus._

trait QuoteCommands {
  case class GetQuoteIN(obj: vo.QuoteSlots, profile: ProfileRemote) extends RequestInfo
  case class GetRefundIN(obj: vo.RefundSlots, profile: ProfileRemote) extends RequestInfo
}

trait SlotsCommands {
  case class BookSlotsIN(obj: vo.BookSlots, profile: ProfileRemote) extends RequestInfo
  case class CancelSlotsIN(obj: vo.CancelSlots, profile: ProfileRemote) extends RequestInfo
  case class UpdateSlotsIN(obj: vo.UpdateSlots, profile: ProfileRemote) extends RequestInfo
}

trait ReferenceCommands {
  case class GetReferenceIN(ref: String, profile: ProfileRemote) extends RequestInfo
  case class NextExpiredReferenceIN(profile: ProfileRemote) extends RequestInfo
  case class ReferencePaidIN(obj: vo.ReferencePaid, profile: ProfileRemote) extends RequestInfo
}

object BookingActor extends QuoteCommands with SlotsCommands with ReferenceCommands {
  def props(bookingDb: BookingDb, placesBaseUrl: String, slotsBaseUrl: String, membersBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new BookingActor(bookingDb, placesBaseUrl, slotsBaseUrl, membersBaseUrl, systemToken, restClient))
}

class BookingActor(bookingDb: BookingDb, placesBaseUrl: String, slotsBaseUrl: String, membersBaseUrl: String, systemToken: String,
                   restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor
  with QuoteReceive with RefundReceive with SlotsReceive with ReferenceReceive {

  val bookingService: BookingService =
    new BookingServiceImpl(bookingDb, placesBaseUrl, slotsBaseUrl, membersBaseUrl, systemToken, restClient)(context.system)

  def receive =
    quoteReceive orElse
    refundReceive orElse
    slotsReceive orElse
    referenceReceive

  val placeModerator = (place: vo.ext.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)
}

trait QuoteReceive {
  self: BookingActor =>
  import BookingActor._

  val quoteReceive: Actor.Receive = {

    case GetQuoteIN(obj, profile) =>
      val invalidEntity = obj.selected.isEmpty

      lazy val (codeA, mySlot) = bookingService.slotById(obj.selected.head.slot_id)
      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canCreate = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): (ApiCode, Option[vo.Quote]) = bookingService.quoteSlots(obj.selected, obj.as_profile_id.getOrElse(profile.profile_id))

      val (code, quote) =
        if (invalidEntity) (ApiCode(SC_BAD_REQUEST), None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canCreate) create()
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, quote)

  }

}

trait RefundReceive {
  self: BookingActor =>
  import BookingActor._

  val refundReceive: Actor.Receive = {

    case GetRefundIN(obj, profile) =>
      val invalidEntity = obj.slot_ids.isEmpty

      lazy val (codeA, mySlot) = bookingService.slotById(obj.slot_ids.head)
      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canCreate = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): (ApiCode, Option[vo.Refund]) = bookingService.refundSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id))

      val (code, refund) =
        if (invalidEntity) (ApiCode(SC_BAD_REQUEST), None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canCreate) create()
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, refund)

  }

}

trait SlotsReceive {
  self: BookingActor =>
  import BookingActor._

  val slotsReceive: Actor.Receive = {

    case BookSlotsIN(obj, profile) =>
      val invalidEntity = // either slot IDs or quote ID
        obj.slot_ids.getOrElse(Nil).isEmpty && obj.quote_id.isEmpty ||
        obj.slot_ids.isDefined && obj.quote_id.isDefined

      lazy val (codeA: ApiCode, mySlot) = {
        val slotId = // get slot ID from supplied quote or slot IDs
          obj.quote_id.flatMap(bookingService.quoteById).map(_.prices.get.head.slot_id)
          .orElse(obj.slot_ids.getOrElse(Nil).headOption)
        slotId.map(bookingService.slotById).getOrElse(SC_NOT_FOUND -> None)
      }

      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canBook = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def book(): (ApiCode, Option[vo.Reference]) = bookingService.bookSlots(obj, obj.as_profile_id.getOrElse(profile.profile_id))

      val (code, reference) =
        if (invalidEntity) (ApiCode(SC_BAD_REQUEST), None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canBook) book()
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, reference)

    case CancelSlotsIN(obj, profile) =>
      val invalidEntity = // either slot IDs or refund ID
        obj.slot_ids.getOrElse(Nil).isEmpty && obj.refund_id.isEmpty ||
        obj.slot_ids.isDefined && obj.refund_id.isDefined

      lazy val (codeA: ApiCode, mySlot) = {
        val slotId = // get slot ID from supplied refund or slot IDs
          obj.refund_id.flatMap(bookingService.refundById).map(_.prices.get.head.slot_id)
          .orElse(obj.slot_ids.getOrElse(Nil).headOption)
        slotId.map(bookingService.slotById).getOrElse(SC_NOT_FOUND -> None)
      }

      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canCancel = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def cancel(): (ApiCode, Option[vo.Reference]) = bookingService.cancelSlots(obj, obj.as_profile_id.getOrElse(profile.profile_id))

      val (code, reference) =
        if (invalidEntity) (ApiCode(SC_BAD_REQUEST), None)
        else if (slotNotFound) (codeA, None)
        else if (placeNotFound) (codeB, None)
        else if (canCancel) cancel()
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, reference)

    case UpdateSlotsIN(obj, profile) =>
      val invalidEntity = obj.slot_ids.isEmpty

      lazy val (codeA, mySlot) = bookingService.slotById(obj.slot_ids.head)
      lazy val (codeB, myPlace) = bookingService.placeById(mySlot.get.place_id)
      lazy val slotNotFound = mySlot.isEmpty
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canUpdate = obj.as_profile_id.isEmpty || profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): ApiCode = bookingService.updateSlots(obj.slot_ids, obj.as_profile_id.getOrElse(profile.profile_id), obj.attributes)

      val code: ApiCode =
        if (invalidEntity) SC_BAD_REQUEST
        else if (slotNotFound) codeA
        else if (placeNotFound) codeB
        else if (canUpdate) update()
        else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

trait ReferenceReceive {
  self: BookingActor =>
  import BookingActor._

  val referenceReceive: Actor.Receive = {

    case GetReferenceIN(ref, profile) =>
      lazy val myReference = bookingService.referenceByRef(ref, profile.profile_id)
      lazy val referenceNotFound = myReference.isEmpty

      val (code, reference) =
        if (referenceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, myReference)

      reply ! CodeEntityOUT(code, reference)

    case NextExpiredReferenceIN(profile) =>
      lazy val myReference = bookingService.nextExpiredReference()
      lazy val referenceNotFound = myReference.isEmpty

      val (code, reference) =
        if (referenceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, myReference)

      reply ! CodeEntityOUT(code, reference)

    case ReferencePaidIN(obj, profile) =>
      val (code, _) = bookingService.referencePaid(obj.ref, obj.profile_id)

      reply ! CodeOUT(code)

  }

}
