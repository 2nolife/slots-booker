package com.coldcore.slotsbooker
package ms.slots.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ms.vo.{EmptyEntity, ProfileRemote}
import ms.rest.BaseRestService
import ms.slots.actors.SlotsActor._
import ms.slots.vo

class SlotsRestService(hostname: String, port: Int, val systemToken: String, val getDeepFields: Boolean,
                       val slotsActor: ActorRef,
                       externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with SlotsRoute {

  bind(slotsRoute, name = "Slots")
}

trait SlotsRoute extends SlotsInnerBookingsRoute with SlotsInnerPricesRoute with SlotsInnerHoldRoute {
  self: SlotsRestService =>

  def slotsRoute =
    authenticateToken { profile =>

      path("slots") {

        post {
          entity(as[vo.CreateSlot]) { entity =>
            completeByActor[vo.Slot](slotsActor, CreateSlotIN(entity, profile))
          }
        }

      } ~
      path("slots" / "search") {

        get {
          parameters('place_id,
                     'space_id,
                     'from,
                     'to,
                     'inner ? true,
                     'booked ?,
                     'group ?,
                     'deep ? getDeepFields,
                     'deep_bookings.as[Boolean].?,
                     'deep_prices  .as[Boolean].?) {
            (place_id,
             space_id,
             from,
             to,
             inner,
             booked,
             group,
             deep,
             deep_bookings,
             deep_prices) =>

            val (dateFrom, timeFrom) = (from.take(8).toInt, from.slice(8, 12).padTo(1, "0000").mkString.toInt)
            val (dateTo, timeTo) = (to.take(8).toInt, to.slice(8, 12).padTo(1, "2400").mkString.toInt)

            completeByActor[Seq[vo.Slot]](slotsActor, SearchSlotsIN(place_id, space_id, profile,
                                                                    dateFrom, dateTo, timeFrom, timeTo,
                                                                    inner, booked, group,
                                                                    deep_bookings.getOrElse(deep), deep_prices.getOrElse(deep)))
          }
        }

      } ~
      path("slots" / "booked") {

        post {
          entity(as[vo.CreateBooked]) { entity =>
            authenticateSystemToken(systemToken, entity.as_profile_id) { userProfile =>
              completeByActor[vo.Booked](slotsActor, CreateBookedIN(entity, userProfile))
            }
          }
        }

      } ~
      path("slots" / "booked" / Segment) { bookedId =>

        patch {
          entity(as[vo.UpdateBooked]) { entity =>
            authenticateSystemToken(systemToken, entity.as_profile_id) { userProfile =>
              completeByActor[vo.Booked](slotsActor, UpdateBookedIN(bookedId, entity, userProfile))
            }
          }
        }

      } ~
      pathPrefix("slots" / Segment) { slotId =>
        pathEnd {

          patch {
            entity(as[vo.UpdateSlot]) { entity =>
              completeByActor[vo.Slot](slotsActor, UpdateSlotIN(slotId, entity, profile))
            }
          } ~
          get {
            parameters('deep ? getDeepFields,
                       'deep_bookings.as[Boolean].?,
                       'deep_prices  .as[Boolean].?,
                       'deep_booked  .as[Boolean].?) {
              (deep,
               deep_bookings,
               deep_prices,
               deep_booked) =>

              completeByActor[vo.Slot](slotsActor, GetSlotIN(slotId, profile,
                                                             deep_bookings.getOrElse(deep), deep_prices.getOrElse(deep), deep_booked.getOrElse(deep)))
            }
          } ~
          delete {
            completeByActor[EmptyEntity](slotsActor, DeleteSlotIN(slotId, profile))
          }

        } ~
        bookingsRoute(profile, slotId) ~
        pricesRoute(profile, slotId) ~
        holdRoute(profile, slotId)

      }

    }

}

trait SlotsInnerBookingsRoute {
  self: SlotsRestService =>

  def bookingsRoute(profile: ProfileRemote, slotId: String) =

    path("bookings") {

      post {
        entity(as[vo.CreateBooking]) { entity =>
          authenticateSystemToken(systemToken, entity.as_profile_id) { userProfile =>
            completeByActor[vo.Booking](slotsActor, CreateBookingIN(slotId, entity, userProfile))
          }
        }
      } ~
      get {
        parameters('active ?) { active =>
          completeByActor[Seq[vo.Booking]](slotsActor, GetBookingsIN(slotId, active, profile))
        }
      }

    } ~
    pathPrefix("bookings" / Segment) { bookingId =>
      pathEnd {

        patch {
          entity(as[vo.UpdateBooking]) { entity =>
            def update(userProfile: ProfileRemote): Route =
              completeByActor[vo.Booking](slotsActor, UpdateBookingIN(slotId, bookingId, entity, userProfile))

            entity.as_profile_id.map(authenticateSystemToken(systemToken, _) { update }).getOrElse { update(profile) }
          }
        } ~
        get {
          completeByActor[vo.Booking](slotsActor, GetBookingIN(slotId, bookingId, profile))
        }

      }
    }

}

trait SlotsInnerPricesRoute {
  self: SlotsRestService =>

  def pricesRoute(profile: ProfileRemote, slotId: String) =

    path("prices") {

      post {
        entity(as[vo.CreatePrice]) { entity =>
          completeByActor[vo.Price](slotsActor, CreatePriceIN(slotId, entity, profile))
        }
      } ~
      get {
        completeByActor[Seq[vo.Price]](slotsActor, GetPricesIN(slotId, profile))
      }

    } ~
    pathPrefix("prices" / Segment) { priceId =>
      pathEnd {

        patch {
          entity(as[vo.UpdatePrice]) { entity =>
            completeByActor[vo.Price](slotsActor, UpdatePriceIN(slotId, priceId, entity, profile))
          }
        } ~
        get {
          completeByActor[vo.Price](slotsActor, GetPriceIN(slotId, priceId, profile))
        } ~
        delete {
          completeByActor[EmptyEntity](slotsActor, DeletePriceIN(slotId, priceId, profile))
        }

      }
    }

}

trait SlotsInnerHoldRoute {
  self: SlotsRestService =>

  def holdRoute(profile: ProfileRemote, slotId: String) =

    path("hold") {

      patch {
        entity(as[vo.UpdateHold]) { entity =>
          authenticateSystemToken(systemToken) { _ =>
            completeByActor[EmptyEntity](slotsActor, UpdateHoldIN(slotId, entity, profile))
          }
        }
      }

    }

}

