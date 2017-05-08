package com.coldcore.slotsbooker
package test

import org.apache.http.HttpStatus._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import ms.payments.vo
import spray.json._

abstract class BaseMsPaymentsSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupTestUser()
    mongoSetupUser("testuser2")
    mongoSetupUser("testuser3")
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoRemovePlace()
  }

}

class MsPaymentsBalanceSpec extends BaseMsPaymentsSpec {

  "GET to /payments/balance?place_id={?}" should "give 401 with invalid bearer token" in {
    val url = s"$paymentsBaseUrl/payments/balance?place_id=$randomId"
    assert401_invalidToken { When getTo url }
  }

  "GET to /payments/balance?place_id={?}" should "return user balance even if entry does not exist" in {
    val placeId = mongoCreatePlace()
    mongoCreateBalance(placeId, 600)

    val url = s"$paymentsBaseUrl/payments/balance?place_id=$placeId"
    val balanceA = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe 600

    val headers = authHeaderSeq("testuser2")
    val balanceB = (When getTo url withHeaders headers expect() code SC_OK).withBody[vo.Balance]

    balanceB.credit shouldBe None
  }

  "GET to /payments/balance?place_id={?}&profile_id={?}" should "give 403 for non moderator" in {
    val placeId = mongoCreatePlace()

    val url = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$randomId"
    val headers = authHeaderSeq("testuser2")
    When getTo url withHeaders headers expect() code SC_FORBIDDEN
  }

  "GET to /payments/balance?place_id={?}&profile_id={?}" should "return place balance for that user" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser2")
    mongoCreateBalance(placeId, 600, username = "testuser2")

    val url = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$profileId"
    val balance = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balance.credit.get.head.amount.get shouldBe 600
  }

  "PATCH to /payments/balance" should "give 401 with invalid bearer token" in {
    val url = s"$paymentsBaseUrl/payments/balance"
    assert401_invalidToken { When patchTo url entity "{}" }
  }

  "PATCH to /payments/balance" should "give 403 for non moderator" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser")

    val url = s"$paymentsBaseUrl/payments/balance"
    val source = """{ "reason": "manual change" }"""
    val json = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "amount": 1200, "currency": "GBP", "source": $source }"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "PATCH to /payments/balance" should "add or remove amount for that user" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/balance"
    val headers = authHeaderSeq("testuser2")
    val source = """{ "reason": "manual change" }"""

    val jsonA = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "amount": 1200, "currency": "GBP", "source": $source }"""
    val balanceA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe 1200

    val jsonB = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "amount": -700, "currency": "GBP", "source": $source }"""
    val balanceB = (When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceB.credit.get.head.amount.get shouldBe 500

    val jsonC = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "amount": 150, "currency": "EUR", "source": $source }"""
    val balanceC = (When patchTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceC.credit.get.map(c => c.currency.get -> c.amount.get) shouldBe Seq("GBP" -> 500, "EUR" -> 150)
  }

}

class MsPaymentsAccountSpec extends BaseMsPaymentsSpec {

  "GET to /payments/account?place_id={?}" should "give 401 with invalid bearer token" in {
    val url = s"$paymentsBaseUrl/payments/account?place_id=$randomId"
    assert401_invalidToken { When getTo url }
  }

  "GET to /payments/account?place_id={?}" should "return place account" in {
    val placeId = mongoCreatePlace()
    mongoCreateAccount(placeId)

    val urlA = s"$paymentsBaseUrl/payments/account?place_id=$placeId"
    val accountA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Account]

    accountA.currencies.get.head.currency.get shouldBe "GBP"
  }

  "GET to /payments/account?place_id={?}" should "return place account even if entry does not exist" in {
    val placeId = mongoCreatePlace()

    val urlB = s"$paymentsBaseUrl/payments/account?place_id=$placeId"
    val accountB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Account]

    accountB.currencies shouldBe None
  }

  "PATCH to /payments/account" should "give 401 with invalid bearer token" in {
    val url = s"$paymentsBaseUrl/payments/account"
    assert401_invalidToken { When patchTo url entity "{}" }
  }

  "PATCH to /payments/account" should "give 403 for non moderator" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser")

    val url = s"$paymentsBaseUrl/payments/account"
    val json = s"""{ "place_id": "$placeId", "currency": "GBP" }"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "PATCH to /payments/balance" should "update place currency account attributes" in {
    val placeId = mongoCreatePlace()

    val url = s"$paymentsBaseUrl/payments/account"
    val source = """{ "reason": "manual change" }"""

    val jsonA = s"""{ "place_id": "$placeId", "currency": "GBP", "attributes": {"kez_rw": "value_a", "kez_rwp": "value_b"} }"""
    val accountA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Account]

    accountA.currencies.get.size shouldBe 1

    {
      val ca = accountA.currencies.get.head
      ca.currency.get shouldBe "GBP"
      ca.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("value_a"), "kez_rwp" -> JsString("value_b")))
    }

    val jsonB = s"""{ "place_id": "$placeId", "currency": "EUR", "attributes": {"kez_rw": "value_c"} }"""
    val accountB = (When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Account]

    accountB.currencies.get.size shouldBe 2

    val jsonC = s"""{ "place_id": "$placeId", "currency": "EUR", "attributes": {"kez_rw": "value_d"} }"""
    val accountC = (When patchTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Account]

    accountC.currencies.get.size shouldBe 2

    {
      val ca = accountC.currencies.get.last
      ca.currency.get shouldBe "EUR"
      ca.attributes.get.value shouldBe JsObject(Map("kez_rw" -> JsString("value_d")))
    }

  }

}

class MsPaymentsReferenceSpec extends BaseMsPaymentsSpec {

  def setupBalanceAndUnpaidQuote(balance: Int, status: Int = 2, forUsername: String = "testuser2"): Map[String,String] = {
    val placeId = mongoCreatePlace()
    mongoCreateBalance(placeId, balance, username = forUsername)
    val quoteId = mongoCreatePaidQuote(placeId, Seq((randomId, 1600), (randomId, 800)), status = status, username = forUsername)
    val bookedId = mongoCreateBooked(placeId, slotIds = Nil, bookingIds = Nil, username = forUsername)
    mongoCreateReference(placeId, Seq(bookedId), quoteId = Some(quoteId), username = forUsername)
    Map(
      "placeId" -> placeId
    )
  }

  "PATCH to /payments/reference/process" should "debit user balance as per reference quote" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(5000)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "ref": "Testuser2_1" }"""
    When patchTo url entity json withHeaders headers expect() code SC_OK

    val urlA = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$profileId"
    val balanceA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe 2600
  }

  "PATCH to /payments/reference/process" should "credit user balance as per reference refund" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser2")
    mongoCreateBalance(placeId, 5000, username = "testuser2")
    val refundId = mongoCreatePaidRefund(placeId, Seq((randomId, 1600), (randomId, 800)), status = 2, username = "testuser2")
    mongoCreateReference(placeId, Nil, refundId = Some(refundId), username = "testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "ref": "Testuser2_1" }"""
    When patchTo url entity json withHeaders headers expect() code SC_OK

    val urlA = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$profileId"
    val balanceA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe 7400
  }

  "PATCH to /payments/reference/process" should "give 409 if quote or refund is not due payment" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(5000, status = 1)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "ref": "Testuser2_1" }"""
    When patchTo url entity json withHeaders headers expect() code SC_CONFLICT

    val urlA = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$profileId"
    val balanceA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe 5000
  }

  "PATCH to /payments/reference/process" should "give 404 if reference does not exist" in {
    val url = s"$paymentsBaseUrl/payments/reference/process"
    val json = s"""{ "ref": "$randomId" }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_NOT_FOUND
  }

  "POST to /payments/reference/process" should "give 403 if non-moderator processes reference of another user" in {
    setupBalanceAndUnpaidQuote(0)
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser3")
    val json = s"""{ "ref": "Testuser2_1", "as_profile_id": "$profileId" }"""
    When patchTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "PATCH to /payments/reference/process" should "process reference of another user if submitted by moderator" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(5000)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "ref": "Testuser2_1", "as_profile_id": "$profileId" }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK

    val urlA = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$profileId"
    val balanceA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe 2600
  }

  "PATCH to /payments/reference/process" should "give 409 if not enough balance" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(2000)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "ref": "Testuser2_1" }"""
    When patchTo url entity json withHeaders headers expect() code SC_CONFLICT
  }

  "PATCH to /payments/reference/process" should "debit user balance as per reference quote and go to negative balance" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(2000)
      ids("placeId")
    }
    mongoSetPlaceAttributes(placeId, """{ "negative_balance": "y" }""".parseJson.asJsObject)
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference/process"
    val headers = authHeaderSeq("testuser2")
    val json = s"""{ "ref": "Testuser2_1" }"""
    When patchTo url entity json withHeaders headers expect() code SC_OK

    val urlA = s"$paymentsBaseUrl/payments/balance?place_id=$placeId&profile_id=$profileId"
    val balanceA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Balance]

    balanceA.credit.get.head.amount.get shouldBe -400
  }

  "GET to /payments/reference?ref={?]" should "return a reference for a user" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(2000)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference?ref=Testuser2_1"
    val headers = authHeaderSeq("testuser2")
    val reference = (When getTo url withHeaders headers expect() code SC_OK).withBody[vo.ext.Reference]

    reference.ref.get shouldBe "Testuser2_1"
  }

  "GET to /payments/reference?ref={?]&profile_id={?}" should "return a reference if submitted by moderator" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(2000)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference?ref=Testuser2_1&profile_id=$profileId"
    val headers = authHeaderSeq("testuser2")
    val reference = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.ext.Reference]

    reference.ref.get shouldBe "Testuser2_1"
  }

  "GET to /payments/reference?ref={?]&profile_id={?}" should "give 403 for non moderator" in {
    val placeId = {
      val ids = setupBalanceAndUnpaidQuote(2000)
      ids("placeId")
    }
    val profileId = mongoProfileId("testuser2")

    val url = s"$paymentsBaseUrl/payments/reference?ref=Testuser2_1&profile_id=$profileId"
    val headers = authHeaderSeq("testuser3")
    When getTo url withHeaders headers expect() code SC_FORBIDDEN
  }

  /*     todo
    "GET to /payments/references/unpaid?place_id={?}" should "return list of references which require payment for a place" in {
      fail()
    }
  */

}
