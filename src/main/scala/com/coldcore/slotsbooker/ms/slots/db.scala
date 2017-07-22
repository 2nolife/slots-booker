package com.coldcore.slotsbooker
package ms.slots.db

import ms.db.MongoQueries
import ms.slots.Constants._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import ms.slots.vo
import ms.vo.Attributes
import org.bson.types.ObjectId
import SlotsDb._

object SlotsDb {

  trait SlotFields {
    val deepBookings: Boolean // TRUE to fetch Booking array from DB (slow), FALSE to include just Booking IDs (fast)
    val deepPrices: Boolean // TRUE to fetch Prices array from DB (slow), FALSE to include just Prices IDs (fast)
    val deepBooked: Boolean // TRUE to fetch Booked object from DB (slow), FALSE to include just Booked ID (fast)
  }

  case class DefaultSlotFields(deepBookings: Boolean = false,
                               deepPrices: Boolean = false,
                               deepBooked: Boolean = false) extends SlotFields

  def defaultSlotFields = new DefaultSlotFields

  def customSlotFields(deep_bookings: Boolean, deep_prices: Boolean, deep_booked: Boolean): SlotFields =
    defaultSlotFields.copy(deepBookings = deep_bookings,
                           deepPrices = deep_prices,
                           deepBooked = deep_booked)

  class BookingConflictException extends RuntimeException

  val slotBookStatus = Map('bookable -> 0, 'booked -> 1, 'being_booked -> 2, 'being_released -> 3)
  val bookedStatus = Map('being_booked -> 1, 'booked -> 2, 'other -> 3)
  val bookingStatus = Map('inactive -> 0, 'active -> 1, 'being_booked -> 2)

  case class SearchCriteria(placeId: String, spaceIds: Seq[String],
                            dateFrom: Int, dateTo: Int, timeFrom: Int, timeTo: Int,
                            bookedByProfileId: Option[String] = None,
                            paidOrUnpaid: Option[Boolean] = None)
}

trait SlotsDb extends SlotCRUD with BookingCRUD with BookedCRUD with PriceCRUD with Search

trait SlotCRUD {
  def slotById(slotId: String, fields: SlotFields = defaultSlotFields): Option[vo.Slot]
  def createSlot(parentPlaceId: String, parentSpaceId: String, obj: vo.CreateSlot, fields: SlotFields = defaultSlotFields): vo.Slot
  def updateSlot(slotId: String, obj: vo.UpdateSlot, fields: SlotFields = defaultSlotFields): Option[vo.Slot]
  def deleteSlot(slotId: String): Boolean
  def acquireHold(slotId: String, bookedId: String): Boolean
  def confirmHold(slotId: String, bookedId: String): Boolean
  def releaseHold(slotId: String, bookedId: String): Boolean
}

trait BookingCRUD {
  def bookingById(bookingId: String): Option[vo.Booking]
  def bookingsBySlotId(slotId: String): Seq[vo.Booking]
  def createBooking(parentSlotId: String, profileId: String, obj: vo.CreateBooking): vo.Booking
  def updateBooking(bookingId: String, obj: vo.UpdateBooking): Option[vo.Booking]
  def deleteBooking(bookingId: String): Boolean
}

trait BookedCRUD {
  def bookedById(bookedId: String): Option[vo.Booked]
  def createBooked(profileId: String, obj: vo.CreateBooked): vo.Booked
  def updateBooked(bookedId: String, obj: vo.UpdateBooked): Option[vo.Booked]
}

trait PriceCRUD {
  def priceById(priceId: String): Option[vo.Price]
  def pricesBySlotId(slotId: String): Seq[vo.Price]
  def createPrice(parentSlotId: String, obj: vo.CreatePrice): vo.Price
  def updatePrice(priceId: String, obj: vo.UpdatePrice): Option[vo.Price]
  def deletePrice(priceId: String): Boolean
}

trait Search {
  def searchSlots(criteria: SearchCriteria, fields: SlotFields = defaultSlotFields): Seq[vo.Slot]
}

class MongoSlotsDb(client: MongoClient, dbName: String) extends SlotsDb with VoFactory with MongoQueries
  with SlotsCrudImpl with BookingCrudImpl with BookedCrudImpl with PriceCrudImpl with SearchImpl {

  private val db = client(dbName)
  val slots = db(MS)
  val bookings = db(MS+"-bookings")
  val booked = db(MS+"-booked")
  val prices = db(MS+"-prices")

}

trait VoFactory {
  self: MongoSlotsDb =>

  def asSlot(data: MongoDBObject, fields: SlotFields): vo.Slot = {
    import data._
    val placeId = as[String]("place_id")
    val spaceId = as[String]("space_id")
    val slotId = as[ObjectId]("_id").toString

    vo.Slot(
      slot_id = slotId,
      place_id = placeId,
      space_id = as[String]("space_id"),
      name = getAs[String]("name"),
      date_from = getAs[Int]("date_from"),
      date_to = getAs[Int]("date_to"),
      time_from = getAs[Int]("time_from"),
      time_to = getAs[Int]("time_to"),
      bookings = if (fields.deepBookings) Some(bookingsBySlotId(slotId)).noneIfEmpty else None,
      prices = if (fields.deepPrices) Some(pricesBySlotId(slotId)).noneIfEmpty else None,
      book_status = getAs[Int]("book_status"),
      booked = if (fields.deepBooked) getAs[String]("booked").flatMap(bookedById) else None,
      disabled = getAs[Int]("disabled").orElse(Some(0)),
      attributes =
        getAs[AnyRef]("attributes")
        .map(json => Attributes(json.toString)),
      book_bounds =
        getAs[DBObject]("book_bounds")
          .map(asBounds(_)),
      cancel_bounds =
        getAs[DBObject]("cancel_bounds")
          .map(asBounds(_))
    )
  }

  def asBooked(data: MongoDBObject): vo.Booked = {
    import data._
    vo.Booked(
      booked_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      profile_id = getAs[String]("profile_id"),
      status = getAs[Int]("status"),
      paid = getAs[Boolean]("paid"),
      slot_ids =
        getAs[Seq[String]]("slot_ids")
          .noneIfEmpty,
      booking_ids =
        getAs[Seq[String]]("booking_ids")
          .noneIfEmpty
    )
  }

  def asBooking(data: MongoDBObject): vo.Booking = {
    import data._
    vo.Booking(
      booking_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      space_id = as[String]("space_id"),
      slot_id = as[String]("slot_id"),
      profile_id = getAs[String]("profile_id"),
      name = getAs[String]("name"),
      status = getAs[Int]("status"),
      attributes =
        getAs[AnyRef]("attributes")
          .map(json => Attributes(json.toString))
    )
  }

  def asPrice(data: MongoDBObject): vo.Price = {
    import data._
    vo.Price(
      price_id = as[ObjectId]("_id").toString,
      place_id = as[String]("place_id"),
      space_id = as[String]("space_id"),
      slot_id = as[String]("slot_id"),
      name = getAs[String]("name"),
      amount = getAs[Int]("amount"),
      currency = getAs[String]("currency"),
      member_level = getAs[Int]("member_level"),
      attributes =
        getAs[AnyRef]("attributes")
          .map(json => Attributes(json.toString))
    )
  }

  def asBounds(data: MongoDBObject): vo.Bounds = {
    import data._
    vo.Bounds(
      open =
        getAs[DBObject]("open")
        .map(asBound(_)),
      close =
        getAs[DBObject]("close")
        .map(asBound(_)))
  }

  def asBound(data: MongoDBObject): vo.Bound = {
    import data._
    vo.Bound(
      date = getAs[Int]("date"),
      time = getAs[Int]("time"),
      before = getAs[Int]("before"))
  }

  def asMongoObject(bound: vo.Bound): MongoDBObject = {
    import bound._
    val f = (key: String, value: Option[Int]) => value.map(v => MongoDBObject(key -> v)).getOrElse(MongoDBObject())
    f("date", date) ++ f("time", time) ++ f("before", before)
  }

  def asMongoObject(bounds: vo.Bounds): MongoDBObject = {
    import bounds._
    val f = (key: String, value: Option[vo.Bound]) => value.map(v => MongoDBObject(key -> asMongoObject(v))).getOrElse(MongoDBObject())
    f("open", open) ++ f("close", close)
  }

}

trait SlotsCrudImpl {
  self: MongoSlotsDb =>

  override def slotById(slotId: String, fields: SlotFields): Option[vo.Slot] =
    slots
      .findOne(finderById(slotId))
      .map(asSlot(_, fields))

  override def deleteSlot(slotId: String): Boolean =
    softDeleteOne(finderById(slotId), slots)

  override def createSlot(parentPlaceId: String, parentSpaceId: String, obj: vo.CreateSlot, fields: SlotFields): vo.Slot = {
    import obj._
    val slot = MongoDBObject(
      "place_id" -> parentPlaceId,
      "space_id" -> parentSpaceId,
      "name" -> name,
      "date_from" -> date_from,
      "date_to" -> date_to,
      "time_from" -> time_from,
      "time_to" -> time_to,
      "book_status" -> slotBookStatus('bookable))

    slots.
      insert(slot)

    attributes.foreach(a => mergeJsObject(finderById(slot.idString), slots, "attributes", a.value))

    entryCreated(slot.idString, slots)

    slotById(slot.idString, fields).get
  }

  override def updateSlot(slotId: String, obj: vo.UpdateSlot, fields: SlotFields): Option[vo.Slot] = {
    import obj._
    Map(
      "name" -> name,
      "date_from" -> date_from,
      "date_to" -> date_to,
      "time_from" -> time_from,
      "time_to" -> time_to,
      "disabled" -> disabled,
      "book_bounds" -> book_bounds.map(asMongoObject),
      "cancel_bounds" -> cancel_bounds.map(asMongoObject)
    ).foreach { case (key, value) =>
      update(finderById(slotId), slots, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(slotId), slots, "attributes", a.value))

    entryUpdated(slotId, slots)

    slotById(slotId, fields)
  }

  override def acquireHold(slotId: String, bookedId: String): Boolean = {
    val found =
      slots
        .findOne(
          finderById(slotId) ++
          ("book_status" $eq slotBookStatus('bookable)))
        .isDefined

    def update() =
      slots
        .findAndModify(finderById(slotId),
          $set("booked" -> bookedId, "book_status" -> slotBookStatus('being_booked)))
        .isDefined

    if (found) update()
    else false
  }

  override def confirmHold(slotId: String, bookedId: String): Boolean = {
    val found =
      slots
        .findOne(
          finderById(slotId) ++
          ("booked" $eq bookedId) ++
          $or("book_status" $eq slotBookStatus('being_booked)))
        .isDefined

    def update() =
      slots
        .findAndModify(finderById(slotId),
          $set("book_status" -> slotBookStatus('booked)))
        .isDefined

    if (found) update()
    else false
  }

  override def releaseHold(slotId: String, bookedId: String): Boolean = {
    val found =
      slots
        .findOne(
          finderById(slotId) ++
          ("booked" $eq bookedId) ++
          $or("book_status" $eq slotBookStatus('booked),
              "book_status" $eq slotBookStatus('being_booked),
              "book_status" $eq slotBookStatus('being_released)))
        .isDefined

    def update() =
      slots
        .findAndModify(
          finderById(slotId),
          $set("book_status" -> slotBookStatus('bookable)) ++ $unset("booked"))
        .isDefined

    if (found) update()
    else false
  }

}

trait BookingCrudImpl {
  self: MongoSlotsDb =>

  override def bookingById(bookingId: String): Option[vo.Booking] =
    bookings
      .findOne(finderById(bookingId))
      .map(asBooking(_))

  override def bookingsBySlotId(slotId: String): Seq[vo.Booking] =
    bookings
      .find(finder("slot_id" $eq slotId))
      .map(asBooking(_))
      .toSeq

  override def deleteBooking(bookingId: String): Boolean =
    softDeleteOne(finderById(bookingId), bookings)

  @throws[BookingConflictException]
  override def createBooking(parentSlotId: String, profileId: String, obj: vo.CreateBooking): vo.Booking = {
    val myBooked =
      booked
        .findOne(
          ("profile_id" $eq profileId) ++
          ("slot_ids" $eq parentSlotId) ++
          ("status" $eq bookedStatus('being_booked)))
        .map(asBooked(_))

    if (myBooked.isEmpty) throw new BookingConflictException

    val parentSlot =
      slots
        .findOne(
          finderById(parentSlotId) ++
          ("book_status" $eq slotBookStatus('being_booked)) ++
          ("booked" $eq myBooked.get.booked_id))
        .map(asSlot(_, defaultSlotFields))

    if (parentSlot.isEmpty) throw new BookingConflictException

    import obj._
    val booking = MongoDBObject(
      "place_id" -> parentSlot.get.place_id,
      "space_id" -> parentSlot.get.space_id,
      "slot_id" -> parentSlotId,
      "profile_id" -> profileId,
      "name" -> name,
      "status" -> bookingStatus('being_booked))

    bookings.
      insert(booking)

    entryCreated(booking.idString, bookings)

    bookingById(booking.idString).get
  }

  override def updateBooking(bookingId: String, obj: vo.UpdateBooking): Option[vo.Booking] = {
    import obj._
    Map(
      "name" -> name,
      "status" -> status
    ).foreach { case (key, value) =>
      update(finderById(bookingId), bookings, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(bookingId), bookings, "attributes", a.value))

    entryUpdated(bookingId, bookings)

    bookingById(bookingId)
  }

}

trait BookedCrudImpl {
  self: MongoSlotsDb =>

  override def bookedById(bookedId: String): Option[vo.Booked] =
    booked
      .findOne(finderById(bookedId))
      .map(asBooked(_))

  override def createBooked(profileId: String, obj: vo.CreateBooked): vo.Booked = {
    val headSlot = slotById(obj.slot_ids.head, defaultSlotFields).get

    import obj._
    val entity = MongoDBObject(
      "place_id" -> headSlot.place_id,
      "profile_id" -> profileId,
      "slot_ids" -> MongoDBList(slot_ids: _*),
      "status" -> 1)

    booked.
      insert(entity)

    entryCreated(entity.idString, booked)

    bookedById(entity.idString).get
  }

  override def updateBooked(bookedId: String, obj: vo.UpdateBooked): Option[vo.Booked] = {
    import obj._
    Map(
      "status" -> status,
      "paid" -> paid,
      "booking_ids" -> booking_ids.map(MongoDBList(_: _*))
    ).foreach { case (key, value) =>
      update(finderById(bookedId), booked, key, value)
    }

    entryUpdated(bookedId, booked)

    bookedById(bookedId)
  }

}

trait PriceCrudImpl {
  self: MongoSlotsDb =>

  override def priceById(priceId: String): Option[vo.Price] =
    prices
      .findOne(finderById(priceId))
      .map(asPrice(_))

  override def pricesBySlotId(slotId: String): Seq[vo.Price] =
    prices
      .find(finder("slot_id" $eq slotId))
      .map(asPrice(_))
      .toSeq

  override def createPrice(parentSlotId: String, obj: vo.CreatePrice): vo.Price = {
    val parentSlot = slotById(parentSlotId, defaultSlotFields).get

    import obj._
    val price = MongoDBObject(
      "place_id" -> parentSlot.place_id,
      "space_id" -> parentSlot.space_id,
      "slot_id" -> parentSlotId,
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency)

    prices.
      insert(price)

    Map(
      "member_level" -> member_level
    ).foreach { case (key, value) =>
      update(finderById(price.idString), prices, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(price.idString), prices, "attributes", a.value))

    entryCreated(price.idString, prices)

    priceById(price.idString).get
  }

  override def updatePrice(priceId: String, obj: vo.UpdatePrice): Option[vo.Price] = {
    import obj._
    Map(
      "name" -> name,
      "amount" -> amount,
      "currency" -> currency,
      "member_level" -> member_level
    ).foreach { case (key, value) =>
      update(finderById(priceId), prices, key, value)
    }

    attributes.foreach(a => mergeJsObject(finderById(priceId), prices, "attributes", a.value))

    entryUpdated(priceId, prices)

    priceById(priceId)
  }

  override def deletePrice(priceId: String): Boolean =
    softDeleteOne(finderById(priceId), prices)

}

trait SearchImpl {
  self: MongoSlotsDb =>

  override def searchSlots(criteria: SearchCriteria, fields: SlotFields): Seq[vo.Slot] = {
    import criteria._

    val placeAndSpaceQuery =
      ("place_id" $eq placeId) ++ ("space_id" $in spaceIds)

    val datesInRangeQuery =
      $or(
        ("date_from" $lte dateFrom) ++ ("date_to" $gte dateFrom),
        ("date_from" $lte dateTo) ++ ("date_to" $gte dateTo),
        ("date_from" $gte dateFrom) ++ ("date_to" $lte dateTo))

    val onlyBookedQuery =
      if (bookedByProfileId.isDefined) "book_status" $eq slotBookStatus('booked) else MongoDBObject()

    val slotsByDate =
      slots
        .find(finder() ++ placeAndSpaceQuery ++ datesInRangeQuery ++ onlyBookedQuery)
        .sort(MongoDBObject("date_from" -> 1, "time_from" -> 1, "date_to" -> 1, "time_to" -> 1))
        .map(asSlot(_, customSlotFields(deep_bookings = false, deep_prices = false, deep_booked = bookedByProfileId.isDefined)))
        .toSeq

    val boundaryFrom = dateFrom*10000L+timeFrom
    val boundaryTo = dateTo*10000L+timeTo

    lazy val slotsByDateTime =
      slotsByDate.filter { slot =>

        (for {
          df <- slot.date_from
          dt <- slot.date_to
          tf <- slot.time_from
          tt <- slot.time_to
          bf = df*10000L+tf
          bt = dt*10000L+tt
        } yield {

          if (df == dateFrom || df == dateTo || dt == dateFrom || dt == dateTo) {
            bf <= boundaryFrom && bt >= boundaryFrom ||
            bf <= boundaryTo && bt >= boundaryTo
          } else {
            true
          }

        }).get

      }

//    val activeBookingsByProfile = (slotIds: Seq[String], profileId: String) =>
//      bookings
//        .find(finder() ++ ("status" $eq bookingStatus('active)) ++ ("profile_id" $eq profileId) ++ ("slot_id" $in slotIds))
//        .map(asBooking(_))
//        .toSeq

// post process: filter with active booking by profile
//    val filterByActiveBookings = (foundSlots: Seq[vo.Slot]) =>
//      if (bookedByProfileId.isEmpty || bookedByProfileId.get == "*") foundSlots
//      else activeBookingsByProfile(foundSlots.map(_.slot_id), bookedByProfileId.get)
//              .flatMap(booking => foundSlots.find(_.slot_id == booking.slot_id))

    // post process: filter by time
    val filterByTime = (_: Unit) =>
      if (timeFrom == 0 && timeTo == 2400) slotsByDate else slotsByDateTime

    // post process: filter with active booking by profile
    val filterByActiveBookings = (foundSlots: Seq[vo.Slot]) =>
      if (bookedByProfileId.isEmpty || bookedByProfileId.get == "*") foundSlots
      else foundSlots.filter(_.booked.exists(_.profile_id.get == bookedByProfileId.get))

    // post process: filter with paid/unpaid
    val filterByPaid = (foundSlots: Seq[vo.Slot]) =>
      if (paidOrUnpaid.isEmpty) foundSlots
      else if (bookedByProfileId.isEmpty || bookedByProfileId.get == "*") Nil // can only operate on a single profile
      else foundSlots.filter(_.booked.exists(_.paid == paidOrUnpaid))

    // get post processed slots with selected fields
    val f = filterByTime andThen filterByActiveBookings andThen filterByPaid
    val retrieve = (slotIds: Seq[String]) => slotIds.flatMap(slotId => slotById(slotId, fields))
    retrieve { f((): Unit).map(_.slot_id) }
  }

}
