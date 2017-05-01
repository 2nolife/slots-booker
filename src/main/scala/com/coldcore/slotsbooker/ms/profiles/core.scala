package com.coldcore.slotsbooker
package ms.profiles

import akka.actor.ActorSystem
import ms._
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

    val profilesActor = system.actorOf(ProfilesActor.props(profilesDb, config.authBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = MS)
    val profilesRegisterActor = system.actorOf(ProfilesRegisterActor.props(profilesDb, config.authBaseUrl, config.systemToken, restClient, config.voAttributes).withRouter(FromConfig), name = s"$MS-register")

    new ProfilesRestService(config.hostname, config.port, config.systemToken, profilesActor, profilesRegisterActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-profiles"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'action_forbidden -> 1,
    'profile_not_found -> 2,
    'update_forbidden_attributes -> 3,
    'update_forbidden_fields -> 4,
    'username_exists -> 5,
    'email_exists -> 6,
    'external_service_failed -> 7
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)
}

object Constants extends Constants
