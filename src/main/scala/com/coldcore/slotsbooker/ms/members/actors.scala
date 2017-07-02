package com.coldcore.slotsbooker
package ms.members.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.vo.Implicits._
import ms.rest.RequestInfo
import ms.http.{ApiCode, RestClient, SystemRestCalls}
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.actors.MsgInterceptor
import ms.vo.ProfileRemote
import ms.members.db.MembersDb
import ms.members.vo
import ms.members.vo.Implicits._
import ms.members.Constants._
import org.apache.http.HttpStatus._

trait MemberCommands {
  case class UpdateMemberIN(obj: vo.UpdateMember, profile: ProfileRemote) extends RequestInfo
  case class GetMemberIN(placeId: String, profileId: Option[String], profile: ProfileRemote) extends RequestInfo
  case class SearchMembersIN(placeId: String, profile: ProfileRemote) extends RequestInfo
}

trait PlacesMsRestCalls extends SystemRestCalls {
  self: {
    val placesBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def placeFromMsPlaces(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    restGet[vo.ext.Place](s"$placesBaseUrl/places/$placeId?deep=false")
}

object MembersActor extends MemberCommands {
  def props(membersDb: MembersDb, placesBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new MembersActor(membersDb, placesBaseUrl, systemToken, restClient))
}

class MembersActor(val membersDb: MembersDb, val placesBaseUrl: String, val systemToken: String,
                   val restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor with PlacesMsRestCalls
  with AmendMember with GetMember with SearchMembers {

  def receive =
    amendMemberReceive orElse getMemberReceive orElse searchMembersReceive

  val placeModerator = (place: vo.ext.Place, profile: ProfileRemote) => place.isModerator(profile.profile_id)

}

trait AmendMember {
  self: MembersActor =>
  import MembersActor._

  val amendMemberReceive: Actor.Receive = {

    case UpdateMemberIN(obj, profile) =>
      lazy val (codeA, myPlace) = placeFromMsPlaces(obj.place_id)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canUpdate = placeModerator(myPlace.get, profile) || profile.isSuper

      def update(): Option[vo.Member] = Some(membersDb.updateMember(obj.place_id, obj.profile_id, obj))

      val (code, member) =
        if (placeNotFound) (codeA, None)
        else if (canUpdate) (ApiCode.OK, update())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, member)

  }

}

trait GetMember {
  self: MembersActor =>
  import MembersActor._

  val getMemberReceive: Actor.Receive = {

    case GetMemberIN(placeId, profileId, profile) =>
      lazy val (codeA, myPlace) = placeFromMsPlaces(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canRead = profileId.isEmpty || placeModerator(myPlace.get, profile) || profile.isSuper || profileId.exists(profile.profile_id ==)

      def read(): Option[vo.Member] = Some(membersDb.getMember(placeId, profileId.getOrElse(profile.profile_id)))

      val (code, member) =
        if (placeNotFound) (codeA, None)
        else if (canRead) (ApiCode.OK, read())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, member)

  }

}

trait SearchMembers {
  self: MembersActor =>
  import MembersActor._

  val searchMembersReceive: Actor.Receive = {

    case SearchMembersIN(placeId, profile) =>
      lazy val (codeA, myPlace) = placeFromMsPlaces(placeId)
      lazy val placeNotFound = myPlace.isEmpty
      lazy val canRead = placeModerator(myPlace.get, profile) || profile.isSuper

      def read(): Option[Seq[vo.Member]] = Some(membersDb.searchMembers(placeId))

      val (code, members) =
        if (placeNotFound) (codeA, None)
        else if (canRead) (ApiCode.OK, read())
        else (ApiCode(SC_FORBIDDEN), None)

      reply ! CodeEntityOUT(code, members)

  }

}
