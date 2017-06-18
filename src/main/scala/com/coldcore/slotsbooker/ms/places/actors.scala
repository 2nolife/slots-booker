package com.coldcore.slotsbooker
package ms.places.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.http.ApiCode
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
  case class CreatePlaceIN(obj: vo.CreatePlace, profile: ProfileRemote) extends RequestInfo
  case class UpdatePlaceIN(placeId: String, obj: vo.UpdatePlace, profile: ProfileRemote) extends RequestInfo
  case class GetPlaceIN(placeId: String, profile: ProfileRemote,
                        deepSpaces: Boolean, deepPrices: Boolean) extends RequestInfo
  case class GetPlacesIN(profile: ProfileRemote,
                         deepSpaces: Boolean, deepPrices: Boolean) extends RequestInfo
  case class DeletePlaceIN(placeId: String, profile: ProfileRemote) extends RequestInfo
  case class SearchPlacesIN(byAttributes: Seq[(String,String)], joinOR: Boolean, profile: ProfileRemote,
                            deepSpaces: Boolean, deepPrices: Boolean) extends RequestInfo
}

trait SpaceCommands {
  case class CreateSpaceIN(placeId: String, obj: vo.CreateSpace, profile: ProfileRemote) extends RequestInfo
  case class CreateInnerSpaceIN(placeId: String, spaceId: String, obj: vo.CreateSpace, profile: ProfileRemote) extends RequestInfo
  case class UpdateSpaceIN(placeId: String, spaceId: String, obj: vo.UpdateSpace, profile: ProfileRemote) extends RequestInfo
  case class GetSpaceIN(placeId: String, spaceId: String, profile: ProfileRemote,
                        deepSpaces: Boolean, deepPrices: Boolean) extends RequestInfo
  case class GetSpacesIN(placeId: String, profile: ProfileRemote,
                         deepSpaces: Boolean, deepPrices: Boolean, limit: Option[Int]) extends RequestInfo
  case class GetInnerSpacesIN(placeId: String, spaceId: String, profile: ProfileRemote,
                              deepSpaces: Boolean, deepPrices: Boolean, limit: Option[Int]) extends RequestInfo
  case class DeleteSpaceIN(placeId: String, spaceId: String, profile: ProfileRemote) extends RequestInfo
  case class SearchSpacesIN(placeId: String, byAttributes: Seq[(String,String)], joinOR: Boolean, profile: ProfileRemote,
                            deepSpaces: Boolean, deepPrices: Boolean) extends RequestInfo
}

trait PriceCommands {
  case class CreatePriceIN(placeId: String, spaceId: String, obj: vo.CreatePrice, profile: ProfileRemote) extends RequestInfo
  case class UpdatePriceIN(placeId: String, spaceId: String, priceId: String, obj: vo.UpdatePrice, profile: ProfileRemote) extends RequestInfo
  case class GetPriceIN(placeId: String, spaceId: String, priceId: String, profile: ProfileRemote) extends RequestInfo
  case class GetPricesIN(placeId: String, spaceId: String, effective: Option[String], profile: ProfileRemote) extends RequestInfo
  case class DeletePriceIN(placeId: String, spaceId: String, priceId: String, profile: ProfileRemote) extends RequestInfo
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

  def permitAttributes(obj: vo.CreatePlace, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("place"), ap.defaultWrite(profile, _ => true))._1

  def permitAttributes(obj: vo.UpdatePlace, place: vo.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("place"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

  def permitAttributes(obj: vo.CreateSpace, place: vo.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("space"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

  def permitAttributes(obj: vo.UpdateSpace, place: vo.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("space"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

  def permitAttributes(obj: vo.CreatePrice, place: vo.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("price"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

  def permitAttributes(obj: vo.UpdatePrice, place: vo.Place, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("price"), ap.defaultWrite(profile, _ => placeModerator(place, profile)))._1

}

trait AmendPlace {
  self: PlacesActor =>
  import PlacesActor._

  val amendPlaceReceive: Actor.Receive = {

    case CreatePlaceIN(obj, profile) =>
      lazy val forbidAttributes = !permitAttributes(obj, profile)

      def create(): Option[vo.Place] = Some(placesDb.createPlace(profile.profile_id, obj))

      val (code, place) =
        if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else (ApiCode.CREATED, create())

      reply ! CodeEntityOUT(ApiCode.CREATED, expose(place, profile))

    case UpdatePlaceIN(placeId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val forbidModerators = obj.moderators.isDefined && !(myPlace.get.profile_id == profile.profile_id || profile.isSuper)
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canUpdate = profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Place] = placesDb.updatePlace(placeId, obj)

      val (code, place) =
        if (placeNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (forbidModerators) (ApiCode(SC_FORBIDDEN), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, expose(place, profile))

    case DeletePlaceIN(placeId, profile) => //todo bookings may exist
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canDelete = profile.isSuper || myPlace.get.profile_id == profile.profile_id

      def delete() = placesDb.deletePlace(placeId)

      val code: ApiCode =
        if (placeNotFound) SC_NOT_FOUND
        else if (canDelete) {
          delete()
          ApiCode.OK
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
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canCreate = profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): Option[vo.Space] = Some(placesDb.createSpace(placeId, obj))

      val (code, space) =
        if (placeNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canCreate) (ApiCode.CREATED, create())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeSpace(space, myPlace, profile))

    case UpdateSpaceIN(placeId, spaceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canUpdate = profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Space] = placesDb.updateSpace(spaceId, obj)

      val (code, space) =
        if (spaceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeSpace(space, myPlace, profile))

    case CreateInnerSpaceIN(placeId, spaceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canCreate = profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): Option[vo.Space] = Some(placesDb.createInnerSpace(spaceId, obj))

      val (code, space) =
        if (spaceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canCreate) (ApiCode.CREATED, create())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeSpace(space, myPlace, profile))

    case DeleteSpaceIN(placeId, spaceId, profile) => //todo bookings may exist
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId)
      lazy val spaceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId
      lazy val canDelete = profile.isSuper || placeModerator(myPlace.get, profile)

      def delete() = placesDb.deleteSpace(spaceId)

      val code: ApiCode =
        if (spaceNotFound) SC_NOT_FOUND
        else if (canDelete) {
          delete()
          ApiCode.OK
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
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canCreate = profile.isSuper || placeModerator(myPlace.get, profile)

      def create(): Option[vo.Price] = Some(placesDb.createPrice(spaceId, obj))

      val (code, price) =
        if (spaceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canCreate) (ApiCode.CREATED, create())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposePrice(price, myPlace, profile))

    case UpdatePriceIN(placeId, spaceId, priceId, obj, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId, customSpaceFields(deep_spaces = false, deep_prices = true))
      lazy val priceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId || !mySpace.get.hasPriceId(priceId)
      lazy val forbidAttributes = !permitAttributes(obj, myPlace.get, profile)
      lazy val canUpdate = profile.isSuper || placeModerator(myPlace.get, profile)

      def update(): Option[vo.Price] = placesDb.updatePrice(priceId, obj)

      val (code, price) =
        if (priceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposePrice(price, myPlace, profile))

    case DeletePriceIN(placeId, spaceId, priceId, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId, customSpaceFields(deep_spaces = false, deep_prices = true))
      lazy val priceNotFound = myPlace.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId || !mySpace.get.hasPriceId(priceId)
      lazy val canDelete = profile.isSuper || placeModerator(myPlace.get, profile)

      def delete() = placesDb.deletePrice(priceId)

      val code: ApiCode =
        if (priceNotFound) SC_NOT_FOUND
        else if (canDelete) {
          delete()
          ApiCode.OK
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
        if (placeNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, myPlace)

      reply ! CodeEntityOUT(code, expose(place, profile))

    case GetPlacesIN(profile, deepSpaces, deepPrices) =>
      val fields = customPlaceFields(deepSpaces, deepPrices)

      def read: Option[Seq[vo.Place]] = Some(placesDb.placesByProfileId(profile.profile_id, fields))

      val (code, places) = (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposeSeq(places, profile))

    case SearchPlacesIN(byAttributes, joinOR, profile, deepSpaces, deepPrices) =>
      val fields = customPlaceFields(deepSpaces, deepPrices)
      val privileged =
        byAttributes.nonEmpty && !byAttributes.exists { case (_, value) => value.endsWith("*") && value.size < 3+1 }

      lazy val canRead = profile.isSuper || privileged

      def read: Option[Seq[vo.Place]] = Some(placesDb.searchPlaces(byAttributes, joinOR, fields))

      val (code, places) =
        if (canRead) (ApiCode.OK, read)
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeSeq(places, profile))

  }

}

trait GetSpace {
  self: PlacesActor =>
  import PlacesActor._

  val getSpaceReceive: Actor.Receive = {

    case GetSpaceIN(placeId, spaceId, profile, deepSpaces, deepPrices) =>
      val fields = customSpaceFields(deepSpaces, deepPrices)

      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId, fields)
      lazy val spaceNotFound = mySpace.isEmpty || mySpace.get.place_id != placeId

      val (code, space) =
        if (spaceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, mySpace)

      reply ! CodeEntityOUT(code, exposeSpace(space, myPlace, profile))

    case GetSpacesIN(placeId, profile, deepSpaces, deepPrices, limit) =>
      val fields = customSpaceFields(deepSpaces, deepPrices)

      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty

      def read: Option[Seq[vo.Space]] = Some(placesDb.immediateSpacesByPlaceId(placeId, fields).take(limit.getOrElse(Int.MaxValue)))

      val (code, spaces) =
        if (placeNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposeSpaces(spaces, myPlace, profile))

    case GetInnerSpacesIN(placeId, spaceId, profile, deepSpaces, deepPrices, limit) =>
      val fields = customSpaceFields(deepSpaces, deepPrices)

      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId, fields)
      lazy val spaceNotFound = mySpace.isEmpty || mySpace.get.place_id != placeId

      def read: Option[Seq[vo.Space]] = Some(placesDb.immediateSpacesByParentId(spaceId, fields).take(limit.getOrElse(Int.MaxValue)))

      val (code, spaces) =
        if (spaceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposeSpaces(spaces, myPlace, profile))

    case SearchSpacesIN(placeId, byAttributes, joinOR, profile, deepSpaces, deepPrices) =>
      val fields = customSpaceFields(deepSpaces, deepPrices)
      val privileged =
        byAttributes.nonEmpty && !byAttributes.exists { case (_, value) => value.endsWith("*") && value.size < 3+1 }

      lazy val myPlace = placesDb.placeById(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canRead = profile.isSuper || privileged

      def read: Option[Seq[vo.Space]] = Some(placesDb.searchSpaces(placeId, byAttributes, joinOR, fields))

      val (code, spaces) =
        if (placeNotFound) (ApiCode(SC_NOT_FOUND), None)
        else if (canRead) (ApiCode.OK, read)
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeSpaces(spaces, myPlace, profile))

  }

}

trait GetPrice {
  self: PlacesActor =>
  import PlacesActor._

  val getPriceReceive: Actor.Receive = {

    case GetPriceIN(placeId, spaceId, priceId, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId, customSpaceFields(deep_spaces = false, deep_prices = true))
      lazy val myPrice = placesDb.priceById(priceId)
      lazy val priceNotFound = myPrice.isEmpty || mySpace.isEmpty || mySpace.get.place_id != placeId || !mySpace.get.hasPriceId(priceId)

      val (code, price) =
        if (priceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, myPrice)

      reply ! CodeEntityOUT(code, exposePrice(price, myPlace, profile))

    case GetPricesIN(placeId, spaceId, effective, profile) =>
      lazy val myPlace = placesDb.placeById(placeId)
      lazy val mySpace = placesDb.spaceById(spaceId, customSpaceFields(deep_spaces = false, deep_prices = false))
      lazy val spaceNotFound = mySpace.isEmpty || mySpace.get.place_id != placeId

      def read: Option[Seq[vo.Price]] =
        Some(if (effective.isDefined) placesDb.effectivePricesBySpaceId(spaceId) else placesDb.pricesBySpaceId(spaceId))

      val (code, prices) =
        if (spaceNotFound) (ApiCode(SC_NOT_FOUND), None)
        else (ApiCode.OK, read)

      reply ! CodeEntityOUT(code, exposePrices(prices, myPlace, profile))

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
      .map { p => exposeSpaces(p.spaces, Some(p), profile); p }
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def expose(obj: Option[vo.Place], profile: ProfileRemote): Option[vo.Place] =
    obj.map(expose(_, profile))

  def exposeSeq(obj: Option[Seq[vo.Place]], profile: ProfileRemote): Option[Seq[vo.Place]] =
    obj.map(_.map(expose(_, profile)).toList)

  def expose(obj: vo.Space, place: vo.Place, profile: ProfileRemote): vo.Space =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("space"), ap.defaultRead(profile, _ => placeModerator(place, profile))))
      .map { p => exposePrices(p.prices, Some(place), profile); p }
      .map { p => exposeSpaces(p.spaces, Some(place), profile); p }
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def exposeSpace(obj: Option[vo.Space], place: Option[vo.Place], profile: ProfileRemote): Option[vo.Space] =
    obj.map(expose(_, place.get, profile))

  def exposeSpaces(obj: Option[Seq[vo.Space]], place: Option[vo.Place], profile: ProfileRemote): Option[Seq[vo.Space]] =
    obj.map(_.map(expose(_, place.get, profile)).toList)

  def expose(obj: vo.Price, place: vo.Place, profile: ProfileRemote): vo.Price =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("price"), ap.defaultRead(profile, _ => placeModerator(place, profile))))
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty))
      .get

  def exposePrice(obj: Option[vo.Price], place: Option[vo.Place], profile: ProfileRemote): Option[vo.Price] =
    obj.map(expose(_, place.get, profile))

  def exposePrices(obj: Option[Seq[vo.Price]], place: Option[vo.Place], profile: ProfileRemote): Option[Seq[vo.Price]] =
    obj.map(_.map(expose(_, place.get, profile)).toList)

}

