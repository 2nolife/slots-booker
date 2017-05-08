package com.coldcore.slotsbooker
package ms.payments

import akka.actor.ActorSystem
import ms._
import actors.{ExpiredActor, PaymentsActor}
import akka.routing.FromConfig
import db.MongoPaymentsDb
import rest.PaymentsRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val paymentsDb = new MongoPaymentsDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val paymentsActor = system.actorOf(PaymentsActor.props(paymentsDb, config.placesBaseUrl, config.bookingBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = MS)
    val expiredActor = system.actorOf(ExpiredActor.props(paymentsDb, config.placesBaseUrl, config.bookingBaseUrl, config.systemToken, restClient).withRouter(FromConfig), name = s"$MS-expired")

    new PaymentsRestService(config.hostname, config.port, paymentsActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-payments"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'not_enough_credit -> 1,
    'invalid_quote_status -> 2,
    'invalid_refund_status -> 3,
    'reason_missing -> 4
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)
}

object Constants extends Constants
