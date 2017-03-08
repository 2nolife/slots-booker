package com.coldcore.slotsbooker
package ms.ui

import akka.actor.ActorSystem
import com.coldcore.slotsbooker.ms.{CreateAuthActors, CreateRestClient, StartSingle}
import rest.UiRestService

@deprecated //todo remove UI micro service, use weblet instead
object start extends StartSingle with Constants with CreateAuthActors with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    new UiRestService(config.hostname, config.port)
  }
}

trait Constants {
  val MS = "ms-ui"
}

object Constants extends Constants
