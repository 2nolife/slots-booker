package com.coldcore.slotsbooker
package ms.auth

import akka.actor.ActorSystem
import actors.{TokenActor, UsersActor}
import akka.routing.FromConfig
import db.MongoAuthDb
import rest.AuthRestService
import ms.{CreateMongoClient, StartSingle}

object start extends StartSingle with Constants with CreateMongoClient {

  def main(args: Array[String]) =
    startSingle()

  def run(implicit system: ActorSystem) {
    implicit val executionContext = system.dispatcher
    val config = Settings(system)

    val mongoClient = createMongoClient(config)
    val authDb = new MongoAuthDb(mongoClient, config.mongoDbName)

    val tokenActor = system.actorOf(TokenActor.props(authDb).withRouter(FromConfig), name = s"$MS-token-actor")
    val usersActor = system.actorOf(UsersActor.props(authDb).withRouter(FromConfig), name = s"$MS-users-actor")

    new AuthRestService(config.hostname, config.port, config.systemToken, tokenActor, usersActor)
  }
}

trait Constants {
  val MS = "ms-auth"

  private def toApiCodes(codes: (Symbol, Int) *): Map[Symbol, String] = codes.map { case (s, code) => (s, MS+"-"+code) }.toMap
  val apiCodes = toApiCodes(
    'invalid_credentials -> 1
  )
}

object Constants extends Constants
