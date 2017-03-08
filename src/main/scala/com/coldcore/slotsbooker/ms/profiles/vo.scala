package com.coldcore.slotsbooker
package ms.profiles.vo

import spray.json.{DefaultJsonProtocol, JsObject}
import ms.vo.{Attributes, ProfileRemote}

case class RegisterUser(username: String, email: String, password: String)
object RegisterUser extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class Profile(profile_id: String, username: Option[String], email: Option[String], roles: Option[Seq[String]],
                   metadata: Option[JsObject], attributes: Option[Attributes])

object Profile extends DefaultJsonProtocol {
  implicit val format = jsonFormat(Profile.apply, "profile_id", "username", "email", "roles", "metadata", "attributes")

  def apply(p: ProfileRemote): Profile = {
    import p._
    new Profile(profile_id, Some(username).noneIfEmpty, Some(email).noneIfEmpty, Some(roles).noneIfEmpty, None, None)
  }
}

case class UpdateProfile(username: Option[String], email: Option[String], password: Option[String], roles: Option[Seq[String]],
                         metadata: Option[JsObject], attributes: Option[Attributes])
object UpdateProfile extends DefaultJsonProtocol { implicit val format = jsonFormat6(apply) }
