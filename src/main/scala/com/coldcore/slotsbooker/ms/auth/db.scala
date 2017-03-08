package com.coldcore.slotsbooker
package ms.auth.db

import ms.auth.Constants._
import ms.auth.vo
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject

trait AuthDb extends TokenCRUD with UserCRUD

trait TokenCRUD {
  def userLogin(username: String, password: String): (Boolean, Option[vo.Token])
  def deleteToken(token: String): Boolean
  def deleteTokenByUsername(username: String): Boolean
  def saveToken(token: vo.Token)
  def getToken(token: String): Option[vo.Token]
}

trait UserCRUD {
  def saveUser(username: String, obj: vo.AmendUser)
  def deleteUser(username: String): Boolean
}

class MongoAuthDb(client: MongoClient, dbName: String) extends AuthDb with VoFactory
  with TokenCrudImpl with UserCrudImpl {

  private val db = client(dbName)
  val users = db(s"$MS-users")
  val tokens = db(s"$MS-tokens")

}

trait VoFactory {
  self: MongoAuthDb =>

  def asToken(data: MongoDBObject): vo.Token = {
    import data._
    val expires = (as[Number]("expires").longValue-System.currentTimeMillis)/1000L
    vo.Token(
      as[String]("token"),
      as[String]("type"),
      expires.toInt,
      as[String]("username"))
  }

}

trait TokenCrudImpl {
  self: MongoAuthDb =>

  override def userLogin(username: String, password: String): (Boolean, Option[vo.Token]) =
    users
      .findOne(MongoDBObject("username" -> username, "password" -> password))
      .map { _ =>
        val existingToken =
          tokens
            .findOne(("username" $eq username) ++ ("expires" $gt System.currentTimeMillis))
            .map(asToken(_))
        (true, existingToken)
      }
      .getOrElse(false, None)

  override def deleteToken(token: String): Boolean =
    tokens.findAndRemove("token" $eq token).isDefined

  override def deleteTokenByUsername(username: String): Boolean =
    tokens.findAndRemove("username" $eq username).isDefined

  override def saveToken(token: vo.Token) {
    import token._
    val mills = expires*1000L+System.currentTimeMillis
    tokens.
      update(
        "username" $eq username,
        MongoDBObject("username" -> username, "token" -> access_token, "type" -> token_type, "expires" -> mills),
        upsert = true)
  }

  override def getToken(token: String): Option[vo.Token] =
    tokens
      .findOne(("token" $eq token) ++ ("expires" $gt System.currentTimeMillis))
      .map(asToken(_))

}

trait UserCrudImpl {
  self: MongoAuthDb =>

  override def saveUser(username: String, obj: vo.AmendUser) {
    val password =
      users
        .findOne("username" $eq username)
        .map(_.as[String]("password"))
        .orNull

    users.
      update(
        "username" $eq username,
        MongoDBObject(
          "username" -> obj.username.getOrElse(username),
          "password" -> obj.password.getOrElse(password)),
        upsert = true)
  }

  override def deleteUser(username: String): Boolean =
    users.findAndRemove("username" $eq username).isDefined

}