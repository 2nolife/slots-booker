package com.coldcore.slotsbooker
package ms.db

import java.util.regex.Pattern

import ms.{Timestamp => ts}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import org.bson.types.ObjectId
import spray.json._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Random

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

  def mergeJsObject(existing: JsObject, value: JsObject): JsObject = {
    val delete = value.fields.collect { case (name, JsString("")) => name }.toSeq
    JsObject((existing.fields ++ value.fields).filter { case (name, _) => !delete.contains(name) })
  }

  def mergeJsObject(finder: DBObject, collection: MongoCollection, propertyName: String, value: JsObject) {
    val existing =
      collection
        .findOne(finder)
        .flatMap(_.getAs[AnyRef](propertyName).map(_.toString.parseJson.asJsObject))
        .getOrElse(JsObject())

    val nvalue = mergeJsObject(existing, value)

    collection
      .findAndModify(finder, $set(propertyName -> asDBObject(nvalue)))
  }

  def softDelete(finder: DBObject, collection: MongoCollection): Unit =
    collection
      .update(finder ++ notDeleted, $set("deleted" -> true, "entry.updated" -> ts.asLong), multi = true)

  def softDeleteOne(finder: DBObject, collection: MongoCollection): Boolean =
    collection
      .findAndModify(finder ++ notDeleted, $set("deleted" -> true, "entry.updated" -> ts.asLong))
      .isDefined

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

  def entryCreated(id: String, collection: MongoCollection) {
    collection
      .update(finderById(id), $set("entry.created" -> ts.asLong))
    entryUpdated(id, collection)
  }

  def entryUpdated(id: String, collection: MongoCollection) =
    collection
      .update(finderById(id), $set("entry.updated" -> ts.asLong))

  private val random = new Random
  private def timeout(decMinutes: Int): Long = ts.asLong(ts.addMinutes(ts.asCalendar, -decMinutes))

  private def lockEntry(id: String, collection: MongoCollection, locks: MongoCollection): Boolean = {
    val lockRandom = Iterator.continually(random.nextInt(16).toHexString).take(40).mkString.toUpperCase
    val lockFinder = ("collection" $eq collection.name) ++ ("external_id" $eq id)

    collection
      .update(finderById(id), $set("entry.locked" -> ts.asLong))

    locks // if a process died and left the lock
      .remove(lockFinder ++ ("created" $lt timeout(1)))

    locks // create a new lock
      .update(
        lockFinder,
        $setOnInsert(
          "collection" -> collection.name,
          "external_id" -> id,
          "lock" -> lockRandom,
          "created" -> ts.asLong),
        upsert = true)

    locks // if more than one process inserted the lock simultaneously then leave the first one
      .find(lockFinder)
      .skip(1)
      .foreach(data => locks.remove(finderById(data.idString)))
    locks // if fail to leave just one then remove this lock
      .count(lockFinder) match {
      case n if n > 1 => locks.findAndRemove(lockFinder ++ ("lock" $eq lockRandom))
      case _ =>
    }

    locks // delete this lock if exists and see if the entry was acquired
      .findAndRemove(lockFinder ++ ("lock" $eq lockRandom))
      .isDefined
  }

  @tailrec final def acquireEntryWithLock(finder: DBObject, collection: MongoCollection, locks: MongoCollection, createdMinutesAgo: Int = 0): Option[String] = {
    val entryNotLocked =
      ("entry.created" $lt timeout(createdMinutesAgo)) ++
        $or(
          "entry.locked" $lt timeout(1),
          "entry.locked" $exists false)

    val entryId =
      collection
        .findOne(finder ++ entryNotLocked)
        .map(_.idString)

    if (entryId.isEmpty) None
    else if (lockEntry(entryId.get, collection, locks)) entryId
    else acquireEntryWithLock(finder, collection, locks, createdMinutesAgo)
  }

}
