package com.coldcore.slotsbooker
package ms.rest

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.RespondWithDirectives
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import ms.actors.Common.{CodeEntityOUT, CodeOUT}
import ms.vo.SystemTokenAndProfile._
import ms.{Logger, WhenTerminated}
import ms.actors.ExternalAuthActor._
import ms.vo.{ProfileRemote, TokenRemote}
import spray.json.RootJsonWriter

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Validates a bearer token extracted from the Authorization header by calling the Auth micro service.
  * Returns a "profile" of a user by getting it from the Profiles micro service.
  * In case if "system" token is used as the access token, a special token and profile will be returned without calling
  * any external micro services (this enables one micro service to get/submit data from/to another micro service).
  */
trait OAuth2TokenValidator {
  self: {
    val externalAuthActor: ActorRef
  } =>

  private implicit val timeout = Timeout(5 seconds)

  type TokenToTokenRemote = String => Future[Option[TokenRemote]]
  type TokenToProfileRemote = TokenRemote => Future[Option[ProfileRemote]]

  private def extractBearerToken(authHeader: Option[Authorization]): Option[String] =
    authHeader.collect { case Authorization(OAuth2BearerToken(token)) => token }

  private val authRejectedReason = AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2(""))

  /** Function that creates a fake token object to use with the "profiles" micro service.
    * As the "profiles" micro service also calls the "auth" micro service to validate the token,
    * no point of validating the token twice in other micro services.
    */
  def fakeTokenFn(implicit ec: ExecutionContext): TokenToTokenRemote =
    (access_token: String) => Future(Some(TokenRemote(access_token, "", 0, "")))

  /** Function to access the "auth" micro service and get a valid token from it by the access token. */
  def makeTokenFn(implicit ec: ExecutionContext): TokenToTokenRemote =
    (access_token: String) => (externalAuthActor ? ValidateTokenIN(access_token)).mapTo[ValidateTokenOUT].map(_.token)

  /** Same as "makeTokenFn" but can also return the "system" token if the access token matches. */
  def makeTokenFn(systemToken: String)(implicit ec: ExecutionContext): TokenToTokenRemote =
    (access_token: String) => if (access_token == systemToken) Future(Some(systemTokenRemote(systemToken))) else makeTokenFn.apply(access_token)

  /** Function to access the "profiles" micro service and get a profile from it by the access token. */
  def makeProfileFn(implicit ec: ExecutionContext): TokenToProfileRemote =
    (token: TokenRemote) => (externalAuthActor ? MyProfileIN(token.access_token)).mapTo[MyProfileOUT].map(_.profile)

  /** Same as "makeProfileFn" but can also return the "system" profile if the token matches. */
  def makeProfileFn(systemToken: String)(implicit ec: ExecutionContext): TokenToProfileRemote =
    (token: TokenRemote) => if (token.access_token == systemToken) Future(Some(systemProfileRemote(systemToken))) else makeProfileFn.apply(token)

  /** Validate if a token matches the shared system token. */
  def makeSystemTokenFn(systemToken: String)(implicit ec: ExecutionContext): TokenToTokenRemote =
    (access_token: String) => if (access_token == systemToken) Future(Some(systemTokenRemote(systemToken))) else Future(None)

  /** Always return the system profile. */
  def makeSystemProfileFn(systemToken: String)(implicit ec: ExecutionContext): TokenToProfileRemote =
    (token: TokenRemote) => Future(Some(systemProfileRemote(systemToken)))

  /** Function to access the "profiles" micro service and get a profile from it by profile ID. */
  def makeProfileByIdFn(profileId: String)(implicit ec: ExecutionContext): TokenToProfileRemote =
    (token: TokenRemote) => (externalAuthActor ? ProfileByIdIN(profileId)).mapTo[ProfileByIdOUT].map(_.profile)

  /** Use the "fake" token to call the "profiles" micro service.
    * The "profiles" micro service should validate the token.
    */
  def authenticateToken: Directive1[ProfileRemote] =
    extractRequestContext.flatMap { ctx ⇒
      import ctx.executionContext
      authenticateTokenAsync(fakeTokenFn, makeProfileFn)
    }

  /** Validate if a token matches the shared system token without calling external micro services. */
  def authenticateSystemToken(systemToken: String): Directive1[ProfileRemote] =
    extractRequestContext.flatMap { ctx ⇒
      import ctx.executionContext
      authenticateTokenAsync(makeSystemTokenFn(systemToken), makeSystemProfileFn(systemToken))
    }

  /** Validate if a token matches the shared system token and then return a profile by "profile_id" parameter. */
  def authenticateSystemToken(systemToken: String, profileId: String): Directive1[ProfileRemote] =
    extractRequestContext.flatMap { ctx ⇒
      import ctx.executionContext
      authenticateTokenAsync(makeSystemTokenFn(systemToken), makeProfileByIdFn(profileId))
    }

  def authenticateTokenAsync(foaFn: TokenToTokenRemote,
                             fobFn: TokenToProfileRemote,
                             default: Option[ProfileRemote] = None): Directive1[ProfileRemote] =
    extractRequestContext.flatMap { ctx =>
      import ctx.executionContext
      extractBearerToken(ctx.request.header[Authorization]) match {
        case Some(access_token) =>

          def foa: Future[Option[TokenRemote]] = foaFn(access_token)
          def fob(token: TokenRemote): Future[Option[ProfileRemote]] = fobFn(token)

          val f = FutureO(foa).flatMap(a => FutureO(fob(a))).future
          onSuccess(f).flatMap {
            case Some(p) => provide(p.copy(access_token = Some(access_token)))
            case _ if default.isDefined => provide(default.get)
            case _ => reject(authRejectedReason): Directive1[ProfileRemote]
          }
        case _ if default.isDefined =>
          provide(default.get)
        case _ =>
          reject(authRejectedReason)
      }
    }

}

trait HeartbeatRoute {

  def heartbeatRoute =
    path("heartbeat") {
      get {
        complete(HttpEntity(ContentTypes.`application/json`, """{"status": "ok"}"""))
      }
    }

}

trait HttpBindTo extends WhenTerminated {
  self: {
    val hostname: String
    val port: Int
  } =>

  /** Bind a service to HTTP and unding when the system terminates */
  def httpBindTo(route: Route)(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer) {
    val bindingFuture = Http().bindAndHandle(Route.handlerFlow(route), hostname, port)
    whenTerminated(bindingFuture.flatMap(_.unbind()))
  }

}

class BaseRestService(val hostname: String, val port: Int,
                      val externalAuthActor: ActorRef)(implicit system: ActorSystem)
  extends Logger with HttpBindTo with SprayJsonSupport with OAuth2TokenValidator with HeartbeatRoute with WhenTerminated with EnableCORSDirectives {

  initLoggingAdapter

  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(5 seconds)

  def bind(route: Route, name: String) {
    httpBindTo { enableCORS { heartbeatRoute ~ route }}
    log.info(s"Bound $name REST service to $hostname:$port")
  }

  def toStatusCode(code: Int): StatusCode = StatusCodes.getForKey(code).get

  private def completeWithHeaders(m: ToResponseMarshallable, apiCode: Option[String] = None) =
    if (apiCode.isDefined) respondWithHeader(RawHeader("X-Api-Code", apiCode.get)) { complete(m) } else complete(m)

  /** Actor provides a code and json entity */
  def completeByActor[T : RootJsonWriter : ClassTag](actor: ActorRef, in: AnyRef) =
    onSuccess(actor ? in) {
      case CodeEntityOUT(code, Some(entity: T), apiCode, _) => completeWithHeaders((toStatusCode(code), entity), apiCode)
      case CodeEntityOUT(code, None, apiCode, _) => completeWithHeaders(toStatusCode(code), apiCode)
      case CodeOUT(code, apiCode, _) => completeWithHeaders(toStatusCode(code), apiCode)
    }

  val clientIpHost: Directive1[ClientIpHost] =
    extractClientIP.flatMap(ip => extractHost.map(hostname => ClientIpHost(ip.toOption.map(_.getHostAddress).getOrElse("?"), hostname)))
}

/** CORS support: https://gist.github.com/evbruno/d173d3c111f8106061aa */
trait EnableCORSDirectives extends RespondWithDirectives {

  private val allowedCorsVerbs = List(CONNECT, DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE)
  private val allowedCorsHeaders = List("X-Requested-With", "content-type", "origin", "accept")

  lazy val enableCORS =
    respondWithHeader(`Access-Control-Allow-Origin`.`*`) &
      respondWithHeader(`Access-Control-Allow-Methods`(allowedCorsVerbs)) &
      respondWithHeader(`Access-Control-Allow-Headers`(allowedCorsHeaders)) &
      respondWithHeader(`Access-Control-Allow-Credentials`(true))
}

case class ClientIpHost(ip: String, hostname: String)

trait RequestInfo {
  var iphost: Option[ClientIpHost] = None
  def withIpHost(ih: ClientIpHost): RequestInfo = {
    iphost = Some(ih)
    this
  }
}
