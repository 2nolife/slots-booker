package com.coldcore.slotsbooker
package ms.paypal

import akka.actor.ActorSystem
import ms._
import actors.EventsActor
import akka.routing.FromConfig
import db.MongoPaypalDb
import rest.PaypalRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val paypalDb = new MongoPaypalDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val eventsActor = system.actorOf(EventsActor.props(paypalDb, config.placesBaseUrl, config.paymentsBaseUrl, config.systemToken, restClient, config.sandboxMode, config.liveEventIp).withRouter(FromConfig), name = s"$MS-events")

    new PaypalRestService(config.hostname, config.port, eventsActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-paypal"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'dummy -> 1
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)

  val eventStatus = Map('new -> 0, 'complete -> 1, 'duplicate -> 2, 'failed -> 3)
}

object Constants extends Constants
