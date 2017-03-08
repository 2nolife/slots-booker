package com.coldcore.slotsbooker
package ms.db

import java.util.regex.Pattern

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import org.bson.types.ObjectId
import spray.json._

trait MongoQueries {

  def update(finder: DBObject, collection: MongoCollection, key: String, value: Option[Any]): Unit =
    value.foreach { v => update(finder, collection, $set(key -> v)) }

  def update(finder: DBObject, collection: MongoCollection, key: String, value: Any): Unit =
    update(finder, collection, $set(key -> value))

  def update(finder: DBObject, collection: MongoCollection, elements: DBObject): Unit =
    collection.findAndModify(finder, elements)

  def asDBObject[A](a: A)(implicit writer: JsonWriter[A]): DBObject =
    JSON.parse(a.toJson.toString).asInstanceOf[DBObject]

  def asDBObject(a: JsObject): DBObject =
    JSON.parse(a.toString).asInstanceOf[DBObject]

  implicit class DBOpbectExt(obj: DBObject) {
    def idString: String = obj.getAs[ObjectId]("_id").get.toString
  }

  def addToArray(finder: DBObject, collection: MongoCollection, arrayName: String, value: String) =
    collection
      .findAndModify(finder, $addToSet(arrayName -> value))

  def removeFromArray(finder: DBObject, collection: MongoCollection, arrayName: String, value: String) =
    collection
      .findAndModify(finder, $pull(arrayName -> value))

  def mergeJsObject(finder: DBObject, collection: MongoCollection, propertyName: String, value: JsObject) {
    val existing =
      collection
        .findOne(finder)
        .flatMap(_.getAs[AnyRef](propertyName).map(_.toString.parseJson.asJsObject))
        .getOrElse(JsObject())

    val delete = value.fields.collect { case (name, JsString("")) => name }.toSeq
    val nvalue = JsObject((existing.fields ++ value.fields).filter { case (name, _) => !delete.contains(name) })

    collection
      .findAndModify(finder, $set(propertyName -> asDBObject(nvalue)))
  }

  def notDeleted: DBObject =
    "deleted" $ne true

  def finder(): DBObject =
    notDeleted

  def finder(finder: DBObject): DBObject =
    finder ++ notDeleted

  def finderById(id: String): DBObject =
    finder("_id" $eq new ObjectId(id))

  def finderByAttributes(byAttributes: Seq[(String,String)], joinOR: Boolean): DBObject = {
    val byAttributesCriteria = byAttributes.map {
      case (name, value) if value.endsWith("*") => "attributes."+name $eq (Pattern.quote(value.dropRight(1))+".*").r
      case (name, value) => "attributes."+name $eq value
    }
    val byAttributesQuery =
      if (byAttributesCriteria.isEmpty) MongoDBObject()
      else if (joinOR) $or(byAttributesCriteria: _*) else $and(byAttributesCriteria: _*)
    byAttributesQuery ++ notDeleted
  }

}
