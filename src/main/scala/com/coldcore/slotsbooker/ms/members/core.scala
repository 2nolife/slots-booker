package com.coldcore.slotsbooker
package ms.members

import akka.actor.ActorSystem
import ms._
import actors.MembersActor
import akka.routing.FromConfig
import db.MongoMembersDb
import rest.MembersRestService


object start extends StartSingle with Constants with CreateAuthActors with CreateMongoClient with CreateRestClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val membersDb = new MongoMembersDb(mongoClient, config.mongoDbName)

    val restClient = createRestClient(config)

    val membersActor = system.actorOf(MembersActor.props(membersDb, config.placesBaseUrl, config.systemToken, restClient).withRouter(FromConfig), name = MS)

    new MembersRestService(config.hostname, config.port, membersActor, externalAuthActor(config, restClient))
  }
}

trait Constants {
  val MS = "ms-members"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap

  val apiCodes = toApiCodes(
    'dummy -> 1
  )

  implicit def symbolToApiCode(key: Symbol): Option[String] = apiCodes.get(key)
}

object Constants extends Constants
