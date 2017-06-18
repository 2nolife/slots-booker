package com.coldcore.slotsbooker
package ms.members.vo

import spray.json.DefaultJsonProtocol
import ms.vo.Attributes

case class UpdateMember(profile_id: String, place_id: String, level: Option[Int])
object UpdateMember extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class Member(profile_id: String, place_id: String, level: Option[Int])
object Member extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

/** External JSON objects from other micro services. */
package ext {

  case class Place(place_id: String, profile_id: String,
                   name: Option[String], moderators: Option[Seq[String]], attributes: Option[Attributes])
  object Place extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

}

object Implicits {

  implicit class PlaceExt(obj: ext.Place) {

    def isModerator(profileId: String): Boolean =
      obj.moderators.getOrElse(Nil).exists(profileId ==) || obj.profile_id == profileId

 }

}