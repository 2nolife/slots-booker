package com.coldcore.slotsbooker
package ms.auth.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.http.ApiCode
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.actors.MsgInterceptor
import ms.auth.db.AuthDb
import ms.auth.{BearerTokenGenerator, vo}
import ms.auth.Constants._
import org.apache.http.HttpStatus._

object TokenActor {
  def props(authDb: AuthDb): Props = Props(new TokenActor(authDb))

  case class LoginIN(username: String, password: String) extends RequestInfo
  case class InvalidateTokenIN(token: String) extends RequestInfo
  case class RefreshTokenIN(token: String) extends RequestInfo
  case class ValidateTokenIN(token: String) extends RequestInfo
}

class TokenActor(authDb: AuthDb) extends Actor with ActorLogging with MsgInterceptor {
  import TokenActor._

  val tgen = new BearerTokenGenerator

  def generateToken(username: String): vo.Token =
    vo.Token(tgen.generateSHAToken(username), "Bearer", 3600, username)

  def receive = {

    case in @ LoginIN(username, password) =>
      val (code, result) =
        authDb.userLogin(username, password) match {
          case (true, t @ Some(_)) =>
            (ApiCode.CREATED, t) // already logged in
          case (true, None) => // create new token
            val newToken = generateToken(username)
            authDb.saveToken(newToken)
            (ApiCode.CREATED, Some(newToken))
          case _ =>
            (ApiCode(SC_UNAUTHORIZED, 'invalid_credentials), None) // invalid username / password
        }

      reply ! CodeEntityOUT(code, result, in)

    case in @ InvalidateTokenIN(token) =>
      val deleted = authDb.deleteToken(token)
      reply ! CodeOUT(ApiCode.OK, in)

    case in @ RefreshTokenIN(token) =>
      val (code, result) =
        authDb.getToken(token) match {
          case Some(vo.Token(_, _, _, username)) =>
            val newToken = generateToken(username)
            authDb.saveToken(newToken)
            (ApiCode.OK, Some(newToken))
          case _ =>
            (ApiCode(SC_UNAUTHORIZED, 'expired_token), None)
        }

      reply ! CodeEntityOUT(code, result, in)

    case in @ ValidateTokenIN(token) =>
      val t = authDb.getToken(token)
      val (code, result) =
        if (t.isDefined) (ApiCode.OK, t)
        else (ApiCode(SC_UNAUTHORIZED, 'expired_token), None)

      reply ! CodeEntityOUT(code, result, in)

  }

}

object UsersActor {
  def props(authDb: AuthDb): Props = Props(new UsersActor(authDb))

  case class CreateUserIN(username: String, obj: vo.AmendUser) extends RequestInfo
  case class UpdateUserIN(username: String, obj: vo.AmendUser) extends RequestInfo
  case class DeleteUserIN(username: String) extends RequestInfo
  case class InvalidateTokenByUsernameIN(username: String) extends RequestInfo
}

class UsersActor(authDb: AuthDb) extends Actor with ActorLogging with MsgInterceptor {
  import UsersActor._

  def receive = {

    case in @ CreateUserIN(username, obj) =>
      authDb.saveUser(username, obj)
      reply ! CodeOUT(ApiCode.CREATED, in)

    case in @ UpdateUserIN(username, obj) =>
      authDb.saveUser(username, obj)
      reply ! CodeOUT(ApiCode.OK, in)

    case in @ DeleteUserIN(username) =>
      authDb.deleteTokenByUsername(username)
      authDb.deleteUser(username)
      reply ! CodeOUT(ApiCode.OK, in)

    case in @ InvalidateTokenByUsernameIN(username) =>
      authDb.deleteTokenByUsername(username)
      reply ! CodeOUT(ApiCode.OK, in)

  }

}

