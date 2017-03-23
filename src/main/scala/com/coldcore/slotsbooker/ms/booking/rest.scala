package com.coldcore.slotsbooker
package ms.booking.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import com.coldcore.slotsbooker.ms.vo.EmptyEntity
import ms.rest.BaseRestService
import ms.booking.actors.BookingActor._
import ms.booking.vo

class BookingRestService(hostname: String, port: Int, val systemToken: String,
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
          entity(as[vo.QuoteSlots]) { entity =>
            completeByActor[vo.Quote](bookingActor, GetQuoteIN(entity, profile))
          }
        }

      } ~
      path("booking" / "refund") {

        post {
          entity(as[vo.RefundSlots]) { entity =>
            completeByActor[vo.Refund](bookingActor, GetRefundIN(entity, profile))
          }
        }

      } ~
      path("booking" / "book") {

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
            completeByActor[EmptyEntity](bookingActor, UpdateSlotsIN(entity, profile))
          }
        }

      } ~
      path("booking" / "reference") {

        get {
          parameters('ref,
                     'profile_id) {
            (ref,
             profileId)  =>

            authenticateSystemToken(systemToken, profileId) { userProfile =>
              completeByActor[vo.Reference](bookingActor, GetReferenceIN(ref, userProfile))
            }
          }
        }

      } ~
      path("booking" / "reference" / "expired") {

        get {
          authenticateSystemToken(systemToken) { profile =>
            completeByActor[vo.Reference](bookingActor, NextExpiredReferenceIN(profile))
          }
        }

      } ~
      path("booking" / "reference" / "paid") {

        patch {
          authenticateSystemToken(systemToken) { profile =>
            entity(as[vo.ReferencePaid]) { entity =>
              completeByActor[EmptyEntity](bookingActor, ReferencePaidIN(entity, profile))
            }
          }
        }

      }

    }

}
