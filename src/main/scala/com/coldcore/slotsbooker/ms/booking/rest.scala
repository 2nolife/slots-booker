package com.coldcore.slotsbooker
package ms.booking.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import ms.rest.BaseRestService
import ms.booking.actors.BookingActor._
import ms.booking.vo

class BookingRestService(hostname: String, port: Int,
                         val bookingActor: ActorRef,
                         externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with BookingRoute {

  bind(bookingRoute, name = "Booking")
}

trait BookingRoute {
  self: BookingRestService =>

  def bookingRoute =
    authenticateToken { profile =>

      path("booking" / "quote") {

        post {
          entity(as[vo.GetQuote]) { entity =>
            completeByActor[vo.Quote](bookingActor, GetQuoteIN(entity, profile))
          }
        }

      } ~
      path("booking" / "slots") {

        post {
          entity(as[vo.BookSlots]) { entity =>
            completeByActor[vo.Reference](bookingActor, BookSlotsIN(entity, profile))
          }
        }

      } ~
      path("booking" / "cancel") {

        post {
          entity(as[vo.CancelSlots]) { entity =>
            completeByActor[vo.Reference](bookingActor, CancelSlotsIN(entity, profile))
          }
        }

      } ~
      path("booking" / "update") {

        post {
          entity(as[vo.UpdateSlots]) { entity =>
            completeByActor[vo.Reference](bookingActor, UpdateSlotsIN(entity, profile))
          }
        }

      }

    }

}
