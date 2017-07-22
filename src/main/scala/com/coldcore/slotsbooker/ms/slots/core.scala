package com.coldcore.slotsbooker
package ms.slots

import akka.actor.ActorSystem
import actors.SlotsActor
import akka.routing.FromConfig
import ms._
import db.MongoSlotsDb
import rest.SlotsRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val slotsDb = new MongoSlotsDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val slotsActor = system.actorOf(SlotsActor.props(slotsDb, config.placesBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = MS)

    new SlotsRestService(config.hostname, config.port, config.anonymousReads, config.systemToken, config.getDeepFields, slotsActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-slots"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'dummy -> -1
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)
}

object Constants extends Constants
