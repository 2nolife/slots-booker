package com.coldcore.slotsbooker
package ms.members.db

import ms.Timestamp
import ms.db.MongoQueries
import ms.members.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import ms.members.vo

trait MembersDb extends MemberCRUD with Search

trait MemberCRUD {
  def getMember(placeId: String, profileId: String): vo.Member
  def updateMember(placeId: String, profileId: String, obj: vo.UpdateMember): vo.Member
}

trait Search {
  def searchMembers(placeId: String): Seq[vo.Member]
}

class MongoMembersDb(client: MongoClient, dbName: String) extends MembersDb with VoFactory with MongoQueries
  with MemberCrudImpl with SearchImpl {

  private val db = client(dbName)
  val members = db(MS)

}

trait VoFactory {
  self: MongoMembersDb =>

  def asMember(data: MongoDBObject): vo.Member = {
    import data._
    vo.Member(
      profile_id = as[String]("profile_id"),
      place_id = as[String]("place_id"),
      level = getAs[Int]("level")
    )
  }

}

trait MemberCrudImpl {
  self: MongoMembersDb =>

  override def getMember(placeId: String, profileId: String): vo.Member =
    members
      .findOne(("place_id" $eq placeId) ++ ("profile_id" $eq profileId))
      .map(asMember(_))
      .getOrElse(vo.Member(profileId, placeId, level = None))

  private def ensureMemberRecord(placeId: String, profileId: String): String = {
    val recordFinder = ("place_id" $eq placeId) ++ ("profile_id" $eq profileId)
    members
      .update(
        recordFinder,
        $setOnInsert(
          "place_id" -> placeId,
          "profile_id" -> profileId,
          "entry.created" -> Timestamp.asLong),
        upsert = true)

    members
      .findOne(recordFinder)
      .map(_.idString)
      .get
  }

  override def updateMember(placeId: String, profileId: String, obj: vo.UpdateMember): vo.Member = {
    import obj._
    val memberId = ensureMemberRecord(placeId, profileId)

    Map(
      "level" -> level
    ).foreach { case (key, value) =>
      update(finderById(memberId), members, key, value)
    }

    entryUpdated(memberId, members)

    members
      .findOne(finderById(memberId))
      .map(asMember(_))
      .get
  }

}

trait SearchImpl {
  self: MongoMembersDb =>

  override def searchMembers(placeId: String): Seq[vo.Member] =
    members
      .find(("place_id" $eq placeId) ++ ("level" $gt 0))
      .map(asMember(_))
      .toSeq

}
