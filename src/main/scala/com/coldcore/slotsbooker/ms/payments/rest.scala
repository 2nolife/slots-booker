package com.coldcore.slotsbooker
package ms.payments.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import ms.vo.EmptyEntity
import ms.payments.actors.PaymentsActor._
import ms.rest.BaseRestService
import ms.payments.vo

class PaymentsRestService(hostname: String, port: Int,
                          val paymentsActor: ActorRef,
                          externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with PaymentsRoute {

  bind(paymentsRoute, name = "Payments")
}

trait PaymentsRoute {
  self: PaymentsRestService =>

  def paymentsRoute =
    pathPrefix("payments") {
      authenticateToken { profile =>

        path("balance") {

          patch {
            entity(as[vo.UpdateCredit]) { entity =>
              completeByActor[vo.Balance](paymentsActor, UpdateCreditIN(entity, profile))
            }
          } ~
          get {
            parameters('place_id,
                       'profile_id ?) {
              (placeId,
               profileId) =>

              completeByActor[vo.Balance](paymentsActor, GetBalanceIN(placeId, profileId, profile))
            }
          }

        } ~
        path("account") {

          patch {
            entity(as[vo.UpdateCurrencyAccount]) { entity =>
              completeByActor[vo.Account](paymentsActor, UpdateCurrencyAccountIN(entity, profile))
            }
          } ~
          get {
            parameters('place_id) { placeId =>
              completeByActor[vo.Account](paymentsActor, GetAccountIN(placeId, profile))
            }
          }

        } ~
        path("reference" / "process") {

          patch {
            entity(as[vo.ProcessReference]) { entity =>
              completeByActor[EmptyEntity](paymentsActor, ProcessReferenceIN(entity, profile))
            }
          }

        } ~
        path("reference") {

          get {
            parameters('ref,
                       'profile_id ?) {
              (ref,
               profileId)  =>
              completeByActor[vo.ext.Reference](paymentsActor, GetReferenceIN(ref, profileId, profile))
            }
          }

        }

      }
    }

}
