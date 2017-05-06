package com.coldcore.slotsbooker
package ms.http

import akka.http.scaladsl.model.headers.Authorization
import org.apache.http.HttpStatus._
import org.apache.http.client.HttpClient
import org.apache.http.client.methods._
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
import spray.json._
import RestClient._
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager

import scala.io.Source

object RestClient {
  sealed trait HttpCall
  case class HttpCallSuccessful(url: String, code: Int, json: JsValue, rh: Seq[(String,String)]) extends HttpCall
  case class HttpCallFailed(url: String, error: Throwable) extends HttpCall
}

class RestClient(connPerRoute: Int = 5, connMaxTotal: Int = 20) {
  import RestClient._

  private val cm = new PoolingHttpClientConnectionManager()
  cm.setMaxTotal(connMaxTotal)
  cm.setDefaultMaxPerRoute(connPerRoute)

  private val client = HttpClientBuilder.create.setConnectionManager(cm).build

  private def doHttpCall(url: String, client: HttpClient, method: HttpRequestBase): HttpCall = {
    try {
      val response = client.execute(method)
      val content = Source.fromInputStream(response.getEntity.getContent).mkString
      val json =
        try { content.parseJson } catch {
          case _: Throwable => ms.vo.ContentEntity(content).toJson
        }

      HttpCallSuccessful(url, response.getStatusLine.getStatusCode, json,
        response.getAllHeaders.map(x => (x.getName, x.getValue)))
    } catch {
      case e : Throwable => HttpCallFailed(url, e)
    } finally {
      method.releaseConnection()
    }
  }

  /** HTTP {method} as "application/json", overwrite with "rh" as "Content-type", "text/xml; charset=UTF-8" */
  private def post_put_patch(method: HttpEntityEnclosingRequestBase, url: String, json: JsObject, rh: Seq[(String,String)]): HttpCall = {
    method.setEntity(new StringEntity(json.toString, ContentType.create("application/json", "UTF-8")))
    for ((name, value) <- rh) method.setHeader(name, value)
    doHttpCall(url, client, method)
  }

  private def get_delete(method: HttpRequestBase, url: String, rh: Seq[(String,String)]): HttpCall = {
    for ((name, value) <- rh) method.setHeader(name, value)
    doHttpCall(url, client, method)
  }

  def close() {
    client.close()
    cm.close()
  }

  def get(url: String, rh: (String,String)*): HttpCall =
    get_delete(new HttpGet(url), url, rh)

  def delete(url: String, rh: (String,String)*): HttpCall =
    get_delete(new HttpDelete(url), url, rh)

  def post(url: String, json: JsObject, rh: (String,String)*): HttpCall =
    post_put_patch(new HttpPost(url), url, json, rh)

  def put(url: String, json: JsObject, rh: (String,String)*): HttpCall =
    post_put_patch(new HttpPut(url), url, json, rh)

  def patch(url: String, json: JsObject, rh: (String,String)*): HttpCall =
    post_put_patch(new HttpPatch(url), url, json, rh)

}

object CodeWithBody {

  implicit class HttpCallX(call: HttpCall) {
    private val apiCodeHeader = (rh: Seq[(String,String)]) => rh.find(_._1 == "X-Api-Code").map(_._2)

    def codeWithBody[T : JsonReader]: (ApiCode, Option[T]) =
      call match {
        case HttpCallSuccessful(_, code @ (SC_OK | SC_CREATED), body, rh) => (ApiCode(code, apiCodeHeader(rh)), Some(body.convertTo[T]))
        case HttpCallSuccessful(_, code, _, rh) => (ApiCode(code, apiCodeHeader(rh)), None)
        case _ => (SC_INTERNAL_SERVER_ERROR, None)
      }

    def code: ApiCode =
      call match {
        case HttpCallSuccessful(_, code, _, rh) => ApiCode(code, apiCodeHeader(rh))
        case _ => SC_INTERNAL_SERVER_ERROR
      }
  }
}

trait SystemRestCalls {
  self: {
    val systemToken: String
    val restClient: RestClient
  } =>

  import CodeWithBody._

  implicit def obj2json[T : JsonWriter](obj: T): JsObject = obj.toJson.asJsObject

  private val header = (Authorization.name, s"Bearer $systemToken")

  def restGet[T : JsonReader](url: String): (ApiCode, Option[T]) =
    restClient.get(url, header).codeWithBody

  def restDelete[T : JsonReader](url: String): (ApiCode, Option[T]) =
    restClient.delete(url, header).codeWithBody

  def restPost[T : JsonReader](url: String, obj: JsObject): (ApiCode, Option[T]) =
    restClient.post(url, obj, header).codeWithBody

  def restPut[T : JsonReader](url: String, obj: JsObject): (ApiCode, Option[T]) =
    restClient.put(url, obj, header).codeWithBody

  def restPatch[T : JsonReader](url: String, obj: JsObject): (ApiCode, Option[T]) =
    restClient.patch(url, obj, header).codeWithBody

}

case class ApiCode(code: Int, apiCode: Option[String]) {
  def is(a: Int): Boolean = code == a
  def not(a: Int): Boolean = code != a
  def csv: String = code+apiCode.map(","+_).mkString
  def +(other: ApiCode): ApiCode = {
    val v = List(apiCode, other.apiCode).flatten.mkString(",")
    ApiCode(code, if (v.nonEmpty) Some(v) else None)
  }
  def +(others: Seq[ApiCode]): ApiCode = if (others.nonEmpty) others.foldLeft(this)((a,b) => a + b) else this
}

object ApiCode {
  implicit def toApiCode(code: Int): ApiCode = ApiCode(code)
  def apply(code: Int): ApiCode = ApiCode(code, None)

  val OK = ApiCode(SC_OK)
  val CREATED = ApiCode(SC_CREATED)
}
