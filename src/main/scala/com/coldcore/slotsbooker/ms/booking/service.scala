package com.coldcore.slotsbooker
package ms.booking.service

import ms.{Timestamp => ts}
import ms.booking.vo.{Reference, SelectedPrice}
import ms.booking.db.BookingDb
import ms.booking.vo
import ms.booking.Constants._
import ms.http.{ApiCode, RestClient, SystemRestCalls}
import ms.vo.{Attributes, EmptyEntity}
import org.apache.http.HttpStatus._
import spray.json.{JsObject, JsString}

import collection.mutable.ListBuffer
import scala.util.Random

trait PlacesMsRestCalls extends SystemRestCalls {
  self: {
    val placesBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  /** Get a place from the "places" micro service */
  def placeFromMsPlaces(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    restGet[vo.ext.Place](s"$placesBaseUrl/places/$placeId?deep=false")

  /** Get a space from the "places" micro service */
  def spaceFromMsPlaces(placeId: String, spaceId: String,
                        withPrices: Boolean = false): (ApiCode, Option[vo.ext.Space]) =
    restGet[vo.ext.Space](s"$placesBaseUrl/places/$placeId/spaces/$spaceId?deep=false&deep_prices=$withPrices")
}

trait SlotsMsRestCalls extends SystemRestCalls {
  self: {
    val slotsBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  /** Get a slot from the "slots" micro service */
  def slotFromMsSlots(slotId: String,
                      withPrices: Boolean = false, withBookings: Boolean = false, withBooked: Boolean = false): (ApiCode, Option[vo.ext.Slot]) =
    restGet[vo.ext.Slot](s"$slotsBaseUrl/slots/$slotId?deep=false&deep_prices=$withPrices&deep_bookings=$withBookings&deep_booked=$withBooked")

  /** Get Booking from the "slots" micro service */
  def getBookingFromMsSlots(slotId: String, bookingId: String): (ApiCode, Option[vo.ext.Booking]) =
    restGet[vo.ext.Booking](s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId")

  /** Create Booking in the "slots" micro service */
  def createBookingWithMsSlots(slotId: String, profileId: String, name: String): (ApiCode, Option[vo.ext.Booking]) =
    restPost[vo.ext.Booking](s"$slotsBaseUrl/slots/$slotId/bookings", vo.ext.CreateSlotBooking(profileId, name))

  /** Create Booked in the "slots" micro service */
  def createBookedWithMsSlots(slotIds: Seq[String], profileId: String): (ApiCode, Option[vo.ext.Booked]) =
    restPost[vo.ext.Booked](s"$slotsBaseUrl/slots/booked", vo.ext.CreateSlotBooked(profileId, slotIds))

  /** Update Booked in the "slots" micro service */
  def updateBookedWithMsSlots(bookedId: String, bookingIds: Option[Seq[String]], status: Option[Int], profileId: String): (ApiCode, Option[vo.ext.Booked]) =
    restPatch[vo.ext.Booked](s"$slotsBaseUrl/slots/booked/$bookedId", vo.ext.UpdateSlotBooked(profileId, status, bookingIds))

  /** Update Hold on a slot in the "slots" micro service */
  def updateHoldWithMsSlots(slotId: String, bookedId: String, status: Int): ApiCode =
    restPatch[EmptyEntity](s"$slotsBaseUrl/slots/$slotId/hold", vo.ext.UpdateSlotHold(bookedId, status))._1

  /** Update Booking in the "slots" micro service */
  def updateBookingWithMsSlots(bookingId: String, slotId: String, status: Option[Int], attributes: Option[Attributes], profileId: Option[String]): (ApiCode, Option[vo.ext.Booking]) =
    restPatch[vo.ext.Booking](s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId", vo.ext.UpdateSlotBooking(status, attributes, profileId))

}

trait VoFactory {

  def asSlotPrice(slotId: String, price: Option[vo.ext.Price]): vo.SlotPrice = {
    if (price.isEmpty) vo.SlotPrice(slotId)
    else {
      val p = price.get; import p._
      vo.SlotPrice(slotId, Some(price_id), name, amount, currency)
    }
  }

}

trait CollectSlots {

  type SlotProviderFnType = String => (ApiCode, Option[vo.ext.Slot])

  /** Get slots, fails if some slot cannot be collected. */
  def collectSlots(slotIds: Seq[String], providerFn: SlotProviderFnType): Either[ApiCode, Seq[vo.ext.Slot]] = {
    val codesAndSlots = slotIds.map(providerFn)
    codesAndSlots.collectFirst { case (code, None) => code } match {
      case Some(errorCode) => Left(errorCode)
      case _ =>
        val slots = codesAndSlots.flatMap { case (_, slot) => slot }
        if (!slots.forall(_.place_id == slots.head.place_id)) Left(SC_BAD_REQUEST)
        else Right(slots)
    }
  }
}

trait CollectSpaces {

  type SpaceProviderFnType = String => (ApiCode, Option[vo.ext.Space])

  /** Get a space with its prices set or try its parent space and so on. */
  def collectPricedSpace(spaceId: String, providerFn: SpaceProviderFnType): (ApiCode, Option[vo.ext.Space]) = {
    val (code, space) = providerFn(spaceId)
    if (code not SC_OK) (code, None)
    else if (space.get.prices.isDefined) (SC_OK, space)
    else if (space.get.parent_space_id.isEmpty) (SC_OK, space) // space without a price
    else collectPricedSpace(space.get.parent_space_id.get, providerFn)
  }

  /** Get spaces with its prices set (uses the "collectPricedSpace" method), fails if some slot cannot be collected. */
  def collectPricedSpaces(spaceIds: Seq[String], providerFn: SpaceProviderFnType): Either[ApiCode, Seq[vo.ext.Space]] = {
    val codesAndSpaces = spaceIds.map(collectPricedSpace(_, providerFn))
    codesAndSpaces.collectFirst { case (code, None) => code } match {
      case Some(errorCode) => Left(errorCode)
      case _ =>
        val spaces = codesAndSpaces.flatMap { case (_, space) => space }
        if (!spaces.forall(_.place_id == spaces.head.place_id)) Left(SC_BAD_REQUEST)
        else Right(spaces)
    }
  }
}

trait BookingService {
  def placeById(placeId: String): (ApiCode, Option[vo.ext.Place])
  def slotById(slotId: String): (ApiCode, Option[vo.ext.Slot])
  def quoteById(quoteId: String): Option[vo.Quote]
  def refundById(refundId: String): Option[vo.Refund]
  def referenceByRef(ref: String, profileId: String): Option[vo.Reference]
  def referencePaid(ref: String, profileId: String): Boolean
  def nextExpiredReference(): Option[vo.Reference]

  def quoteSlots(selected: Seq[vo.SelectedPrice], profileId: String): (ApiCode, Option[vo.Quote])
  def refundSlots(slotsIds: Seq[String], profileId: String): (ApiCode, Option[vo.Refund])
  def bookSlots(obj: vo.BookSlots, profileId: String): (ApiCode, Option[vo.Reference])
  def cancelSlots(obj: vo.CancelSlots, profileId: String): (ApiCode, Option[vo.Reference])
  def updateSlots(slotIds: Seq[String], profileId: String, attributes: Option[Attributes]): ApiCode

  val slotBookStatus = Map('bookable -> 0, 'booked -> 1, 'being_booked -> 2, 'being_released -> 3)
  val bookedStatus = Map('being_booked -> 1, 'booked -> 2, 'other -> 3)
  val bookingStatus = Map('inactive -> 0, 'active -> 1, 'being_booked -> 2)
  val quoteStatus = Map('inactive -> 0, 'complete -> 1, 'pending_payment -> 2)
  val refundStatus = Map('inactive -> 0, 'complete -> 1, 'pending_payment -> 2)
}

class BookingServiceImpl(val bookingDb: BookingDb, val placesBaseUrl: String, val slotsBaseUrl: String, val systemToken: String,
                         val restClient: RestClient)
    extends BookingService with PlacesMsRestCalls with SlotsMsRestCalls with CollectSlots with CollectSpaces with VoFactory
    with Auxiliary with QuoteSlots with RefundSlots with BookSlots {

  def resolveSlotPrices(slots: Seq[vo.ext.Slot]): Either[ApiCode, Seq[vo.ext.Slot]] = {
    val (pricedSlots, notPricedSlots) = slots.partition(_.prices.isDefined)
    val either =
      if (notPricedSlots.isEmpty) Right(Seq.empty[vo.ext.Space])
      else collectPricedSpaces(notPricedSlots.map(_.space_id), slotId => spaceFromMsPlaces(slots.head.place_id, slotId, withPrices = true))

    either match {
      case Left(code) => Left(code)
      case Right(pricedSpaces) =>
        val resolvedSlots = notPricedSlots.zip(pricedSpaces).map { case (slot, space) => slot.copy(prices = space.prices) }
        Right(pricedSlots ++ resolvedSlots)
    }
  }

}

trait Auxiliary {
  self: BookingServiceImpl =>

  override def placeById(placeId: String): (ApiCode, Option[vo.ext.Place]) =
    placeFromMsPlaces(placeId)

  override def slotById(slotId: String): (ApiCode, Option[vo.ext.Slot]) =
    slotFromMsSlots(slotId)

  override def quoteById(quoteId: String): Option[vo.Quote] =
    bookingDb.quoteById(quoteId)

  override def refundById(refundId: String): Option[vo.Refund] =
    bookingDb.refundById(refundId)

  override def referenceByRef(ref: String, profileId: String): Option[vo.Reference] =
    bookingDb.referenceByRef(ref, profileId)

  override def nextExpiredReference(): Option[vo.Reference] =
    bookingDb.nextExpiredReference(5) //todo 5 minutes timeout, config in Place

  override def referencePaid(ref: String, profileId: String): Boolean =
    bookingDb.referencePaid(ref, profileId)

  def referenceById(referenceId: String): Option[vo.Reference] =
    bookingDb.referenceById(referenceId)

  def slotIdsByQuote(quoteId: String): Option[Seq[String]] =
    quoteById(quoteId).map(_.prices.get.map(_.slot_id))

  def slotIdsByRefund(refundId: String): Option[Seq[String]] =
    refundById(refundId).map(_.prices.get.map(_.slot_id))

  def updateQuoteAfterBookSlots(referenceId: String) =
    referenceById(referenceId).foreach { reference =>
      val quote = reference.quote.get
      val status = if (quote.amount.getOrElse(0) == 0) quoteStatus('complete) else quoteStatus('pending_payment)
      bookingDb.updateQuoteStatus(quote.quote_id, status)
    }

  def updateRefundAfterCancelSlots(referenceId: String) =
    referenceById(referenceId).foreach { reference =>
      val refund = reference.refund.get
      val status = if (refund.amount.getOrElse(0) == 0) refundStatus('complete) else refundStatus('pending_payment)
      bookingDb.updateRefundStatus(refund.refund_id, status)
    }

  private val random = new Random
  def generateRef(profileId: String): String = {
    val gen = () => Iterator.continually(random.nextInt(16).toHexString).take(12).mkString.toUpperCase
    Iterator.fill(999)(gen()).find(!bookingDb.isRefUnique(_, profileId)).getOrElse(throw new IllegalStateException("Ref generator exhausted"))
  }
}

trait QuoteSlots {
  self: BookingServiceImpl =>

  /** Generate a new Quote for selected slots and prices without saving it to a database. */
  private def getQuote(selected: Seq[vo.SelectedPrice], profileId: String): (ApiCode, Option[vo.Quote]) = {
    def step1(): Either[ApiCode, Seq[vo.ext.Slot]] = // get all slots, Prices object filled
      collectSlots(selected.map(_.slot_id), slotId => slotFromMsSlots(slotId, withPrices = true))

    def step2(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('bookable))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // check slots dates
      val (codeA, place) = placeFromMsPlaces(slots.head.place_id)
      if (place.isEmpty) Left(codeA)
      else {
        val offsetMinutes = place.get.datetime.flatMap(_.offset_minutes).getOrElse(0)
        val localAsLong = ts.asLong(ts.addMinutes(ts.asCalendar, offsetMinutes))

        val past = (f: vo.ext.Slot => (Int, Int)) =>
          slots
            .map(f)
            .map { case (date, time) => ts.asLong(ts.asCalendar(date, time)) }
            .exists(localAsLong >=)

        val fromPast = past(slot => slot.date_from.getOrElse(0) -> slot.time_from.getOrElse(0))
        val toPast = past(slot => slot.date_to.getOrElse(0) -> slot.time_to.getOrElse(0))

        if (fromPast || toPast) Left(SC_CONFLICT) else Right(SC_OK)
      }
    }

    def step4(slots: Seq[vo.ext.Slot]): Either[ApiCode, Seq[vo.ext.Slot]] = // resolve slot prices
      resolveSlotPrices(slots)

    def step5(resolvedSlots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // check prices validity
      val valid =
        resolvedSlots.map { slot =>
          selected.exists { sp =>
            sp.slot_id == slot.slot_id &&
            (slot.prices.isEmpty || slot.prices.get.map(_.price_id).contains(sp.price_id.orNull))
          }
        }
      if (valid.contains(false)) Left(SC_CONFLICT) else Right(SC_OK)
    }

    def step6(resolvedSlots: Seq[vo.ext.Slot]): Either[ApiCode, Seq[vo.SlotPrice]] = { // convert selected into objects
      val pairs =
        selected.map { sp =>
          resolvedSlots
            .filter(_.slot_id == sp.slot_id)
            .collectFirst { case slot => slot -> slot.prices.getOrElse(Nil).find(_.price_id == sp.price_id.orNull) }
            .get
        }
      Right(pairs.flatMap { case (slot, price) => Some(asSlotPrice(slot.slot_id, price)) })
    }

    val eitherA: Either[ApiCode,vo.Quote] =
      for {
        slots         <- step1().right
        _             <- step2(slots).right
        _             <- step3(slots).right
        resolvedSlots <- step4(slots).right
        _             <- step5(resolvedSlots).right
        prices        <- step6(resolvedSlots).right
      } yield {
        val amount = if (prices.map(_.price_id).exists(None !=)) Some(prices.foldLeft(0)((a,b) => a+b.amount.getOrElse(0))) else None
        vo.Quote(null, slots.head.place_id, Some(profileId), amount, prices.headOption.flatMap(_.currency),
                 Some(quoteStatus('inactive)), Some(prices), deal = Some(false))
      }

    if (eitherA.isRight) (SC_CREATED, Some(eitherA.right.get))
    else (eitherA.left.get, None)
  }

  /** Generate a new Quote for selected slots and prices. */
  override def quoteSlots(selected: Seq[vo.SelectedPrice], profileId: String): (ApiCode, Option[vo.Quote]) = {
    val (code, quote) = getQuote(selected, profileId)
    (code, quote.map(bookingDb.createQuote))
  }

  /** Generate a new Quote for selected slots, slots should not require payment. */
  def quoteSlotsNoPayment(slotIds: Seq[String], profileId: String): (ApiCode, Option[vo.Quote]) =
    quoteSlots(slotIds.map(SelectedPrice(_, None)), profileId)

  /** Verify that existing Quote is valid by comparing it with a newly generated one. */
  def verifyQuote(quoteId: String, profileId: String): (ApiCode, Option[vo.Quote]) = {
    val existing = bookingDb.quoteById(quoteId)
    if (existing.isEmpty) (SC_NOT_FOUND, None)
    else {
      val selected = existing.get.prices.getOrElse(Nil).map(sp => SelectedPrice(sp.slot_id, sp.price_id))
      val (code, quote) = getQuote(selected, profileId)
      if (quote.isEmpty) (code, None)
      else if (existing.get != quote.get.copy(quote_id = quoteId, status = existing.get.status)) (SC_CONFLICT, None)
      else (SC_OK, existing)
    }
  }
}

trait RefundSlots {
  self: BookingServiceImpl =>

  /** Generate a new Refund for selected slots without saving it to a database. */
  private def getRefund(slotIds: Seq[String], profileId: String): (ApiCode, Option[vo.Refund]) = {
    def step1(): Either[ApiCode, Seq[vo.ext.Slot]] = // get all slots, Booked objects filled
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId, withBooked = true))

    def step2(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = // check slots status
      if (slots.exists(_.book_status.get == slotBookStatus('bookable))) Left(ApiCode(SC_CONFLICT, 'slots_contain_bookable)) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // check slots dates
    val (codeA, place) = placeFromMsPlaces(slots.head.place_id)
      if (place.isEmpty) Left(codeA)
      else {
        val offsetMinutes = place.get.datetime.flatMap(_.offset_minutes).getOrElse(0)
        val localAsLong = ts.asLong(ts.addMinutes(ts.asCalendar, offsetMinutes))

        val past = (f: vo.ext.Slot => (Int, Int)) =>
          slots
            .map(f)
            .map { case (date, time) => ts.asLong(ts.asCalendar(date, time)) }
            .exists(localAsLong >=)

        val fromPast = past(slot => slot.date_from.getOrElse(0) -> slot.time_from.getOrElse(0))
        val toPast = past(slot => slot.date_to.getOrElse(0) -> slot.time_to.getOrElse(0))

        if (fromPast || toPast) Left(ApiCode(SC_CONFLICT, 'slots_contain_expired)) else Right(SC_OK)
      }
    }

    def step4(slots: Seq[vo.ext.Slot]): Either[ApiCode, Seq[vo.Reference]] = { // get references for slots
      val references = slots.flatMap(_.booked).flatMap(booked => bookingDb.referenceByBookedId(booked.booked_id, quote = true))
      if (references.size != slots.size) Left(ApiCode(SC_CONFLICT, 'refs_slots_mismatch))
      else if (references.map(_.place_id).exists(slots.head.place_id !=)) Left(ApiCode(SC_CONFLICT, 'refs_place_mismatch))
      else if (references.map(_.profile_id.get).exists(profileId !=)) Left(ApiCode(SC_CONFLICT, 'refs_profile_mismatch))
      else Right(references.distinct)
    }

    def step5(references: Seq[vo.Reference]): Either[ApiCode, Seq[vo.Quote]] = // get quotes from references
      Right(references.map(_.quote.get))

    def step6(quotes: Seq[vo.Quote]): Either[ApiCode, Seq[vo.SlotPrice]] = { // collect cancellable prices
      val prices = quotes.flatMap(_.prices).flatten
      val incomplete =
        quotes
          .filter(_.status.get != quoteStatus('complete))
          .flatMap(_.prices.getOrElse(Nil).map(_.slot_id))
      val promoted =
        quotes
          .filter(_.deal.getOrElse(false))
          .flatMap(_.prices.getOrElse(Nil).map(_.slot_id))
      val cancellable =
        slotIds
          .flatMap(slotId => prices.find(_.slot_id == slotId))
          .map(sp => if (incomplete.contains(sp.slot_id)) sp.copy(amount = Some(0)) else sp)

      val conflictedA = !incomplete.forall(slotIds.contains)
      val conflictedB = !promoted.forall(slotIds.contains)
      if (conflictedA) Left(ApiCode(SC_CONFLICT, 'quotes_complete_not_all))
      else if (conflictedB) Left(ApiCode(SC_CONFLICT, 'quotes_deal_not_all))
      else Right(cancellable)
    }

    val eitherA: Either[ApiCode, vo.Refund] =
      for {
        slots      <- step1().right
        _          <- step2(slots).right
        _          <- step3(slots).right
        references <- step4(slots).right
        quotes     <- step5(references).right
        prices     <- step6(quotes).right
      } yield {
        val amount = if (prices.map(_.price_id).exists(None !=)) Some(prices.foldLeft(0)((a,b) => a+b.amount.getOrElse(0))) else None
        vo.Refund(null, slots.head.place_id, Some(profileId), amount, prices.headOption.flatMap(_.currency),
                  Some(refundStatus('inactive)), Some(prices), Some(quotes))
      }

    if (eitherA.isRight) (SC_CREATED, Some(eitherA.right.get))
    else (eitherA.left.get, None)
  }

  /** Generate a new Refund for selected slots. */
  override def refundSlots(slotIds: Seq[String], profileId: String): (ApiCode, Option[vo.Refund]) = {
    val (code, refund) = getRefund(slotIds, profileId)
    (code, refund.map(bookingDb.createRefund))
  }

  /** Generate a new Refund for selected slots, slots should not require payment. */
  def refundSlotsNoPayment(slotIds: Seq[String], profileId: String): (ApiCode, Option[vo.Refund]) = {
    val (code, refund) = getRefund(slotIds, profileId)
    if (refund.isDefined && refund.get.amount.getOrElse(0) != 0) (ApiCode(SC_CONFLICT, 'refund_requires_payment), None)
    else (code, refund.map(bookingDb.createRefund))
  }

  /** Verify that existing Refund is valid by comparing it with a newly generated one. */
  def verifyRefund(refundId: String, profileId: String): (ApiCode, Option[vo.Refund]) = {
    val existing = bookingDb.refundById(refundId)
    if (existing.isEmpty) (ApiCode(SC_NOT_FOUND), None)
    else {
      val slotIds = existing.get.prices.getOrElse(Nil).map(_.slot_id)
      val (code, refund) = getRefund(slotIds, profileId)
      if (refund.isEmpty) (code, None)
      else if (existing.get != refund.get.copy(refund_id = refundId, status = existing.get.status)) (ApiCode(SC_CONFLICT, 'generated_refund_mismatch), None)
      else (SC_OK, existing)
    }
  }
}

trait BookSlots {
  self: BookingServiceImpl =>

  private def bookSlots(quoteId: String, profileId: String): (ApiCode, Option[vo.Reference]) = {
    val rollbacks = new ListBuffer[() => Unit]

    def step0(): Either[ApiCode, vo.Quote] = { // check quote
      val (code, quote) = verifyQuote(quoteId, profileId)
      if (quote.isEmpty) Left(code)
      else if (quote.get.status.get != quoteStatus('inactive)) Left(SC_CONFLICT)
      else Right(quote.get)
    }

    def step1(quote: vo.Quote): Either[ApiCode, Seq[vo.ext.Slot]] = { // get all slots
      val slotIds = quote.prices.getOrElse(Nil).map(_.slot_id)
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId))
    }

    def step2(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('bookable))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[ApiCode, vo.ext.Booked] = { // create Booked object
      val slotIds = slots.map(_.slot_id)
      val (code, booked) = createBookedWithMsSlots(slotIds, profileId)
      val either = if (code not SC_CREATED) Left(code) else Right(booked.get)

      val rollback = () => booked.foreach(_ => updateBookedWithMsSlots(booked.get.booked_id, bookingIds = None, Some(bookedStatus('other)), profileId)): Unit
      rollback +=: rollbacks

      either
    }

    def step4(slots: Seq[vo.ext.Slot], booked: vo.ext.Booked): Either[ApiCode, _] = { // acquire slots
      val slotIds = slots.map(_.slot_id)
      val codes = slotIds.map(slotId => updateHoldWithMsSlots(slotId, booked.booked_id, slotBookStatus('being_booked)))
      val either = codes.find(_ not SC_OK).map(Left(_)).getOrElse(Right(SC_OK))

      val rollback = () => slotIds.foreach(slotId => updateHoldWithMsSlots(slotId, booked.booked_id, slotBookStatus('being_released)))
      rollback +=: rollbacks

      either
    }

    def step5(slots: Seq[vo.ext.Slot]): Either[ApiCode, Seq[vo.ext.Booking]] = { // create bookings
      val slotIds = slots.map(_.slot_id)
      val (codes, bookings) =
        slotIds.map(slotId => createBookingWithMsSlots(slotId, profileId, "Booking")) match {
          case cb => (cb.map(_._1), cb.map(_._2))
        }
      val either = codes.find(_ not SC_CREATED).map(Left(_)).getOrElse(Right(bookings.map(_.get)))

      val rollback = () => bookings.flatten.foreach(booking => updateBookingWithMsSlots(booking.booking_id, booking.slot_id, Some(bookingStatus('inactive)), attributes = None, profileId = None))
      rollback +=: rollbacks

      either
    }

    def step6(booked: vo.ext.Booked, bookings: Seq[vo.ext.Booking]): Either[ApiCode, _] = { // update Booked object
      val (code, _) = updateBookedWithMsSlots(booked.booked_id, Some(bookings.map(_.booking_id)), Some(bookedStatus('booked)), profileId)
      if (code not SC_OK) Left(code) else Right(SC_OK)
    }

    def step7(slots: Seq[vo.ext.Slot], booked: vo.ext.Booked): Either[ApiCode, _] = { // confirm slots
      val slotIds = slots.map(_.slot_id)
      val codes = slotIds.map(slotId => updateHoldWithMsSlots(slotId, booked.booked_id, slotBookStatus('booked)))
      codes.find(_ not SC_OK).map(Left(_)).getOrElse(Right(SC_OK))
    }

    def step8(bookings: Seq[vo.ext.Booking], quote: vo.Quote): Either[ApiCode, _] = { // update bookings as active and set attributes
      val attrs = (booking: vo.ext.Booking) => {
        val sp = quote.prices.getOrElse(Nil).filter(_.slot_id == booking.slot_id)
        val value = sp.head.price_id.map("price_id" -> JsString(_)).toList
        Attributes(value: _*)
      }

      val codes =
        bookings.map(booking => updateBookingWithMsSlots(booking.booking_id, booking.slot_id, Some(bookingStatus('active)), Some(attrs(booking)), profileId = None)) match {
          case cb => cb.map(_._1)
        }
      codes.find(_ not SC_OK).map(Left(_)).getOrElse(Right(SC_OK))
    }

    val eitherA: Either[ApiCode,vo.Reference] =
      for {
        quote    <- step0().right
        slots    <- step1(quote).right
        _        <- step2(slots).right
        booked   <- step3(slots).right
        _        <- step4(slots, booked).right
        bookings <- step5(slots).right
        _        <- step6(booked, bookings).right
        _        <- step7(slots, booked).right
        _        <- step8(bookings, quote).right
      } yield {
        val reference = Reference(null, slots.head.place_id, Some(generateRef(profileId)), Some(profileId), Some(Seq(booked.booked_id)),
                                  quote = Some(quote), refund = None)
        bookingDb.createReference(reference)
      }

    if (eitherA.isRight) (SC_CREATED, Some(eitherA.right.get))
    else {
      rollbacks.foreach(rollback => rollback())
      (eitherA.left.get, None)
    }
  }

  private def cancelSlots(refundId: String, profileId: String): (ApiCode, Option[vo.Reference]) = {
    def step0(): Either[ApiCode, vo.Refund] = { // check refund
      val (code, refund) = verifyRefund(refundId, profileId)
      if (refund.isEmpty) Left(code)
      else if (refund.get.status.get != quoteStatus('inactive)) Left(SC_CONFLICT)
      else Right(refund.get)
    }

    def step1(refund: vo.Refund): Either[ApiCode, Seq[vo.ext.Slot]] = { // get all slots, Booked objects filled
      val slotIds = refund.prices.getOrElse(Nil).map(_.slot_id)
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId, withBooked = true))
    }

    def step2(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('booked))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[ApiCode, Seq[vo.ext.Booked]] = { // check slots Booked objects belong to a user and have proper status
      val booked = slots.map(_.booked.get).distinct
      if (booked.exists(_.profile_id.get != profileId)) Left(SC_FORBIDDEN)
      else if (booked.exists(_.status.get != bookedStatus('booked))) Left(SC_CONFLICT)
      else Right(booked)
    }

    def step4(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // release slots
      val ids = slots.groupBy(slot => slot.booked.get.booked_id).map { case (bookedId, slotSeq) => bookedId -> slotSeq.map(_.slot_id) }
      val codes = ids.flatMap { case (bookedId, slotIdSeq) => slotIdSeq.map(slotId => updateHoldWithMsSlots(slotId, bookedId, slotBookStatus('being_released))) }
      codes.find(_ not SC_OK).map(Left(_)).getOrElse(Right(SC_OK))
    }

    def step5(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // update Bookings as inactive
      val codes =
        slots.map { slot =>
          val b = slot.booked.get
          val i = b.slot_ids.get.indexOf(slot.slot_id)
          val bookingId = b.booking_ids.get.apply(i)
          updateBookingWithMsSlots(bookingId, slot.slot_id, Some(bookingStatus('inactive)), attributes = None, profileId = None)._1
        }
      codes.find(_ not SC_OK).map(Left(_)).getOrElse(Right(SC_OK))
    }

    val eitherA: Either[ApiCode,vo.Reference] =
      for {
        refund   <- step0().right
        slots    <- step1(refund).right
        _        <- step2(slots).right
        booked   <- step3(slots).right
        _        <- step4(slots).right
        _        <- step5(slots).right
      } yield {
        val reference = Reference(null, slots.head.place_id, Some(generateRef(profileId)), Some(profileId), Some(booked.map(_.booked_id)),
                                  quote = None, refund = Some(refund))
        bookingDb.createReference(reference)
      }

    if (eitherA.isRight) (SC_OK, Some(eitherA.right.get))
    else (eitherA.left.get, None)
  }

  override def bookSlots(obj: vo.BookSlots, profileId: String): (ApiCode, Option[vo.Reference]) = {
    val (codeQ: ApiCode, quoteId) = // if no quote supplied then try to create one for slot IDs
      obj.quote_id.map(ApiCode.OK -> Some(_)).getOrElse {
        val (c, q) = quoteSlotsNoPayment(obj.slot_ids.get, profileId)
        (c, q.map(_.quote_id))
      }

    if (quoteId.isEmpty) (codeQ, None)
    else {
      val (codeR, reference) = bookSlots(quoteId.get, profileId)
      reference.foreach(r => updateQuoteAfterBookSlots(r.reference_id))

      val systemAttrs = Attributes(JsObject(
        "ref" -> JsString(reference.get.ref.get)
      ))

      val slotIds = slotIdsByQuote(quoteId.get).get
      val codeU = updateSlots(slotIds, profileId, obj.attributes) //todo check codeU
      val codeX = updateSlots(slotIds, "*", Some(systemAttrs)) //todo check codeX

      if (reference.isEmpty) (codeR, None) else (SC_CREATED, referenceById(reference.get.reference_id))
    }
  }

  override def cancelSlots(obj: vo.CancelSlots, profileId: String): (ApiCode, Option[vo.Reference]) = {
    val (codeQ: ApiCode, refundId) = // if no refund supplied then try to create one for slot IDs
      obj.refund_id.map(ApiCode.OK -> Some(_)).getOrElse {
        val (c, r) = refundSlotsNoPayment(obj.slot_ids.get, profileId)
        (c, r.map(_.refund_id))
      }

    if (refundId.isEmpty) (codeQ, None)
    else {
      val slotIds = slotIdsByRefund(refundId.get).get
      val codeU = updateSlots(slotIds, profileId, obj.attributes) //todo check codeU

      val (codeR, reference) = cancelSlots(refundId.get, profileId)
      reference.foreach(r => updateRefundAfterCancelSlots(r.reference_id))

      if (reference.isEmpty) (codeR, None) else (SC_CREATED, referenceById(reference.get.reference_id))
    }
  }

  override def updateSlots(slotIds: Seq[String], profileId: String, attributes: Option[Attributes]): ApiCode = {
    def step1(): Either[ApiCode, Seq[vo.ext.Slot]] =  // get all slots, Booked objects filled
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId, withBooked = true))

    def step2(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('booked))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // check slots Booked objects belong to a user and have proper status
      val booked = slots.map(_.booked.get)
      if (booked.exists(_.profile_id.get != profileId) && profileId != "*") Left(SC_FORBIDDEN)
      else if (booked.exists(_.status.get != bookedStatus('booked))) Left(SC_CONFLICT)
      else Right(SC_OK)
    }

    def step4(slots: Seq[vo.ext.Slot]): Either[ApiCode, _] = { // update Bookings attributes (apply profile permissions)
      val codes =
        slots.map { slot =>
          val b = slot.booked.get
          val i = b.slot_ids.get.indexOf(slot.slot_id)
          val bookingId = b.booking_ids.get.apply(i)
          updateBookingWithMsSlots(bookingId, slot.slot_id, status = None, attributes, if (profileId != "*") Some(profileId) else None)._1
        }
      codes.find(_ not SC_OK).map(Left(_)).getOrElse(Right(SC_OK))
    }

    val eitherA: Either[ApiCode, ApiCode] =
      for {
        slots    <- step1().right
        _        <- step2(slots).right
        _        <- step3(slots).right
        _        <- step4(slots).right
      } yield SC_OK

    if (eitherA.isRight) eitherA.right.get
    else eitherA.left.get
  }

}
