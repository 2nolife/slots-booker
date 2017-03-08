package com.coldcore.slotsbooker
package ms.places.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.attributes.Types._
import ms.attributes.{Permission => ap, Util => au}
import ms.actors.MsgInterceptor
import ms.vo.ProfileRemote
import ms.places.db.PlacesDb
import ms.places.db.PlacesDb._
import ms.places.vo
import ms.places.vo.Implicits._
import ms.vo.Implicits._
import org.apache.http.HttpStatus._

trait PlaceCommands {
  case class CreatePlaceIN(obj: vo.CreatePlace, profile: ProfileRemote)
  case class UpdatePlaceIN(placeId: String, obj: vo.UpdatePlace, profile: ProfileRemote)
  case class GetPlaceIN(placeId: String, profile: ProfileRemote,
                        deepSpaces: Boolean, deepPrices: Boolean)
  case class DeletePlaceIN(placeId: String, profile: ProfileRemote)
  case class GetPlacesIN(byAttributes: Seq[(String,String)], joinOR: Boolean, profile: ProfileRemote,
                         deepSpaces: Boolean, deepPrices: Boolean)
}

trait SpaceCommands {
  case class CreateSpaceIN(placeId: String, obj: vo.CreateSpace, profile: ProfileRemote)
  case class CreateInnerSpaceIN(placeId: String, spaceId: String, obj: vo.CreateSpace, profile: ProfileRemote)
  case class UpdateSpaceIN(placeId: String, spaceId: String, obj: vo.UpdateSpace, profile: ProfileRemote)
  case class GetSpaceIN(placeId: String, spaceId: String, profile: ProfileRemote,
                        deepSpaces: Boolean, deepPrices: Boolean)
  case class GetSpacesIN(placeId: String, profile: ProfileRemote,
                         deepSpaces: Boolean, deepPrices: Boolean)
  case class DeleteSpaceIN(placeId: String, spaceId: String, profile: ProfileRemote)
}

trait PriceCommands {
  case class CreatePriceIN(placeId: String, spaceId: String, obj: vo.CreatePrice, profile: ProfileRemote)
  case class UpdatePriceIN(placeId: String, spaceId: String, priceId: String, obj: vo.UpdatePrice, profile: ProfileRemote)
  case class GetPriceIN(placeId: String, spaceId: String, priceId: String, profile: ProfileRemote)
  case class GetPricesIN(placeId: String, spaceId: String, profile: ProfileRemote)
  case class DeletePriceIN(placeId: String, spaceId: String, priceId: String, profile: ProfileRemote)
}

object PlacesActor extends PlaceCommands with SpaceCommands with PriceCommands {
  def props(placesDb: PlacesDb, voAttributes: VoAttributes): Props = Props(new PlacesActor(placesDb, voAttributes))
}

class PlacesActor(val placesDb: PlacesDb, val voAttributes: VoAttributes) extends Actor with ActorLogging with MsgInterceptor with VoExpose
  with AmendPlace with GetPlace with AmendSpace with GetSpace with AmendPrice with GetPrice {

  def receive =
    amendPlaceReceive orElse getPlaceReceive orElse
    amendSpaceReceive orElse getSpaceReceive orElse
    amendPriceReceive orElse getPriceReceive

  val placeModerator = (place: vo.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)

  def permitAttributes(obj: vo.UpdatePlace, place: vo.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("place"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

}

trait AmendPlace {
  self: PlacesActor =>
  import PlacesActor._

  val amendPlaceReceive: Actor.Receive = {

    case CreatePlaceIN(obj, profile) =>
      val place = Some(placesDb.createPlace(profile.profile_id, obj))
      reply ! CodeEntityOUT(SC_CREATED, expose(place, profile))

    case UpdatePlaceIN(placeId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val forbidModerators = obj.moderators.isDefined && !(myPlace.get.profile_id == profile.profile_id || profile.isSuper)
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canUpdate = profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Place] = placesDb.updatePlace(placeId, obj)

      val (code, place) =
        if (placeNotFound) (SC_NOT_FOUND, None)
        else if (forbidModerators) (SC_FORBIDDEN, None)
        else if (forbidAttributes) (SC_FORBIDDEN, None)
        else if (canUpdate) (SC_OK, update())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, expose(place, profile))

    case DeletePlaceIN(placeId, profile) => //todo bookings may exist
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canDelete = profile.isSuper || myPlace.get.profile_id == profile.profile_id

      def delete() = placesDb.deletePlace(placeId)

      val code =
        if (placeNotFound) SC_NOT_FOUND
        else if (canDelete) {
          delete()
          SC_OK
        } else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

trait AmendSpace {
  self: PlacesActor =>
  import PlacesActor._

  val amendSpaceReceive: Actor.Receive = {

    case CreateSpaceIN(placeId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canCreate = profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): Option[vo.Space] = Some(placesDb.createSpace(placeId, obj))

      val (code, space) =
        if (placeNotFound) (SC_NOT_FOUND, None)
        else if (canCreate) (SC_CREATED, create())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, space)

    case UpdateSpaceIN(placeId, spaceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val canUpdate = profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Space] = placesDb.updateSpace(spaceId, obj)

      val (code, space) =
        if (spaceNotFound) (SC_NOT_FOUND, None)
        else if (canUpdate) (SC_OK, update())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, space)

    case CreateInnerSpaceIN(placeId, spaceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val canCreate = profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): Option[vo.Space] = Some(placesDb.createInnerSpace(spaceId, obj))

      val (code, space) =
        if (spaceNotFound) (SC_NOT_FOUND, None)
        else if (canCreate) (SC_CREATED, create())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, space)

    case DeleteSpaceIN(placeId, spaceId, profile) => //todo bookings may exist
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val canDelete = profile.isSuper || placeModerator(myPlace.get, profile)

      def delete() = placesDb.deleteSpace(spaceId)

      val code =
        if (spaceNotFound) SC_NOT_FOUND
        else if (canDelete) {
          delete()
          SC_OK
        } else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

trait AmendPrice {
  self: PlacesActor =>
  import PlacesActor._

  val amendPriceReceive: Actor.Receive = {

    case CreatePriceIN(placeId, spaceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val canCreate = profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): Option[vo.Price] = Some(placesDb.createPrice(spaceId, obj))

      val (code, price) =
        if (spaceNotFound) (SC_NOT_FOUND, None)
        else if (canCreate) (SC_CREATED, create())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, price)

    case UpdatePriceIN(placeId, spaceId, priceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val priceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId || !mySpace.get.hasPriceId(priceId)
      lazy val canUpdate = profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Price] = placesDb.updatePrice(priceId, obj)

      val (code, price) =
        if (priceNotFound) (SC_NOT_FOUND, None)
        else if (canUpdate) (SC_OK, update())
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, price)

    case DeletePriceIN(placeId, spaceId, priceId, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val priceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId || !mySpace.get.hasPriceId(priceId)
      lazy val canDelete = profile.isSuper || placeModerator(myPlace.get, profile)

      def delete() = placesDb.deletePrice(priceId)

      val code =
        if (priceNotFound) SC_NOT_FOUND
        else if (canDelete) {
          delete()
          SC_OK
        } else SC_FORBIDDEN

      reply ! CodeOUT(code)

  }

}

trait GetPlace {
  self: PlacesActor =>
  import PlacesActor._

  val getPlaceReceive: Actor.Receive = {

    case GetPlaceIN(placeId, profile, deepSpaces, deepPrices) =>
      val fields = customPlaceFields(deepSpaces, deepPrices)

      lazy val myPlace = placesDb.placeById(placeId, fields)
      lazy val placeNotFound = myPlace.isEmpty

      val (code, place) =
        if (placeNotFound) (SC_NOT_FOUND, None)
        else (SC_OK, myPlace)

      reply ! CodeEntityOUT(code, expose(place, profile))

    case GetPlacesIN(byAttributes, joinOR, profile, deepSpaces, deepPrices) =>
      val fields = customPlaceFields(deepSpaces, deepPrices)
      val unprivileged =
        byAttributes.nonEmpty && !byAttributes.exists { case (_, value) => value.endsWith("*") && value.size < 3+1 }

      lazy val canRead = profile.isSuper || unprivileged

      def read: Option[Seq[vo.Place]] = Some(placesDb.searchPlaces(byAttributes, joinOR, fields))

      val (code, places) =
        if (canRead) (SC_OK, read)
        else (SC_FORBIDDEN, None)

      reply ! CodeEntityOUT(code, exposeSeq(places, profile))

  }

}

trait GetSpace {
  self: PlacesActor =>
  import PlacesActor._

  val getSpaceReceive: Actor.Receive = {

    case GetSpaceIN(placeId, spaceId, profile, deepSpaces, deepPrices) =>
      val fields = customSpaceFields(deepSpaces, deepPrices)

      lazy val mySpace = placesDb.spaceById(spaceId, fields)
      lazy val spaceNotFound = mySpace.isEmpty || mySpace.get.place_id != placeId

      val (code, space) =
        if (spaceNotFound) (SC_NOT_FOUND, None)
        else (SC_OK, mySpace)

      reply ! CodeEntityOUT(code, space)

    case GetSpacesIN(placeId, profile, deepSpaces, deepPrices) =>
      val fields = customPlaceFields(deepSpaces, deepPrices)

      lazy val myPlace = placesDb.placeById(placeId, fields)
      lazy val placeNotFound = myPlace.isEmpty

      def read: Option[Seq[vo.Space]] = Some(myPlace.get.spaces.getOrElse(Nil))

      val (code, spaces) =
        if (placeNotFound) (SC_NOT_FOUND, None)
        else (SC_OK, read)

      reply ! CodeEntityOUT(code, spaces)

  }

}

trait GetPrice {
  self: PlacesActor =>
  import PlacesActor._

  val getPriceReceive: Actor.Receive = {

    case GetPriceIN(placeId, spaceId, priceId, profile) =>
      val fields = customSpaceFields(deep_spaces = false, deep_prices = false)

      lazy val mySpace = placesDb.spaceById(spaceId, fields)
      lazy val myPrice = placesDb.priceById(priceId)
      lazy val priceNotFound = myPrice.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId || !mySpace.get.hasPriceId(priceId)

      val (code, price) =
        if (priceNotFound) (SC_NOT_FOUND, None)
        else (SC_OK, myPrice)

      reply ! CodeEntityOUT(code, price)

    case GetPricesIN(placeId, spaceId, profile) =>
      val fields = customSpaceFields(deep_spaces = false, deep_prices = true)

      lazy val mySpace = placesDb.spaceById(spaceId, fields)
      lazy val spaceNotFound = mySpace.isEmpty || mySpace.get.place_id != placeId

      def read: Option[Seq[vo.Price]] = Some(mySpace.get.prices.getOrElse(Nil))

      val (code, prices) =
        if (spaceNotFound) (SC_NOT_FOUND, None)
        else (SC_OK, read)

      reply ! CodeEntityOUT(code, prices)

  }

}

trait VoExpose {
  self: {
    val voAttributes: VoAttributes
  } =>

  private val placeModerator = (place: vo.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)

  def expose(obj: vo.Place, profile: ProfileRemote): vo.Place =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("place"), ap.defaultRead(profile, _ => placeModerator(p, profile))))
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def expose(obj: Option[vo.Place], profile: ProfileRemote): Option[vo.Place] =
    obj.map(expose(_, profile))

  def exposeSeq(obj: Option[Seq[vo.Place]], profile: ProfileRemote): Option[Seq[vo.Place]] =
    obj.map(_.map(expose(_, profile)))

}

