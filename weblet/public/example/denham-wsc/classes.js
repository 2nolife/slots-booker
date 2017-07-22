function MyPlace(/*Place*/ source) {

  var _this = this

  this.source = source

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource(/*str*/ key) {
    _this.id = source.id
    _this.name = source.name
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  source.refresh('spaces', false, wrapSeasons)

  function wrapSeasons() {
    _this.seasons = source.spaces.map(function(space) { return new MySeason(space, _this) })
    _this.activeSeason = _this.seasons[0]
    _this.onChangeCallback.trigger('seasons-wrap', _this)
  }

}

function MySeason(/*Space*/ source, /*MyPlace*/ place) {

  var _this = this

  this.source = source
  this.place = place

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource(/*str*/ key) {
    _this.id = source.id
    _this.name = source.name

    if (key == 'slots') wrapBookedSlots()
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  source.refresh('spaces', false, wrapLakes)

  function wrapLakes() {
    _this.lakes = source.spaces.map(function(space) { return new MyLake(space, _this) })
    _this.firstLake = _this.lakes[0]
    _this.onChangeCallback.trigger('lakes-wrap', _this)
  }

  function wrapBookedSlots() {
    _this.bookedSlots = source.slots.map(function(slot) { return new MySlot(slot, null) })
    _this.onChangeCallback.trigger('booked-slots-wrap', _this)
  }

  function bookedPaidSlotsFromToday(/*bool*/ force, /*fn*/ callback) {
    var today = parseInt(sb.utils.todayDate()),
        plus6 = parseInt(sb.utils.addDaysDate(today, 360))
    source.slotsFilter = { from: today, to: plus6, inner: true, booked: '', paid: true }
    source.refreshRetry('slots', force, callback)
  }

  function bookedSlotsForMonth(/*str*/ date, /*bool*/ force, /*fn*/ callback) {
    var firstDay = parseInt(sb.utils.monthFirstDate(date)),
        lastDay = parseInt(sb.utils.monthLastDate(date))
    source.slotsFilter = { from: firstDay, to: lastDay, inner: true, booked: '' }
    source.refreshRetry('slots', force, callback)
  }

  this.refreshBookedPaidSlotsFromToday = function(/*bool*/ force, /*fn*/ callback) {
    bookedPaidSlotsFromToday(force, callback)
  }

  this.transactionHistoryForMonth = function(/*str*/ date, /*bool*/ force, /*fn*/ callback) {
    _this.transactionHistory = []
    var history = []
    
    bookedSlotsForMonth(date, force, function() {
      _this.bookedSlots.forEach(function(/*MySlot*/ slot) {
        slot.refreshBookings(false, function() {
          slot.bookings.forEach(function(/*MyBooking*/ booking) {
            booking.refreshReference(false, function() {
              booking.refreshReferenceCancel(false, function() {

                if (booking.reference) {
                  _this.transactionHistory.push({
                    updated: booking.reference.quote.entry.updated,
                    paid: booking.slotPaid,
                    type: 'paid',
                    status: booking.reference.quote.status,
                    slot: slot
                  })
                }
                if (booking.referenceCancel) {
                  _this.transactionHistory.push({
                    updated: booking.referenceCancel.refund.entry.updated,
                    paid: booking.slotRefund,
                    type: 'refund',
                    status: booking.referenceCancel.refund.status,
                    slot: slot
                  })
                }

                _this.transactionHistory.sort(function(a, b) {
                  var dtA = parseInt(sb.utils.datetime(a.updated.date, a.updated.time)),
                      dtB = parseInt(sb.utils.datetime(b.updated.date, b.updated.time))
                  return dtA < dtB ? -1 : dtA > dtB ? 1 : 0
                })

              })
            })
          })
        })
      })

    })
  }

}

function MyLake(/*Space*/ source, /*MySeason*/ season) {

  var _this = this

  this.source = source
  this.season = season

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource(/*str*/ key) {
    _this.id = source.id
    _this.name = source.name
    _this.effectivePrices = source.effectivePrices

    if (key == 'slots') wrapSlots()
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  this.refreshSlotsByDate = function(/*str|num*/ from, /*str|num*/ to, /*bool*/ force, /*fn*/ callback) {
    source.slotsFilter = { from: parseInt(from), to: parseInt(to) }
    //console.log(from, to, force)
    source.refreshRetry('slots', force, callback)
  }

  function wrapSlots() {
    _this.slots = source.slots.map(function(slot) { return new MySlot(slot, _this) })
    _this.firstSlot = _this.slots.length ? _this.slots[0] : null
    _this.onChangeCallback.trigger('slots-wrap', _this)
  }

  this.refreshEffectivePrices = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('effectivePrices', force, callback)
  }

  this.onSlotBounds = function() {
    var effectiveBookBounds = _this.firstSlot.effectiveBookBounds,
        effectiveCancelBounds = _this.firstSlot.effectiveCancelBounds,
        slots = _this.slots || []
    slots.map(function(slot) {
      slot.effectiveBookBounds = effectiveBookBounds
      slot.effectiveCancelBounds = effectiveCancelBounds
      slot.onChangeCallback.trigger()
    })
  }

}

function MySlot(/*Slot*/ source, /*MyLake*/ lake) {

  var _this = this

  this.source = source
  this.lake = lake

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource(/*str*/ key) {
    _this.id = source.id
    _this.placeId = source.placeId
    _this.spaceId = source.spaceId
    _this.name = source.name
    _this.dateFrom = source.dateFrom
    _this.dateTo = source.dateTo
    _this.timeFrom = source.timeFrom
    _this.timeTo = source.timeTo
    _this.attributes = source.attributes
    _this.bookStatus = source.bookStatus
    _this.disabled = source.disabled
    _this.effectiveBookBounds = _this.effectiveBookBounds || source.effectiveBookBounds
    _this.effectiveCancelBounds = _this.effectiveCancelBounds || source.effectiveCancelBounds

    _this.custom = customProperties()

    if (key == 'bookings') wrapBookings()
    if (key == 'activeBookings') wrapActiveBooking()
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  function customProperties() {
    var now = new Date(),
        nowDatetime = sb.utils.datetime(sb.utils.todayDate(), now.getHours()*100+now.getMinutes()), //todo use place local time
        slotDatetime = sb.utils.datetime(_this.dateFrom, _this.timeFrom),
        expired = sb.utils.datetimeCompare(slotDatetime, nowDatetime) <= 0,
        bookableBounds = _this.effectiveBookBounds ? inBounds(_this.effectiveBookBounds) : false,
        afterBookableBounds = _this.effectiveBookBounds ? afterBounds(_this.effectiveBookBounds) : false,
        cancellableBounds = _this.effectiveCancelBounds ? inBounds(_this.effectiveCancelBounds) : false,
        booked = _this.bookStatus > 0,
        mine = ((_this.activeBooking || {}).custom || {}).mine,
        legend = 'unavailable'  //todo club reservations

    function inBounds(/*Bounds*/ bounds) {
      var datetimeA = bounds.dateFrom && bounds.timeFrom ? sb.utils.datetime(bounds.dateFrom, bounds.timeFrom) : null,
          datetimeB = bounds.dateTo && bounds.timeTo ? sb.utils.datetime(bounds.dateTo, bounds.timeTo) : null
      return (!datetimeA || sb.utils.datetimeCompare(nowDatetime, datetimeA) >= 0) &&
             (!datetimeB || sb.utils.datetimeCompare(nowDatetime, datetimeB) <= 0)
    }

    function afterBounds(/*Bounds*/ bounds) {
      var datetimeB = bounds.dateTo && bounds.timeTo ? sb.utils.datetime(bounds.dateTo, bounds.timeTo) : null
      return datetimeB && sb.utils.datetimeCompare(nowDatetime, datetimeB) > 0
    }

    if (expired || _this.disabled) legend = 'unavailable'
    else if (mine && cancellableBounds) legend = 'your'
    else if (!bookableBounds) legend = 'unavailable'
    else if (booked) legend = 'member'
    else if (bookableBounds) legend = 'available'

    var bookable = legend == 'available',
        cancellable = legend == 'your'

    var name = _this.name == 'Slot' ? '' : _this.name
    if (mine) name = 'Your booking'
    else if (booked) name = 'Booked'
    else if (!name && _this.effectiveBookBounds && !bookableBounds && !afterBookableBounds) name = 'Too far ahead to book'

    return {
      bookable: bookable,
      cancellable: cancellable,
      legend: legend,
      expired: expired,
      booked: booked,
      mine: mine,
      disabled: _this.disabled,
      bookableBounds: bookableBounds,
      cancellableBounds: cancellableBounds,
      name: name
    }
  }

  this.refreshBookings = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('bookings', force, callback)
  }

  function wrapBookings() {
    var bookings = source.bookings.map(function(booking) { return new MyBooking(booking, _this) })
    _this.bookings = bookings
    _this.onChangeCallback.trigger('bookings-wrap', _this)
  }

  this.refreshActiveBooking = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('activeBookings', force, callback)
  }

  function wrapActiveBooking() {
    _this.activeBooking = source.activeBookings.length ? new MyBooking(source.activeBookings[0], _this) : null
    _this.onChangeCallback.trigger('active-booking-wrap', _this)
  }

  this.onActiveBooking = function() {
    applyChangesFromSource()
  }

  this.refreshEffectiveBookBounds = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('effectiveBookBounds', force, callback)
  }

  this.refreshEffectiveCancelBounds = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('effectiveCancelBounds', force, callback)
  }

}

function MyBooking(/*Booking*/ source, /*MySlot*/ slot) {

  var _this = this

  this.source = source
  this.slot = slot

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource(/*str*/ key) {
    _this.id = source.id
    _this.slotId = source.slotId
    _this.reference = source.reference
    _this.referenceCancel = source.referenceCancel

    _this.custom = customProperties()
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  function customProperties() {
    var mine = false
    
    if (_this.signedInUser) mine = _this.signedInUser.id == source.profileId

    return {
      mine: mine
    }
  }

  this.refreshReference = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('reference', force, function() {
      _this.slotPaid = source.reference.quote.priceFor(_this.slotId)
      if (callback) callback()
    })
  }

  this.refreshReferenceCancel = function(/*bool*/ force, /*fn*/ callback) {
    source.refresh('referenceCancel', force, function() {
      _this.slotRefund = source.referenceCancel ? source.referenceCancel.refund.priceFor(_this.slotId) : null
      if (callback) callback()
    })
  }

  this.onSingedInUser = function(/*User*/ user) {
    _this.signedInUser = user
    applyChangesFromSource()
  }

}

function MyUser(/*User*/ source) {

  var _this = this

  this.sc = source.sc
  this.source = source

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource(/*str*/ key) {
    _this.id = source.id
    _this.username = source.username
    _this.fullName = source.fullName
    _this.attributes = source.attributes

    _this.members = _this.members || {}
    _this.balances = _this.balances || {}
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  this.memberFor = function(/*str*/ placeId, /*bool*/ force, /*fn*/ callback) {
    if (force) delete _this.members[placeId]
    if (!_this.members[placeId])
      _this.sc.apiMembersService.getPlaceMember(placeId, _this.id, function(/*Member*/ member) {
        _this.members[placeId] = member
        if (callback) callback()
      })
    else if (callback) callback()
  }

  this.balanceFor = function(/*str*/ placeId, /*bool*/ force, /*fn*/ callback) {
    if (force) delete _this.balances[placeId]
    if (!_this.balances[placeId])
      _this.sc.apiPaymentsService.getUserBalance(placeId, _this.id, function(/*Balance*/ balance) {
        _this.balances[placeId] = balance
        if (callback) callback()
      })
    else if (callback) callback()
  }

}
