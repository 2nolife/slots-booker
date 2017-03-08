package com.coldcore.slotsbooker
package test

import akka.http.scaladsl.model.headers.Authorization
import ms.profiles.vo
import org.apache.http.HttpStatus._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import spray.json.{JsObject, JsString}

class MsProfilesSpec extends FlatSpec with BeforeAndAfterAll with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupTestUser()
    mongoSetupUser("testuser2")
  }

  override protected def afterAll() {
    systemStop()
  }

  "GET /profiles/{id}" should "return a profile" in {
    val profileId = mongoProfileId("testuser2")
    val url = s"$profilesBaseUrl/profiles/$profileId"
    val profile = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2"
    profile.email shouldBe None
  }

  "GET /profiles/{id}" should "return a profile authenticated by the system bearer token" in {
    val profileId = mongoProfileId("testuser2")
    val url = s"$profilesBaseUrl/profiles/$profileId"
    val profile = (When getTo url withHeaders systemTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2"
    profile.email.get shouldBe "testuser2@example.org"
  }

  "POST /profiles/register" should "create a new profile" in {
    val url = s"$profilesBaseUrl/profiles/register"
    val json = """{"username": "testnew", "password": "newpass", "email": "testnew@example.org"}"""
    val profile = (When postTo url entity json expect() code SC_CREATED).withBody[vo.Profile]

    profile.username.get shouldBe "testnew"
    profile.email.get shouldBe "testnew@example.org"
  }

  "POST /profiles/register" should "give 409 if a profile with the same username exists" in {
    val url = s"$profilesBaseUrl/profiles/register"
    val json = """{"username": "testuser", "password": "newpass", "email": "testuser2@example.org"}"""
    When postTo url entity json expect() code SC_CONFLICT
  }

  "POST /profiles/register" should "give 409 if a profile with the same email exists" in {
    val url = s"$profilesBaseUrl/profiles/register"
    val json = """{"username": "testuser2", "password": "newpass", "email": "testuser@example.org"}"""
    When postTo url entity json expect() code SC_CONFLICT
  }

  "GET to /profiles/{id}" should "return a profile with publicly readable attributes" in {
    val profileId = mongoProfileId("testuser2")
    mongoSetUserAttributes("""{"key_rw": "value_a", "key_rwp": "value_b", "key_r": "value_c", "key_p": "value_d", "key": "value_e"}""", username = "testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val profile = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.attributes.get.value shouldBe JsObject(Map("key_rwp" -> JsString("value_b"), "key_p" -> JsString("value_d")))
  }

  "GET to /profiles/search?{attribute_name}={?}" should "list found profiles by attributes joined by 'and' condition" in {
    mongoSetUserAttributes("""{"key_rw": "value_c", "key": "value_d"}""")
    mongoSetUserAttributes("""{"key_rw": "value_a", "key": "value_e"}""", username = "testuser2")

    val baseUrl = s"$profilesBaseUrl/profiles/search"

    val urlA = s"$baseUrl?key_rw=value_a"
    val profilesA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Profile]]

    profilesA.map(_.username.get) should contain only "testuser2"

    val urlB = s"$baseUrl?key=value*"
    val profilesB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Profile]]

    profilesB.map(_.username.get) should contain allOf("testuser", "testuser2")

    val urlC = s"$baseUrl?key_rw=value_a&key=value_e"
    val profilesC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Profile]]

    profilesC.size shouldBe 1
    profilesC.map(_.username.get) should contain only "testuser2"
  }

  "GET to /profiles/search?{attribute_name}={?}" should "list found profiles by attributes joined by 'or' condition" in {
    mongoSetUserAttributes("""{"key_rw": "value_c", "key": "value_d"}""")
    mongoSetUserAttributes("""{"key_rw": "value_a", "key": "value_e"}""", username = "testuser2")

    val baseUrl = s"$profilesBaseUrl/profiles/search"

    val urlA = s"$baseUrl?key_rw=value_a&key_rw=value_c&or"
    val profilesA = (When getTo urlA withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Profile]]

    profilesA.map(_.username.get) should contain allOf("testuser", "testuser2")

    val urlB = s"$baseUrl?key_rw=value_a&key=value_d&or"
    val profilesB = (When getTo urlB withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Profile]]

    profilesB.map(_.username.get) should contain allOf("testuser", "testuser2")

    val urlC = s"$baseUrl?key_rw=value_a&key=value_e&or"
    val profilesC = (When getTo urlC withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Profile]]

    profilesC.size shouldBe 1
    profilesC.map(_.username.get) should contain only "testuser2"
  }

}

class MsProfilesMeSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupUser("testuser2")
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoSetupTestUser()
  }

  "GET /profiles/me" should "give 401 with invalid bearer token" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val headers = Seq((Authorization.name, s"Bearer ABCDEF"))
    When getTo url withHeaders headers expect() code SC_UNAUTHORIZED
  }

  "GET /profiles/me" should "return a profile" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val profile = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser"
    profile.email.get shouldBe "testuser@example.org"
  }

  "PATCH /profiles/me" should "give 409 if a profile with the same username exists" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{ "username": "testuser2" }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT
  }

  "PATCH /profiles/me" should "give 409 if a profile with the same email exists" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{ "email": "testuser2@example.org" }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_CONFLICT
  }

  "PATCH /profiles/me" should "give 403 if a user tries to update roles" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{ "roles": ["TEST_ROLE_A", "TEST_ROLE_B"] }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "PATCH /profiles/me" should "give 403 if a user tries to update metadata" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{"metadata": {"key_a": "value_a", "key_b": "value_b"} }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "PATCH /profiles/me" should "update a profile" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{ "username": "testuser", "email": "testuser-new@example.org" }"""
    val profile = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser"
    profile.email.get shouldBe "testuser-new@example.org"
  }

  "PATCH /profiles/me" should "update a profile and also update the username in the auth service" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{ "username": "testusernew", "email": "testuser-new@example.org" }"""
    val profile = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testusernew"
    profile.email.get shouldBe "testuser-new@example.org"

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testusernew", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

  "PATCH /profiles/me" should "update a profile and also update the password in the auth service" in {
    val url = s"$profilesBaseUrl/profiles/me"
    val json = """{ "password": "testpassnew" }"""
    val profile = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser"
    profile.email.get shouldBe "testuser@example.org"

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser", "password": "testpassnew"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

  "DELETE /profiles/me" should "mark a profile as deleted" in {
    val url = s"$profilesBaseUrl/profiles/me"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_OK

    When getTo url withHeaders testuserTokenHeader expect() code SC_UNAUTHORIZED

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_UNAUTHORIZED
  }

  "PATCH to /profiles/me" should "update user writeable attributes" in {
    mongoSetUserAttributes("{}")

    val url = s"$profilesBaseUrl/profiles/me"
    val jsonA = """{"attributes": {"key_rw": "value_a", "key_rwp": "value_b"} }"""
    val profileA = (When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profileA.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("value_a"), "key_rwp" -> JsString("value_b")))

    val jsonB = """{"attributes": {"key_rw": "value_c"} }"""
    val profileB = (When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profileB.attributes.get.value shouldBe JsObject(Map("key_rw" -> JsString("value_c"), "key_rwp" -> JsString("value_b")))

    val jsonC = """{"attributes": {"key_rw": ""} }"""
    val profileC = (When patchTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profileC.attributes.get.value shouldBe JsObject(Map("key_rwp" -> JsString("value_b")))

    val jsonD = """{"attributes": {"key_rwp": ""} }"""
    val profileD = (When patchTo url entity jsonD withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profileD.attributes shouldBe None
  }

  "PATCH to /profiles/me" should "give 403 if a user tries to update non-writable and not configured attributes" in {
    mongoSetUserAttributes("{}")

    val url = s"$profilesBaseUrl/profiles/me"
    val jsonA = """{"attributes": {"key_r": "value_a"} }"""
    When patchTo url entity jsonA withHeaders testuserTokenHeader expect() code SC_FORBIDDEN

    val jsonB = """{"attributes": {"key_p": "value_a"} }"""
    When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_FORBIDDEN

    val jsonC = """{"attributes": {"key": "value_a"} }"""
    When patchTo url entity jsonC withHeaders testuserTokenHeader expect() code SC_FORBIDDEN

    val jsonD = """{"attributes": {"abc": "value_a"} }"""
    When patchTo url entity jsonD withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "GET to /profiles/me" should "return a profile with readable attributes" in {
    mongoSetUserAttributes("""{"key_rw": "value_a", "key_rwp": "value_b", "key_r": "value_c", "key_p": "value_d", "key": "value_e", "abc": "value_f"}""")

    val url = s"$profilesBaseUrl/profiles/me"
    val profile = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Profile]

    profile.attributes.get.value shouldBe JsObject(
      Map("key_rw" -> "value_a", "key_rwp" -> "value_b", "key_r" -> "value_c", "key_p" -> "value_d")
        .map { case (k,v) => k -> JsString(v) })
  }

}

class MsProfilesAdminSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()

    mongoSetupTestUser()
    mongoSetupUser("testadmin", roles = "ADMIN" :: Nil)
  }

  override protected def afterAll() {
    systemStop()
  }

  before {
    mongoSetupUser("testuser2")
  }

  "GET /profiles/search" should "give 403 if user is not admin" in {
    val url = s"$profilesBaseUrl/profiles/search"
    When getTo url withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "GET /profiles/search" should "return all profiles" in {
    val url = s"$profilesBaseUrl/profiles/search"
    val headers = authHeaderSeq("testadmin")
    val profiles = (When getTo url withHeaders headers expect() code SC_OK).withBody[Seq[vo.Profile]]

    profiles.map(_.username.get) should contain allOf("testuser", "testuser2", "testadmin")
  }

  "PATCH /profiles/{id}" should "give 403 if user is not admin" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{ }"""
    When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "PATCH /profiles/{id}" should "give 404 if a profile does not exist" in {
    val url = s"$profilesBaseUrl/profiles/$randomId"
    val json = """{ }"""
    val headers = authHeaderSeq("testadmin")
    When patchTo url entity json withHeaders headers expect() code SC_NOT_FOUND
  }

  "PATCH /profiles/{id}" should "give 409 if a profile with the same username exists" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{ "username": "testuser" }"""
    val headers = authHeaderSeq("testadmin")
    When patchTo url entity json withHeaders headers expect() code SC_CONFLICT
  }

  "PATCH /profiles/{id}" should "give 409 if a profile with the same email exists" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{ "email": "testuser@example.org" }"""
    val headers = authHeaderSeq("testadmin")
    When patchTo url entity json withHeaders headers expect() code SC_CONFLICT
  }

  "PATCH /profiles/{id}" should "update a profile" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{ "username": "testuser2", "email": "testuser2-new@example.org", "roles": ["TEST_ROLE_A", "TEST_ROLE_B"] }"""
    val headers = authHeaderSeq("testadmin")
    val profile = (When patchTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2"
    profile.email.get shouldBe "testuser2-new@example.org"
    profile.roles.get should contain only ("TEST_ROLE_A", "TEST_ROLE_B")
  }

  "PATCH /profiles/{id}" should "update a profile and also update the username in the auth service" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{ "username": "testuser2new", "email": "testuser2-new@example.org" }"""
    val headers = authHeaderSeq("testadmin")
    val profile = (When patchTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2new"
    profile.email.get shouldBe "testuser2-new@example.org"

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser2new", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

  "PATCH /profiles/{id}" should "update a profile and also update the password in the auth service" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{ "password": "testpass2new" }"""
    val headers = authHeaderSeq("testadmin")
    val profile = (When patchTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2"
    profile.email.get shouldBe "testuser2@example.org"

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser2", "password": "testpass2new"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

  "PATCH to /profiles/{id}" should "update arbitrary metadata" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{"metadata": {"key_a": "value_a", "key_b": "value_b"} }"""
    val headers = authHeaderSeq("testadmin")
    val profile = (When patchTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2"
    profile.metadata.get shouldBe JsObject(Map("key_a" -> JsString("value_a"), "key_b" -> JsString("value_b")))
  }

  "DELETE /profiles/{id}" should "give 403 if user is not admin" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "DELETE /profiles/{id}" should "mark a profile as deleted" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val headers = authHeaderSeq("testadmin")
    When deleteTo url withHeaders headers expect() code SC_OK

    When getTo url withHeaders headers expect() code SC_NOT_FOUND

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser2", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_UNAUTHORIZED
  }

  "DELETE /profiles/{id}/token" should "give 403 if user is not admin" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId/token"
    When deleteTo url withHeaders testuserTokenHeader expect() code SC_FORBIDDEN
  }

  "DELETE /profiles/{id}/token" should "invalidate a token" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId/token"
    val headers = authHeaderSeq("testadmin")
    When deleteTo url withHeaders headers expect() code SC_OK

    val urlA = s"$authBaseUrl/auth/token?access_token=Testuser2_BearerToken"
    When getTo urlA expect() code SC_UNAUTHORIZED

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testuser2", "password": "testpass"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

  "PATCH to /profiles/{id}" should "update any including not configured attributes" in {
    val profileId = mongoProfileId("testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val json = """{"attributes": {"key": "value_a", "key_rw": "value_b", "abc": "value_c"} }"""
    val headers = authHeaderSeq("testadmin")
    val profile = (When patchTo url entity json withHeaders headers expect() code SC_OK).withBody[vo.Profile]

    profile.username.get shouldBe "testuser2"
    profile.attributes.get.value shouldBe JsObject(
      Map("key" -> "value_a", "key_rw" -> "value_b", "abc" -> "value_c")
        .map { case (k,v) => k -> JsString(v) })
  }

  "GET to /profiles/{id}" should "return a profile with all attributes" in {
    val profileId = mongoProfileId("testuser2")
    mongoSetUserAttributes("""{"key_rw": "value_a", "key_rwp": "value_b", "key_r": "value_c", "key_p": "value_d", "key": "value_e", "abc": "value_f"}""",
      username = "testuser2")

    val url = s"$profilesBaseUrl/profiles/$profileId"
    val headers = authHeaderSeq("testadmin")
    val profile = (When getTo url withHeaders headers expect() code SC_OK).withBody[vo.Profile]

    profile.attributes.get.value shouldBe JsObject(
      Map("key_rw" -> "value_a", "key_rwp" -> "value_b", "key_r" -> "value_c", "key_p" -> "value_d", "key" -> "value_e", "abc" -> "value_f")
        .map { case (k,v) => k -> JsString(v) })
  }

}

class MsProfilesFlowSpec extends FlatSpec with BeforeAndAfterAll with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()
  }

  override protected def afterAll() {
    systemStop()
  }

  it should "register a new profile then login with new credentials" in {
    val urlA = s"$profilesBaseUrl/profiles/register"
    val jsonA = """{"username": "testnew", "password": "newpass", "email": "testnew@example.org"}"""
    When postTo urlA entity jsonA expect() code SC_CREATED

    val urlB = s"$authBaseUrl/auth/token"
    val jsonB = """{"username": "testnew", "password": "newpass"}"""
    When postTo urlB entity jsonB expect() code SC_CREATED
  }

}
