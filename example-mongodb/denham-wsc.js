var assert = require('assert'),
    Q = require('q'),
    ObjectID = require('mongodb').ObjectID

var du = require('./date-utils'),
    su = require('./slot-utils'),
    qu = require('./query-utils'),
    users = require('./users')

module.exports = {
  setVars: setVars,
  setupPlace: setupPlace
}

var _profileIds, _placeIds
var _places, _spaces, _slots, _placeId

function setVars(/*obj*/ db, /*{}*/ profileIds, /*{}*/ placeIds) {
  _profileIds = profileIds
  _placeIds = placeIds

  _places = db.collection('ms-places')
  _spaces = db.collection('ms-places-spaces')
  _prices = db.collection('ms-places-prices')
  _slots = db.collection('ms-slots')

  su.setDb(db)

  console.log('Denham WSC module set up')
}

var _seasonName = 'Season '+du.todayDate().substring(0,4),
    _seasonId = null,
    _lakeNames = ['Denham Lake'],
    _lakeIds = {}, // { 'Lake A': ID, 'Lake B': ID }
    _lakeSlotIds = {}, // { 'Lake A': [list of IDs], 'Lake B': [list of IDs] }
    _slotTimes = [],
    _slotDates = []

var _seasonPrices = [
  { name: 'Slalom', amount: 1800, member_level: 1 },
  { name: 'Slalom', amount: 2400, member_level: 0 },
  { name: 'Wakeboard', amount: 2000, member_level: 1 },
  { name: 'Wakeboard', amount: 2600, member_level: 0 },
  { name: 'Ringo 1 person', amount: 2500, member_level: 0 },
  { name: 'Ringo 2 people', amount: 3500, member_level: 0 }
]

function addLakeSlots(/*str*/ lakeName) {
  var times = [],
      days = 30,
      openTime = 900,
      closeTime = 2100,
      curTime = openTime,
      slotMinutes = 12,
      today = du.todayDate(),
      arr = []

  while (curTime <= closeTime) {
    times.push(curTime)
    curTime = parseInt(du.addMinutesTime(curTime, slotMinutes))
  }
  _slotTimes = times.slice()
  _slotTimes.pop()

  console.log(lakeName+' has '+(times.length-1)+' slots per day of '+slotMinutes+' minutes each, '+days+' days from now')

  for (var day = 0; day < days; day++) {
    var date = parseInt(du.addDaysDate(today, day))
    _slotDates.push(date)

    for (var t = 0; t < times.length-1; t++) {
      arr.push(su.addSlot.bind(null, _placeId, _lakeIds[lakeName], 'Slot', date, date, times[t], times[t+1], {}))
    }
  }

  return qu.sequentialPromise(arr)
}

function addSlots() {
  var arr =
    _lakeNames.map(function(lakeName) {
      return addLakeSlots.bind(null, lakeName)
    })
  return qu.sequentialPromise(arr)
}

function bookRandom() {
  var deferred = Q.defer()
  su.bookRandomSlots(_placeId, _profileIds['customer'], 30)
    .then(function() {
      console.log('Random slots booked')
      deferred.resolve()
    })
  return deferred.promise
}

function addLakes() {
  var deferred = Q.defer()
  var n = _lakeNames.length
  console.log('Has '+n+' lakes')

  qu.insert_n(_spaces, n,
    function newItem(i) {
      return {
        place_id: _placeId,
        parent_space_id: _seasonId,
        name: _lakeNames[i],
        attributes: { prm0: { template: 'lake' }}
      }
    },
    function onItem(lakeItem) {
      _lakeIds[lakeItem.name] = ''+lakeItem._id.valueOf()
    },
    function after(lakeItems) {
      console.log('Lakes added')
      deferred.resolve()
    }
  )

  return deferred.promise
}

function addPrices() {
  var deferred = Q.defer()
  var n = _seasonPrices.length

  qu.insert_n(_prices, n,
    function newItem(i) {
      return {
        place_id: _placeId,
        space_id: _seasonId,
        name: _seasonPrices[i].name,
        amount: _seasonPrices[i].amount,
        currency: 'GBP',
        member_level: _seasonPrices[i].member_level,
      }
    },
    function onItem(priceItem) {},
    function after(priceItems) {
      console.log('Prices added')
      deferred.resolve()
    }
  )

  return deferred.promise
}

function updateBounds() {
  var deferred = Q.defer()

  qu.findAndModify_byId(_spaces, _seasonId,
    { $set: {
      book_bounds: { open: { before: 1440*14, time: 800 }, close: { before: 1440, time: 2400 } },
      cancel_bounds: { close: { before: 1440*2, time: 2400 } }
    }},
    function() {
      console.log('Bounds updated')
      deferred.resolve()
    },
    true)

  return deferred.promise
}

function updateSlotsByDatesAndCancel(/*[num]*/ dates, /*[num]*/ times, update, /*str*/ msg) {
  var deferred = Q.defer()
  var n = dates.length * times.length

  for (var d = 0; d < dates.length; d++) {
    for (var t = 0; t < times.length; t++) {
      qu.findAndModify_single(_slots, { date_from: dates[d], time_from: times[t] },
        { $set: update },
        function(result) {
          su.cancelSlot(''+result.value._id)
          if (--n == 0) {
            if (msg) console.log(msg)
            deferred.resolve()
          }
        },
        true)
    }
  }

  return deferred.promise
}

function updateSwimmers() {
  var datesA = _slotDates.filter(function(date) { return du.weekdayAsWord(date) == 'Monday' || du.weekdayAsWord(date) == 'Thursday' }),
      timesA = _slotTimes.filter(function(time) { return time >= 1900 }),
      datesB = _slotDates.filter(function(date) { return du.weekdayAsWord(date) == 'Saturday' }),
      timesB = _slotTimes.filter(function(time) { return time <= 924 })

    var differA = updateSlotsByDatesAndCancel(datesA, timesA, { name: 'Swim', disabled: 1 }),
        differB = updateSlotsByDatesAndCancel(datesB, timesB, { name: 'Swim', disabled: 1 })

    var deferred = Q.defer()
    Q.all([differA, differB])
      .then(function() {
        console.log('Swimmers time updated')
        deferred.resolve()
      })
    return deferred.promise
}

function updateClubClosed() { 
  var dates = _slotDates.filter(function(date) { return du.weekdayAsWord(date) != 'Saturday' && du.weekdayAsWord(date) != 'Sunday' }),
      times = _slotTimes.filter(function(time) { return time < 1000 })

  return updateSlotsByDatesAndCancel(dates, times, { name: 'Closed', disabled: 1 }, 'Closed time updated')
}

function updateReservations() {
  var n = 0,
      dates = _slotDates.filter(function(date) {
        if (du.weekdayAsWord(date) == 'Monday') n++
        return du.weekdayAsWord(date) == 'Tuesday' && n % 2 != 0 || du.weekdayAsWord(date) == 'Wednesday' && n % 2 == 0
      }),
      times = _slotTimes.filter(function(time) { return time >= 1600 })

  return updateSlotsByDatesAndCancel(dates, times, { name: 'Guides', disabled: 1 }, 'Reservations updated')
}

function addSeason() {
  var deferred = Q.defer()

  qu.insert_one(_spaces,
    { place_id: _placeId,
      name: _seasonName,
      attributes: { prm0: { template: 'season' }}
    },
    function onItem(seasonItem) {
      _seasonId = ''+seasonItem._id.valueOf()
      console.log('Season added')
      deferred.resolve()
    })

  return deferred.promise
}

function addPlace() {
  var deferred = Q.defer(),
      newPlace = {
        profile_id: _profileIds['support'],
        name: 'Denham WSC',
        attributes: { client_key: 'example', external_key: 'denham-wsc', prm0: { template: 'waterski' }}
      }

  qu.insert_one(_places, newPlace, function onItem(item) {
    _placeId = ''+item._id.valueOf()
    _placeIds['denham-wsc'] = _placeId
    console.log('Place created')
    deferred.resolve()
  })

  return deferred.promise
}

function setupPlace() {
  console.log('Setting up place: Denham WSC')
  var deferred = Q.defer()

  addPlace()
    .then(addSeason)
    .then(addLakes)
    .then(addPrices)
    .then(addSlots)
    .then(updateBounds)
    .then(bookRandom)
    .then(updateSwimmers)
    .then(updateClubClosed)
    .then(updateReservations)
    .then(function() { users.updateBalance('tester', _placeId, 36000, 'GBP') })
    .then(function() { users.updatePaymentsAccount(_placeId, 'GBP') })
    .then(function() {
      console.log('Denham WSC place set up')
      deferred.resolve()
    })

  return deferred.promise
}
