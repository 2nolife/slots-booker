package com.coldcore.slotsbooker
package ms.payments.service

import ms.payments.db.PaymentsDb
import ms.payments.vo.UpdateCredit
import ms.http.{ApiCode, RestClient, SystemRestCalls}
import ms.payments.vo
import ms.vo.{Attributes, EmptyEntity}
import ms.payments.Constants._
import org.apache.http.HttpStatus._
import spray.json.{JsObject, JsString}

trait PlacesMsRestCalls extends SystemRestCalls {
  self: {
    val placesBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def placeFromMsPlaces(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    restGet[vo.ext.Place](s"$placesBaseUrl/places/$placeId?deep=false")
}

trait BookingMsRestCalls extends SystemRestCalls {
  self: {
    val bookingBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def referenceFromMsBooking(ref: String, profileId: String): (ApiCode, Option[vo.ext.Reference]) =
    restGet[vo.ext.Reference](s"$bookingBaseUrl/booking/reference?ref=$ref&profile_id=$profileId")

  def updateReferenceAsPaidWithMsBooking(ref: String, profileId: String): ApiCode =
    restPatch[EmptyEntity](s"$bookingBaseUrl/booking/reference/paid", vo.ext.ReferencePaid(ref, profileId))._1

  def expiredReferenceFromMsBooking: (ApiCode, Option[vo.ext.Reference]) =
    restGet[vo.ext.Reference](s"$bookingBaseUrl/booking/reference/expired")

  def cancelExpiredReferenceWithMsBooking(slotIds: Seq[String]): ApiCode =
    restPost[EmptyEntity](s"$bookingBaseUrl/booking/cancel", vo.ext.CancelSlots(Some(slotIds),
      Some(Attributes(JsObject("reason" -> JsString("expired"))))))._1
}

trait PaymentsService {
  def placeById(placeId: String): (ApiCode, Option[vo.ext.Place])
  def addCredit(placeId: String, profileId: String, obj: vo.UpdateCredit): vo.Balance
  def getBalance(placeId: String, profileId: String): vo.Balance
  def referenceByRef(ref: String, profileId: String): (ApiCode, Option[vo.ext.Reference])
  def processReference(ref: String, profileId: String): ApiCode
  def expiredReference(): (ApiCode, Option[vo.ext.Reference])

  val quoteStatus = Map('inactive -> 0, 'complete -> 1, 'pending_payment -> 2)
  val refundStatus = Map('inactive -> 0, 'complete -> 1, 'pending_payment -> 2)
}

class PaymentsServiceImpl(val paymentsDb: PaymentsDb, val placesBaseUrl: String, val bookingBaseUrl: String, val systemToken: String,
                         val restClient: RestClient)
  extends PaymentsService with PlacesMsRestCalls with BookingMsRestCalls with Auxiliary with ProcessReference


trait Auxiliary {
  self: PaymentsServiceImpl =>

  override def placeById(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    placeFromMsPlaces(placeId)

  override def referenceByRef(ref: String, profileId: String): (ApiCode, Option[vo.ext.Reference]) =
    referenceFromMsBooking(ref, profileId)

  override def addCredit(placeId: String, profileId: String, obj: vo.UpdateCredit): vo.Balance =
    paymentsDb.addCredit(placeId, profileId, obj)

  override def getBalance(placeId: String, profileId: String): vo.Balance =
    paymentsDb.getBalance(placeId, profileId)
}

trait ProcessReference {
  self: PaymentsServiceImpl =>

  override def processReference(ref: String, profileId: String): ApiCode = {
    def step1(): Either[ApiCode, vo.ext.Reference] = { // get reference
      val (code, reference) = referenceByRef(ref, profileId)
      if (reference.isEmpty) Left(code) else Right(reference.get)
    }

    def step2(reference: vo.ext.Reference): Either[ApiCode, _] = // check status
      if (reference.quote.isDefined && reference.quote.get.status.get != quoteStatus('pending_payment)) Left(ApiCode(SC_CONFLICT, 'invalid_quote_status))
      else if (reference.refund.isDefined && reference.refund.get.status.get != refundStatus('pending_payment)) Left(ApiCode(SC_CONFLICT, 'invalid_refund_status))
      else Right(SC_OK)

    def step3(reference: vo.ext.Reference): Either[ApiCode, ApiCode] = // check if quote requires payment and check balance
      reference.quote.filter(_.amount.getOrElse(0) > 0) match {
        case None => Right(SC_OK)
        case Some(quote) =>
          val placeId = quote.place_id
          val (code, place) = placeById(placeId)
          if (place.isEmpty) Left(code)
          else {
            val (required, currency) = (quote.amount.get, quote.currency.get)
            val credit = getBalance(placeId, profileId).credit.getOrElse(Nil).filter(_.currency.get == currency).map(_.amount.get).headOption.getOrElse(0)
            val negative = place.get.attributes.exists(_.fields.collectFirst { case ("negative_balance", JsString("y")) => true }.isDefined)
            if (negative || credit >= required) Right(SC_OK) else Left(ApiCode(SC_CONFLICT, 'not_enough_credit))
          }
      }

    def step4(reference: vo.ext.Reference): Either[ApiCode, _] = { // deduct or refund
      reference.quote.foreach { quote =>
        addCredit(reference.place_id, profileId,
          UpdateCredit(profileId, reference.place_id, -quote.amount.get, quote.currency.get,
            source = JsObject("reason" -> JsString("Paid REF "+ref))))
      }
      reference.refund.foreach { refund =>
        addCredit(reference.place_id, profileId,
          UpdateCredit(profileId, reference.place_id, refund.amount.get, refund.currency.get,
            source = JsObject("reason" -> JsString("Refunded REF "+ref))))
      }

      Right(SC_OK)
    }

    def step5(reference: vo.ext.Reference): Either[ApiCode, _] = { // update reference
      val code = updateReferenceAsPaidWithMsBooking(ref, profileId)
      if (code not SC_OK) Left(code) else Right(SC_OK)
    }

    val eitherA: Either[ApiCode, ApiCode] =
      for {
        reference     <- step1().right
        _             <- step2(reference).right
        _             <- step3(reference).right
        _             <- step4(reference).right
        _             <- step5(reference).right
      } yield SC_OK

    if (eitherA.isRight) ApiCode.OK
    else eitherA.left.get
  }

  override def expiredReference(): (ApiCode, Option[vo.ext.Reference]) = {
    val (code, reference) = expiredReferenceFromMsBooking
    if (reference.isEmpty) (code, None)
    else {
      val slotIds = reference.get.quote.get.prices.getOrElse(Nil).map(_.slot_id)
      val code = cancelExpiredReferenceWithMsBooking(slotIds)
      (code, reference)
    }
  }

}
