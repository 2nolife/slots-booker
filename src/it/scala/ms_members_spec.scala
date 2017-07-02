package com.coldcore.slotsbooker
package test

import org.apache.http.HttpStatus._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import ms.members.vo
import spray.json._

abstract class BaseMsMembersSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

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

class MsMembersSpec extends BaseMsMembersSpec {

  "GET to /members/member?place_id={?}" should "give 401 with invalid bearer token" in {
    val url = s"$membersBaseUrl/members/member?place_id=$randomId"
    assert401_invalidToken { When getTo url }
  }

  "GET to /members/member?place_id={?}" should "return a member even if entry does not exist" in {
    val placeId = mongoCreatePlace()
    mongoCreateMember(placeId)

    val url = s"$membersBaseUrl/members/member?place_id=$placeId"
    val memberA = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Member]

    memberA.level.get shouldBe 1

    val headers = authHeaderSeq("testuser2")
    val memberB = (When getTo url withHeaders headers expect() code SC_OK).withBody[vo.Member]

    memberB.level shouldBe None
  }

  "GET to /members/member?place_id={?}&profile_id={?}" should "give 403 for non moderator" in {
    val placeId = mongoCreatePlace()

    val url = s"$membersBaseUrl/members/member?place_id=$placeId&profile_id=$randomId"
    val headers = authHeaderSeq("testuser2")
    When getTo url withHeaders headers expect() code SC_FORBIDDEN
  }

  "GET to /members/member?place_id={?}&profile_id={?}" should "return a member for that user" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser2")
    mongoCreateMember(placeId, username = "testuser2")

    val url = s"$membersBaseUrl/members/member?place_id=$placeId&profile_id=$profileId"
    val member = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Member]

    member.level.get shouldBe 1
  }

  "PATCH to /members/member" should "give 401 with invalid bearer token" in {
    val url = s"$membersBaseUrl/members/member"
    assert401_invalidToken { When patchTo url entity "{}" }
  }

  "PATCH to /members/member" should "give 403 for non moderator" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser")

    val url = s"$membersBaseUrl/members/member"
    val json = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "level": 2 }"""
    val headers = authHeaderSeq("testuser2")
    When patchTo url entity json withHeaders headers expect() code SC_FORBIDDEN
  }

  "PATCH to /members/member" should "update a member" in {
    val placeId = mongoCreatePlace()
    val profileId = mongoProfileId("testuser2")

    val url = s"$membersBaseUrl/members/member"
    val json = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "level": 2 }"""
    val member = (When patchTo url entity json withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Member]

    member.level.get shouldBe 2

    val jsonB = s"""{ "profile_id": "$profileId", "place_id": "$placeId", "level": 0 }"""
    val memberB = (When patchTo url entity jsonB withHeaders testuserTokenHeader expect() code SC_OK).withBody[vo.Member]

    memberB.level.get shouldBe 0
  }

  "GET to /members/search?place_id={?}" should "give 403 for non moderator" in {
    val placeId = mongoCreatePlace()

    val url = s"$membersBaseUrl/members/search?place_id=$placeId"
    val headers = authHeaderSeq("testuser2")
    When getTo url withHeaders headers expect() code SC_FORBIDDEN
  }

  "GET to /members/search?place_id={?}" should "list found members that place" in {
    val placeId = mongoCreatePlace()
    val profileId2 = mongoProfileId("testuser2")
    val profileId3 = mongoProfileId("testuser3")
    mongoCreateMember(placeId, username = "testuser2")
    mongoCreateMember(placeId, level = 0, username = "testuser3")

    val url = s"$membersBaseUrl/members/search?place_id=$placeId"
    val members = (When getTo url withHeaders testuserTokenHeader expect() code SC_OK).withBody[Seq[vo.Member]]

    members.map(_.profile_id) should contain only profileId2
  }
}
