package com.coldcore.slotsbooker
package ms.places.db

import ms.db.MongoQueries
import ms.places.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import ms.places.vo
import ms.places.vo.Implicits._
import ms.places.{DateTimeUtil => du}
import PlacesDb._
import ms.vo.Attributes
import spray.json._

import scala.annotation.tailrec

object PlacesDb {

  trait PlaceFields {
    val deepSpaces: Boolean // TRUE to fetch Space array from DB (slow), FALSE to include just Space IDs (fast)
    val spaceFields: SpaceFields // applies to Space array
  }

  trait SpaceFields {
    val deepSpaces: Boolean // TRUE to fetch inner Space array from DB (slow), FALSE to include just Space IDs (fast)
    val deepPrices: Boolean // TRUE to fetch Price array from DB (slow), FALSE to include just Price IDs (fast)
  }

  case class DefaultPlaceFields(deepSpaces: Boolean = false,
                                spaceFields: SpaceFields = new DefaultSpaceFields) extends PlaceFields

  case class DefaultSpaceFields(deepSpaces: Boolean = false,
                                deepPrices: Boolean = false) extends SpaceFields

  def defaultPlaceFields = new DefaultPlaceFields
  def defaultSpaceFields = new DefaultSpaceFields

  def deepPlaceFields =
    defaultPlaceFields.copy(deepSpaces = true,
                            spaceFields = deepSpaceFields)

  def deepSpaceFields =
    defaultSpaceFields.copy(deepSpaces = true,
                            deepPrices = true)

  def customSpaceFields(deep_spaces: Boolean, deep_prices: Boolean): SpaceFields =
    defaultSpaceFields.copy(deepSpaces = deep_spaces,
                            deepPrices = deep_prices)

  def customPlaceFields(deep_spaces: Boolean, deep_prices: Boolean): PlaceFields =
    defaultPlaceFields.copy(deepSpaces = deep_spaces,
                            spaceFields = customSpaceFields(deep_spaces, deep_prices))
}

trait PlacesDb extends PlaceCRUD with SpaceCRUD with PriceCRUD with BoundsCRUD with PlaceSearch with SpaceSearch

trait PlaceCRUD {
  def placeById(placeId: String, fields: PlaceFields = defaultPlaceFields): Option[vo.Place]
  def placesByProfileId(profileId: String, fields: PlaceFields = defaultPlaceFields): Seq[vo.Place]
  def createPlace(profileId: String, obj: vo.CreatePlace, fields: PlaceFields = defaultPlaceFields): vo.Place
  def updatePlace(placeId: String, obj: vo.UpdatePlace, fields: PlaceFields = defaultPlaceFields): Option[vo.Place]
  def deletePlace(placeId: String): Boolean
}

trait SpaceCRUD {
  def spaceById(spaceId: String, fields: SpaceFields = defaultSpaceFields): Option[vo.Space]
  def immediateSpacesByPlaceId(placeId: String, fields: SpaceFields = defaultSpaceFields): Seq[vo.Space]
  def immediateSpacesByParentId(parentSpaceId: String, fields: SpaceFields = defaultSpaceFields): Seq[vo.Space]
  def createSpace(parentPlaceId: String, obj: vo.CreateSpace, fields: SpaceFields = defaultSpaceFields): vo.Space
  def createInnerSpace(parentSpaceId: String, obj: vo.CreateSpace, fields: SpaceFields = defaultSpaceFields): vo.Space
  def updateSpace(spaceId: String, obj: vo.UpdateSpace, fields: SpaceFields = defaultSpaceFields): Option[vo.Space]
  def deleteSpace(spaceId: String): Boolean
}

trait PriceCRUD {
  def priceById(priceId: String): Option[vo.Price]
  def pricesBySpaceId(spaceId: String): Seq[vo.Price]
  def effectivePricesBySpaceId(spaceId: String): Seq[vo.Price]
  def createPrice(parentSpaceId: String, obj: vo.CreatePrice): vo.Price
  def updatePrice(priceId: String, obj: vo.UpdatePrice): Option[vo.Price]
  def deletePrice(priceId: String): Boolean
}

trait BoundsCRUD {
  def effectiveBookBoundsBySpaceId(spaceId: String): Option[vo.Bounds]
  def effectiveCancelBoundsBySpaceId(spaceId: String): Option[vo.Bounds]
}

trait PlaceSearch {
  def searchPlaces(byAttributes: Seq[(String,String)], joinOR: Boolean, fields: PlaceFields = defaultPlaceFields): Seq[vo.Place]
}

trait SpaceSearch {
  def searchSpaces(placeId: String, byAttributes: Seq[(String,String)], joinOR: Boolean, fields: SpaceFields): Seq[vo.Space]
}

class MongoPlacesDb(client: MongoClient, dbName: String) extends PlacesDb with VoFactory with MongoQueries
  with PlaceCrudImpl with SpaceCrudImpl with PriceCrudImpl with BoundsCrudImpl with PlaceSearchImpl with SpaceSearchImpl {

  private val db = client(dbName)
  val places = db(MS)
  val spaces = db(MS+"-spaces")
  val prices = db(MS+"-prices")

}

trait VoFactory {
  self: MongoPlacesDb =>

  def asPlace(data: MongoDBObject, fields: PlaceFields): vo.Place = {
    import data._
    val placeId = as[ObjectId]("_id").toString

    vo.Place(
      place_id = placeId,
      profile_id = as[String]("profile_id"),
      name = getAs[String]("name"),
      address =
        getAs[DBObject]("address")
        .map(asAddress(_)),
      spaces = if (fields.deepSpaces) Some(immediateSpacesByPlaceId(placeId, fields.spaceFields)).noneIfEmpty else None,
      moderators =
        getAs[Seq[String]]("moderators")
        .noneIfEmpty,
      attributes =
        getAs[AnyRef]("attributes")
        .map(json => Attributes(json.toString)),
      datetime =
        getAs[DBObject]("datetime")
        .map(asDateTime(_))
        .map(du.datetime)
        .orElse(Some(du.datetime))
    )
  }

  def asDateTime(data: MongoDBObject): vo.DateTime = {
    import data._
    vo.DateTime(
      timezone = getAs[String]("timezone"),
      offset_minutes = getAs[Int]("offset_minutes")
    )
  }

  def asAddress(data: MongoDBObject): vo.Address = {
    import data._
    vo.Address(
      line1 = getAs[String]("line1"),
      line2 = getAs[String]("line2"),
      line3 = getAs[String]("line3"),
      postcode = getAs[String]("postcode"),
      town = getAs[String]("town"),
      country = getAs[String]("country"))
  }

  def asBounds(data: MongoDBObject): vo.Bounds = {
    import data._
    vo.Bounds(
      open =
        getAs[DBObject]("open")
        .map(asBound(_)),
      close =
        getAs[DBObject]("close")
        .map(asBound(_)))
  }

  def asBound(data: MongoDBObject): vo.Bound = {
    import data._
    vo.Bound(
      date = getAs[Int]("date"),
      time = getAs[Int]("time"),
      before = getAs[Int]("before"))
  }

  def asSpace(data: MongoDBObject, fields: SpaceFields): vo.Space = {
    import data._
    val placeId = as[String]("place_id")
    val spaceId = as[ObjectId]("_id").toString

    vo.Space(
      space_id = spaceId,
      place_id = placeId,
      parent_space_id = getAs[String]("parent_space_id"),
      name = getAs[String]("name"),
      spaces = if (fields.deepSpaces) Some(immediateSpacesByParentId(spaceId, fields)).noneIfEmpty else None,
      prices = if (fields.deepPrices) Some(pricesBySpaceId(spaceId)).noneIfEmpty else None,
      metadata =
        getAs[AnyRef]("metadata")
        .map(_.toString.parseJson.asJsObject),
      attributes =
        getAs[AnyRef]("attributes")
        .map(json => Attributes(json.toString)),
      book_bounds =
        getAs[DBObject]("book_bounds")
        .map(asBounds(_)),
      cancel_bounds =
        getAs[DBObject]("cancel_bounds")
        .map(asBounds(_))
    )
  }

  def asPrice(data: MongoDBObject): vo.Price = {
    import data._
    vo.Price(
      price_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      space_id = as[String]("space_id"),
      name = getAs[String]("name"),
      amount = getAs[Int]("amount"),
      currency = getAs[String]("currency"),
      member_level = getAs[Int]("member_level"),
      attributes =
        getAs[AnyRef]("attributes")
          .map(json => Attributes(json.toString))
    )
  }

  def asMongoObject(bound: vo.Bound): MongoDBObject = {
    import bound._
    val f = (key: String, value: Option[Int]) => value.map(v => MongoDBObject(key -> v)).getOrElse(MongoDBObject())
    f("date", date) ++ f("time", time) ++ f("before", before)
  }

  def asMongoObject(bounds: vo.Bounds): MongoDBObject = {
    import bounds._
    val f = (key: String, value: Option[vo.Bound]) => value.map(v => MongoDBObject(key -> asMongoObject(v))).getOrElse(MongoDBObject())
    f("open", open) ++ f("close", close)
  }

}

trait PlaceCrudImpl {
  self: MongoPlacesDb =>

  override def placeById(placeId: String, fields: PlaceFields): Option[vo.Place] =
    places
      .findOne(finderById(placeId))
      .map(asPlace(_, fields))

  override def placesByProfileId(profileId: String, fields: PlaceFields = defaultPlaceFields): Seq[vo.Place] =
    places
      .find(finder(
        $or(
          "profile_id" $eq profileId,
          "moderators" $eq profileId)))
      .map(asPlace(_, fields))
      .toSeq

  override def createPlace(profileId: String, obj: vo.CreatePlace, fields: PlaceFields): vo.Place = {
    import obj._
    val place = MongoDBObject(
      "profile_id" -> profileId,
      "name" -> name)

    places.
      insert(place)

    attributes.foreach(a => mergeJsObject(finderById(place.idString), places, "attributes", a.value))

    entryCreated(place.idString, places)

    placeById(place.idString, fields).get
  }

  override def updatePlace(placeId: String, obj: vo.UpdatePlace, fields: PlaceFields): Option[vo.Place] = {
    import obj._
    Map(
      "name" -> name,
      "address" -> address.map(asDBObject(_)),
      "moderators" -> moderators.map(MongoDBList(_: _*))
    ).foreach { case (key, value) =>
      update(finderById(placeId), places, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(placeId), places, "attributes", a.value))

    datetime.foreach { dt =>
      Map(
        "datetime.timezone" -> dt.timezone,
        "datetime.offset_minutes" -> dt.offset_minutes
      ).foreach { case (key, value) =>
        update(finderById(placeId), places, key, value)
      }
    }

    entryUpdated(placeId, places)

    placeById(placeId, fields)
  }

  override def deletePlace(placeId: String): Boolean =
    placeById(placeId, defaultPlaceFields).exists { _ =>
      softDelete("place_id" $eq placeId, prices)
      softDelete("place_id" $eq placeId, spaces)
      softDeleteOne(finderById(placeId), places)
    }

}

trait SpaceCrudImpl {
  self: MongoPlacesDb =>

  override def spaceById(spaceId: String, fields: SpaceFields): Option[vo.Space] =
    spaces
      .findOne(finderById(spaceId))
      .map(asSpace(_, fields))

  override def immediateSpacesByParentId(parentSpaceId: String, fields: SpaceFields): Seq[vo.Space] =
    spaces
      .find(finder("parent_space_id" $eq parentSpaceId))
      .map(asSpace(_, fields))
      .toSeq

  override def immediateSpacesByPlaceId(placeId: String, fields: SpaceFields): Seq[vo.Space] =
    spaces
      .find(finder() ++ ("place_id" $eq placeId) ++ ("parent_space_id" $exists false))
      .map(asSpace(_, fields))
      .toSeq

  override def createSpace(parentPlaceId: String, obj: vo.CreateSpace, fields: SpaceFields): vo.Space = {
    import obj._
    val space = MongoDBObject(
      "place_id" -> parentPlaceId,
      "name" -> name)

    spaces.
      insert(space)

    attributes.foreach(a => mergeJsObject(finderById(space.idString), spaces, "attributes", a.value))

    entryCreated(space.idString, spaces)

    spaceById(space.idString, fields).get
  }

  override def createInnerSpace(parentSpaceId: String, obj: vo.CreateSpace, fields: SpaceFields): vo.Space = {
    val parentSpace = spaceById(parentSpaceId, defaultSpaceFields).get

    import obj._
    val space = MongoDBObject(
      "place_id" -> parentSpace.place_id,
      "parent_space_id" -> parentSpaceId,
      "name" -> name)

    spaces.
      insert(space)

    attributes.foreach(a => mergeJsObject(finderById(space.idString), spaces, "attributes", a.value))

    entryCreated(space.idString, spaces)

    spaceById(space.idString, fields).get
  }

  override def updateSpace(spaceId: String, obj: vo.UpdateSpace, fields: SpaceFields): Option[vo.Space] = {
    import obj._
    Map(
      "name" -> name,
      "metadata" -> metadata.map(asDBObject),
      "book_bounds" -> book_bounds.map(asMongoObject),
      "cancel_bounds" -> cancel_bounds.map(asMongoObject)
    ).foreach { case (key, value) =>
      update(finderById(spaceId), spaces, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(spaceId), spaces, "attributes", a.value))

    entryUpdated(spaceId, spaces)

    spaceById(spaceId, fields)
  }

  override def deleteSpace(spaceId: String): Boolean = {
    val space = spaceById(spaceId, customSpaceFields(deep_spaces = true, deep_prices = false))

    // delete inner spaces
    space.foreach(_.flatSpaces.foreach { inner =>
      softDelete("space_id" $eq inner.space_id, prices)
      softDelete(finderById(inner.space_id), spaces)
    })

    softDeleteOne(finderById(spaceId), spaces)
  }

}

trait PriceCrudImpl {
  self: MongoPlacesDb =>

  override def priceById(priceId: String): Option[vo.Price] =
    prices
      .findOne(finderById(priceId))
      .map(asPrice(_))


  override def pricesBySpaceId(spaceId: String): Seq[vo.Price] =
    prices
      .find(finder("space_id" $eq spaceId))
      .map(asPrice(_))
      .toSeq

  override def effectivePricesBySpaceId(spaceId: String): Seq[vo.Price] = {
    @tailrec def f(id: String): Seq[vo.Price] = {
      spaceById(id, customSpaceFields(deep_spaces = false, deep_prices = true)).get match {
        case space if space.prices.isDefined => space.prices.get
        case space if space.parent_space_id.isEmpty => Seq.empty
        case space => f(space.parent_space_id.get)
      }
    }
    f(spaceId)
  }

  override def deletePrice(priceId: String) =
    softDeleteOne(finderById(priceId), prices)

  override def updatePrice(priceId: String, obj: vo.UpdatePrice): Option[vo.Price] = {
    import obj._
    Map(
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency,
      "member_level" -> member_level
    ).foreach { case (key, value) =>
      update(finderById(priceId), prices, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(priceId), prices, "attributes", a.value))

    entryUpdated(priceId, prices)

    priceById(priceId)
  }

  override def createPrice(parentSpaceId: String, obj: vo.CreatePrice): vo.Price = {
    val parentSpace = spaceById(parentSpaceId, defaultSpaceFields).get

    import obj._
    val price = MongoDBObject(
      "place_id" -> parentSpace.place_id,
      "space_id" -> parentSpace.space_id,
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency)

    prices.
      insert(price)

    Map(
      "member_level" -> member_level
    ).foreach { case (key, value) =>
      update(finderById(price.idString), prices, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(price.idString), prices, "attributes", a.value))

    entryCreated(price.idString, prices)

    priceById(price.idString).get
  }

}

trait BoundsCrudImpl {
  self: MongoPlacesDb =>

  @tailrec private def bounds(id: String, extract: vo.Space => Option[vo.Bounds]): Option[vo.Bounds] = {
    spaceById(id, defaultSpaceFields).get match {
      case space if extract(space).isDefined => extract(space)
      case space if space.parent_space_id.isEmpty => None
      case space => bounds(space.parent_space_id.get, extract)
    }
  }

  override def effectiveBookBoundsBySpaceId(spaceId: String): Option[vo.Bounds] =
    bounds(spaceId, space => space.book_bounds)

  override def effectiveCancelBoundsBySpaceId(spaceId: String): Option[vo.Bounds] =
    bounds(spaceId, space => space.cancel_bounds) orElse bounds(spaceId, space => space.book_bounds)

}

trait PlaceSearchImpl {
  self: MongoPlacesDb =>

  override def searchPlaces(byAttributes: Seq[(String,String)], joinOR: Boolean, fields: PlaceFields): Seq[vo.Place] =
    places
      .find(finderByAttributes(byAttributes, joinOR))
      .map(asPlace(_, fields))
      .toSeq

}

trait SpaceSearchImpl {
  self: MongoPlacesDb =>

  override def searchSpaces(placeId: String, byAttributes: Seq[(String,String)], joinOR: Boolean, fields: SpaceFields): Seq[vo.Space] =
    spaces
      .find(("place_id" $eq placeId) ++ finderByAttributes(byAttributes, joinOR))
      .map(asSpace(_, fields))
      .toSeq

}
