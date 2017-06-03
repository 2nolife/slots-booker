function CinemaPlace(/*Place*/ source) {

  var _this = this

  this.source = source

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource() {
    _this.id = source.id
    _this.name = source.name
    _this.halls = _this.halls || []
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  source.refresh('spaces', false, wrapHalls)

  function wrapHalls() {
    var halls = (source.spaces || []).map(function(space) { return new CinemaHall(space, _this) })
    halls.sort(function(a, b) { return a.name > b.name })
    _this.halls = halls
    _this.onChangeCallback.trigger('halls-created', _this)
  }

}

function CinemaHall(/*Space*/ source, /*CinemaPlace*/ cinemaPlace) {

  var _this = this

  this.source = source
  this.cinemaPlace = cinemaPlace

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource() {
    _this.id = source.id
    _this.name = source.name
    _this.seats = _this.seats || []
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  this.refreshWeekSchedule = function(/*fn*/ callback) {
    if (!_this.weekSchedule)
      source.refresh('firstSpace', false, function(/*str*/ status) {
        if (status == 'success' || status == 'noop') {

          var firstSeat = new CinemaSeat(source.firstSpace, cinemaPlace, _this)
          firstSeat.refreshSlotsWeekInAdvance(function() {
            _this.weekSchedule = {
              slots: firstSeat.slots,
              daily: firstSeat.daily
            }
            _this.onChangeCallback.trigger('weekSchedule', _this)
            if (callback) callback()
          })

        }
      })
    else if (callback) callback()
  }

  this.refreshSeats = function(/*fn*/ callback) {
    source.refresh('spaces', false, wrapSeats.bind(null, callback))
  }

  function wrapSeats(/*fn*/ callback, /*str*/ status) {
    if ((status == 'success' || status == 'noop') && !_this.seats.length) {
      var seats = (source.spaces || []).map(function(space) { return new CinemaSeat(space, cinemaPlace, _this) })
      seats.sort(function(a, b) {
        return a.ordered < b.ordered ? -1 : a.ordered > b.ordered ? 1 : 0
      })
      _this.seats = seats
      _this.onChangeCallback.trigger('seats-created', _this)
    }  
    if (callback) callback()
  }

  function wrapSlots() {
    _this.slots = (source.slots || []).map(function(slot) {
      var cinemaSeat = $.grep(_this.seats, function(seat) { return seat.id == slot.spaceId })[0]
      var cinemaSlot = new CinemaSlot(slot, cinemaPlace, _this, cinemaSeat)
      cinemaSeat.slot = cinemaSlot
      cinemaSeat.status = cinemaSlot.status
      return cinemaSlot
    })
  }

  function slotsByDateTime(/*str|num*/ date, /*str|num*/ time, /*fn*/ callback) {
    var datetime = parseInt(date)*10000+parseInt(time)
    source.slotsFilter = { from: datetime, to: datetime, inner: true }
    source.refreshRetry('slots', false, function(/*str*/ status) {
      if ((status == 'success' || status == 'noop')) {
        wrapSlots()
        if (callback) callback()
      }
    })
  }

  this.refreshSlotsByDateTime = function(/*str|num*/ date, /*str|num*/ time, /*fn*/ callback) {
    slotsByDateTime(date, time, callback)
  }

  function wrapBookedSlots() {
    _this.bookedSlots = (source.slots || []).map(function(slot) {
      var cinemaSeat = $.grep(_this.seats, function(seat) { return seat.id == slot.spaceId })[0]
      var cinemaSlot = new CinemaSlot(slot, cinemaPlace, _this, cinemaSeat)
      return cinemaSlot
    })
  }

  function bookedSlotsFromToday(/*bool*/ force, /*fn*/ callback) {
    var today = parseInt(sb.utils.todayDate()),
        plus6 = parseInt(sb.utils.addDaysDate(today, 30)),
        yesterday = parseInt(sb.utils.addDaysDate(today, -1))
    source.slotsFilter = { from: yesterday, to: plus6, inner: true, booked: '', paid: true }
    source.refreshRetry('slots', force, function(/*str*/ status) {
      if ((status == 'success' || status == 'noop')) {
        wrapBookedSlots()
        if (callback) callback()
      }
    })
  }

  this.refreshBookedSlots = function(/*bool*/ force, /*fn*/ callback) {
    bookedSlotsFromToday(force, callback)
  }

}

function CinemaSeat(/*Space*/ source, /*CinemaPlace*/ cinemaPlace, /*CinemaHall*/ cinemaHall) {

  var _this = this

  this.source = source
  this.cinemaPlace = cinemaPlace
  this.cinemaHall = cinemaHall

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource() {
    _this.id = source.id
    _this.name = source.name
    _this.slots = source.slots
    _this.seatN = source.attributes.prm1.seat_n
    _this.type = source.attributes.prm1.type

    _this.rowNum = parseInt(_this.seatN.split('.')[0])
    _this.colNum = source.attributes.prm1.position
    _this.ordered = _this.rowNum*100+parseInt(_this.colNum)
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  function slotsDaysInAdvance(/*num*/ daysInAdvance, /*fn*/ callback) {
    var today = parseInt(sb.utils.todayDate()),
        plus6 = parseInt(sb.utils.addDaysDate(today, daysInAdvance))
    source.slotsFilter = { from: today, to: plus6 }
    source.refreshRetry('slots', false, function(/*str*/ status) {
      if ((status == 'success' || status == 'noop')) {
        wrapSlots()
        slotsByDay(daysInAdvance)
        if (callback) callback()
      }
    })
  }

  function slotsByDay(/*num*/ daysInAdvance) {
    var today = parseInt(sb.utils.todayDate()),
        array = []
    for (var n = 0; n < daysInAdvance; n++) {
      var date = parseInt(sb.utils.addDaysDate(today, n)),
          slots = $.grep(_this.slots, function(slot) { return slot.dateFrom == date }),
          day = sb.utils.weekdayAsWord(date),
          short = sb.utils.strToDate(date).getDate()+' '+sb.utils.monthAsWord(date)

      array.push({ shortDate: short, day: day, slots: slots })
    }

    _this.daily = array
  }

  function wrapSlots() {
    _this.slots = (source.slots || []).map(function(slot) { return new CinemaSlot(slot, cinemaPlace, cinemaHall, _this) })
  }

  this.refreshSlotsWeekInAdvance = function(/*fn*/ callback) {
    slotsDaysInAdvance(7, callback)
  }

}

function CinemaSlot(/*Slot*/ source, /*CinemaPlace*/ cinemaPlace, /*CinemaHall*/ cinemaHall, /*CinemaSeat*/ cinemaSeat) {

  var _this = this,
      sc = source.sc

  this.source = source
  this.cinemaPlace = cinemaPlace
  this.cinemaHall = cinemaHall
  this.cinemaSeat = cinemaSeat

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource() {
    _this.id = source.id
    _this.movieKey = source.attributes.prm1.movie_key
    _this.dateFrom = source.dateFrom
    _this.timeFrom = source.timeFrom
    _this.day = sb.utils.weekdayAsWord(_this.dateFrom)
    _this.shortDate = shortDate()
    _this.status = source.bookStatus == 0 ? 'available' : 'booked'
    _this.prices = _this.prices || []
  }

  _this.selectedPriceId = null

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  function shortDate() {
    return sb.utils.strToDate(_this.dateFrom).getDate()+' '+sb.utils.monthAsWord(_this.dateFrom)
  }

  this.refreshPrices = function(/*fn*/ callback) {
    source.refresh('prices', false, wrapPrices.bind(null, callback))
  }

  function wrapPrices(/*fn*/ callback, /*str*/ status) {
    if ((status == 'success' || status == 'noop') && !_this.prices.length) {
      var prices = (source.prices || []).map(function(price) { return new CinemaPrice(price, _this) })
      prices.sort(function(a, b) { return a.amount < b.amount })
      _this.prices = prices
      _this.selectedPriceId = prices.length ? prices[0].id : null
      _this.onChangeCallback.trigger('prices-created', _this)
    }
    if (callback) callback()
  }

  this.refreshSeat = function(/*fn*/ callback) {
    if (!_this.cinemaSeat)
      sc.apiSpacesService.getSpace(source.placeId, source.spaceId, function(/*Space*/ space) {
        _this.cinemaSeat = new CinemaSeat(space, _this.cinemaPlace, _this.cinemaHall)
        if (callback) callback()
      })
    else if (callback) callback()
  }

  this.refreshActiveBooking = function(/*fn*/ callback) {
    source.refresh('activeBookings', false, function(/*str*/ status) {
      if ((status == 'success' || status == 'noop') && !_this.activeBooking) {
        _this.activeBooking = source.activeBookings.length ? new CinemaBooking(source.activeBookings[0], _this) : null
      }
      if (callback) callback()
    })
  }

}

function CinemaPrice(/*Price*/ source, /*CinemaSlot*/ cinemaSlot) {

  var _this = this

  this.source = source
  this.cinemaSlot = cinemaSlot

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource() {
    _this.id = source.id
    _this.placeId = source.placeId
    _this.name = source.name
    _this.amount = source.amount
    _this.currency = source.currency
    _this.sign = source.currency == 'GBP' ? '£' : source.currency
    _this.movieKey = source.attributes.prm1.movie_key

    _this.age = function() {
      switch (source.attributes.prm1.age) {
        case 'adult': return 'Adult'
        case 'child': return 'Child'
        default: return 'Adult or Child'
      }
    }()
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

}

function CinemaBooking(/*Booking*/ source, /*CinemaSlot*/ cinemaSlot) {

  var _this = this

  this.source = source
  this.cinemaSlot = cinemaSlot

  this.onChangeCallback = source.onChangeCallback

  function applyChangesFromSource() {
    _this.id = source.id
  }

  applyChangesFromSource()

  source.onChangeCallback.add(applyChangesFromSource)

  this.refreshPrice = function(/*fn*/ callback) {
    source.refresh('price', false, function(/*str*/ status) {
      if ((status == 'success' || status == 'noop') && !_this.price) {
        _this.price = source.price ? new CinemaPrice(source.price, cinemaSlot) : null
      }
      if (callback) callback()
    })
  }

  this.refreshReference = function(/*fn*/ callback) {
    source.refresh('reference', false, function(/*str*/ status) {
      if (status == 'success' || status == 'noop') {
        _this.reference = source.reference
      }
      if (callback) callback()
    })
  }

}
