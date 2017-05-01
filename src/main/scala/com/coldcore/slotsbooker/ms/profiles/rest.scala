package com.coldcore.slotsbooker
package ms.profiles.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import ms.actors.Common.CodeEntityOUT
import ms.profiles.actors.ProfilesActor._
import ms.profiles.actors.ProfilesRegisterActor._
import ms.profiles.vo
import ms.rest.BaseRestService
import ms.vo.{EmptyEntity, ProfileRemote, TokenRemote}
import ms.vo.SystemTokenAndProfile._

class ProfilesRestService(hostname: String, port: Int, val systemToken: String,
                          val profilesActor: ActorRef,
                          val profilesRegisterActor: ActorRef,
                          externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor) with ProfilesRoute with RegisterRoute {

  bind(registerRoute ~ profilesRoute, name = "Profiles")

  def asProfileRemote(p: vo.Profile): ProfileRemote = {
    import p._
    ProfileRemote(profile_id, username.get, email.get, roles.getOrElse(Nil), None)
  }

  /** Retrieve a profile from DB or return the "system" profile */
  def makeLocalProfileFn: TokenToProfileRemote =
    (token: TokenRemote) =>
      if (token.access_token == systemToken) makeSystemProfileFn(systemToken).apply(token)
      else (profilesActor ? GetProfileByUsernameIN(token.username, profile = systemProfileRemote(systemToken)))
        .mapTo[CodeEntityOUT[vo.Profile]]
        .map(_.entity.map(asProfileRemote))
}

trait RegisterRoute {
  self: ProfilesRestService =>

  def registerRoute =
    clientIpHost { iphost =>
      path("profiles" / "register") {
        authenticateTokenAsync(makeTokenFn(systemToken), makeLocalProfileFn, default = Some(anonymousProfileRemote)) { profile =>

          post {
            entity(as[vo.RegisterUser]) { entity =>
              completeByActor[vo.Profile](profilesRegisterActor, RegisterIN(entity, profile).withIpHost(iphost))
            }
          }

        }
      }
    }

}

trait ProfilesRoute {
  self: ProfilesRestService =>

  def profilesRoute =
    clientIpHost { iphost =>
      pathPrefix("profiles") {
        authenticateTokenAsync(makeTokenFn(systemToken), makeLocalProfileFn) { profile =>

          path("me") {

            get {
              completeByActor[vo.Profile](profilesActor, GetProfileByIdIN(profile.profile_id, profile).withIpHost(iphost))
            } ~
            patch {
              entity(as[vo.UpdateProfile]) { entity =>
                completeByActor[vo.Profile](profilesActor, UpdateProfileIN(profile.profile_id, entity, profile).withIpHost(iphost))
              }
            } ~
            delete {
              completeByActor[EmptyEntity](profilesActor, DeleteProfileIN(profile.profile_id, profile).withIpHost(iphost))
            }

          } ~
          path("search") {

            get {
              parameterSeq { attributes =>
                completeByActor[Seq[vo.Profile]](profilesActor, SearchProfilesIN(attributes.filterNot(p => Seq("and", "or").contains(p._1)), joinOR = attributes.exists(p => p._1 == "or"), profile).withIpHost(iphost))
              }
            }

          } ~
          path(Segment) { profileId =>

            get {
              completeByActor[vo.Profile](profilesActor, GetProfileByIdIN(profileId, profile).withIpHost(iphost))
            } ~
            patch {
              entity(as[vo.UpdateProfile]) { entity =>
                completeByActor[vo.Profile](profilesActor, UpdateProfileIN(profileId, entity, profile).withIpHost(iphost))
              }
            } ~
            delete {
              completeByActor[EmptyEntity](profilesActor, DeleteProfileIN(profileId, profile).withIpHost(iphost))
            }

          } ~
          path(Segment / "token") { profileId =>

            delete {
              completeByActor[EmptyEntity](profilesActor, InvalidateTokenByUsernameIN(profileId, profile).withIpHost(iphost))
            }

          }

        }
      }
    }

}