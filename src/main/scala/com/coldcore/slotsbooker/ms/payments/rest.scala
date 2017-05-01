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

          post {
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
        path("reference" / "process") {

          post {
            entity(as[vo.ProcessReference]) { entity =>
              completeByActor[EmptyEntity](paymentsActor, ProcessReferenceIN(entity, profile))
            }
          }

        }

      }
    }

}
