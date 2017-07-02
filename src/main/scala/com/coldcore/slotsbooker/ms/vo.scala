package com.coldcore.slotsbooker
package ms.vo

import spray.json._

/** Representation of an access token retrieved from the "auth" micro service. */
case class TokenRemote(access_token: String, token_type: String, expires: Int, username: String)
object TokenRemote extends DefaultJsonProtocol {
  implicit val format = jsonFormat(TokenRemote.apply, "access_token", "token_type", "expires_in", "username")
}

/** Representation of a profile retrieved from the "profiles" micro service with "access_token" appended by the caller actor. */
case class ProfileRemote(profile_id: String, username: String, email: String, roles: Seq[String], access_token: Option[String]) {
  def isAdmin: Boolean = roles.contains("ADMIN")
  def isSystem: Boolean = profile_id == "*"
  def isSuper: Boolean = isAdmin || isSystem
  def isAnonymous: Boolean = profile_id == "anonymous"
  def isMy(profileId: String): Boolean = profileId == profile_id
}

object ProfileRemote {

  implicit object JsonFormat extends DefaultJsonProtocol with RootJsonFormat[ProfileRemote] {

    override def read(v: JsValue): ProfileRemote =
      ProfileRemote(
        fromField[String](v, "profile_id"),
        fromField[Option[String]](v, "username").getOrElse(""),
        fromField[Option[String]](v, "email").getOrElse(""),
        fromField[Option[Seq[String]]](v, "roles").getOrElse(Nil),
        None
      )

    override def write(p: ProfileRemote): JsValue = {
      import p._
      JsObject(
        "profile_id" -> profile_id.toJson,
        "username" -> username.toJson,
        "email" -> email.toJson,
        "roles" -> roles.toJson
      )
    }

  }

}

object SystemTokenAndProfile {

  def systemTokenRemote(systemToken: String): TokenRemote =
    TokenRemote(systemToken, "Bearer", 3600, "*")

  def systemProfileRemote(systemToken: String): ProfileRemote =
    ProfileRemote(profile_id = "*", username = "*", email = "*", roles = Nil, access_token = Some(systemToken))

  def anonymousProfileRemote: ProfileRemote =
    ProfileRemote(profile_id = "anonymous", username = "*", email = "*", roles = Nil, None)

}

case class ContentEntity(content: String)
object ContentEntity extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }

case class EmptyEntity()
object EmptyEntity {
  implicit object JsonFormat extends DefaultJsonProtocol with RootJsonFormat[EmptyEntity] {
    override def read(v: JsValue): EmptyEntity = EmptyEntity()
    override def write(p: EmptyEntity): JsValue = JsObject()
  }
}

case class Attributes(var value: JsObject) {
  def set(value: JsObject) = this.value = value
  var exposed: Boolean = _
}

object Attributes {

  implicit object JsonFormat extends DefaultJsonProtocol with RootJsonFormat[Attributes] {
    override def read(v: JsValue): Attributes = Attributes(v.asJsObject)
    override def write(a: Attributes): JsValue = a.value
  }

  def apply(fields: JsField*): Attributes = Attributes(JsObject(fields: _*))
  def apply(json: String): Attributes = Attributes(json.parseJson.asJsObject)
  def apply: Attributes = Attributes(JsObject())

}

object Implicits {

  implicit class EmptyAttributes[A](a: Option[Attributes]) {
    def noneIfEmpty =
      a match {
        case Some(attrs) if attrs.value.fields.isEmpty => None
        case x => x
      }
  }

  implicit class EmptyJsObject[A](a: Option[JsObject]) {
    def noneIfEmpty =
      a match {
        case Some(obj) if obj.fields.isEmpty => None
        case x => x
      }
  }

}
