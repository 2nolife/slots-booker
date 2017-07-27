package com.coldcore.slotsbooker
package ms.slots.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ms.vo.{EmptyEntity, ProfileRemote}
import ms.rest.BaseRestService
import ms.slots.actors.SlotsActor._
import ms.slots.vo

class SlotsRestService(hostname: String, port: Int, anonymousReads: Boolean, val systemToken: String, val getDeepFields: Boolean,
                       val slotsActor: ActorRef,
                       externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor, "Slots") with SlotsRoute {

  val authenticate = if (anonymousReads) authenticateTokenOrAnonymous else authenticateToken

  bind(slotsRoute)
}

trait SlotsRoute extends SlotsInnerBookingsRoute with SlotsInnerPricesRoute with SlotsInnerHoldRoute with SlotsInnerBoundsRoute {
  self: SlotsRestService =>

  def slotsRoute =
    pathPrefix("slots") {
      authenticate { profile =>

        pathEnd {

          post {
            authorized(profile) {
              entity(as[vo.CreateSlot]) { entity =>
                completeByActor[vo.Slot](slotsActor, CreateSlotIN(entity, profile))
              }
            }
          }

        } ~
        path("search") {

          get {
            parameters('place_id,
                       'space_id,
                       'from,
                       'to,
                       'inner ? true,
                       'booked ?,
                       'paid.as[Boolean].?,
                       'deep ? getDeepFields,
                       'deep_bookings.as[Boolean].?,
                       'deep_prices  .as[Boolean].?) {
              (place_id,
               space_id,
               from,
               to,
               inner,
               booked,
               paid,
               deep,
               deep_bookings,
               deep_prices) =>

              val (dateFrom, timeFrom) = (from.take(8).toInt, from.slice(8, 12).padTo(1, "0000").mkString.toInt)
              val (dateTo, timeTo) = (to.take(8).toInt, to.slice(8, 12).padTo(1, "2400").mkString.toInt)

              completeByActor[Seq[vo.Slot]](slotsActor, SearchSlotsIN(place_id, space_id, profile,
                                                                      dateFrom, dateTo, timeFrom, timeTo,
                                                                      inner, booked, paid,
                                                                      deep_bookings.getOrElse(deep), deep_prices.getOrElse(deep)))
            }
          }

        } ~
        path("booked") {

          post {
            authorized(profile) {
              entity(as[vo.CreateBooked]) { entity =>
                authenticateSystemToken(systemToken, entity.as_profile_id) { userProfile =>
                  completeByActor[vo.Booked](slotsActor, CreateBookedIN(entity, userProfile))
                }
              }
            }
          }

        } ~
        path("booked" / Segment) { bookedId =>

          patch {
            authorized(profile) {
              entity(as[vo.UpdateBooked]) { entity =>
                authenticateSystemToken(systemToken, entity.as_profile_id) { userProfile =>
                  completeByActor[vo.Booked](slotsActor, UpdateBookedIN(bookedId, entity, userProfile))
                }
              }
            }
          }

        } ~
        pathPrefix(Segment) { slotId =>
          pathEnd {

            patch {
              authorized(profile) {
                entity(as[vo.UpdateSlot]) { entity =>
                  completeByActor[vo.Slot](slotsActor, UpdateSlotIN(slotId, entity, profile))
                }
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
              authorized(profile) {
                completeByActor[EmptyEntity](slotsActor, DeleteSlotIN(slotId, profile))
              }
            }

          } ~
          bookingsRoute(profile, slotId) ~
          pricesRoute(profile, slotId) ~
          holdRoute(profile, slotId) ~
          boundsRoute(profile, slotId)

        }

      }
    }

}

trait SlotsInnerBookingsRoute {
  self: SlotsRestService =>

  def bookingsRoute(profile: ProfileRemote, slotId: String) =

    path("bookings") {

      post {
        authorized(profile) {
          entity(as[vo.CreateBooking]) { entity =>
            authenticateSystemToken(systemToken, entity.as_profile_id) { userProfile =>
              completeByActor[vo.Booking](slotsActor, CreateBookingIN(slotId, entity, userProfile))
            }
          }
        }
      } ~
      get {
        parameters('active ?) { active =>
          completeByActor[Seq[vo.Booking]](slotsActor, GetBookingsIN(slotId, active, profile))
        }
      }

    } ~
    path("bookings" / Segment) { bookingId =>

      patch {
        authorized(profile) {
          entity(as[vo.UpdateBooking]) { entity =>
            def update(userProfile: ProfileRemote): Route =
              completeByActor[vo.Booking](slotsActor, UpdateBookingIN(slotId, bookingId, entity, userProfile))

            entity.as_profile_id.map(authenticateSystemToken(systemToken, _) { update }).getOrElse { update(profile) }
          }
        }
      } ~
      get {
        completeByActor[vo.Booking](slotsActor, GetBookingIN(slotId, bookingId, profile))
      }

    }

}

trait SlotsInnerPricesRoute {
  self: SlotsRestService =>

  def pricesRoute(profile: ProfileRemote, slotId: String) =

    path("effective" / "prices") {

      get {
        completeByActor[Seq[vo.Price]](slotsActor, GetPricesIN(slotId, effective = Some(""), profile))
      }

    } ~
    path("prices") {

      post {
        authorized(profile) {
          entity(as[vo.CreatePrice]) { entity =>
            completeByActor[vo.Price](slotsActor, CreatePriceIN(slotId, entity, profile))
          }
        }
      } ~
      get {
        completeByActor[Seq[vo.Price]](slotsActor, GetPricesIN(slotId, effective = None, profile))
      }

    } ~
    path("prices" / Segment) { priceId =>

      patch {
        authorized(profile) {
          entity(as[vo.UpdatePrice]) { entity =>
            completeByActor[vo.Price](slotsActor, UpdatePriceIN(slotId, priceId, entity, profile))
          }
        }
      } ~
      get {
        completeByActor[vo.Price](slotsActor, GetPriceIN(slotId, priceId, profile))
      } ~
      delete {
        authorized(profile) {
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
        authorized(profile) {
          entity(as[vo.UpdateHold]) { entity =>
            authenticateSystemToken(systemToken) { _ =>
              completeByActor[EmptyEntity](slotsActor, UpdateHoldIN(slotId, entity, profile))
            }
          }
        }
      }

    }

}

trait SlotsInnerBoundsRoute {
  self: SlotsRestService =>

  def boundsRoute(profile: ProfileRemote, slotId: String) =

    path("effective" / "bounds") {

      get {
        parameters('book ?, 'cancel ?) { (book, cancel) =>
          completeByActor[vo.Bounds](slotsActor, GetBoundsIN(slotId,
                                                             of = book.map(_ => 'book) orElse cancel.map(_ => 'cancel) getOrElse '?,
                                                             profile))
        }
      }

    }

}
