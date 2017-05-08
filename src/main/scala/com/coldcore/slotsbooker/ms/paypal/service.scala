package com.coldcore.slotsbooker
package ms.paypal.service

import ms.paypal.vo.SourceInfo
import ms.rest.{ClientIpHost}
import ms.paypal.db.PaypalDb
import ms.http.{ApiCode, RestClient, SystemRestCalls}
import ms.paypal.vo
import ms.utils.StringUtil._
import ms.vo.EmptyEntity
import ms.paypal.Constants._
import spray.json._

class InvalidPayloadException(val payload: String, cause: Throwable) extends RuntimeException(cause)
class EventProcessException(val eventId: String, cause: Throwable) extends RuntimeException(cause)

trait PlacesMsRestCalls extends SystemRestCalls {
  self: {
    val placesBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def placeFromMsPlaces(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    restGet[vo.ext.Place](s"$placesBaseUrl/places/$placeId?deep=false")
}

trait PaymentsMsRestCalls extends SystemRestCalls {
  self: {
    val paymentsBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  def processReferenceWithMsPaymets(ref: String, profileId: String): ApiCode =
    restPatch[EmptyEntity](s"$paymentsBaseUrl/payments/reference/process", vo.ext.ProcessReference(ref, Some(profileId)))._1

  def updateCreditWithMsPaymets(placeId: String, profileId: String, amount: Int, currency: String, source: JsObject): ApiCode =
    restPatch[EmptyEntity](s"$paymentsBaseUrl/payments/balance", vo.ext.UpdateCredit(profileId, placeId, amount, currency, source))._1
}

trait PaypalService {
  def placeById(placeId: String): (ApiCode, Option[vo.ext.Place])
}

class PaypalServiceImpl(val paypalDb: PaypalDb, val placesBaseUrl: String, val paymentsBaseUrl: String, val systemToken: String,
                        val restClient: RestClient)
  extends PaypalService with PlacesMsRestCalls with PaymentsMsRestCalls with Auxiliary


trait Auxiliary {
  self: PaypalServiceImpl =>

  override def placeById(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    placeFromMsPlaces(placeId)

}

trait EventService {
  def incomingEvent(json: String, iphost: ClientIpHost)
  def processNextEvent()
}

class EventServiceImpl(val paypalDb: PaypalDb, val placesBaseUrl: String, val paymentsBaseUrl: String, val systemToken: String,
                       val restClient: RestClient, val sandboxMode: Boolean, val liveEventIp: String)
  extends EventService with PlacesMsRestCalls with PaymentsMsRestCalls with ProcessEvent

trait ProcessEvent {
  self: EventServiceImpl =>

  @throws[InvalidPayloadException]
  override def incomingEvent(json: String, iphost: ClientIpHost): Unit = {
    val source =
      try {
        val obj = json.parseJson.asJsObject
        val event = obj.convertTo[vo.ext.EventVersion]
        val (version, id) = (event.event_version, event.id) // version and ID are required
        obj
      } catch {
        case e: Throwable => throw new InvalidPayloadException(json, e)
      }

    paypalDb.createEvent(source, SourceInfo(Some(iphost.ip), Some(iphost.hostname)))
  }

  private def failEvent(eventId: String, cause: String) =
    paypalDb.updateEventStatus(eventId, eventStatus('failed), Some(cause))

  private def processEvent10_paymentCompleted(event: vo.Event) {
    val source = event.source.get.convertTo[vo.ext.Event10]
    val amount = source.resource.get.amount.get
    val (total, currency) = (toX100(amount.total.get).toInt, amount.currency.get)
    val (custom, invoice) = (source.resource.get.custom, source.resource.get.invoice_number)

    val placeId = custom.flatMap(parseCSVMap(_).get("place")) // required
    val profileId = custom.flatMap(parseCSVMap(_).get("profile")) // required
    val ref = invoice.flatMap(parseCSVMap(_).get("ref")) // optional

    def updateCredit(): Boolean = {
      val code = updateCreditWithMsPaymets(placeId.get, profileId.get, total, currency, JsObject("reason" -> JsString("PayPal event "+event.event_id)))
      if (code != ApiCode.OK) failEvent(event.event_id, s"update credit ${code.csv}")
      code == ApiCode.OK
    }

    def processReference(): Boolean =
      ref.forall { _ =>
        val code = processReferenceWithMsPaymets(ref.get, profileId.get)
        if (code != ApiCode.OK) failEvent(event.event_id, s"process reference ${code.csv}")
        code == ApiCode.OK
      }
    
    if (placeId.isEmpty || profileId.isEmpty) failEvent(event.event_id, "invalid custom field")
    else if (updateCredit() && processReference()) paypalDb.updateEventStatus(event.event_id, eventStatus('complete))
  }

  private def validateEvent10(event: vo.Event): Boolean = {
    val source = event.source.get.convertTo[vo.ext.Event10]
    val selfHref = source.links.getOrElse(Nil).find(_.rel.get == "self").map(_.href.get).getOrElse("")

    def validateLive: Boolean = {
      val ip = event.source_info.get.ip.getOrElse("")
      parseCSV(liveEventIp).exists(ip ==)
    }

    (selfHref match {
      case _ if selfHref.contains("//api.sandbox.paypal.com/") => 'sandbox
      case _ if selfHref.contains("//api.paypal.com/") => 'live
      case _ => 'unknown
    }) match {
      case 'sandbox if sandboxMode => true
      case 'sandbox =>
        failEvent(event.event_id, "sandbox event")
        false
      case 'live if validateLive => true
      case 'live =>
        failEvent(event.event_id, "live validation")
        false
      case _ =>
        failEvent(event.event_id, "validation error")
        false
    }
  }

  private def processEvent10(event: vo.Event) =
    if (validateEvent10(event)) {
      val source = event.source.get.convertTo[vo.ext.Event10]
      source.event_type.get match {
        case "PAYMENT.SALE.COMPLETED" => processEvent10_paymentCompleted(event)
        case _ => failEvent(event.event_id, "unsupported type")
      }
    }

  @throws[EventProcessException]
  override def processNextEvent() {
    val nextEvent = paypalDb.nextNewEvent
    nextEvent.foreach { event =>
      val ev = event.source.get.convertTo[vo.ext.EventVersion]
      val duplicate = paypalDb.isDuplicateEvent(event.event_id, ev.id)

      ev.event_version match {
        case _ if duplicate => paypalDb.updateEventStatus(event.event_id, eventStatus('duplicate))
        case "1.0" =>
          try { processEvent10(event) } catch {
            case e: Throwable =>
              failEvent(event.event_id, "general error")
              throw new EventProcessException(event.event_id, e)
          }
        case _ => failEvent(event.event_id, "unsupported version")
      }
    }
  }

}
