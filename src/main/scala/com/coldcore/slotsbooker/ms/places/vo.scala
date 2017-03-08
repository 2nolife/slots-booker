package com.coldcore.slotsbooker
package ms.places.vo

import com.coldcore.slotsbooker.ms.vo.Attributes
import spray.json.{DefaultJsonProtocol, JsObject, RootJsonFormat}

case class Address(line1: Option[String], line2: Option[String], line3: Option[String],
                   postcode: Option[String], town: Option[String], country: Option[String])
object Address extends DefaultJsonProtocol { implicit val format = jsonFormat6(apply) }

case class Price(price_id: String, place_id: String, space_id: String, name: Option[String],
                 amount: Option[Int], currency: Option[String], roles: Option[Seq[String]])
object Price extends DefaultJsonProtocol {
  implicit val format = jsonFormat7(apply)

  def apply(priceId: String, placeId: String, spaceId: String): Price =
    Price(priceId, placeId, spaceId, None, None, None, None)
}

case class Space(space_id: String, place_id: String, parent_space_id: Option[String], name: Option[String],
                 spaces: Option[Seq[Space]], prices: Option[Seq[Price]], metadata: Option[JsObject])
object Space extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[Space] =
    rootFormat(lazyFormat(jsonFormat(Space.apply, "space_id", "place_id", "parent_space_id", "name", "spaces", "prices", "metadata")))

  def apply(spaceId: String, placeId: String): Space =
    Space(spaceId, placeId, None, None, None, None, None)
}

case class Place(place_id: String, profile_id: String,
                 name: Option[String], url: Option[String], email: Option[String], address: Option[Address],
                 spaces: Option[Seq[Space]], moderators: Option[Seq[String]], attributes: Option[Attributes])
object Place extends DefaultJsonProtocol {
  implicit val placeFormat: RootJsonFormat[Place] =
    jsonFormat(Place.apply, "place_id", "profile_id", "name", "url", "email", "address", "spaces", "moderators", "attributes")
}

case class CreatePlace(name: String)
object CreatePlace extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }

case class UpdatePlace(name: Option[String], address: Option[Address],
                       moderators: Option[Seq[String]], attributes: Option[Attributes])
object UpdatePlace extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }


case class CreateSpace(name: String)
object CreateSpace extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }

case class UpdateSpace(name: Option[String], metadata: Option[JsObject])
object UpdateSpace extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }


case class CreatePrice(name: String, amount: Int, currency: String)
object CreatePrice extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class UpdatePrice(name: Option[String], amount: Option[Int], currency: Option[String], roles: Option[Seq[String]])
object UpdatePrice extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }


object Implicits {

  implicit class PlaceExt(obj: Place) {

    def isModerator(profileId: String): Boolean =
      obj.moderators.getOrElse(Nil).exists(profileId==) || obj.profile_id == profileId

    def flatSpaces: Seq[Space] =
      obj.spaces.getOrElse(Nil).flatMap(_.flatSpaces)

    def flatPrices: Seq[Price] =
      flatSpaces.flatMap(_.prices).flatten

    def hasSpaceId(spaceId: String): Boolean =
      flatSpaces.exists(_.space_id == spaceId)

    def hasPriceId(priceId: String): Boolean =
      flatPrices.exists(_.price_id == priceId)
  }

  implicit class SpaceExt(obj: Space) {

    private def flatSpaces(space: Space): Seq[Space] =
      space +: space.spaces.getOrElse(Nil).flatMap(flatSpaces(_))

    def flatSpaces: Seq[Space] =
      flatSpaces(obj)

    def flatPrices: Seq[Price] =
      flatSpaces.flatMap(_.prices).flatten

    def hasSpaceId(spaceId: String): Boolean =
      flatSpaces.exists(_.space_id == spaceId)

    def hasPriceId(priceId: String): Boolean =
      flatPrices.exists(_.price_id == priceId)
  }
}
