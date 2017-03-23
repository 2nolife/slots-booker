package com.coldcore.slotsbooker
package ms.booking

import akka.actor.ActorSystem
import actors.BookingActor
import akka.routing.FromConfig
import db.MongoBookingDb
import ms._
import rest.BookingRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val bookingDb = new MongoBookingDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val bookingActor = system.actorOf(BookingActor.props(bookingDb, config.placesBaseUrl, config.slotsBaseUrl, config.systemToken, restClient).withRouter(FromConfig), name = s"$MS-actor")

    new BookingRestService(config.hostname, config.port, config.systemToken, bookingActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-booking"
}

object Constants extends Constants
