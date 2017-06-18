package com.coldcore.slotsbooker
package test

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.Authorization
import ms.http.RestClient
import ms.http.RestClient.{HttpCall, HttpCallSuccessful}
import ms._
import ms.db.MongoQueries
import ms.config.Constants.APP
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoCollection}
import com.mongodb.casbah.Imports._
import spray.json.{JsObject, _}
import org.apache.http.HttpStatus._
import org.bson.types.ObjectId
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/** Common trait to include into the integration tests. */
trait HelperObjects extends MongoOps with SystemStart with RestClientOps with RestClientDSL with RestAssert with FilesReader {

  implicit def string2jso(s: String): JsObject = s.parseJson.asJsObject

}

/** Read files from resources directory. */
trait FilesReader {

  def readFileAsString(path: String): String = {
    val stream = getClass.getResourceAsStream(path)
    scala.io.Source.fromInputStream(stream).mkString
  }

}

/** REST client and authorization headers. */
trait RestClientOps {

  val restClient = new RestClient

  val authHeaderSeq = (username: String) =>
    Seq((Authorization.name, s"Bearer ${username.capitalize}_BearerToken"))

  val testuserTokenHeader = authHeaderSeq("testuser")
  val systemTokenHeader = Seq((Authorization.name, s"Bearer MySystemToken"))

  /** Check a response code and return its body:
    *   restClient.post(url, json, Authorization.name, "Bearer ABCDEF").expectWithCode(SC_CREATED).convertTo[TokenRemote]
    */
  implicit class ExpectedResponse(httpCall: HttpCall) {
    def expectWithCode(code: Int): JsValue = httpCall match {
      case HttpCallSuccessful(_, c, body, _) if c == code => body
      case x => throw new TestFailedException(s"Received: $x", 4)
    }
  }

}

/** Start and stop the system. */
trait SystemStart extends BaseURLs {

  implicit val system = ActorSystem("slotsbooker")

  sys.addShutdownHook {
    println("Shutting down ...")
    system.terminate()
  }

  def systemStart() {
    auth.start.run
    profiles.start.run
    slots.start.run
    places.start.run
    booking.start.run
    payments.start.run
    paypal.start.run
    members.start.run

    // wait till all micro services start
    val restClient = new RestClient
    implicit val executionContext = system.dispatcher
    def started: Boolean = baseUrls.map(url => isStarted(url, restClient)).forall(true==)
    Await.result(Future { while (!started) Thread.sleep(100) }, 20 seconds)
  }

  def systemStop() {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }

  private def isStarted(baseUrl: String, restClient: RestClient): Boolean =
    restClient.get(s"$baseUrl/heartbeat") match {
      case HttpCallSuccessful(_, SC_OK, _, _) => true
      case _ => false
    }

}

/** Micro services base URLs. */
trait BaseURLs {

  val authBaseUrl = "http://localhost:8022"
  val profilesBaseUrl = "http://localhost:8021"
  val placesBaseUrl = "http://localhost:8024"
  val slotsBaseUrl = "http://localhost:8023"
  val bookingBaseUrl = "http://localhost:8025"
  val paymentsBaseUrl = "http://localhost:8026"
  val paypalBaseUrl = "http://localhost:8027"
  val membersBaseUrl = "http://localhost:8028"

  val baseUrls =
    authBaseUrl :: profilesBaseUrl :: placesBaseUrl :: slotsBaseUrl :: bookingBaseUrl :: paymentsBaseUrl ::
    paypalBaseUrl :: membersBaseUrl :: Nil
}

/** MongoDB client and operations. */
trait MongoOps extends MongoQueries with MongoTables with MongoUsers with MongoCreate with MongoCleaner with MongoVerify {
  lazy val mongoClient = MongoClient("localhost", 27017)
  lazy val mongoDB = mongoClient(APP+"-test")

  def randomId: String = ObjectId.get.toString
}

/** MongoDB collections. */
trait MongoTables {
  self: MongoOps =>

  lazy val mongoAuthUsers: MongoCollection = mongoDB(s"${auth.Constants.MS}-users")
  lazy val mongoAuthTokens: MongoCollection = mongoDB(s"${auth.Constants.MS}-tokens")

  lazy val mongoProfiles: MongoCollection = mongoDB(profiles.Constants.MS)

  lazy val mongoPlaces: MongoCollection = mongoDB(places.Constants.MS)
  lazy val mongoSpaces: MongoCollection = mongoDB(places.Constants.MS+"-spaces")
  lazy val mongoPrices: MongoCollection = mongoDB(places.Constants.MS+"-prices")

  lazy val mongoSlots: MongoCollection = mongoDB(slots.Constants.MS)
  lazy val mongoBookings: MongoCollection = mongoDB(slots.Constants.MS+"-bookings")
  lazy val mongoBooked: MongoCollection = mongoDB(slots.Constants.MS+"-booked")
  lazy val mongoSlotPrices: MongoCollection = mongoDB(slots.Constants.MS+"-prices")

  lazy val mongoBalances: MongoCollection = mongoDB(payments.Constants.MS+"-balances")
  lazy val mongoAccounts: MongoCollection = mongoDB(payments.Constants.MS+"-accounts")

  lazy val mongoQuotes: MongoCollection = mongoDB(booking.Constants.MS+"-quotes")
  lazy val mongoRefunds: MongoCollection = mongoDB(booking.Constants.MS+"-refunds")
  lazy val mongoReferences: MongoCollection = mongoDB(booking.Constants.MS+"-references")

  lazy val mongoMembers: MongoCollection = mongoDB(members.Constants.MS)
}

/** MongoDB users setup. */
trait MongoUsers {
  self: MongoOps =>

  def mongoSetupTestUser() {
    mongoRemoveTestUser()
    mongoSetupUser("testuser")
  }

  def mongoSetupUser(username: String, roles: Seq[String] = Nil) {
    mongoRemoveUser(username)

    mongoAuthUsers.update(
      "username" $eq username,
      MongoDBObject(
        "test" -> true,
        "username" -> username,
        "password" -> "testpass"),
      upsert = true
    )
    mongoAuthTokens.update(
      "username" $eq username,
      MongoDBObject(
        "test" -> true,
        "username" -> username,
        "token" -> s"${username.capitalize}_BearerToken",
        "type" -> "Bearer",
        "expires" -> (3600*1000L+System.currentTimeMillis)),
      upsert = true
    )
    mongoProfiles.update(
      "username" $eq username,
      MongoDBObject(
        "test" -> true,
        "username" -> username,
        "email" -> s"$username@example.org",
        "roles" -> MongoDBList(roles: _*)),
      upsert = true
    )
  }

  def mongoRemoveTestUser() = mongoRemoveUser("testuser")

  def mongoRemoveUser(username: String) {
    mongoAuthUsers.findAndRemove("username" $eq username)
    mongoAuthTokens.findAndRemove("username" $eq username)
    mongoProfiles.findAndRemove("username" $eq username)
  }

  def mongoProfileId(username: String): String =
    mongoProfiles
      .findOne("username" $eq username)
      .map(_.getAs[ObjectId]("_id").get)
      .getOrElse(ObjectId.get)
      .toString

}

/** Create objects in MongoDB */
trait MongoCreate {
  self: MongoOps =>

  def mongoSetUserAttributes(attributes: JsObject, username: String = "testuser") =
    mongoProfiles
      .findAndModify(finderById(mongoProfileId(username)),
        $set("attributes" -> asDBObject(attributes)))

  def mongoSetPlaceAttributes(placeId: String, attributes: JsObject) =
    mongoPlaces
      .findAndModify(finderById(placeId),
        $set("attributes" -> asDBObject(attributes)))

  def mongoSetPlaceModerators(placeId: String, moderators: Seq[String]) =
    mongoPlaces
      .findAndModify(finderById(placeId),
        $set("moderators" -> MongoDBList(moderators: _*)))

  def mongoCreatePlace(offset_minutes: Option[Int] = None, username: String = "testuser"): String = {
    val place = MongoDBObject(
      "test" -> true,
      "profile_id" -> mongoProfileId(username),
      "name" -> "My Place Name")
    mongoPlaces
      .insert(place)

    Map(
      "datetime.offset_minutes" -> offset_minutes
    ).foreach { case (key, value) =>
      update(finderById(place.idString), mongoPlaces, key, value)
    }

    place.idString
  }

  def mongoCreateSpace(placeId: String, name: String = "Small Lake"): String = {
    val space = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "name" -> name)
    mongoSpaces
      .insert(space)

    //addToArray(finderById(placeId), mongoPlaces, "spaces", space.idString)

    space.idString
  }

  def mongoCreateInnerSpace(placeId: String, parentSpaceId: String, name: String = "Tiny Lake"): String = {
    val space = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "parent_space_id" -> parentSpaceId,
      "name" -> name)
    mongoSpaces
      .insert(space)

    //addToArray(finderById(parentSpaceId), mongoSpaces, "spaces", space.idString)

    space.idString
  }

  def mongoSetSpaceAttributes(spaceId: String, attributes: JsObject) =
    mongoSpaces
      .findAndModify(finderById(spaceId), $set("attributes" -> asDBObject(attributes)))

  def mongoSetSpacePriceAttributes(priceId: String, attributes: JsObject) =
    mongoPrices
      .findAndModify(finderById(priceId), $set("attributes" -> asDBObject(attributes)))

  def mongoSetSlotPriceAttributes(priceId: String, attributes: JsObject) =
    mongoSlotPrices
      .findAndModify(finderById(priceId), $set("attributes" -> asDBObject(attributes)))

  def mongoCreateSlot(placeId: String, spaceId: String, name: String = "Slot A",
                      dateFrom: Int = 0, dateTo: Int = 0, timeFrom: Int = 0, timeTo: Int = 2400,
                      bookStatus: Int = 0, bookedId: Option[String] = None): String = {
    val slot = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "space_id" -> spaceId,
      "name" -> name,
      "date_from" -> dateFrom,
      "date_to" -> dateTo,
      "time_from" -> timeFrom,
      "time_to" -> timeTo,
      "book_status" -> bookStatus)
    mongoSlots
      .insert(slot)

    if (bookedId.isDefined)
      mongoSlots
        .findAndModify(finderById(slot.idString), $set("booked" -> bookedId.get))

    slot.idString
  }

  def mongoSetSlotAttributes(slotId: String, attributes: JsObject) =
    mongoSlots
      .findAndModify(finderById(slotId), $set("attributes" -> asDBObject(attributes)))

  def mongoUpdateSlot(slotId: String, dateFrom: Some[Int], dateTo: Some[Int], timeFrom: Some[Int], timeTo: Some[Int]) =
    Map(
      "date_from" -> dateFrom,
      "date_to" -> dateTo,
      "time_from" -> timeFrom,
      "time_to" -> timeTo
    ).foreach { case (key, value) =>
      update(finderById(slotId), mongoSlots, key, value)
    }

  def mongoSetSlotBooked(slotId: String, bookedId: String) =
    mongoSlots
      .findAndModify(finderById(slotId), $set("booked" -> bookedId))

  def mongoCreateBooked(placeId: String, slotIds: Seq[String] = Nil, bookingIds: Seq[String] = Nil,
                        username: String = "testuser", status: Int = 2, paid: Option[Boolean] = None): String = {
    val booked = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "slot_ids" -> MongoDBList(slotIds: _*),
      "booking_ids" -> MongoDBList(bookingIds: _*),
      "profile_id" -> mongoProfileId(username),
      "status" -> status)
    mongoBooked
      .insert(booked)

    update(finderById(booked.idString), mongoBooked, "paid", paid)

    booked.idString
  }

  def mongoCreateBooking(placeId: String, spaceId: String, slotId: String, name: String = "Booking A",
                         username: String = "testuser", status: Int = 1): String = {
    val booking = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "space_id" -> spaceId,
      "slot_id" -> slotId,
      "profile_id" -> mongoProfileId(username),
      "name" -> name,
      "status" -> status)
    mongoBookings
      .insert(booking)

    //addToArray(finderById(slotId), mongoSlots, "bookings", booking.idString)

    if (status == 1)
      mongoSlots
        .findAndModify(finderById(slotId), $set("book_status" -> 1))

    booking.idString
  }

  def mongoSetBookingAttributes(bookingId: String, attributes: JsObject) =
    mongoBookings
      .findAndModify(finderById(bookingId), $set("attributes" -> asDBObject(attributes)))

  def mongoCreateSlotPrice(placeId: String, spaceId: String, slotId: String,
                           name: String = "Price A", amount: Int = 1700, currency: String = "GBP",
                           member_level: Int = 0): String = {
    val price = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "space_id" -> spaceId,
      "slot_id" -> slotId,
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency,
      "member_level" -> member_level)
    mongoSlotPrices
      .insert(price)

    //addToArray(finderById(slotId), mongoSlots, "prices", price.idString)

    price.idString
  }

  def mongoCreateSpacePrice(placeId: String, spaceId: String,
                            name: String = "Default Price", amount: Int = 1700, currency: String = "GBP",
                            member_level: Int = 0): String = {
    val price = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "space_id" -> spaceId,
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency,
      "member_level" -> member_level)

    mongoPrices
      .insert(price)

    //addToArray(finderById(spaceId), mongoSpaces, "prices", price.idString)

    price.idString
  }

  def mongoCreateBalance(placeId: String, amount: Int, currency: String = "GBP", username: String = "testuser") = {
    val balance = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "profile_id" -> mongoProfileId(username),
      "credit" -> MongoDBList(MongoDBObject(
        "amount" -> amount,
        "currency" -> currency
      )))
    mongoBalances
      .insert(balance)
  }

  def mongoCreateAccount(placeId: String, currency: String = "GBP") = {
    val account = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "currencies" -> MongoDBList(MongoDBObject(
        "currency" -> currency
      )))
    mongoAccounts
      .insert(account)
  }

  def mongoCreateMember(placeId: String, level: Int = 1, username: String = "testuser"): String = {
    val member = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "profile_id" -> mongoProfileId(username),
      "level" -> level)

    mongoMembers
      .insert(member)

    member.idString
  }

  def mongoCreateFreeRefund(placeId: String, slotsIds: Seq[String], quoteIds: Seq[String], status: Int = 0, username: String = "testuser"): String = {
    val refund = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "profile_id" -> mongoProfileId(username),
      "status" -> status,
      "prices" -> slotsIds.map(slotId => MongoDBObject("slot_id" -> slotId)),
      "quote_ids" -> quoteIds)

    mongoRefunds
      .insert(refund)

    val refundId = refund.idString

    entryCreated(refundId, mongoRefunds)

    refundId
  }

  def mongoCreatePaidRefund(placeId: String, slotsPrices: Seq[(String, Int)],
                            quoteIds: Seq[String] = Nil, status: Int = 0, username: String = "testuser"): String = {
    val refundId = mongoCreateFreeRefund(placeId, Seq.empty, quoteIds, status, username)
    mongoRefunds
      .findAndModify(
        finderById(refundId),
        $set(
          "prices" -> slotsPrices.map { case (slotId, amount) => MongoDBObject(
            "slot_id" -> slotId,
            "amount" -> amount,
            "price_id" -> randomId,
            "currency" -> "GBP",
            "name" -> "Price A")
          },
          "amount" -> slotsPrices.map(_._2).sum,
          "currency" -> "GBP"
        )
      )

    refundId
  }

  def mongoCreateFreeQuote(placeId: String, slotsIds: Seq[String] = Nil, status: Int = 0, username: String = "testuser"): String = {
    val quote = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "profile_id" -> mongoProfileId(username),
      "status" -> status,
      "prices" -> slotsIds.map(slotId => MongoDBObject("slot_id" -> slotId)))

    mongoQuotes
      .insert(quote)

    val quoteId = quote.idString

    entryCreated(quoteId, mongoQuotes)

    quoteId
  }

  def mongoCreatePaidQuote(placeId: String, slotsPrices: Seq[(String, Int)],
                           status: Int = 0, username: String = "testuser"): String = {
    val quoteId = mongoCreateFreeQuote(placeId, Seq.empty, status, username)
    mongoQuotes
      .findAndModify(
        finderById(quoteId),
        $set(
          "prices" -> slotsPrices.map { case (slotId, amount) => MongoDBObject(
            "slot_id" -> slotId,
            "amount" -> amount,
            "price_id" -> randomId,
            "currency" -> "GBP",
            "name" -> "Price A")
          },
          "amount" -> slotsPrices.map(_._2).sum,
          "currency" -> "GBP"
        )
      )

    quoteId
  }

  def mongoCreateReference(placeId: String, bookedIds: Seq[String], quoteId: Option[String] = None, refundId: Option[String] = None, refSfx: String = "1", username: String = "testuser"): String = {
    val reference = MongoDBObject(
      "test" -> true,
      "place_id" -> placeId,
      "ref" -> s"${username.capitalize}_$refSfx",
      "profile_id" -> mongoProfileId(username),
      "booked_ids" -> MongoDBList(bookedIds: _*)
    )

    mongoReferences
      .insert(reference)

    val referenceId = reference.idString

    Map(
      "quote_id" -> quoteId,
      "refund_id" -> refundId
    ).foreach { case (key, value) =>
      update(finderById(referenceId), mongoReferences, key, value)
    }

    referenceId
  }

  def mongoEntryDates(id: String, collection: MongoCollection,
                      created: Option[Long] = None, updated: Option[Long] = None, locked: Option[Long] = None) {
    Map(
      "entry.created" -> created,
      "entry.updated" -> updated,
      "entry.locked" -> locked
    ).foreach { case (key, value) =>
      update(finderById(id), collection, key, value)
    }
  }

}

/** Verify objects in MongoDB */
trait MongoVerify {
  self: MongoOps =>

  private def fail(msg: String) = throw new TestFailedException(msg, 4)

  private def mongoVerifyBookingIntegrity(slotId: String, username: String, bookedStatus: Int, bookStatus: Int, bookingStatus: Int) {
    val profileId = mongoProfileId(username)

    val bookedData =
      mongoBooked
        .find(
          ("profile_id" $eq profileId) ++
          ("status" $eq bookedStatus) ++
          ("slot_ids" $eq slotId))
        .toSeq match {
        case Seq(x) => x
        case Seq() => fail(s"Booked record not found")
        case _ => fail(s"Found more than one Booked records")
      }

    val bookedId = bookedData.as[ObjectId]("_id").toString

    val slotData =
      mongoSlots
        .find(
          finderById(slotId) ++
          ("book_status" $eq bookStatus) ++
          (if (bookStatus == 1) "booked" $eq bookedId else "booked" $exists false)) // set only on booked slots
        .toSeq match {
        case Seq(x) => x
        case Seq() => fail(s"Slot record not found")
      }

    val bookingData =
      mongoBookings
        .find(
          ("profile_id" $eq profileId) ++
          ("slot_id" $eq slotId) ++
          ("status" $eq bookingStatus))
        .toSeq match {
        case Seq(x) => x
        case Seq() => fail(s"Booking record not found")
        case _ => fail(s"Found more than one Booking records")
      }

    val bookingId = bookingData.as[ObjectId]("_id").toString

    if (!bookedData.as[Seq[String]]("booking_ids").exists(bookingId==))
      fail(s"Booked record does not contain Booking $bookingId")

    val placeId = bookedData.as[String]("place_id")

    if (placeId != slotData.as[String]("place_id") ||
        placeId != bookingData.as[String]("place_id"))
      fail("Booked record Place mismatch")

  }

  def mongoVerifyActiveBookingIntegrity(slotId: String, username: String = "testuser") =
    mongoVerifyBookingIntegrity(slotId, username, bookedStatus = 2, bookStatus = 1, bookingStatus = 1)

  def mongoVerifyCancelledBookingIntegrity(slotId: String, username: String = "testuser") =
    mongoVerifyBookingIntegrity(slotId, username, bookedStatus = 2, bookStatus = 0, bookingStatus = 0)
}

/** Delete objects from MongoDB. */
trait MongoCleaner {
  self: MongoOps =>

  def mongoDropDatabase() =
    mongoDB.dropDatabase()

  /** Remove a place and all its spaces, prices etc.
    * To verify that the method removes all children, start with an empty MongoDB and after all tests
    * verify that it is still empty.
    */
  def mongoRemovePlace(username: String = "testuser") {
    val findByProfileId = "profile_id" $eq mongoProfileId(username)
    mongoPlaces
      .find(findByProfileId).foreach { place =>
      val placeId = place.idString

      ( mongoBookings ::
        mongoBooked ::
        mongoSlotPrices ::
        mongoSlots ::
        mongoPrices ::
        mongoSpaces ::
        mongoBalances ::
        mongoAccounts ::
        mongoQuotes ::
        mongoRefunds ::
        mongoReferences ::
        mongoMembers ::
        Nil
      ).foreach(_.remove("place_id" $eq placeId))

      mongoPlaces
        .findAndRemove(finderById(placeId))
    }
  }

}

/** REST DSL
  * Examples:
  *   When.postTo(url).entity(json).withHeaders(headers).expect.code(SC_CREATED).withBody[TokenRemote]
  *   val token = (When postTo url entity json withHeaders headers expect() code SC_CREATED).withBody[TokenRemote]
  *   When postTo url entity json withHeaders headers expect() code SC_CREATED
  */
trait RestClientDSL {

  private val restClient = new RestClient
  implicit private def string2jso(s: String): JsObject = s.parseJson.asJsObject
  private def fail(msg: String) = throw new TestFailedException(msg, 4)

  case class Values(method: Symbol = null, url: String = null, json: JsObject = null,
                    headers: Seq[(String,String)] = Nil,
                    restCallResult: Option[HttpCall] = None)

  case object When {
    def postTo(url: String): Post = Post(Values(url = url, method = 'post))
    def getTo(url: String): Get = Get(Values(url = url, method = 'get))
    def putTo(url: String): Put = Put(Values(url = url, method = 'put))
    def deleteTo(url: String): Delete = Delete(Values(url = url, method = 'delete))
    def patchTo(url: String): Patch = Patch(Values(url = url, method = 'patch))
  }

  case class Post(values: Values) {
    def entity(json: JsObject): Entity = Entity(values.copy(json = json))
  }

  case class Put(values: Values) {
    def entity(json: JsObject): Entity = Entity(values.copy(json = json))
  }

  case class Patch(values: Values) {
    def entity(json: JsObject): Entity = Entity(values.copy(json = json))
  }

  case class Get(values: Values) {
    def expect(): Expect = Expect(values)
    def withHeaders(headers: Seq[(String,String)]): Headers = Headers(values.copy(headers = headers))
  }

  case class Delete(values: Values) {
    def expect(): Expect = Expect(values)
    def withHeaders(headers: Seq[(String,String)]): Headers = Headers(values.copy(headers = headers))
  }

  case class Entity(values: Values) {
    def expect(): Expect = Expect(values)
    def withHeaders(headers: Seq[(String,String)]): Headers = Headers(values.copy(headers = headers))
  }

  case class Headers(values: Values) {
    def expect(): Expect = Expect(values)
  }

  case class Expect(values: Values) {
    import values._
    lazy val httpCall =
      restCallResult.getOrElse {
        values.method match {
          case 'post => restClient.post(url, json, headers: _*)
          case 'put => restClient.put(url, json, headers: _*)
          case 'patch => restClient.patch(url, json, headers: _*)
          case 'get => restClient.get(url, headers: _*)
          case 'delete => restClient.delete(url, headers: _*)
        }
      }
    lazy val nvalues = values.copy(restCallResult = Some(httpCall))

    def code(code: Int): Expect =
      httpCall match {
        case HttpCallSuccessful(_, c, _, _) if c == code => Expect(nvalues)
        case x => fail(s"Received: $x")
      }

    def withHeader(name: String, value: String): Expect =
      httpCall match {
        case HttpCallSuccessful(_, _, _, rh) if rh.exists { case (n, v) => n == name && v == value } => Expect(nvalues)
        case x => fail(s"Received: $x")
      }

    def withBody[T : JsonReader]: T =
      httpCall match {
        case HttpCallSuccessful(_, _, body, _) => body.convertTo[T]
        case x => fail(s"Received: $x")
      }

    def withApiCode(value: String) = withHeader("X-Api-Code", value)
  }

}

trait RestAssert {
  self: RestClientDSL =>

  private type RestDslVerbs = { def withHeaders(headers: Seq[(String,String)]): Headers }

  def assert401_invalidToken(verbs: RestDslVerbs) {
    val headers = Seq((Authorization.name, s"Bearer ABCDEF"))
    verbs withHeaders headers expect() code SC_UNAUTHORIZED
  }
}