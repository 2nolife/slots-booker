package com.coldcore.slotsbooker
package ms.auth.vo

import spray.json.DefaultJsonProtocol

case class Credentials(username: String, password: String)
object Credentials extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class AmendUser(username: Option[String], password: Option[String])
object AmendUser extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class Token(access_token: String, token_type: String, expires: Int, username: String)
object Token extends DefaultJsonProtocol {
  implicit val format = jsonFormat(Token.apply, "access_token", "token_type", "expires_in", "username")
}

case class AccessToken(access_token: String)
object AccessToken extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }
