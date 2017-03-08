package com.coldcore.slotsbooker
package ms.booking

import akka.actor.ActorSystem
import actors.BookingActor
import akka.routing.FromConfig
import com.coldcore.slotsbooker.ms.{CreateAuthActors, CreateRestClient, StartSingle}
import rest.BookingRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val restClient = createRestClient(config)

    val bookingActor = system.actorOf(BookingActor.props(config.placesBaseUrl, config.slotsBaseUrl, config.systemToken, restClient).withRouter(FromConfig), name = s"$MS-actor")

    new BookingRestService(config.hostname, config.port, bookingActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-booking"
}

object Constants extends Constants
