package com.coldcore.slotsbooker
package ms.profiles.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.attributes.Types.VoAttributes
import ms.actors.MsgInterceptor
import ms.http.{RestClient, SystemRestCalls}
import ms.profiles.db.ProfilesDb
import ms.profiles.vo
import ms.vo.{ProfileRemote, StringEntity}
import ms.attributes.{Permission => ap, Util => au}
import ms.vo.Implicits._
import org.apache.http.HttpStatus._
import spray.json._

trait AuthMsRestCalls extends SystemRestCalls {
  self: {
    val authBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def registerWithMsAuth(username: String, obj: JsObject): Int =
    restPut[StringEntity](s"$authBaseUrl/auth/users/$username", obj)._1

  def updateUserInMsAuth(username: String, obj: JsObject): Int =
    restPatch[StringEntity](s"$authBaseUrl/auth/users/$username", obj)._1

  def deleteUserFromMsAuth(username: String): Int =
    restDelete[StringEntity](s"$authBaseUrl/auth/users/$username")._1

  def invalidateUserInMsAuth(username: String): Int =
    restDelete[StringEntity](s"$authBaseUrl/auth/users/$username/token")._1
}

object ProfilesActor {
  def props(profilesDb: ProfilesDb, authBaseUrl: String, systemToken: String, restClient: RestClient, voAttributes: VoAttributes): Props =
    Props(new ProfilesActor(profilesDb, authBaseUrl, systemToken, restClient, voAttributes))

  case class GetProfileByUsernameIN(username: String, profile: ProfileRemote)
  case class GetProfileByIdIN(profileId: String, profile: ProfileRemote)
  case class GetProfilesIN(byAttributes: Seq[(String,String)], joinOR: Boolean, profile: ProfileRemote)
  case class UpdateProfileIN(profileId: String, obj: vo.UpdateProfile, profile: ProfileRemote)
  case class DeleteProfileIN(profileId: String, profile: ProfileRemote)
  case class InvalidateTokenByUsernameIN(profileId: String, profile: ProfileRemote)
}

class ProfilesActor(val profilesDb: ProfilesDb, val authBaseUrl: String, val systemToken: String,
                    val restClient: RestClient, val voAttributes: VoAttributes)
  extends Actor with ActorLogging with MsgInterceptor with AuthMsRestCalls with VoExpose with GetProfile with AmendProfile {
                                                                 //todo refactor to lazy
  def receive =
    getProfileReceive orElse amendProfileReceive

  val isProfileOwner = (profileId: String, profile: ProfileRemote) => profile.isMy(profileId)

  def permitAttributes(obj: vo.UpdateProfile, profileId: String, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("profile"), ap.defaultWrite(profile, _ => isProfileOwner(profileId, profile)))._1

}

trait GetProfile {
  self: ProfilesActor =>
  import ProfilesActor._

  val getProfileReceive: Actor.Receive = {

    case GetProfileByUsernameIN(username, profile) =>
      val result = profilesDb.profileByUsername(username)

      reply ! CodeEntityOUT(SC_OK, expose(result, profile))

    case GetProfileByIdIN(profileId, profile) =>
      val result = profilesDb.profileById(profileId)
      val code =
        if (result.isDefined) SC_OK
        else SC_NOT_FOUND

      reply ! CodeEntityOUT(code, expose(result, profile))

    case GetProfilesIN(byAttributes, joinOR, profile) =>
      val unprivileged =
        byAttributes.nonEmpty && !byAttributes.exists { case (_, value) => value.endsWith("*") && value.size < 3+1 }

      val (code, profiles) =
        if (unprivileged || profile.isSuper) {
          val profiles = profilesDb.searchProfiles(byAttributes, joinOR)
          (SC_OK, Some(profiles))
        } else {
          (SC_FORBIDDEN, None)
        }

      reply ! CodeEntityOUT(code, exposeSeq(profiles, profile))

  }

}

trait AmendProfile {
  self: ProfilesActor =>
  import ProfilesActor._

  val amendProfileReceive: Actor.Receive = {

    case UpdateProfileIN(profileId, obj, profile) =>
      val exists = (
          obj.username.flatMap(profilesDb.profileByUsername(_).filter(_.profile_id != profileId)) orElse
          obj.email.flatMap(profilesDb.profileByEmail(_).filter(_.profile_id != profileId))
        ).isDefined

      def updateMsAuthOrRollback(originalUsername: String, p: vo.Profile): vo.Profile =
        Some(originalUsername != p.username.get || obj.password.isDefined)
          .filter(true ==)
          .map(_ => updateUserInMsAuth(originalUsername, obj.toJson.asJsObject))
          .filter(SC_OK !=)
          .map { _ => // rollback profile username change in case if Auth micro service fails
            profilesDb.updateProfile(profileId, obj.copy(username = Some(originalUsername)))
            log.warning(s"MS Auth was unable to update user #$profileId")
            p.copy(username = Some(originalUsername))
          }
          .getOrElse(p)

      val (code, updatedProfile) =
        if (!profile.isSuper && (obj.roles.isDefined || obj.metadata.isDefined)) {
          (SC_FORBIDDEN, None)
        } else if ((profile.isSuper || profile.isMy(profileId)) && exists) {
          (SC_CONFLICT, None)
        } else if (!permitAttributes(obj, profileId, profile)) {
          (SC_FORBIDDEN, None)
        } else if (profile.isSuper || profile.isMy(profileId)) {
          val original = profilesDb.profileById(profileId)
          profilesDb.updateProfile(profileId, obj) match {
            case Some(p0) =>
              val p = updateMsAuthOrRollback(original.get.username.get, p0)
              (SC_OK, Some(p))
            case None =>
              (SC_NOT_FOUND, None)
          }
        } else {
          (SC_FORBIDDEN, None)
        }

      reply ! CodeEntityOUT(code, expose(updatedProfile, profile))

    case DeleteProfileIN(profileId, profile) =>
      val code =
        if (profile.isSuper || profile.isMy(profileId)) {
          val foundProfile = profilesDb.profileById(profileId)
          profilesDb.softDeleteProfile(profileId) match {
            case true =>
              val codeA = foundProfile.map(p => deleteUserFromMsAuth(p.username.get)).getOrElse(SC_OK)
              if (codeA != SC_OK) log.warning(s"MS Auth was unable to delete user #$profileId")
              SC_OK
            case false =>
              SC_NOT_FOUND
          }
        } else {
          SC_FORBIDDEN
        }

      reply ! CodeOUT(code)

    case InvalidateTokenByUsernameIN(profileId, profile) =>
      val code =
        if (profile.isSuper || profile.isMy(profileId)) {
          val foundProfile = profilesDb.profileById(profileId)
          val codeA = foundProfile.map(p => invalidateUserInMsAuth(p.username.get)).getOrElse(SC_OK)
          if (codeA != SC_OK) log.warning(s"MS Auth was unable to invalidate user #$profileId")
          SC_OK
        } else {
          SC_FORBIDDEN
        }

      reply ! CodeOUT(code)
  }

}

object ProfilesRegisterActor {
  def props(profilesDb: ProfilesDb, authBaseUrl: String, systemToken: String, restClient: RestClient, voAttributes: VoAttributes): Props =
    Props(new ProfilesRegisterActor(profilesDb, authBaseUrl, systemToken, restClient, voAttributes))

  case class RegisterIN(obj: vo.RegisterUser, profile: ProfileRemote)
  case class RegisterOUT(code: Int, profile: Option[vo.Profile])
}

class ProfilesRegisterActor(profilesDb: ProfilesDb, val authBaseUrl: String, val systemToken: String,
                            val restClient: RestClient, val voAttributes: VoAttributes)
  extends Actor with ActorLogging with MsgInterceptor with AuthMsRestCalls with VoExpose {
  import ProfilesRegisterActor._

  val isProfileOwner = (profileId: String, profile: ProfileRemote) => true

  def receive = {

    case RegisterIN(obj, profile) =>
      val exists = (
          profilesDb.profileByUsername(obj.username) orElse
          profilesDb.profileByEmail(obj.email)
        ).isDefined

      val (code, registeredProfile) =
        if (exists) (SC_CONFLICT, None)
        else {
          val myProfile = profilesDb.createProfile(obj)
          val codeA = registerWithMsAuth(obj.username, obj.toJson.asJsObject)
          if (codeA == SC_CREATED) {
            (SC_CREATED, Some(myProfile))
          } else { // rollback profile creation in case if Auth micro service fails
            log.warning(s"MS Auth was unable to register user #${myProfile.profile_id}")
            profilesDb.deleteProfile(myProfile.profile_id)
            (SC_SERVICE_UNAVAILABLE, None)
          }
        }

      reply ! CodeEntityOUT(code, expose(registeredProfile, profile))

  }

}

trait VoExpose {
  self: {
    val voAttributes: VoAttributes
    val isProfileOwner: (String, ProfileRemote) => Boolean
  } =>

  def expose(obj: vo.Profile, profile: ProfileRemote): vo.Profile =
    Some(obj)
      .map(p => au.exposeClass(p, voAttributes("profile"), ap.defaultRead(profile, _ => isProfileOwner(p.profile_id, profile))))
      .map(p => p.copy(attributes = p.attributes.noneIfEmpty, metadata = p.metadata.noneIfEmpty))
      .map(p => if (isProfileOwner(p.profile_id, profile) || profile.isSuper) p else p.copy(email = None, roles = None, metadata = None))
      .get

  def expose(obj: Option[vo.Profile], profile: ProfileRemote): Option[vo.Profile] =
    obj.map(expose(_, profile))

  def exposeSeq(obj: Option[Seq[vo.Profile]], profile: ProfileRemote): Option[Seq[vo.Profile]] =
    obj.map(_.map(expose(_, profile)))

}
