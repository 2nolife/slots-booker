package com.coldcore.slotsbooker
package ms.places.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import ms.places.actors.PlacesActor._
import ms.rest.BaseRestService
import ms.vo.{EmptyEntity, ProfileRemote}
import ms.places.vo

class PlacesRestService(hostname: String, port: Int, val getDeepFields: Boolean,
                        val placesActor: ActorRef,
                        externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with PlacesRoute {

  bind(placesRoute, name = "Places")
}

trait PlacesRoute extends PlacesInnerSpacesRoute {
  self: PlacesRestService =>

  def placesRoute =
    authenticateToken { profile =>

      path("places") {

        post {
          entity(as[vo.CreatePlace]) { entity =>
            completeByActor[vo.Place](placesActor, CreatePlaceIN(entity, profile))
          }
        }

      } ~
      pathPrefix("places" / "search") {

        get {
          parameters('deep ? getDeepFields,
                     'deep_spaces.as[Boolean].?,
                     'deep_prices.as[Boolean].?) {
            (deep,
             deep_spaces,
             deep_prices) =>

            parameterSeq { attributes =>
              completeByActor[Seq[vo.Place]](placesActor, GetPlacesIN(attributes.filterNot(p => Seq("and", "or", "deep", "deep_spaces", "deep_prices").contains(p._1)),
                                                                      joinOR = attributes.exists(p => p._1 == "or"),
                                                                      profile,
                                                                      deep_spaces.getOrElse(deep), deep_prices.getOrElse(deep)))
            }
          }
        }

      } ~
      pathPrefix("places" / Segment) { placeId =>
        pathEnd {

          patch {
            entity(as[vo.UpdatePlace]) { entity =>
              completeByActor[vo.Place](placesActor, UpdatePlaceIN(placeId, entity, profile))
            }
          } ~
          get {
            parameters('deep ? getDeepFields,
                       'deep_spaces.as[Boolean].?,
                       'deep_prices.as[Boolean].?) {
              (deep,
               deep_spaces,
               deep_prices) =>

              completeByActor[vo.Place](placesActor, GetPlaceIN(placeId, profile,
                                                                deep_spaces.getOrElse(deep), deep_prices.getOrElse(deep)))
            }
          } ~
          delete {
            completeByActor[EmptyEntity](placesActor, DeletePlaceIN(placeId, profile))
          }

        } ~
        spacesRoute(profile, placeId)

      }

    }

}

trait PlacesInnerSpacesRoute extends SpacesInnerPricesRoute {
  self: PlacesRestService =>

  def spacesRoute(profile: ProfileRemote, placeId: String) =

      path("spaces") {

        post {
          entity(as[vo.CreateSpace]) { entity =>
            completeByActor[vo.Space](placesActor, CreateSpaceIN(placeId, entity, profile))
          }
        } ~
        get {
          parameters('deep ? getDeepFields,
                     'deep_spaces.as[Boolean].?,
                     'deep_prices.as[Boolean].?,
                     'limit.as[Int].?) {
            (deep,
             deep_spaces,
             deep_prices,
             limit) =>

            completeByActor[Seq[vo.Space]](placesActor, GetSpacesIN(placeId, profile,
                                                                    deep_spaces.getOrElse(deep), deep_prices.getOrElse(deep), limit))
          }
        }

      } ~
      pathPrefix("spaces" / Segment) { spaceId =>
        pathEnd {

          patch {
            entity(as[vo.UpdateSpace]) { entity =>
              completeByActor[vo.Space](placesActor, UpdateSpaceIN(placeId, spaceId, entity, profile))
            }
          } ~
          post {
            entity(as[vo.CreateSpace]) { entity =>
              completeByActor[vo.Space](placesActor, CreateInnerSpaceIN(placeId, spaceId, entity, profile))
            }
          } ~
          get {
            parameters('deep ? getDeepFields,
                       'deep_spaces.as[Boolean].?,
                       'deep_prices.as[Boolean].?) {
              (deep,
               deep_spaces,
               deep_prices) =>

              completeByActor[vo.Space](placesActor, GetSpaceIN(placeId, spaceId, profile,
                                                                deep_spaces.getOrElse(deep), deep_prices.getOrElse(deep)))
            }
          } ~
          delete {
            completeByActor[EmptyEntity](placesActor, DeleteSpaceIN(placeId, spaceId, profile))
          }

        } ~
        path("spaces") {

          get {
            parameters('deep ? getDeepFields,
                       'deep_spaces.as[Boolean].?,
                       'deep_prices.as[Boolean].?,
                       'limit.as[Int].?) {
              (deep,
               deep_spaces,
               deep_prices,
               limit) =>

              completeByActor[Seq[vo.Space]](placesActor, GetInnerSpacesIN(placeId, spaceId, profile,
                                                                           deep_spaces.getOrElse(deep), deep_prices.getOrElse(deep), limit))
            }
          }

        } ~
        pricesRoute(profile, placeId, spaceId)

      }

}

trait SpacesInnerPricesRoute {
  self: PlacesRestService =>

  def pricesRoute(profile: ProfileRemote, placeId: String, spaceId: String) =

      path("prices") {

        post {
          entity(as[vo.CreatePrice]) { entity =>
            completeByActor[vo.Price](placesActor, CreatePriceIN(placeId, spaceId, entity, profile))
          }
        } ~
        get {
          completeByActor[Seq[vo.Price]](placesActor, GetPricesIN(placeId, spaceId, profile))
        }

      } ~
      pathPrefix("prices" / Segment) { priceId =>
        pathEnd {

          patch {
            entity(as[vo.UpdatePrice]) { entity =>
              completeByActor[vo.Price](placesActor, UpdatePriceIN(placeId, spaceId, priceId, entity, profile))
            }
          } ~
          get {
            completeByActor[vo.Price](placesActor, GetPriceIN(placeId, spaceId, priceId, profile))
          } ~
          delete {
            completeByActor[EmptyEntity](placesActor, DeletePriceIN(placeId, spaceId, priceId, profile))
          }

        }
      }

}
