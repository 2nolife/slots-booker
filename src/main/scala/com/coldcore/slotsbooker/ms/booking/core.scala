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

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'slots_contain_bookable -> 1,
    'slots_contain_expired -> 2,
    'refs_slots_mismatch -> 3,
    'refs_place_mismatch -> 4,
    'refs_profile_mismatch -> 5,
    'quotes_complete_not_all -> 6,
    'quotes_deal_not_all -> 7,
    'refund_requires_payment -> 8,
    'generated_refund_mismatch -> 9
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)
}

object Constants extends Constants
