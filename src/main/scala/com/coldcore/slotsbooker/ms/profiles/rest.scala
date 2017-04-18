package com.coldcore.slotsbooker
package ms.profiles.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import com.coldcore.slotsbooker.ms.actors.Common.CodeEntityOUT
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
    authenticateTokenAsync(makeTokenFn(systemToken), makeLocalProfileFn, default = Some(anonymousProfileRemote)) { profile =>

      path("profiles" / "register") {

        post {
          entity(as[vo.RegisterUser]) { entity =>
            completeByActor[vo.Profile](profilesRegisterActor, RegisterIN(entity, profile))
          }
        }

      }

    }

}

trait ProfilesRoute {
  self: ProfilesRestService =>

  def profilesRoute =
    authenticateTokenAsync(makeTokenFn(systemToken), makeLocalProfileFn) { profile =>

      path("profiles" / "me") {

        get {
          completeByActor[vo.Profile](profilesActor, GetProfileByIdIN(profile.profile_id, profile))
        } ~
        patch {
          entity(as[vo.UpdateProfile]) { entity =>
            completeByActor[vo.Profile](profilesActor, UpdateProfileIN(profile.profile_id, entity, profile))
          }
        } ~
        delete {
          completeByActor[EmptyEntity](profilesActor, DeleteProfileIN(profile.profile_id, profile))
        }

      } ~
      path("profiles" / "search") {

        get {
          parameterSeq { attributes =>
            completeByActor[Seq[vo.Profile]](profilesActor, SearchProfilesIN(attributes.filterNot(p => Seq("and", "or").contains(p._1)), joinOR = attributes.exists(p => p._1 == "or"), profile))
          }
        }

      } ~
      path("profiles" / Segment) { profileId =>

        get {
          completeByActor[vo.Profile](profilesActor, GetProfileByIdIN(profileId, profile))
        } ~
        patch {
          entity(as[vo.UpdateProfile]) { entity =>
            completeByActor[vo.Profile](profilesActor, UpdateProfileIN(profileId, entity, profile))
          }
        } ~
        delete {
          completeByActor[EmptyEntity](profilesActor, DeleteProfileIN(profileId, profile))
        }

      } ~
      path("profiles" / Segment / "token") { profileId =>

        delete {
          completeByActor[EmptyEntity](profilesActor, InvalidateTokenByUsernameIN(profileId, profile))
        }

      }

    }

}