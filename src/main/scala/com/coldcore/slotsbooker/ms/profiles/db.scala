package com.coldcore.slotsbooker
package ms.profiles.db

import java.util.regex.Pattern

import com.coldcore.slotsbooker.ms.vo.Attributes
import ms.profiles.Constants._
import ms.profiles.vo
import ms.db.MongoQueries
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import spray.json._

trait ProfilesDb extends ProfileCRUD with Search

trait ProfileCRUD {
  def profileByUsername(username: String): Option[vo.Profile]
  def profileByEmail(email: String): Option[vo.Profile]
  def profileById(profileId: String): Option[vo.Profile]
  def createProfile(obj: vo.RegisterUser): vo.Profile
  def deleteProfile(profileId: String): Boolean
  def rollbackProfile(profileId: String): Boolean
  def updateProfile(profileId: String, obj: vo.UpdateProfile): Option[vo.Profile]
}

trait Search {
  def searchProfiles(byAttributes: Seq[(String,String)], joinOR: Boolean): Seq[vo.Profile]
}

class MongoProfilesDb(client: MongoClient, dbName: String) extends ProfilesDb with MongoQueries with VoFactory
  with ProfileCrudImpl with SearchImpl {

  private val db = client(dbName)
  val profiles = db(MS)

}

trait VoFactory {
  self: MongoProfilesDb =>

  def asProfile(data: MongoDBObject): vo.Profile = {
    import data._
    vo.Profile(
      profile_id = as[ObjectId]("_id").toString,
      username = getAs[String]("username"),
      email = getAs[String]("email"),
      roles =
        getAs[Seq[String]]("roles")
        .noneIfEmpty,
      metadata =
        getAs[AnyRef]("metadata")
        .map(_.toString.parseJson.asJsObject),
      attributes =
        getAs[AnyRef]("attributes")
        .map(json => Attributes(json.toString))
    )
  }

}

trait ProfileCrudImpl {
  self: MongoProfilesDb =>

  override def profileByUsername(username: String): Option[vo.Profile] =
    profiles
      .findOne(finder("username" $eq username))
      .map(asProfile(_))

  override def profileByEmail(email: String): Option[vo.Profile] =
    profiles
      .findOne(finder("email" $eq email))
      .map(asProfile(_))

  override def profileById(profileId: String): Option[vo.Profile] =
    profiles
      .findOne(finderById(profileId))
      .map(asProfile(_))

  override def createProfile(obj: vo.RegisterUser): vo.Profile = {
    import obj._
    val profile = MongoDBObject(
      "username" -> username,
      "email" -> email,
      "roles" -> MongoDBList())

    profiles.insert(profile)

    val profileId = profile.getAs[ObjectId]("_id").get.toString
    profileById(profileId).get
  }

  override def deleteProfile(profileId: String): Boolean =
    softDeleteOne(finderById(profileId), profiles)

  override def rollbackProfile(profileId: String): Boolean =
    profiles
      .findAndRemove(finderById(profileId))
      .isDefined

  override def updateProfile(profileId: String, obj: vo.UpdateProfile): Option[vo.Profile] = {
    import obj._
    Map(
      "username" -> username,
      "email" -> email,
      "roles" -> roles,
      "metadata" -> metadata.map(asDBObject)
    ).foreach { case (key, value) =>
      update(finderById(profileId), profiles, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(profileId), profiles, "attributes", a.value))

    profileById(profileId)
  }

}

trait SearchImpl {
  self: MongoProfilesDb =>

  override def searchProfiles(byAttributes: Seq[(String,String)], joinOR: Boolean): Seq[vo.Profile] =
    profiles
      .find(finderByAttributes(byAttributes, joinOR))
      .map(asProfile(_))
      .toSeq

}
