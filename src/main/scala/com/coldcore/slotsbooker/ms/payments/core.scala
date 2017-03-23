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

    val paymentsActor = system.actorOf(PaymentsActor.props(paymentsDb, config.placesBaseUrl, config.bookingBaseUrl, config.systemToken, restClient).withRouter(FromConfig), name = s"$MS-actor")
    val expiredActor = system.actorOf(ExpiredActor.props(paymentsDb, config.placesBaseUrl, config.bookingBaseUrl, config.systemToken, restClient).withRouter(FromConfig), name = s"$MS-expired-actor")

    new PaymentsRestService(config.hostname, config.port, config.getDeepFields, paymentsActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-payments"
}

object Constants extends Constants
