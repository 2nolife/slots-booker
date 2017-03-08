package com.coldcore.slotsbooker
package ms.auth.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.actors.MsgInterceptor
import ms.auth.db.AuthDb
import ms.auth.{BearerTokenGenerator, vo}
import ms.auth.Constants._
import org.apache.http.HttpStatus._

object TokenActor {
  def props(authDb: AuthDb): Props = Props(new TokenActor(authDb))

  case class LoginIN(username: String, password: String)
  case class InvalidateTokenIN(token: String)
  case class RefreshTokenIN(token: String)
  case class ValidateTokenIN(token: String)
}

class TokenActor(authDb: AuthDb) extends Actor with ActorLogging with MsgInterceptor {
  import TokenActor._

  val tgen = new BearerTokenGenerator

  def generateToken(username: String): vo.Token =
    vo.Token(tgen.generateSHAToken(username), "Bearer", 3600, username)

  def receive = {

    case LoginIN(username, password) =>
      val (code, result, apiCode) =
        authDb.userLogin(username, password) match {
          case (true, t @ Some(_)) =>
            (SC_CREATED, t, None) // already logged in
          case (true, None) => // create new token
            val newToken = generateToken(username)
            authDb.saveToken(newToken)
            (SC_CREATED, Some(newToken), None)
          case _ =>
            (SC_UNAUTHORIZED, None, apiCodes.get('invalid_credentials)) // invalid username / password
        }

      reply ! CodeEntityOUT(code, result, apiCode)

    case InvalidateTokenIN(token) =>
      val deleted = authDb.deleteToken(token)
      val code =
        if (deleted) SC_OK
        else SC_UNAUTHORIZED

      reply ! CodeOUT(code)

    case RefreshTokenIN(token) =>
      val (code, result) =
        authDb.getToken(token) match {
          case Some(vo.Token(_, _, _, username)) =>
            val newToken = generateToken(username)
            authDb.saveToken(newToken)
            (SC_OK, Some(newToken))
          case _ =>
            (SC_UNAUTHORIZED, None)
        }

      reply ! CodeEntityOUT(code, result)

    case ValidateTokenIN(token) =>
      val t = authDb.getToken(token)
      val (code, result) =
        if (t.isDefined) (SC_OK, t)
        else (SC_UNAUTHORIZED, None)

      reply ! CodeEntityOUT(code, result)

  }

}

object UsersActor {
  def props(authDb: AuthDb): Props = Props(new UsersActor(authDb))

  case class CreateUserIN(username: String, obj: vo.AmendUser)
  case class UpdateUserIN(username: String, obj: vo.AmendUser)
  case class DeleteUserIN(username: String)
  case class InvalidateTokenByUsernameIN(username: String)
}

class UsersActor(authDb: AuthDb) extends Actor with ActorLogging with MsgInterceptor {
  import UsersActor._

  def receive = {

    case CreateUserIN(username, obj) =>
      authDb.saveUser(username, obj)
      reply ! CodeOUT(SC_CREATED)

    case UpdateUserIN(username, obj) =>
      authDb.saveUser(username, obj)
      reply ! CodeOUT(SC_OK)

    case DeleteUserIN(username) =>
      authDb.deleteTokenByUsername(username)
      authDb.deleteUser(username)
      reply ! CodeOUT(SC_OK)

    case InvalidateTokenByUsernameIN(username) =>
      authDb.deleteTokenByUsername(username)
      reply ! CodeOUT(SC_OK)

  }

}

