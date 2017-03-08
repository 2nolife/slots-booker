package com.coldcore.slotsbooker
package ms.booking.service

import ms.booking.vo
import ms.http.{RestClient, SystemRestCalls}
import ms.vo.{Attributes, StringEntity}
import org.apache.http.HttpStatus._

import collection.mutable.ListBuffer

trait PlacesMsRestCalls extends SystemRestCalls {
  self: {
    val placesBaseUrl: String
    val systemToken: String
    val restClient: RestClient
  } =>

  /** Get a place from the "places" micro service */
  def placeFromMsPlaces(placeId: String): (Int, Option[vo.ext.Place]) =
    restGet[vo.ext.Place](s"$placesBaseUrl/places/$placeId?deep=false")

  /** Get a space from the "places" micro service */
  def spaceFromMsPlaces(placeId: String, spaceId: String,
                        withPrices: Boolean = false): (Int, Option[vo.ext.Space]) =
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
                      withPrices: Boolean = false, withBookings: Boolean = false, withBooked: Boolean = false): (Int, Option[vo.ext.Slot]) =
    restGet[vo.ext.Slot](s"$slotsBaseUrl/slots/$slotId?deep=false&deep_prices=$withPrices&deep_bookings=$withBookings&deep_booked=$withBooked")

  /** Get Booking from the "slots" micro service */
  def getBookingFromMsSlots(slotId: String, bookingId: String): (Int, Option[vo.ext.Booking]) =
    restGet[vo.ext.Booking](s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId")

  /** Create Booking in the "slots" micro service */
  def createBookingWithMsSlots(slotId: String, profileId: String, name: String): (Int, Option[vo.ext.Booking]) =
    restPost[vo.ext.Booking](s"$slotsBaseUrl/slots/$slotId/bookings", vo.ext.CreateSlotBooking(profileId, name))

  /** Create Booked in the "slots" micro service */
  def createBookedWithMsSlots(slotIds: Seq[String], profileId: String): (Int, Option[vo.ext.Booked]) =
    restPost[vo.ext.Booked](s"$slotsBaseUrl/slots/booked", vo.ext.CreateSlotBooked(profileId, slotIds))

  /** Update Booked in the "slots" micro service */
  def updateBookedWithMsSlots(bookedId: String, bookingIds: Option[Seq[String]], status: Option[Int], profileId: String): (Int, Option[vo.ext.Booked]) =
    restPatch[vo.ext.Booked](s"$slotsBaseUrl/slots/booked/$bookedId", vo.ext.UpdateSlotBooked(profileId, status, deal = None, bookingIds))

  /** Update Hold on a slot in the "slots" micro service */
  def updateHoldWithMsSlots(slotId: String, bookedId: String, status: Int): Int =
    restPatch[StringEntity](s"$slotsBaseUrl/slots/$slotId/hold", vo.ext.UpdateSlotHold(bookedId, status))._1

  /** Update Booking in the "slots" micro service */
  def updateBookingWithMsSlots(bookingId: String, slotId: String, status: Option[Int], attributes: Option[Attributes], profileId: Option[String]): (Int, Option[vo.ext.Booking]) =
    restPatch[vo.ext.Booking](s"$slotsBaseUrl/slots/$slotId/bookings/$bookingId", vo.ext.UpdateSlotBooking(status, attributes, profileId))

}

trait VoFactory {

  def asQuotedPrice(p: vo.ext.Price, slotId: Option[String] = None): vo.QuotedPrice = {
    import p._
    vo.QuotedPrice(Some(place_id), Some(space_id), slotId.orElse(slot_id), Some(price_id), name, amount, currency)
  }

}

trait CollectSlots {

  type SlotProviderFnType = String => (Int, Option[vo.ext.Slot])

  /** Get slots, fails if some slot cannot be collected. */
  def collectSlots(slotIds: Seq[String], providerFn: SlotProviderFnType): Either[Int, Seq[vo.ext.Slot]] = {
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

  type SpaceProviderFnType = String => (Int, Option[vo.ext.Space])

  /** Get a space with its prices set or try its parent space and so on. */
  def collectPricedSpace(spaceId: String, providerFn: SpaceProviderFnType): (Int, Option[vo.ext.Space]) = {
    val (code, space) = providerFn(spaceId)
    if (code != SC_OK) (code, None)
    else if (space.get.prices.isDefined) (SC_OK, space)
    else if (space.get.parent_space_id.isEmpty) (SC_NOT_FOUND, None)
    else collectPricedSpace(space.get.parent_space_id.get, providerFn)
  }

  /** Get spaces with its prices set (uses the "collectPricedSpace" method), fails if some slot cannot be collected. */
  def collectPricedSpaces(spaceIds: Seq[String], providerFn: SpaceProviderFnType): Either[Int, Seq[vo.ext.Space]] = {
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
  def placeById(placeId: String): (Int, Option[vo.ext.Place])
  def slotById(slotId: String): (Int, Option[vo.ext.Slot])
  def quoteSlots(slotIds: Seq[String]): (Int, Option[vo.Quote])
  def bookSlots(slotIds: Seq[String], profileId: String): (Int, Option[vo.Reference])
  def cancelSlots(slotIds: Seq[String], profileId: String): (Int, Option[vo.Reference])
  def updateSlots(slotIds: Seq[String], profileId: String, attributes: Option[Attributes]): (Int, Option[vo.Reference])

  val slotBookStatus = Map('bookable -> 0, 'booked -> 1, 'being_booked -> 2, 'being_released -> 3)
  val bookedStatus = Map('being_booked -> 1, 'booked -> 2, 'other -> 3)
  val bookingStatus = Map('inactive -> 0, 'active -> 1, 'being_booked -> 2)
}

class BookingServiceImpl(val placesBaseUrl: String, val slotsBaseUrl: String, val systemToken: String,
                         val restClient: RestClient)
    extends BookingService with PlacesMsRestCalls with SlotsMsRestCalls with CollectSlots with CollectSpaces with VoFactory
    with Auxiliary with QuoteSlots with BookSlots {

  def resolveSlotPrices(slots: Seq[vo.ext.Slot]): Either[Int, Seq[vo.ext.Slot]] = {
    val (pricedSlots, notPricedSlots) = slots.partition(_.prices.isDefined)
    val either =
      if (notPricedSlots.isEmpty) Right(Seq.empty[vo.ext.Space])
      else collectPricedSpaces(notPricedSlots.map(_.space_id), slotId => spaceFromMsPlaces(slots.head.place_id, slotId, withPrices = true))

    either match {
      case Left(code) => Left(code)
      case Right(pricedSpaces) =>
        val resovedSlots = notPricedSlots.zip(pricedSpaces).map { case (slot, space) => slot.copy(prices = space.prices) }
        Right(pricedSlots ++ resovedSlots)
    }
  }

}

trait Auxiliary {
  self: BookingServiceImpl =>

  def placeById(placeId: String): (Int, Option[vo.ext.Place]) =
    placeFromMsPlaces(placeId)

  def slotById(slotId: String): (Int, Option[vo.ext.Slot]) =
    slotFromMsSlots(slotId)
}

trait QuoteSlots {
  self: BookingServiceImpl =>

  private def quoteSlots(slotIds: Seq[String], onResult: (Int, Option[vo.Quote]) => Unit) {
    val eitherA = collectSlots(slotIds, slotId => slotFromMsSlots(slotId, withPrices = true))

    for (code <- eitherA.left)
      onResult(code, None)

    for (slots <- eitherA.right) {
      val eitherB = resolveSlotPrices(slots)

      for (code <- eitherB.left)
        onResult(code, None)

      for (resolvedSlots <- eitherB.right) {
        val prices = resolvedSlots.flatMap(slot => slot.prices.get.map(asQuotedPrice(_)))
        val quote = vo.Quote(prices = Some(prices))
        onResult(SC_OK, Some(quote))
      }
    }
  }

  override def quoteSlots(slotIds: Seq[String]): (Int, Option[vo.Quote]) = {
    var result: (Int, Option[vo.Quote]) = null
    quoteSlots(slotIds, (code, quote) => result = (code, quote))
    result
  }

}

trait BookSlots {
  self: BookingServiceImpl =>

  override def bookSlots(slotIds: Seq[String], profileId: String): (Int, Option[vo.Reference]) = {
    val rollbacks = new ListBuffer[() => Unit]

    def step1(): Either[Int, Seq[vo.ext.Slot]] = // get all slots
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId))

    def step2(slots: Seq[vo.ext.Slot]): Either[Int, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('bookable))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(): Either[Int, vo.ext.Booked] = { // create Booked object
      val (code, booked) = createBookedWithMsSlots(slotIds, profileId)
      val either = if (code != SC_CREATED) Left(code) else Right(booked.get)

      val rollback = () => booked.foreach(_ => updateBookedWithMsSlots(booked.get.booked_id, bookingIds = None, Some(bookedStatus('other)), profileId)): Unit
      rollback +=: rollbacks

      either
    }

    def step4(booked: vo.ext.Booked): Either[Int, _] = { // acquire slots
      val codes = slotIds.map(slotId => updateHoldWithMsSlots(slotId, booked.booked_id, slotBookStatus('being_booked)))
      val either = codes.find(SC_OK !=).map(Left(_)).getOrElse(Right(SC_OK))

      val rollback = () => slotIds.foreach(slotId => updateHoldWithMsSlots(slotId, booked.booked_id, slotBookStatus('being_released)))
      rollback +=: rollbacks

      either
    }

    def step5(): Either[Int, Seq[vo.ext.Booking]] = { // create bookings
      val (codes, bookings) =
        slotIds.map(slotId => createBookingWithMsSlots(slotId, profileId, "Booking")) match {
          case cb => (cb.map(_._1), cb.map(_._2))
        }
      val either = codes.find(SC_CREATED !=).map(Left(_)).getOrElse(Right(bookings.map(_.get)))

      val rollback = () => bookings.flatten.foreach(booking => updateBookingWithMsSlots(booking.booking_id, booking.slot_id, Some(bookingStatus('inactive)), attributes = None, profileId = None))
      rollback +=: rollbacks

      either
    }

    def step6(booked: vo.ext.Booked, bookings: Seq[vo.ext.Booking]): Either[Int, _] = { // update Booked object
      val (code, _) = updateBookedWithMsSlots(booked.booked_id, Some(bookings.map(_.booking_id)), Some(bookedStatus('booked)), profileId)
      if (code != SC_OK) Left(code) else Right(SC_OK)
    }

    def step7(booked: vo.ext.Booked): Either[Int, _] = { // confirm slots
      val codes = slotIds.map(slotId => updateHoldWithMsSlots(slotId, booked.booked_id, slotBookStatus('booked)))
      codes.find(SC_OK !=).map(Left(_)).getOrElse(Right(SC_OK))
    }

    def step8(bookings: Seq[vo.ext.Booking]): Either[Int, _] = { // update bookings as active
      val codes =
        bookings.map(booking => updateBookingWithMsSlots(booking.booking_id, booking.slot_id, Some(bookingStatus('active)), attributes = None, profileId = None)) match {
          case cb => cb.map(_._1)
        }
      codes.find(SC_OK !=).map(Left(_)).getOrElse(Right(SC_OK))
    }

    val eitherA: Either[Int,vo.Reference] =
      for {
        slots    <- step1().right
        _        <- step2(slots).right
        booked   <- step3().right
        _        <- step4(booked).right
        bookings <- step5().right
        _        <- step6(booked, bookings).right
        _        <- step7(booked).right
        _        <- step8(bookings).right
      } yield vo.Reference(ref = None)

    if (eitherA.isRight) (SC_CREATED, Some(eitherA.right.get))
    else {
      rollbacks.foreach(rollback => rollback())
      (eitherA.left.get, None)
    }
  }

  override def cancelSlots(slotIds: Seq[String], profileId: String): (Int, Option[vo.Reference]) = {
    def step1(): Either[Int, Seq[vo.ext.Slot]] =  // get all slots, Booked objects filled
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId, withBooked = true))

    def step2(slots: Seq[vo.ext.Slot]): Either[Int, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('booked))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[Int, _] = { // check slots Booked objects belong to a user and have proper status
      val booked = slots.map(_.booked.get)
      if (booked.exists(_.profile_id.get != profileId)) Left(SC_FORBIDDEN)
      else if (booked.exists(_.status.get != bookedStatus('booked))) Left(SC_CONFLICT)
      else Right(SC_OK)
    }

    def step4(slots: Seq[vo.ext.Slot]): Either[Int, _] = { // check "deal" property (cancel all or nothing)
      val booked = slots.map(_.booked.get)
      if (booked.exists(_.deal.getOrElse(false))) Left(SC_FORBIDDEN) else Right(SC_OK)
    }

    def step5(slots: Seq[vo.ext.Slot]): Either[Int, _] = { // release slots
      val ids = slots.groupBy(slot => slot.booked.get.booked_id).map { case (bookedId, slotSeq) => bookedId -> slotSeq.map(_.slot_id) }
      val codes = ids.flatMap { case (bookedId, slotIdSeq) => slotIdSeq.map(slotId => updateHoldWithMsSlots(slotId, bookedId, slotBookStatus('being_released))) }
      codes.find(SC_OK !=).map(Left(_)).getOrElse(Right(SC_OK))
    }

    def step6(slots: Seq[vo.ext.Slot]): Either[Int, _] = { // update Bookings as inactive
      val codes =
        slots.map { slot =>
          val b = slot.booked.get
          val i = b.slot_ids.get.indexOf(slot.slot_id)
          val bookingId = b.booking_ids.get.apply(i)
          updateBookingWithMsSlots(bookingId, slot.slot_id, Some(bookingStatus('inactive)), attributes = None, profileId = None)._1
        }
      codes.find(SC_OK !=).map(Left(_)).getOrElse(Right(SC_OK))
    }

    val eitherA: Either[Int,vo.Reference] =
      for {
        slots    <- step1().right
        _        <- step2(slots).right
        _        <- step3(slots).right
        _        <- step4(slots).right
        _        <- step5(slots).right
        _        <- step6(slots).right
      } yield vo.Reference(ref = None)

    if (eitherA.isRight) (SC_OK, Some(eitherA.right.get))
    else (eitherA.left.get, None)
  }

  override def updateSlots(slotIds: Seq[String], profileId: String, attributes: Option[Attributes]): (Int, Option[vo.Reference]) = {
    def step1(): Either[Int, Seq[vo.ext.Slot]] =  // get all slots, Booked objects filled
      collectSlots(slotIds, slotId => slotFromMsSlots(slotId, withBooked = true))

    def step2(slots: Seq[vo.ext.Slot]): Either[Int, _] = // check slots status
      if (slots.exists(_.book_status.get != slotBookStatus('booked))) Left(SC_CONFLICT) else Right(SC_OK)

    def step3(slots: Seq[vo.ext.Slot]): Either[Int, _] = { // check slots Booked objects belong to a user and have proper status
      val booked = slots.map(_.booked.get)
      if (booked.exists(_.profile_id.get != profileId)) Left(SC_FORBIDDEN)
      else if (booked.exists(_.status.get != bookedStatus('booked))) Left(SC_CONFLICT)
      else Right(SC_OK)
    }

    def step4(slots: Seq[vo.ext.Slot]): Either[Int, _] = { // update Bookings attributes (apply profile permissions)
      val codes =
        slots.map { slot =>
          val b = slot.booked.get
          val i = b.slot_ids.get.indexOf(slot.slot_id)
          val bookingId = b.booking_ids.get.apply(i)
          updateBookingWithMsSlots(bookingId, slot.slot_id, status = None, attributes, Some(profileId))._1
        }
      codes.find(SC_OK !=).map(Left(_)).getOrElse(Right(SC_OK))
    }

    val eitherA: Either[Int,vo.Reference] =
      for {
        slots    <- step1().right
        _        <- step2(slots).right
        _        <- step3(slots).right
        _        <- step4(slots).right
      } yield vo.Reference(ref = None)

    if (eitherA.isRight) (SC_OK, Some(eitherA.right.get))
    else (eitherA.left.get, None)
  }

}