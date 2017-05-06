package com.coldcore.slotsbooker
package ms.payments.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.payments.service.{PaymentsService, PaymentsServiceImpl}
import ms.http.{ApiCode, RestClient}
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.actors.MsgInterceptor
import ms.vo.ProfileRemote
import ms.payments.db.PaymentsDb
import ms.payments.vo
import ms.payments.vo.Implicits._
import ms.payments.Constants._
import org.apache.http.HttpStatus._

import scala.concurrent.duration.DurationInt

trait BalanceCommands {
  case class UpdateCreditIN(obj: vo.UpdateCredit, profile: ProfileRemote) extends RequestInfo
  case class GetBalanceIN(placeId: String, profileId: Option[String], profile: ProfileRemote) extends RequestInfo
}

trait ReferenceCommands {
  case class GetReferenceIN(ref: String, profileId: Option[String], profile: ProfileRemote) extends RequestInfo
  case class ProcessReferenceIN(obj: vo.ProcessReference, profile: ProfileRemote) extends RequestInfo
}

object PaymentsActor extends BalanceCommands with ReferenceCommands {
  def props(paymentsDb: PaymentsDb, placesBaseUrl: String, bookingBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new PaymentsActor(paymentsDb, placesBaseUrl, bookingBaseUrl, systemToken, restClient))
}

class PaymentsActor(paymentsDb: PaymentsDb, placesBaseUrl: String, bookingBaseUrl: String, systemToken: String,
                    restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor
  with AmendBalance with GetBalance with ProcessReference with GetReference {

  val paymentsService: PaymentsService = new PaymentsServiceImpl(paymentsDb, placesBaseUrl, bookingBaseUrl, systemToken, restClient)

  def receive =
    amendBalanceReceive orElse getBalanceReceive orElse
    processReferenceReceive orElse getReferenceReceive

  val placeModerator = (place: vo.ext.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)
}

trait AmendBalance {
  self: PaymentsActor =>
  import PaymentsActor._

  val amendBalanceReceive: Actor.Receive = {

    case UpdateCreditIN(obj, profile) =>
      val invalidEntity = obj.source.fields.get("reason").map(_.toString).getOrElse("").isEmpty

      lazy val (codeA, myPlace) = paymentsService.placeById(obj.place_id)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canUpdate = placeModerator(myPlace.get, profile) || profile.isSuper

      def update(): Option[vo.Balance] = Some(paymentsService.addCredit(obj.place_id, obj.profile_id, obj))

      val (code, credit) =
        if (invalidEntity) (ApiCode(SC_BAD_REQUEST, 'reason_missing), None)
        else if (placeNotFound) (codeA, None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, credit)

  }

}

trait GetBalance {
  self: PaymentsActor =>
  import PaymentsActor._

  val getBalanceReceive: Actor.Receive = {

    case GetBalanceIN(placeId, profileId, profile) =>
      lazy val (codeA, myPlace) = paymentsService.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canRead = profileId.isEmpty || placeModerator(myPlace.get, profile) || profile.isSuper || profileId.exists(profile.profile_id ==)

      def read(): Option[vo.Balance] = Some(paymentsService.getBalance(placeId, profileId.getOrElse(profile.profile_id)))

      val (code, credit) =
        if (placeNotFound) (codeA, None)
        else if (canRead) (ApiCode.OK, read())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, credit)

  }

}

trait GetReference {
  self: PaymentsActor =>
  import PaymentsActor._

  val getReferenceReceive: Actor.Receive = {

    case GetReferenceIN(ref, profileId, profile) =>
      lazy val (codeB, myReference) = paymentsService.referenceByRef(ref, profileId.getOrElse(profile.profile_id))
      lazy val (codeA, myPlace) = myReference.map(ref => paymentsService.placeById(ref.place_id)).getOrElse(codeB, None)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canRead = profileId.isEmpty || placeModerator(myPlace.get, profile) || profile.isSuper || profileId.exists(profile.profile_id ==)

      val (code, reference) =
        if (placeNotFound) (codeA, None)
        else if (canRead) (codeB, myReference)
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, reference)

  }

}

trait ProcessReference {
  self: PaymentsActor =>
  import PaymentsActor._

  val processReferenceReceive: Actor.Receive = {

    case ProcessReferenceIN(obj, profile) =>
      val profileId = obj.as_profile_id.getOrElse(profile.profile_id)

      lazy val (codeR, myReference) = paymentsService.referenceByRef(obj.ref, profileId)
      lazy val (codeA, myPlace) = paymentsService.placeById(myReference.get.place_id)
      lazy val referenceNotFound = myReference.isEmpty
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canProcess = obj.as_profile_id.isEmpty || placeModerator(myPlace.get, profile) || profile.isSuper

      def process(): ApiCode = paymentsService.processReference(obj.ref, profileId)

      val code: ApiCode =
        if (referenceNotFound) codeR
        else if (placeNotFound) codeA
        else if (canProcess) process()
        else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

object ExpiredActor extends BalanceCommands with ReferenceCommands {
  def props(paymentsDb: PaymentsDb, placesBaseUrl: String, bookingBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new ExpiredActor(paymentsDb, placesBaseUrl, bookingBaseUrl, systemToken, restClient))
}

class ExpiredActor(paymentsDb: PaymentsDb, placesBaseUrl: String, bookingBaseUrl: String, systemToken: String,
                    restClient: RestClient) extends Actor with ActorLogging {

  import context.dispatcher

  case object Tick

  context.system.scheduler.schedule(10 seconds, 10 seconds, self, Tick)

  val paymentsService: PaymentsService = new PaymentsServiceImpl(paymentsDb, placesBaseUrl, bookingBaseUrl, systemToken, restClient)

  def receive = {

    case Tick =>
      paymentsService.expiredReference() match {
        case (code, Some(reference)) if (code is SC_OK) || (code is SC_CREATED) => log.debug(s"Cancelled expired reference ${reference.ref.get}")
        case (code, None) if code is SC_NOT_FOUND =>
        case (code, Some(reference)) => log.warning(s"Received $code for expired reference ${reference.ref.get}")
        case (code, None) => log.warning(s"Received $code")
      }

  }
}
