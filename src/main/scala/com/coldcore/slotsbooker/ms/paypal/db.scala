package com.coldcore.slotsbooker
package ms.paypal.db

import ms.db.MongoQueries
import ms.paypal.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import ms.paypal.vo
import spray.json._

import scala.util.Random

trait PaypalDb extends EventCRUD

trait EventCRUD {
  def eventById(eventId: String): Option[vo.Event]
  def createEvent(source: JsObject, sourceInfo: vo.SourceInfo): vo.Event
  def updateEventStatus(eventId: String, status: Int, cause: Option[String] = None): Boolean
  def isDuplicateEvent(eventId: String, sourceId: String): Boolean
  def nextNewEvent: Option[vo.Event]
}

class MongoPaypalDb(client: MongoClient, dbName: String) extends PaypalDb with VoFactory with MongoQueries
  with EventCrudImpl {

  private val db = client(dbName)
  val events = db(MS+"-events")
  val locks = db(MS+"-locks")

}

trait VoFactory {
  self: MongoPaypalDb =>

  def asEvent(data: MongoDBObject): vo.Event = {
    import data._
    vo.Event(
      event_id = as[ObjectId]("_id").toString,
      status = getAs[Int]("status"),
      source =
        getAs[AnyRef]("source")
          .map(_.toString.parseJson.asJsObject),
      source_info =
        getAs[DBObject]("source_info")
          .map(asSourceInfo(_))

    )
  }

  def asSourceInfo(data: MongoDBObject): vo.SourceInfo = {
    import data._
    vo.SourceInfo(
      ip = getAs[String]("ip"),
      hostname = getAs[String]("hostname")
    )
  }

  def asMongoObject(sourceInfo: vo.SourceInfo): MongoDBObject = {
    import sourceInfo._
    MongoDBObject(
      "ip" -> ip.get,
      "hostname" -> hostname.get
    )
  }

}

trait EventCrudImpl {
  self: MongoPaypalDb =>

  override def eventById(eventId: String): Option[vo.Event] =
    events
      .findOne(finderById(eventId))
      .map(asEvent(_))

  override def createEvent(source: JsObject, sourceInfo: vo.SourceInfo): vo.Event = {
    val event =
      MongoDBObject(
        "status" -> eventStatus('new),
        "source" -> asDBObject(source),
        "source_info" -> asMongoObject(sourceInfo)
      )

    events
      .insert(event)

    entryCreated(event.idString, events)

    eventById(event.idString).get
  }

  override def updateEventStatus(eventId: String, status: Int, cause: Option[String]): Boolean = {
    val result =
      events
        .findAndModify(finderById(eventId), $set("status" -> status))
        .isDefined

    update(finderById(eventId), events, "cause", cause)
    
    entryUpdated(eventId, events)

    result
  }

  override def nextNewEvent: Option[vo.Event] =
    acquireEntryWithLock("status" $eq eventStatus('new), events, locks)
      .flatMap(id => eventById(id))

  override def isDuplicateEvent(eventId: String, sourceId: String): Boolean =
    events
      .findOne(
        ("_id" $ne new ObjectId(eventId)) ++ ("source.id" $eq sourceId) ++ // exclude itself
        $or( // either complete or new in progress (locked by a process)
          "status" $eq eventStatus('complete),
          ("status" $eq eventStatus('new)) ++ ("entry.locked" $exists true)
        ))
      .isDefined

}