package com.coldcore.slotsbooker
package ms.profiles

import akka.actor.ActorSystem
import ms.{CreateAuthActors, CreateMongoClient, CreateRestClient, StartSingle}
import actors.{ProfilesActor, ProfilesRegisterActor}
import akka.routing.FromConfig
import db.MongoProfilesDb
import rest.ProfilesRestService

object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val profilesDb = new MongoProfilesDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val profilesActor = system.actorOf(ProfilesActor.props(profilesDb, config.authBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = s"$MS-actor")
    val profilesRegisterActor = system.actorOf(ProfilesRegisterActor.props(profilesDb, config.authBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = s"$MS-register-actor")

    new ProfilesRestService(config.hostname, config.port, config.systemToken, profilesActor, profilesRegisterActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-profiles"
}

object Constants extends Constants
