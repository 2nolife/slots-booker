package com.coldcore.slotsbooker
package ms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import akka.http.scaladsl.model.headers.Authorization
import ms.attributes.{Util => au}
import ms.http.{ApiCode, RestClient}
import ms.http.RestClient.HttpCallSuccessful
import ms.vo._
import ms.vo.SystemTokenAndProfile._
import org.apache.http.HttpStatus.SC_OK
import spray.json.RootJsonWriter

object ExternalAuthActor {
  def props(authBaseUrl: String, profilesBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new ExternalAuthActor(authBaseUrl, profilesBaseUrl, systemToken, restClient))

  case class ValidateTokenIN(token: String)
  case class ValidateTokenOUT(token: Option[TokenRemote])

  case class MyProfileIN(username: String)
  case class MyProfileOUT(profile: Option[ProfileRemote])

  case class ProfileByIdIN(profileId: String)
  case class ProfileByIdOUT(profile: Option[ProfileRemote])
}

class ExternalAuthActor(authBaseUrl: String, profilesBaseUrl: String, systemToken: String,
                        restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor {
  import ExternalAuthActor._

  implicit val executionContext = context.dispatcher

  def receive = {

    case ValidateTokenIN(token) =>
      val result =
        if (token == systemToken) {
          Some(systemTokenRemote(systemToken))
        } else {
          val json =
            restClient.get(s"$authBaseUrl/auth/token?access_token=$token") match {
              case HttpCallSuccessful(_, SC_OK, body, _) => Some(body)
              case _ => None
            }
          json.map(_.convertTo[TokenRemote])
        }

      reply ! ValidateTokenOUT(result)

    case MyProfileIN(token) =>
      val profile =
        if (token == systemToken) {
          Some(systemProfileRemote(systemToken))
        } else {
          val json =
            restClient.get(s"$profilesBaseUrl/profiles/me", (Authorization.name, s"Bearer $token")) match {
              case HttpCallSuccessful(_, SC_OK, body, _) => Some(body)
              case _ => None
            }
          json.map(_.convertTo[ProfileRemote])
        }

      reply ! MyProfileOUT(profile)

    case ProfileByIdIN(profileId) =>
      val json =
        restClient.get(s"$profilesBaseUrl/profiles/$profileId", (Authorization.name, s"Bearer $systemToken")) match {
          case HttpCallSuccessful(_, SC_OK, body, _) => Some(body)
          case _ => None
        }
      val profile = json.map(_.convertTo[ProfileRemote])

      reply ! ProfileByIdOUT(profile)

  }

}

trait InterceptFn {
  val incoming: PartialFunction[Any,Unit] = PartialFunction[Any,Unit](msg => Unit)
  val outgoing: PartialFunction[Any,Unit] = PartialFunction[Any,Unit](msg => Unit)
}

trait MsgInterceptor extends ReceivePipeline with ActorLogging {
  self: Actor =>

  val LogMessages = new InterceptFn {
    override val incoming = PartialFunction[Any,Unit](msg => log.debug(s"<-- $msg"))
    override val outgoing = PartialFunction[Any,Unit](msg => log.debug(s"--> $msg"))
  }

  val AssertAttributesExposed = new InterceptFn {
    override val outgoing = PartialFunction[Any,Unit] { case msg: Product => au.assertExposed(msg) }
  }

  val fns = LogMessages :: AssertAttributesExposed :: Nil

  pipelineInner {
    case msg => Inner {
      fns.foreach(_.incoming.apply(msg))
      msg
    }
  }

  class Reply(receiver: ActorRef) {
    def !(msg: Any) = {
      fns.reverse.foreach(_.outgoing.apply(msg))
      receiver ! msg
    }
  }

  def reply: Reply = reply(sender)
  def reply(receiver: ActorRef): Reply = new Reply(receiver)

}

object Common {

  case class CodeEntityOUT[T : RootJsonWriter](code: Int, entity: Option[T], apiCode: Option[String])
  object CodeEntityOUT {
    def apply[T : RootJsonWriter](code: Int, entity: Option[T]): CodeEntityOUT[T] = CodeEntityOUT(code, entity, None)
    def apply[T : RootJsonWriter](a: ApiCode, entity: Option[T]): CodeEntityOUT[T] = CodeEntityOUT(a.code, entity, a.apiCode)
  }

  case class CodeOUT(code: Int, apiCode: Option[String])
  object CodeOUT {
    def apply(code: Int): CodeOUT = CodeOUT(code, None)
    def apply(a: ApiCode): CodeOUT = CodeOUT(a.code, a.apiCode)
  }

}