package com.coldcore.slotsbooker
package ms.payments.db

import ms.Timestamp
import ms.db.MongoQueries
import ms.payments.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import ms.payments.vo
import spray.json._

trait PaymentsDb extends BalanceCRUD

trait BalanceCRUD {
  def getBalance(placeId: String, profileId: String): vo.Balance
  def addCredit(placeId: String, profileId: String, obj: vo.UpdateCredit): vo.Balance
}

class MongoPaymentsDb(client: MongoClient, dbName: String) extends PaymentsDb with VoFactory with MongoQueries
  with BalanceCrudImpl with TxCrudImpl {

  private val db = client(dbName)
  val balances = db(MS+"-balances")
  val tx = db(MS+"-tx")

}

trait VoFactory {
  self: MongoPaymentsDb =>

  def asBalance(data: MongoDBObject): vo.Balance = {
    import data._
    vo.Balance(
      profile_id = as[String]("profile_id"),
      place_id = as[String]("place_id"),
      credit =
        getAs[Seq[DBObject]]("credit")
          .map(_.map(asCredit(_)))
          .noneIfEmpty
    )
  }

  def asCredit(data: MongoDBObject): vo.Credit = {
    import data._
    vo.Credit(
      amount = getAs[Int]("amount"),
      currency = getAs[String]("currency")
    )
  }

  def asMongoObject(credit: vo.Credit): MongoDBObject = {
    import credit._
    MongoDBObject(
      "amount" -> amount.get,
      "currency" -> currency.get
    )
  }

}

trait BalanceCrudImpl {
  self: MongoPaymentsDb =>

  override def getBalance(placeId: String, profileId: String): vo.Balance =
    balances
      .findOne(("place_id" $eq placeId) ++ ("profile_id" $eq profileId))
      .map(asBalance(_))
      .getOrElse(vo.Balance(profileId, placeId, credit = None))

  private def ensureCreditRecord(placeId: String, profileId: String, currency: String): String = {
    val recordFinder = ("place_id" $eq placeId) ++ ("profile_id" $eq profileId)
    balances
      .update(
        recordFinder,
        $setOnInsert(
          "place_id" -> placeId,
          "profile_id" -> profileId,
          "entry.created" -> Timestamp.asLong),
        upsert = true)

    balances
      .findOne(recordFinder ++ ("credit.currency" $eq currency))
      .orElse {
        balances
          .findAndModify(
            recordFinder,
            $addToSet("credit" -> MongoDBObject(
              "amount" -> 0,
              "currency" -> currency
            )))
      }

    balances
      .findOne(recordFinder)
      .map(_.idString)
      .get
  }

  override def addCredit(placeId: String, profileId: String, obj: vo.UpdateCredit): vo.Balance = {
    import obj._
    val balanceId = ensureCreditRecord(placeId, profileId, currency)

    val credit =
      balances
        .findOne(finderById(balanceId))
        .map(asBalance(_))
        .flatMap(_.credit)
        .get

    val oc =
      credit.find(_.currency.get == currency).get

    val nc =
      credit.find(_.currency.get == currency).map(c => c.copy(amount = Some(c.amount.get+amount))).get

    txCreditChange(profileId, placeId, oc, nc,
      JsObject("type" -> JsString("add"), "amount" -> JsNumber(amount)),
      source)

    val ncredit =
      credit.filter(_.currency.get != currency) :+ nc

    balances
      .update(finderById(balanceId), $set("credit" -> ncredit.map(asMongoObject)))

    entryUpdated(balanceId, balances)
    
    balances
      .findOne(finderById(balanceId))
      .map(asBalance(_))
      .get
  }
}

trait TxCrudImpl {
  self: MongoPaymentsDb =>

  def txCreditChange(profileId: String, placeId: String, oldCredit: vo.Credit, newCredit: vo.Credit, change: JsObject, source: JsObject) {
    val transaction =
      MongoDBObject(
        "place_id" -> placeId,
        "profile_id" -> profileId,
        "old_credit" -> asMongoObject(oldCredit),
        "new_credit" -> asMongoObject(newCredit),
        "change" -> asDBObject(change),
        "source" -> asDBObject(source)
      )

    tx
      .insert(transaction)

    entryCreated(transaction.idString, tx)
  }

}