package com.coldcore.slotsbooker
package ms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import akka.http.scaladsl.model.headers.Authorization
import akka.routing.FromConfig
import ms.rest.RequestInfo
import ms.attributes.{Util => au}
import ms.http.{ApiCode, RestClient}
import ms.vo._
import ms.vo.SystemTokenAndProfile._
import spray.json.RootJsonWriter

object ExternalAuthActor {
  def props(authBaseUrl: String, profilesBaseUrl: String, systemToken: String, restClient: RestClient): Props =
    Props(new ExternalAuthActor(authBaseUrl, profilesBaseUrl, systemToken, restClient))

  case class ValidateTokenIN(token: String, receiver: Option[ActorRef] = None) extends RequestInfo
  case class ValidateTokenOUT(token: Option[TokenRemote], receiver: Option[ActorRef] = None)

  case class MyProfileIN(token: String, receiver: Option[ActorRef] = None) extends RequestInfo
  case class MyProfileOUT(profile: Option[ProfileRemote], receiver: Option[ActorRef] = None)

  case class ProfileByIdIN(profileId: String, receiver: Option[ActorRef] = None) extends RequestInfo
  case class ProfileByIdOUT(profile: Option[ProfileRemote], receiver: Option[ActorRef] = None)
}

class ExternalAuthActor(authBaseUrl: String, profilesBaseUrl: String, systemToken: String,
                        restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor {
  import ExternalAuthActor._
  import ms.http.CodeWithBody._

  val (validateWorker, profileWorker) = (
    context.actorOf(Props(new ValidateWorker(authBaseUrl, profilesBaseUrl, systemToken, restClient)).withRouter(FromConfig), name = "validate"),
    context.actorOf(Props(new ProfileWorker(authBaseUrl, profilesBaseUrl, systemToken, restClient)).withRouter(FromConfig), name = "profile"))

  def receive = {

    case ValidateTokenIN(token, None) =>
      validateWorker ! ValidateTokenIN(token, Some(sender))

    case ValidateTokenOUT(token, Some(receiver)) =>
      reply(receiver) ! ValidateTokenOUT(token)

    case MyProfileIN(token, None) =>
      profileWorker ! MyProfileIN(token, Some(sender))

    case MyProfileOUT(profile, Some(receiver)) =>
      reply(receiver) ! MyProfileOUT(profile)

    case ProfileByIdIN(profileId, None) =>
      profileWorker ! ProfileByIdIN(profileId, Some(sender))

    case ProfileByIdOUT(profile, Some(receiver)) =>
      reply(receiver) ! ProfileByIdOUT(profile)

  }

  class ValidateWorker(authBaseUrl: String, profilesBaseUrl: String, systemToken: String,
                       restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor {
    def receive = {

      case ValidateTokenIN(token, receiver) =>
        val result =
          if (token == systemToken) Some(systemTokenRemote(systemToken))
          else restClient.get(s"$authBaseUrl/auth/token?access_token=$token").codeWithBody[TokenRemote]._2

        reply ! ValidateTokenOUT(result, receiver)

    }
  }

  class ProfileWorker(authBaseUrl: String, profilesBaseUrl: String, systemToken: String,
                      restClient: RestClient) extends Actor with ActorLogging with MsgInterceptor {
    def receive = {

      case MyProfileIN(token, receiver) =>
        val profile =
          if (token == systemToken) Some(systemProfileRemote(systemToken))
          else restClient.get(s"$profilesBaseUrl/profiles/me", (Authorization.name, s"Bearer $token")).codeWithBody[ProfileRemote]._2

        reply ! MyProfileOUT(profile, receiver)

      case ProfileByIdIN(profileId, receiver) =>
        val profile =
          restClient.get(s"$profilesBaseUrl/profiles/$profileId", (Authorization.name, s"Bearer $systemToken")).codeWithBody[ProfileRemote]._2

        reply ! ProfileByIdOUT(profile, receiver)

    }
  }
}

trait InterceptFn {
  val incoming: PartialFunction[Any,Unit] = PartialFunction[Any,Unit](msg => Unit)
  val outgoing: PartialFunction[Any,Unit] = PartialFunction[Any,Unit](msg => Unit)
}

trait MsgInterceptor extends ReceivePipeline with ActorLogging {
  self: Actor =>

  private val LogMessages = new InterceptFn {
    import Common._

    val clientInfo = (pfx: String, ri: RequestInfo) => ri.iphost.map(ih => s"$pfx ${ih.ip} ${ih.hostname}").getOrElse("")
    
    override val incoming = PartialFunction[Any,Unit] { msg =>
      val client = msg match {
        case ri: RequestInfo => clientInfo("from", ri)
        case _ => ""
      }
      log.debug(s"<-- $msg $client")
    }

    override val outgoing = PartialFunction[Any,Unit] { msg =>
      val client = msg match {
        case CodeEntityOUT(_, _, _, Some(ri)) => clientInfo("to", ri)
        case CodeOUT(_, _, Some(ri)) => clientInfo("to", ri)
        case _ => ""
      }
      log.debug(s"--> $msg $client")
    }
  }

  private val AssertAttributesExposed = new InterceptFn {
    override val outgoing = PartialFunction[Any,Unit] { case msg: Product => au.assertExposed(msg) }
  }

  private val fns = LogMessages :: AssertAttributesExposed :: Nil

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

  case class CodeEntityOUT[T : RootJsonWriter](code: Int, entity: Option[T], apiCode: Option[String], ri: Option[RequestInfo])
  object CodeEntityOUT {
    def apply[T : RootJsonWriter](code: Int, entity: Option[T]): CodeEntityOUT[T] = CodeEntityOUT(code, entity, None, None)
    def apply[T : RootJsonWriter](a: ApiCode, entity: Option[T]): CodeEntityOUT[T] = CodeEntityOUT(a.code, entity, a.apiCode, None)
    def apply[T : RootJsonWriter](code: Int, entity: Option[T], ri: RequestInfo): CodeEntityOUT[T] = CodeEntityOUT(code, entity, None, Some(ri))
    def apply[T : RootJsonWriter](a: ApiCode, entity: Option[T], ri: RequestInfo): CodeEntityOUT[T] = CodeEntityOUT(a.code, entity, a.apiCode, Some(ri))
  }

  case class CodeOUT(code: Int, apiCode: Option[String], ri: Option[RequestInfo])
  object CodeOUT {
    def apply(code: Int): CodeOUT = CodeOUT(code, None, None)
    def apply(a: ApiCode): CodeOUT = CodeOUT(a.code, a.apiCode, None)
    def apply(code: Int, ri: RequestInfo): CodeOUT = CodeOUT(code, None, Some(ri))
    def apply(a: ApiCode, ri: RequestInfo): CodeOUT = CodeOUT(a.code, a.apiCode, Some(ri))
  }

}