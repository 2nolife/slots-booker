package com.coldcore.slotsbooker
package ms.slots

import akka.actor.ActorSystem
import actors.SlotsActor
import akka.routing.FromConfig
import ms.{CreateAuthActors, CreateMongoClient, CreateRestClient, StartSingle}
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

    val slotsActor = system.actorOf(SlotsActor.props(slotsDb, config.placesBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = s"$MS-actor")

    new SlotsRestService(config.hostname, config.port, config.systemToken, config.getDeepFields, slotsActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-slots"
}

object Constants extends Constants
