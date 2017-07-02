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

    val bookingActor = system.actorOf(BookingActor.props(bookingDb, config.placesBaseUrl, config.slotsBaseUrl, config.membersBaseUrl,
                                      config.systemToken, restClient).withRouter(FromConfig), name = MS)

    new BookingRestService(config.hostname, config.port, config.systemToken, bookingActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-booking"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'slot_bookable -> 1,
    'slot_expired -> 2,
    'ref_slot_mismatch -> 3,
    'ref_place_mismatch -> 4,
    'ref_profile_mismatch -> 5,
    'quote_incomplete -> 6,
    'quote_promoted -> 7,
    'refund_requires_payment -> 8,
    'generated_refund_mismatch -> 9,
    'slot_not_bookable -> 10,
    'price_invalid -> 11,
    'generated_quote_mismatch -> 12,
    'quote_active -> 13,
    'refund_active -> 14,
    'booked_status_mismatch -> 15,
    'low_member_level -> 16,
    'slot_early_bound -> 17,
    'slot_late_bound -> 18
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)
}

object Constants extends Constants
