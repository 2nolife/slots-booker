package com.coldcore.slotsbooker
package ms.booking.db

import ms.db.MongoQueries
import ms.booking.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import ms.booking.vo
import ms.{Timestamp => ts}

trait BookingDb extends QuoteCRUD with RefundCRUD with ReferenceCRUD

trait QuoteCRUD {
  def quoteById(quoteId: String): Option[vo.Quote]
  def createQuote(obj: vo.Quote): vo.Quote
  def updateQuoteStatus(quoteId: String, status: Int): Boolean
}

trait RefundCRUD {
  def refundById(refundId: String): Option[vo.Refund]
  def createRefund(obj: vo.Refund): vo.Refund
  def updateRefundStatus(refundId: String, status: Int): Boolean
}

trait ReferenceCRUD {
  def referenceById(referenceId: String): Option[vo.Reference]
  def referenceByBookedId(bookedId: String, quote: Boolean = false, refund: Boolean = false): Option[vo.Reference]
  def referenceByRef(ref: String, profileId: String): Option[vo.Reference]
  def nextExpiredReference(minutes: Int): Option[vo.Reference]
  def referencePaid(ref: String, profileId: String): Option[vo.Reference]
  def createReference(obj: vo.Reference): vo.Reference
  def isRefUnique(ref: String, profileId: String): Boolean
}

class MongoBookingDb(client: MongoClient, dbName: String) extends BookingDb with VoFactory with MongoQueries
  with QuoteCrudImpl with RefundCrudImpl with ReferenceCrudImpl {

  private val db = client(dbName)
  val quotes = db(MS+"-quotes")
  val refunds = db(MS+"-refunds")
  val references = db(MS+"-references")
  val locks = db(MS+"-locks")

  val quoteStatus = Map('inactive -> 0, 'complete -> 1, 'pending_payment -> 2, 'not_paid -> 3)
  val refundStatus = Map('inactive -> 0, 'complete -> 1, 'pending_payment -> 2)
}

trait VoFactory {
  self: MongoBookingDb =>

  def asReference(data: MongoDBObject): vo.Reference = {
    import data._
    vo.Reference(
      reference_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      ref = getAs[String]("ref"),
      profile_id = getAs[String]("profile_id"),
      booked_ids =
        getAs[Seq[String]]("booked_ids")
          .noneIfEmpty,
      quote = getAs[String]("quote_id").flatMap(quoteById),
      refund = getAs[String]("refund_id").flatMap(refundById)
    )
  }

  def asQuote(data: MongoDBObject): vo.Quote = {
    import data._
    vo.Quote(
      quote_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      profile_id = getAs[String]("profile_id"),
      amount = getAs[Int]("amount"),
      currency = getAs[String]("currency"),
      status = getAs[Int]("status"),
      prices =
        getAs[Seq[DBObject]]("prices")
          .map(_.map(asSlotPrice(_)))
          .noneIfEmpty,
      deal = getAs[Boolean]("deal"),
      entry_updated = getAs[Long]("entry.updated")
    )
  }

  def asRefund(data: MongoDBObject): vo.Refund = {
    import data._
    vo.Refund(
      refund_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      profile_id = getAs[String]("profile_id"),
      amount = getAs[Int]("amount"),
      currency = getAs[String]("currency"),
      status = getAs[Int]("status"),
      prices =
        getAs[Seq[DBObject]]("prices")
          .map(_.map(asSlotPrice(_)))
          .noneIfEmpty,
      quotes =
        getAs[Seq[String]]("quote_ids")
          .map(_.flatMap(quoteById))
          .noneIfEmpty,
      entry_updated = getAs[Long]("entry.updated")
    )
  }

  def asSlotPrice(data: MongoDBObject): vo.SlotPrice = {
    import data._
    vo.SlotPrice(
      slot_id = as[String]("slot_id"),
      price_id = getAs[String]("price_id"),
      name = getAs[String]("name"),
      amount = getAs[Int]("amount"),
      currency = getAs[String]("currency"))
  }

  def asMongoObject(sp: vo.SlotPrice): MongoDBObject = {
    import sp._
    MongoDBObject(
      "slot_id" -> slot_id
    ) ++
    (if (sp.price_id.isEmpty) MongoDBObject()
    else MongoDBObject(
      "price_id" -> price_id,
      "name" -> name.get,
      "amount" -> amount.get,
      "currency" -> currency.get
    ))
  }

}

trait QuoteCrudImpl {
  self: MongoBookingDb =>

  override def quoteById(quoteId: String): Option[vo.Quote] =
    quotes
      .findOne(finderById(quoteId))
      .map(asQuote(_))

  override def updateQuoteStatus(quoteId: String, status: Int): Boolean = {
    val result =
      quotes
        .findAndModify(finderById(quoteId), $set("status" -> status))
        .isDefined

    entryUpdated(quoteId, quotes)

    result
  }

  override def createQuote(obj: vo.Quote): vo.Quote = {
    import obj._

    val quote = MongoDBObject(
      "place_id" -> place_id,
      "profile_id" -> profile_id.get,
      "status" -> status.get,
      "prices" -> prices.getOrElse(Nil).map(asMongoObject))

    quotes
      .insert(quote)

    val quoteId = quote.idString

    Map(
      "amount" -> amount,
      "currency" -> currency,
      "deal" -> deal
    ).foreach { case (key, value) =>
      update(finderById(quoteId), quotes, key, value)
    }

    entryCreated(quoteId, quotes)

    quoteById(quoteId).get
  }
}

trait RefundCrudImpl {
  self: MongoBookingDb =>

  override def refundById(refundId: String): Option[vo.Refund] =
    refunds
      .findOne(finderById(refundId))
      .map(asRefund(_))

  override def updateRefundStatus(refundId: String, status: Int): Boolean = {
    val result =
      refunds
        .findAndModify(finderById(refundId), $set("status" -> status))
        .isDefined

    entryUpdated(refundId, refunds)

    result
  }

  override def createRefund(obj: vo.Refund): vo.Refund = {
    import obj._

    val refund = MongoDBObject(
      "place_id" -> place_id,
      "profile_id" -> profile_id.get,
      "status" -> status.get,
      "prices" -> prices.getOrElse(Nil).map(asMongoObject),
      "quote_ids" -> obj.quotes.getOrElse(Nil).map(_.quote_id))

    refunds
      .insert(refund)

    val refundId = refund.idString

    Map(
      "amount" -> amount,
      "currency" -> currency
    ).foreach { case (key, value) =>
      update(finderById(refundId), refunds, key, value)
    }

    entryCreated(refundId, refunds)

    refundById(refundId).get
  }
}

trait ReferenceCrudImpl {
  self: MongoBookingDb =>

  override def referenceById(referenceId: String): Option[vo.Reference] =
    references
      .findOne(finderById(referenceId))
      .map(asReference(_))

  override def referenceByBookedId(bookedId: String, quote: Boolean, refund: Boolean): Option[vo.Reference] =
    references
      .findOne(("booked_ids" $eq bookedId) ++ ("quote_id" $exists quote) ++ ("refund_id" $exists refund))
      .map(asReference(_))

  override def createReference(obj: vo.Reference): vo.Reference = {
    import obj._

    val reference = MongoDBObject(
      "place_id" -> place_id,
      "profile_id" -> profile_id.get,
      "booked_ids" -> booked_ids.map(MongoDBList(_: _*)),
      "ref" -> ref.get)

    references
      .insert(reference)

    val referenceId = reference.idString

    Map(
      "quote_id" -> quote.map(_.quote_id),
      "refund_id" -> refund.map(_.refund_id)
    ).foreach { case (key, value) =>
      update(finderById(referenceId), references, key, value)
    }

    entryCreated(referenceId, references)

    referenceById(referenceId).get
  }

  override def isRefUnique(ref: String, profileId: String): Boolean =
    references
      .findOne(("ref" $eq ref) ++ ("profile_id" $eq profileId))
      .isDefined

  override def referenceByRef(ref: String, profileId: String): Option[vo.Reference] =
    references
      .findOne(("ref" $eq ref) ++ ("profile_id" $eq profileId))
      .map(asReference(_))

  override def referencePaid(ref: String, profileId: String): Option[vo.Reference] =
    referenceByRef(ref, profileId).map { reference =>
      reference.quote.foreach { q =>
        updateQuoteStatus(q.quote_id, quoteStatus('complete))
      }
      reference.refund.foreach { r =>
        updateRefundStatus(r.refund_id, refundStatus('complete))
      }
      referenceById(reference.reference_id).get
    }

  override def nextExpiredReference(minutes: Int): Option[vo.Reference] = 
    acquireEntryWithLock("status" $eq quoteStatus('pending_payment), quotes, locks, minutes)
      .flatMap { id =>
        val refunded =
          refunds // check if already refunded
            .findOne(("quote_ids" $eq id) ++ ("status" $ne refundStatus('inactive)))
            .isDefined

        if (refunded)
          quotes // mask as expired to eliminate from future searches
            .findAndModify(finderById(id), $set("status" -> quoteStatus('not_paid)))

        if (refunded) None
        else
          references
            .findOne("quote_id" $eq id)
            .map(asReference(_))
      }

}
