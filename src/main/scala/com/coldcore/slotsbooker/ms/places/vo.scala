package com.coldcore.slotsbooker
package ms.places.vo

import ms.vo.Attributes
import spray.json.{DefaultJsonProtocol, JsObject, RootJsonFormat}

case class Address(line1: Option[String], line2: Option[String], line3: Option[String],
                   postcode: Option[String], town: Option[String], country: Option[String])
object Address extends DefaultJsonProtocol { implicit val format = jsonFormat6(apply) }

case class Price(price_id: String, place_id: String, space_id: String, name: Option[String],
                 amount: Option[Int], currency: Option[String], attributes: Option[Attributes], member_level: Option[Int])
object Price extends DefaultJsonProtocol { implicit val format = jsonFormat8(apply) }

case class Space(space_id: String, place_id: String, parent_space_id: Option[String], name: Option[String],
                 spaces: Option[Seq[Space]], prices: Option[Seq[Price]], metadata: Option[JsObject], attributes: Option[Attributes])
object Space extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[Space] =
    rootFormat(lazyFormat(jsonFormat(apply, "space_id", "place_id", "parent_space_id", "name", "spaces", "prices", "metadata", "attributes")))
}

case class DateTime(timezone: Option[String], offset_minutes: Option[Int],
                    date: Option[Int], time: Option[Int], utc_date: Option[Int], utc_time: Option[Int])
object DateTime extends DefaultJsonProtocol {
  implicit val format = jsonFormat6(apply)

  def apply(timezone: Option[String], offset_minutes: Option[Int]): DateTime =
    DateTime(timezone, offset_minutes, None, None, None, None)
}

case class Place(place_id: String, profile_id: String,
                 name: Option[String], address: Option[Address],
                 spaces: Option[Seq[Space]], moderators: Option[Seq[String]], attributes: Option[Attributes],
                 datetime: Option[DateTime])
object Place extends DefaultJsonProtocol {
  implicit val placeFormat: RootJsonFormat[Place] =
    jsonFormat(apply, "place_id", "profile_id", "name", "address", "spaces", "moderators", "attributes", "datetime")
}

case class CreatePlace(name: String, attributes: Option[Attributes])
object CreatePlace extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class UpdatePlace(name: Option[String], address: Option[Address],
                       moderators: Option[Seq[String]], attributes: Option[Attributes],
                       datetime: Option[DateTime])
object UpdatePlace extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }


case class CreateSpace(name: String, attributes: Option[Attributes])
object CreateSpace extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class UpdateSpace(name: Option[String], metadata: Option[JsObject], attributes: Option[Attributes])
object UpdateSpace extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }


case class CreatePrice(name: String, amount: Int, currency: String,
                       attributes: Option[Attributes], member_level: Option[Int])
object CreatePrice extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

case class UpdatePrice(name: Option[String], amount: Option[Int], currency: Option[String],
                       attributes: Option[Attributes], member_level: Option[Int])
object UpdatePrice extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }


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
