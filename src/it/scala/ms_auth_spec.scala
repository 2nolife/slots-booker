package com.coldcore.slotsbooker
package test

import ms.vo.TokenRemote
import com.mongodb.casbah.Imports._
import org.apache.http.HttpStatus._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}

class MsAuthSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoSetupTestUser()
  }

  "POST to /auth/token" should "give 401 with invalid credentials" in {
    val url = s"$authBaseUrl/auth/token"
    val json = """{"username": "testuser", "password": "mypass"}"""
    (When postTo url entity json expect() code SC_UNAUTHORIZED).withApiCode("ms-auth-1")
  }

  "POST to /auth/token" should "create a token" in {
    val url = s"$authBaseUrl/auth/token"
    val json = """{"username": "testuser", "password": "testpass"}"""
    val token = (When postTo url entity json expect() code SC_CREATED).withBody[TokenRemote]

    token.username shouldBe "testuser"
    token.access_token.size should be > 10
  }

  "GET to /auth/token?access_token={?}" should "give 401 with invalid access_token parameter" in {
    val url = s"$authBaseUrl/auth/token?access_token=123"
    When getTo url expect() code SC_UNAUTHORIZED
  }

  "GET to /auth/token?access_token={?}" should "return a token" in {
    val url = s"$authBaseUrl/auth/token?access_token=Testuser_BearerToken"
    val token = (When getTo url expect() code SC_OK).withBody[TokenRemote]

    token.username shouldBe "testuser"
    token.access_token.size should be > 10
  }

  "PUT to /auth/users/{username}" should "give 401 with invalid system bearer token" in {
    val url = s"$authBaseUrl/auth/users/testnew"
    val json = """{"username": "testnew", "password": "newpass"}"""
    When putTo url entity json withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "PUT to /auth/users/{username}" should "create or update a user entry" in {
    val url = s"$authBaseUrl/auth/users/testnew"
    val json = """{"username": "testnew", "password": "newpass"}"""
    When putTo url entity json withHeaders systemTokenHeader expect() code SC_CREATED
  }

  "PATCH to /auth/users/{username}" should "give 401 with invalid system bearer token" in {
    val url = s"$authBaseUrl/auth/users/testnew"
    val json = """{"username": "testnew", "password": "newpass"}"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "PATCH to /auth/users/{username}" should "create or update a user entry" in {
    val url = s"$authBaseUrl/auth/users/testnew"
    val json = """{"username": "testnew", "password": "newpass2"}"""
    When patchTo url entity json withHeaders systemTokenHeader expect() code SC_OK
  }

  "DELETE to /auth/users/{username}" should "give 401 with invalid system bearer token" in {
    val url = s"$authBaseUrl/auth/users/testuser"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "DELETE to /auth/users/{username}" should "delete a user entry and invalidate a token" in {
    val url = s"$authBaseUrl/auth/users/testuser"
    When deleteTo url withHeaders systemTokenHeader expect() code SC_OK

    val urlA = s"$authBaseUrl/auth/token?access_token=Testuser_BearerToken"
    When getTo urlA expect() code SC_UNAUTHORIZED

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_UNAUTHORIZED
  }

  "DELETE to /auth/users/{username}/token" should "give 401 with invalid system bearer token" in {
    val url = s"$authBaseUrl/auth/users/testuser/token"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED
  }

  "DELETE to /auth/users/{username}/token" should "invalidate a token" in {
    val url = s"$authBaseUrl/auth/users/testuser/token"
    When deleteTo url withHeaders systemTokenHeader expect() code SC_OK

    val urlA = s"$authBaseUrl/auth/token?access_token=Testuser_BearerToken"
    When getTo urlA expect() code SC_UNAUTHORIZED

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

}
