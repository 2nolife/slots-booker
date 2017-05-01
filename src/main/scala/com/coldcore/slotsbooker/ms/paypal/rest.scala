package com.coldcore.slotsbooker
package ms.paypal.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import ms.paypal.actors.EventsActor._
import ms.rest.BaseRestService
import ms.vo.EmptyEntity

class PaypalRestService(hostname: String, port: Int,
                        val eventsActor: ActorRef,
                        externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with PaypalRoute {

  bind(paypalRoute, name = "Paypal")
}

trait PaypalRoute {
  self: PaypalRestService =>

  def paypalRoute =
    clientIpHost { iphost =>
      path("paypal" / "events") {

        post {
          entity(as[String]) { entity =>
            completeByActor[EmptyEntity](eventsActor, IncomingEventIN(entity).withIpHost(iphost))
          }
        }

      }
    }

}
