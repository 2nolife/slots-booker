package com.coldcore.slotsbooker
package ms.places.db

import ms.db.MongoQueries
import ms.places.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import ms.places.vo
import spray.json._
import ms.places.vo.Implicits._
import PlacesDb._
import ms.vo.Attributes

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

trait PlacesDb extends PlaceCRUD with SpaceCRUD with PriceCRUD with PlaceSearch

trait PlaceCRUD {
  def placeById(placeId: String, fields: PlaceFields = defaultPlaceFields): Option[vo.Place]
  def createPlace(profileId: String, obj: vo.CreatePlace, fields: PlaceFields = defaultPlaceFields): vo.Place
  def updatePlace(placeId: String, obj: vo.UpdatePlace, fields: PlaceFields = defaultPlaceFields): Option[vo.Place]
  def deletePlace(placeId: String)
}

trait SpaceCRUD {
  def spaceById(spaceId: String, fields: SpaceFields = defaultSpaceFields): Option[vo.Space]
  def createSpace(parentPlaceId: String, obj: vo.CreateSpace, fields: SpaceFields = defaultSpaceFields): vo.Space
  def createInnerSpace(parentSpaceId: String, obj: vo.CreateSpace, fields: SpaceFields = defaultSpaceFields): vo.Space
  def updateSpace(spaceId: String, obj: vo.UpdateSpace, fields: SpaceFields = defaultSpaceFields): Option[vo.Space]
  def deleteSpace(spaceId: String)
}

trait PriceCRUD {
  def priceById(priceId: String): Option[vo.Price]
  def createPrice(parentSpaceId: String, obj: vo.CreatePrice): vo.Price
  def updatePrice(priceId: String, obj: vo.UpdatePrice): Option[vo.Price]
  def deletePrice(priceId: String)
}

trait PlaceSearch {
  def searchPlaces(byAttributes: Seq[(String,String)], joinOR: Boolean, fields: PlaceFields = defaultPlaceFields): Seq[vo.Place]
}

class MongoPlacesDb(client: MongoClient, dbName: String) extends PlacesDb with VoFactory with MongoQueries
  with PlaceCrudImpl with SpaceCrudImpl with PriceCrudImpl with PlaceSearchImpl {

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

    def fakeSpace(spaceId: String): Option[vo.Space] =
      Some(vo.Space(spaceId, placeId))

    vo.Place(
      place_id = placeId,
      profile_id = as[String]("profile_id"),
      name = getAs[String]("name"),
      url = getAs[String]("url"),
      email = getAs[String]("email"),
      address =
        getAs[DBObject]("address")
        .map(asAddress(_)),
      spaces =
        getAs[Seq[String]]("spaces")
        .map(_.flatMap(id => if (fields.deepSpaces) spaceById(id, fields.spaceFields) else fakeSpace(id)))
        .noneIfEmpty,
      moderators =
        getAs[Seq[String]]("moderators")
        .noneIfEmpty,
      attributes =
        getAs[AnyRef]("attributes")
        .map(json => Attributes(json.toString))
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

  def asSpace(data: MongoDBObject, fields: SpaceFields): vo.Space = {
    import data._
    val placeId = as[String]("place_id")
    val spaceId = as[ObjectId]("_id").toString

    def fakeSpace(spaceId: String): Option[vo.Space] =
      Some(vo.Space(spaceId, placeId))

    def fakePrice(priceId: String): Option[vo.Price] =
      Some(vo.Price(priceId, placeId, spaceId))

    vo.Space(
      space_id = spaceId,
      place_id = placeId,
      parent_space_id = getAs[String]("parent_space_id"),
      name = getAs[String]("name"),
      spaces =
        getAs[Seq[String]]("spaces")
        .map(_.flatMap(id => if (fields.deepSpaces) spaceById(id, fields) else fakeSpace(id)))
        .noneIfEmpty,
      prices =
        getAs[Seq[String]]("prices")
        .map(_.flatMap(id => if (fields.deepPrices) priceById(id) else fakePrice(id)))
        .noneIfEmpty,
      metadata =
        getAs[AnyRef]("metadata")
        .map(_.toString.parseJson.asJsObject)
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
      roles = getAs[Seq[String]]("roles").noneIfEmpty
    )
  }

}

trait PlaceCrudImpl {
  self: MongoPlacesDb =>

  override def placeById(placeId: String, fields: PlaceFields): Option[vo.Place] =
    places
      .findOne(finderById(placeId))
      .map(asPlace(_, fields))

  override def createPlace(profileId: String, obj: vo.CreatePlace, fields: PlaceFields): vo.Place = {
    import obj._
    val place = MongoDBObject(
      "profile_id" -> profileId,
      "name" -> name)

    places.
      insert(place)

    val placeId = place.idString
    placeById(placeId, fields).get
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

    placeById(placeId, fields)
  }

  override def deletePlace(placeId: String) =
    placeById(placeId, defaultPlaceFields).foreach { _ =>
      prices
        .remove("place_id" $eq placeId)

      spaces
        .remove("place_id" $eq placeId)

      places
        .findAndRemove(finderById(placeId))
    }

}

trait SpaceCrudImpl {
  self: MongoPlacesDb =>

  override def spaceById(spaceId: String, fields: SpaceFields): Option[vo.Space] =
    spaces
      .findOne(finderById(spaceId))
      .map(asSpace(_, fields))

  override def createSpace(parentPlaceId: String, obj: vo.CreateSpace, fields: SpaceFields): vo.Space = {
    import obj._
    val space = MongoDBObject(
      "place_id" -> parentPlaceId,
      "name" -> name)

    spaces.
      insert(space)

    addToArray(finderById(parentPlaceId), places, "spaces", space.idString)

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

    addToArray(finderById(parentSpaceId), spaces, "spaces", space.idString)

    spaceById(space.idString, fields).get
  }

  override def updateSpace(spaceId: String, obj: vo.UpdateSpace, fields: SpaceFields): Option[vo.Space] = {
    import obj._
    Map(
      "name" -> name,
      "metadata" -> metadata.map(asDBObject)
    ).foreach { case (key, value) =>
      update(finderById(spaceId), spaces, key, value)
    }

    spaceById(spaceId, fields)
  }

  override def deleteSpace(spaceId: String) {
    val space = spaceById(spaceId, defaultSpaceFields)

    // delete inner spaces
    space.foreach(
      _.flatSpaces.foreach { inner =>
        spaces
          .findAndRemove(finderById(inner.space_id))
    })

    // delete the space from its parent Place / Space array
    space.map(_.place_id).foreach(placeId => removeFromArray(finderById(placeId), places, "spaces", spaceId))
    space.flatMap(_.parent_space_id).foreach(parentSpaceId => removeFromArray(finderById(parentSpaceId), spaces, "spaces", spaceId))

    spaces
      .findAndRemove(finderById(spaceId))

  }

}

trait PriceCrudImpl {
  self: MongoPlacesDb =>

  override def priceById(priceId: String): Option[vo.Price] =
    prices
      .findOne(finderById(priceId))
      .map(asPrice(_))


  override def deletePrice(priceId: String): Unit = {
    val price = priceById(priceId)

    price.map(_.space_id).foreach(spaceId => removeFromArray(finderById(spaceId), spaces, "prices", priceId))

    prices
      .findAndRemove(finderById(priceId))
  }

  override def updatePrice(priceId: String, obj: vo.UpdatePrice): Option[vo.Price] = {
    import obj._
    Map(
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency,
      "roles" -> roles.map(MongoDBList(_: _*))
    ).foreach { case (key, value) =>
      update(finderById(priceId), prices, key, value)
    }

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

    addToArray(finderById(parentSpaceId), spaces, "prices", price.idString)

    priceById(price.idString).get
  }

}

trait PlaceSearchImpl {
  self: MongoPlacesDb =>

  override def searchPlaces(byAttributes: Seq[(String,String)], joinOR: Boolean, fields: PlaceFields): Seq[vo.Place] =
    places
      .find(finderByAttributes(byAttributes, joinOR))
      .map(asPlace(_, fields))
      .toSeq

}
