package com.coldcore.slotsbooker
package ms.members.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import ms.members.actors.MembersActor._
import ms.rest.BaseRestService
import ms.members.vo

class MembersRestService(hostname: String, port: Int,
                          val membersActor: ActorRef,
                          externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, externalAuthActor, "Members") with MembersRoute {

  bind(membersRoute)
}

trait MembersRoute {
  self: MembersRestService =>

  def membersRoute =
    pathPrefix("members") {
      authenticateToken { profile =>

        path("member") {

          patch {
            entity(as[vo.UpdateMember]) { entity =>
              completeByActor[vo.Member](membersActor, UpdateMemberIN(entity, profile))
            }
          } ~
          get {
            parameters('place_id,
                       'profile_id ?) {
              (placeId,
               profileId) =>

              completeByActor[vo.Member](membersActor, GetMemberIN(placeId, profileId, profile))
            }
          }

        } ~
        path("search") {

          get {
            parameters('place_id) { placeId =>
              completeByActor[Seq[vo.Member]](membersActor, SearchMembersIN(placeId, profile))
            }
          }

        }

      }
    }

}
