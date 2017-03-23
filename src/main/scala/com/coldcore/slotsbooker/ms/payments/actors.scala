package com.coldcore.slotsbooker
package ms.payments.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.payments.service.{PaymentsService, PaymentsServiceImpl}
import ms.http.RestClient
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.actors.MsgInterceptor
import ms.vo.ProfileRemote
import ms.payments.db.PaymentsDb
import ms.payments.vo
import ms.payments.vo.Implicits._
import org.apache.http.HttpStatus._
import scala.concurrent.duration.DurationInt

trait BalanceCommands {
  case class UpdateCreditIN(obj: vo.UpdateCredit, profile: ProfileRemote)
  case class GetBalanceIN(placeId: String, profileId: Option[String], profile: ProfileRemote)
}

trait ReferenceCommands {
  case class ProcessReferenceIN(obj: vo.ProcessReference, profile: ProfileRemote)
}

object PaymentsActor extends BalanceCommands with ReferenceCommands {
  def props(paymentsDb: PaymentsDb, placesBaseUrl: String, bookingBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new PaymentsActor(paymentsDb, placesBaseUrl, bookingBaseUrl, systemToken, restClient))
}

class PaymentsActor(paymentsDb: PaymentsDb, placesBaseUrl: String, bookingBaseUrl: String, systemToken: String,
                    restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor
  with AmendBalance with GetBalance with ProcessReference {

  val paymentsService: PaymentsService = new PaymentsServiceImpl(paymentsDb, placesBaseUrl, bookingBaseUrl, systemToken, restClient)

  def receive =
    amendBalanceReceive orElse getBalanceReceive orElse
    processReferenceReceive

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
        if (invalidEntity) (SC_BAD_REQUEST, None)
        else if (placeNotFound) (codeA, None)
        else if (canUpdate) (SC_OK, update())
        else (SC_FORBIDDEN, None)

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
      lazy val canRead = profileId.isEmpty || placeModerator(myPlace.get, profile) || profile.isSuper

      def read(): Option[vo.Balance] = Some(paymentsService.getBalance(placeId, profileId.getOrElse(profile.profile_id)))

      val (code, credit) =
        if (placeNotFound) (codeA, None)
        else if (canRead) (SC_OK, read())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, credit)

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

      def process(): Int = paymentsService.processReference(obj.ref, profileId)

      val code =
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
        case (SC_OK, Some(reference)) => log.debug(s"Cancelled expired reference ${reference.ref.get}")
        case (SC_NOT_FOUND, None) =>
        case (code, Some(reference)) => log.warning(s"Code received $code for expired reference ${reference.ref.get}")
        case (code, None) => log.warning(s"Code received $code")
      }

  }
}
