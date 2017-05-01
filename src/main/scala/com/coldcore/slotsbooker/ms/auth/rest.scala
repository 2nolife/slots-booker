package com.coldcore.slotsbooker
package ms.auth.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import ms.vo.EmptyEntity
import ms.auth.actors.TokenActor._
import ms.auth.actors.UsersActor._
import ms.auth.vo
import ms.rest.BaseRestService

class AuthRestService(hostname: String, port: Int, val systemToken: String,
                      val tokenActor: ActorRef,
                      val usersActor: ActorRef,
                      externalAuthActor: ActorRef = null)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with TokenRoute with UsersRoute {

  bind(tokenRoute ~ usersRoute, name = "Auth")
}

trait TokenRoute {
  self: AuthRestService =>

  def tokenRoute =
    clientIpHost { iphost =>
      path("auth" / "token") {

        post {
          entity(as[vo.Credentials]) { credentials =>
            completeByActor[vo.Token](tokenActor, LoginIN(credentials.username, credentials.password).withIpHost(iphost))
          }
        } ~
        delete {
          entity(as[vo.AccessToken]) { token =>
            completeByActor[EmptyEntity](tokenActor, InvalidateTokenIN(token.access_token).withIpHost(iphost))
          }
        } ~
        get {
          parameter('access_token) { token =>
            completeByActor[vo.Token](tokenActor, ValidateTokenIN(token).withIpHost(iphost))
          }
        }

      } ~
      path("auth" / "refresh_token") {

        post {
          entity(as[vo.AccessToken]) { token =>
            completeByActor[vo.Token](tokenActor, RefreshTokenIN(token.access_token).withIpHost(iphost))
          }
        }

      }
    }

}

trait UsersRoute {
  self: AuthRestService =>

  def usersRoute =
    clientIpHost { iphost =>
      pathPrefix("auth" / "users") {
        authenticateSystemToken(systemToken) { _ =>

          path(Segment) { username =>

            put {
              entity(as[vo.AmendUser]) { entity =>
                completeByActor[EmptyEntity](usersActor, CreateUserIN(username, entity).withIpHost(iphost))
              }
            } ~
            patch {
              entity(as[vo.AmendUser]) { entity =>
                completeByActor[EmptyEntity](usersActor, UpdateUserIN(username, entity).withIpHost(iphost))
              }
            } ~
            delete {
              completeByActor[EmptyEntity](usersActor, DeleteUserIN(username).withIpHost(iphost))
            }

          } ~
          path(Segment / "token") { username =>

            delete {
              completeByActor[EmptyEntity](usersActor, InvalidateTokenByUsernameIN(username).withIpHost(iphost))
            }

          }

        }
      }
    }

}
