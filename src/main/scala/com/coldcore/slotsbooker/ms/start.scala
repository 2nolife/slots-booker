package com.coldcore.slotsbooker
package ms

import akka.actor.{ActorRef, ActorSystem}
import akka.routing.FromConfig
import akka.util.Timeout
import com.coldcore.slotsbooker.ms.http.RestClient
import com.mongodb.casbah.MongoClient
import ms.actors.ExternalAuthActor
import config.Constants._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object start {

  def main(args: Array[String]) {
    implicit val system: ActorSystem = ActorSystem("slotsbooker")

    sys.addShutdownHook {
      println("Shutting down ...")
      system.terminate()
    }

    auth.start.run
    profiles.start.run
    slots.start.run
    places.start.run
    booking.start.run
    ui.start.run

    Await.result(system.whenTerminated, Duration.Inf)
  }

}

trait StartSingle {
  self: {
    val MS: String
    def run(implicit system: ActorSystem)
  } =>


  def startSingle() {
    implicit val system = ActorSystem(s"$APP-$MS")

    sys.addShutdownHook {
      println("Shutting down ...")
      system.terminate()
    }

    run

    Await.result(system.whenTerminated, Duration.Inf)
  }

}

trait CreateAuthActors {
  private type Config = {
    val systemToken: String
    val authBaseUrl: String
    val profilesBaseUrl: String
  }

  def externalAuthActor(config: Config, restClient: RestClient)(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.synchronized {
      implicit val timeout = Timeout(1 second)
      val selectorFuture = system.actorSelection("user/global-external-auth-actor").resolveOne

      Await.ready(selectorFuture, 1 second).value.get match {
        case Success(actorRef) =>
          actorRef
        case Failure(_) =>
          import config._
          system.actorOf(ExternalAuthActor.props(authBaseUrl, profilesBaseUrl, systemToken, restClient).withRouter(FromConfig),
            name = "global-external-auth-actor")

      }
    }

}

trait CreateMongoClient extends WhenTerminated {
  private type Config = {
    val mongoDbHostname: String
    val mongoDbPort: Int
  }

  /** Create a new MongoDB client connection and close it when the system terminates */
  def createMongoClient(config: Config)(implicit system: ActorSystem, ec: ExecutionContext): MongoClient = {
    import config._
    val mongoClient = MongoClient(mongoDbHostname, mongoDbPort)
    whenTerminated(mongoClient.close())
    mongoClient
  }
}

trait CreateRestClient extends WhenTerminated {
  private type Config = {
    val restConnPerRoute: Int
    val restConnMaxTotal: Int
  }

  /** Create a new REST client and close it when the system terminates */
  def createRestClient(config: Config)(implicit system: ActorSystem, ec: ExecutionContext): RestClient = {
    import config._
    val restClient = new RestClient(restConnPerRoute, restConnMaxTotal)
    whenTerminated(restClient.close())
    restClient
  }
}

trait WhenTerminated {
  def whenTerminated(body: => Unit)(implicit system: ActorSystem, ec: ExecutionContext) =
    system.whenTerminated.onComplete(_ => body)

}
