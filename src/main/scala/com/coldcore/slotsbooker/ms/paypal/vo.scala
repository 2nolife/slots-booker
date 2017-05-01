package com.coldcore.slotsbooker
package ms.paypal.vo

import spray.json.{DefaultJsonProtocol, JsObject}
import ms.vo.Attributes

case class SourceInfo(ip: Option[String], hostname: Option[String])
object SourceInfo extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class Event(event_id: String, source: Option[JsObject], status: Option[Int], source_info: Option[SourceInfo])
object Event extends DefaultJsonProtocol { implicit val format = jsonFormat4(apply) }

/** External JSON objects from other micro services. */
package ext {

  case class Place(place_id: String, profile_id: String,
                   name: Option[String], moderators: Option[Seq[String]], attributes: Option[Attributes])
  object Place extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

  case class ProcessReference(ref: String, as_profile_id: Option[String])
  object ProcessReference extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class UpdateCredit(profile_id: String, place_id: String, amount: Int, currency: String, source: JsObject)
  object UpdateCredit extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

  /** PayPal Webhook Event */
  object Event10Parts {
    case class ResourceAmount(total: Option[String], currency: Option[String])
    object ResourceAmount extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

    case class Resource(amount: Option[ResourceAmount], invoice_number: Option[String], custom: Option[String])
    object Resource extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

    case class Links(href: Option[String], rel: Option[String])
    object Links extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }
  }

  import ext.{Event10Parts => ev10}
  
  /** PayPal Webhook Event */
  case class Event10(id: String, event_type: Option[String], summary: Option[String],
                     resource: Option[ev10.Resource], links: Option[Seq[ev10.Links]])
  object Event10 extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

  /** PayPal Webhook Event */
  case class EventVersion(id: String, event_version: String)
  object EventVersion extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

}
