package com.coldcore.slotsbooker
package ms.profiles.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.attributes.Types.VoAttributes
import ms.actors.MsgInterceptor
import ms.http.{ApiCode, RestClient, SystemRestCalls}
import ms.profiles.db.ProfilesDb
import ms.profiles.vo
import ms.profiles.Constants._
import ms.vo.{EmptyEntity, ProfileRemote}
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

  def registerWithMsAuth(username: String, obj: JsObject): ApiCode =
    restPut[EmptyEntity](s"$authBaseUrl/auth/users/$username", obj)._1

  def updateUserInMsAuth(username: String, obj: JsObject): ApiCode =
    restPatch[EmptyEntity](s"$authBaseUrl/auth/users/$username", obj)._1

  def deleteUserFromMsAuth(username: String): ApiCode =
    restDelete[EmptyEntity](s"$authBaseUrl/auth/users/$username")._1

  def invalidateUserInMsAuth(username: String): ApiCode =
    restDelete[EmptyEntity](s"$authBaseUrl/auth/users/$username/token")._1
}

object ProfilesActor {
  def props(profilesDb: ProfilesDb, authBaseUrl: String, systemToken: String, restClient: RestClient, voAttributes: VoAttributes): Props =
    Props(new ProfilesActor(profilesDb, authBaseUrl, systemToken, restClient, voAttributes))

  case class GetProfileByUsernameIN(username: String, profile: ProfileRemote) extends RequestInfo
  case class GetProfileByIdIN(profileId: String, profile: ProfileRemote) extends RequestInfo
  case class SearchProfilesIN(byAttributes: Seq[(String,String)], joinOR: Boolean, profile: ProfileRemote) extends RequestInfo
  case class UpdateProfileIN(profileId: String, obj: vo.UpdateProfile, profile: ProfileRemote) extends RequestInfo
  case class DeleteProfileIN(profileId: String, profile: ProfileRemote) extends RequestInfo
  case class InvalidateTokenByUsernameIN(profileId: String, profile: ProfileRemote) extends RequestInfo
}

class ProfilesActor(val profilesDb: ProfilesDb, val authBaseUrl: String, val systemToken: String,
                    val restClient: RestClient, val voAttributes: VoAttributes)
  extends Actor with ActorLogging with MsgInterceptor with AuthMsRestCalls with VoExpose
    with GetProfile with AmendProfile with SearchProfiles {
                                                                 
  def receive =
    getProfileReceive orElse amendProfileReceive orElse searchProfilesReceive

  val isProfileOwner = (profileId: String, profile: ProfileRemote) => profile.isMy(profileId)

  def permitAttributes(obj: vo.UpdateProfile, profileId: String, profile: ProfileRemote): Boolean =
    au.permit(obj, voAttributes("profile"), ap.defaultWrite(profile, _ => isProfileOwner(profileId, profile)))._1

}

trait GetProfile {
  self: ProfilesActor =>
  import ProfilesActor._

  val getProfileReceive: Actor.Receive = {

    case in @ GetProfileByUsernameIN(username, profile) =>
      val result = profilesDb.profileByUsername(username)

      reply ! CodeEntityOUT(SC_OK, expose(result, profile), in)

    case in @ GetProfileByIdIN(profileId, profile) =>
      val result = profilesDb.profileById(profileId)
      val code =
        if (result.isDefined) SC_OK
        else SC_NOT_FOUND

      reply ! CodeEntityOUT(code, expose(result, profile), in)

  }

}

trait SearchProfiles {
  self: ProfilesActor =>
  import ProfilesActor._

  val searchProfilesReceive: Actor.Receive = {

    case in @ SearchProfilesIN(byAttributes, joinOR, profile) =>
      val privileged =
        byAttributes.nonEmpty && !byAttributes.exists { case (_, value) => value.endsWith("*") && value.size < 3+1 }

      lazy val canRead = profile.isSuper || privileged

      def read: Option[Seq[vo.Profile]] = Some(profilesDb.searchProfiles(byAttributes, joinOR))

      val (code, profiles) =
        if (canRead) (ApiCode.OK, read)
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, exposeSeq(profiles, profile), in)

  }

}

trait AmendProfile {
  self: ProfilesActor =>
  import ProfilesActor._

  val amendProfileReceive: Actor.Receive = {

    case in @ UpdateProfileIN(profileId, obj, profile) =>
      def updateMsAuthOrRollback(originalUsername: String, p: vo.Profile): vo.Profile =
        Some(originalUsername != p.username.get || obj.password.isDefined)
          .filter(true ==)
          .map(_ => updateUserInMsAuth(originalUsername, obj.toJson.asJsObject))
          .filter(_ not SC_OK)
          .map { _ => // rollback profile username change in case if Auth micro service fails
            profilesDb.updateProfile(profileId, obj.copy(username = Some(originalUsername)))
            log.warning(s"MS Auth was unable to update user #$profileId")
            p.copy(username = Some(originalUsername))
          }
          .getOrElse(p)

      lazy val usernameExists = obj.username.flatMap(profilesDb.profileByUsername(_).filter(_.profile_id != profileId)).isDefined
      lazy val emailExists = obj.email.flatMap(profilesDb.profileByEmail(_).filter(_.profile_id != profileId)).isDefined
      lazy val forbidFields = !profile.isSuper && (obj.roles.isDefined || obj.metadata.isDefined)
      lazy val forbidAttributes = !permitAttributes(obj, profileId, profile)
      lazy val myProfile = profilesDb.profileById(profileId)
      lazy val profileNotFound = myProfile.isEmpty
      lazy val canUpdate = profile.isSuper || profile.isMy(profileId)

      def update(): Option[vo.Profile] = {
        val p0 = profilesDb.updateProfile(profileId, obj)
        val updated = updateMsAuthOrRollback(myProfile.get.username.get, p0.get)
        Some(updated)
      }

      val (code, updatedProfile) =
        if (forbidFields) (ApiCode(SC_FORBIDDEN, 'update_forbidden_fields), None)
        else if (profileNotFound) (ApiCode(SC_NOT_FOUND, 'profile_not_found), None)
        else if (usernameExists) (ApiCode(SC_CONFLICT, 'username_exists), None)
        else if (emailExists) (ApiCode(SC_CONFLICT, 'email_exists), None)
        else if (forbidAttributes) (ApiCode(SC_FORBIDDEN, 'update_forbidden_attributes), None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN, 'action_forbidden), None)

      reply ! CodeEntityOUT(code, expose(updatedProfile, profile), in)

    case in @ DeleteProfileIN(profileId, profile) =>
      lazy val myProfile = profilesDb.profileById(profileId)
      lazy val profileNotFound = myProfile.isEmpty
      lazy val canDelete = profile.isSuper || profile.isMy(profileId)

      def delete() {
        profilesDb.deleteProfile(profileId)
        val codeA = deleteUserFromMsAuth(myProfile.get.username.get)
        if (codeA not SC_OK) log.warning(s"MS Auth was unable to delete user #$profileId")
      }

      val code =
        if (profileNotFound) ApiCode(SC_NOT_FOUND, 'profile_not_found)
        else if (canDelete) {
          delete()
          ApiCode.OK
        } else ApiCode(SC_FORBIDDEN, 'action_forbidden)

      reply ! CodeOUT(code, in)

    case in @ InvalidateTokenByUsernameIN(profileId, profile) =>
      lazy val myProfile = profilesDb.profileById(profileId)
      lazy val profileNotFound = myProfile.isEmpty
      lazy val canUpdate = profile.isSuper || profile.isMy(profileId)

      def update() = {
        val codeA = invalidateUserInMsAuth(myProfile.get.username.get)
        if (codeA not SC_OK) log.warning(s"MS Auth was unable to invalidate user #$profileId")
      }

      val code =
        if (profileNotFound) ApiCode(SC_NOT_FOUND, 'profile_not_found)
        else if (canUpdate) {
          update()
          ApiCode.OK
        } else ApiCode(SC_FORBIDDEN, 'action_forbidden)

      reply ! CodeOUT(code, in)
  }

}

object ProfilesRegisterActor {
  def props(profilesDb: ProfilesDb, authBaseUrl: String, systemToken: String, restClient: RestClient, voAttributes: VoAttributes): Props =
    Props(new ProfilesRegisterActor(profilesDb, authBaseUrl, systemToken, restClient, voAttributes))

  case class RegisterIN(obj: vo.RegisterUser, profile: ProfileRemote) extends RequestInfo
}

class ProfilesRegisterActor(profilesDb: ProfilesDb, val authBaseUrl: String, val systemToken: String,
                            val restClient: RestClient, val voAttributes: VoAttributes)
  extends Actor with ActorLogging with MsgInterceptor with AuthMsRestCalls with VoExpose {
  import ProfilesRegisterActor._

  val isProfileOwner = (profileId: String, profile: ProfileRemote) => true

  def receive = {

    case in @ RegisterIN(obj, profile) =>
      lazy val usernameExists = profilesDb.profileByUsername(obj.username).isDefined
      lazy val emailExists = profilesDb.profileByEmail(obj.email).isDefined

      val (code, registeredProfile) =
        if (usernameExists) (ApiCode(SC_CONFLICT, 'username_exists), None)
        else if (emailExists) (ApiCode(SC_CONFLICT, 'email_exists), None)
        else {
          val myProfile = profilesDb.createProfile(obj)
          val codeA = registerWithMsAuth(obj.username, obj.toJson.asJsObject)
          if (codeA is SC_CREATED) {
            (ApiCode.CREATED, Some(myProfile))
          } else { // rollback profile creation in case if Auth micro service fails
            log.warning(s"MS Auth was unable to register user #${myProfile.profile_id}")
            profilesDb.rollbackProfile(myProfile.profile_id)
            (ApiCode(SC_SERVICE_UNAVAILABLE, 'external_service_failed), None)
          }
        }

      reply ! CodeEntityOUT(code, expose(registeredProfile, profile), in)

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
    obj.map(_.map(expose(_, profile)).toList)

}
