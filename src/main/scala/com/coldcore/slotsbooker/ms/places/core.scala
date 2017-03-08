package com.coldcore.slotsbooker
package ms.places

import akka.actor.ActorSystem
import ms._
import actors.PlacesActor
import akka.routing.FromConfig
import db.MongoPlacesDb
import rest.PlacesRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val placesDb = new MongoPlacesDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val placesActor = system.actorOf(PlacesActor.props(placesDb, config.voAttributes).withRouter(FromConfig), name = s"$MS-actor")

    new PlacesRestService(config.hostname, config.port, config.getDeepFields, placesActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-places"
}

object Constants extends Constants
