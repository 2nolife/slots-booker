package com.coldcore.slotsbooker
package test

import org.apache.http.HttpStatus._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import ms.paypal.vo
import spray.json._

abstract class BaseMsPaypalSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupTestUser()
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoRemovePlace()
  }

}

class MsPaypalSpec extends BaseMsPaypalSpec {

  val sale_complete_event_json =
    """
      |{
      |  "id": "WH-32402289KU4145032-67R32717E6919780U",
      |  "create_time": "2017-04-23T15:41:14.671Z",
      |  "resource_type": "sale",
      |  "event_type": "PAYMENT.SALE.COMPLETED",
      |  "summary": "Payment completed for GBP 1.0 GBP",
      |  "resource": {
      |    "amount": {
      |      "total": "1.00",
      |      "currency": "GBP",
      |      "details": {
      |        "subtotal": "1.00"
      |      }
      |    },
      |    "id": "8DD45480M3876045S",
      |    "parent_payment": "PAY-1KM22259E78131352LD6MVTI",
      |    "update_time": "2017-04-23T15:40:20Z",
      |    "create_time": "2017-04-23T15:40:20Z",
      |    "payment_mode": "INSTANT_TRANSFER",
      |    "state": "completed",
      |    "links": [
      |      {
      |        "href": "https://api.sandbox.paypal.com/v1/payments/sale/8DD45480M3876045S",
      |        "rel": "self",
      |        "method": "GET"
      |      },
      |      {
      |        "href": "https://api.sandbox.paypal.com/v1/payments/sale/8DD45480M3876045S/refund",
      |        "rel": "refund",
      |        "method": "POST"
      |      },
      |      {
      |        "href": "https://api.sandbox.paypal.com/v1/payments/payment/PAY-1KM22259E78131352LD6MVTI",
      |        "rel": "parent_payment",
      |        "method": "GET"
      |      }
      |    ],
      |    "invoice_number": "ref=48787589673",
      |    "protection_eligibility_type": "ITEM_NOT_RECEIVED_ELIGIBLE,UNAUTHORIZED_PAYMENT_ELIGIBLE",
      |    "custom": "place=663256237643868344,profile=87348398764847854",
      |    "transaction_fee": {
      |      "value": "0.23",
      |      "currency": "GBP"
      |    },
      |    "protection_eligibility": "ELIGIBLE"
      |  },
      |  "links": [
      |    {
      |      "href": "https://api.sandbox.paypal.com/v1/notifications/webhooks-events/WH-32402289KU4145032-67R32717E6919780U",
      |      "rel": "self",
      |      "method": "GET",
      |      "encType": "application/json"
      |    },
      |    {
      |      "href": "https://api.sandbox.paypal.com/v1/notifications/webhooks-events/WH-32402289KU4145032-67R32717E6919780U/resend",
      |      "rel": "resend",
      |      "method": "POST",
      |      "encType": "application/json"
      |    }
      |  ],
      |  "event_version": "1.0"
      |}
    """.stripMargin
  
  "POST to /paypal/events" should "accept an event" in {
    val url = s"$paypalBaseUrl/paypal/events"
    val json = sale_complete_event_json
    When postTo url entity json expect() code SC_OK
  }

  "POST to /paypal/events" should "accept an event with unsupported version" in {
    val url = s"$paypalBaseUrl/paypal/events"
    val json = """ { "id": "WH-32402289KU4145032-67R32717E6919780U", "event_version": "0.0" } """
    When postTo url entity json expect() code SC_OK
  }

  "POST to /paypal/events" should "give 400 if an incorrect json was sent" in {
    val url = s"$paypalBaseUrl/paypal/events"
    val json = """ { "abc": 123 } """
    When postTo url entity json expect() code SC_BAD_REQUEST
  }

}
