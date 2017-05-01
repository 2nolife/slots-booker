package com.coldcore.slotsbooker
package ms.paypal.actors

import akka.actor.{Actor, ActorLogging, Props}
import ms.rest.RequestInfo
import ms.paypal.service._
import ms.http.{ApiCode, RestClient}
import ms.actors.Common.CodeOUT
import ms.actors.MsgInterceptor
import ms.paypal.db.PaypalDb
import org.apache.http.HttpStatus._

import scala.concurrent.duration.DurationInt

trait EventCommands {
  case class IncomingEventIN(json: String) extends RequestInfo
}

object EventsActor extends EventCommands {
  def props(paypalDb: PaypalDb, placesBaseUrl: String, paymentsBaseUrl: String, systemToken: String, restClient: RestClient, sandboxMode: Boolean): Props =
    Props(new EventsActor(paypalDb, placesBaseUrl, paymentsBaseUrl, systemToken, restClient, sandboxMode))
}

class EventsActor(paypalDb: PaypalDb, placesBaseUrl: String, paymentsBaseUrl: String, systemToken: String,
                  restClient: RestClient, sandboxMode: Boolean) extends Actor with ActorLogging with MsgInterceptor {
  import EventsActor._
  import context.dispatcher

  case object Tick

  context.system.scheduler.schedule(10 seconds, 10 seconds, self, Tick)

  val eventService: EventService = new EventServiceImpl(paypalDb, placesBaseUrl, paymentsBaseUrl, systemToken, restClient, sandboxMode)

  def receive = {

    case in @ IncomingEventIN(json) =>
      val code =
        try {
          eventService.incomingEvent(json, in.iphost.get)
          ApiCode.OK
        } catch {
          case e: InvalidPayloadException =>
            log.error(s"Invalid event payload: ${e.payload}", e)
            ApiCode(SC_BAD_REQUEST)
        }
      
      reply ! CodeOUT(code)


    case Tick =>
      try {
        eventService.processNextEvent()
      } catch {
        case e: EventProcessException => log.error(s"Event process error: ${e.eventId}", e)
      }

  }

}
