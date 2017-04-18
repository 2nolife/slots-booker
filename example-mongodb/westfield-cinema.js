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
  _slots = db.collection('ms-slots')

  su.setDb(db)

  console.log('Westfield Cinema module set up')
}

function finderById(/*str*/ id) {
  return { _id: ObjectID.createFromHexString(id) }
}

function sortById() {
  return [['_id', 'asc']]
}

var _hallNames = ['Hall A', 'Hall B', 'Hall C', 'Hall D'],
    _hallSeatIds = {}, // { 'Hall A': [list of IDs], 'Hall B': [list of IDs] }
    _seatTypes = {}, // { seatId: seatType, seatId: seatType , seatId: seatType }
    _movies = [
      { key: 'ro', title: 'Rogue One',         time: 60+45, cover: 'cover_rogue_one.jpeg',       price_add: 150 },
      { key: 'ca', title: 'Captain America',   time: 60+35, cover: 'cover_captain_america.jpeg', price_add: 150 },
      { key: 'jb', title: 'Jason Bourne',      time: 60+15, cover: 'cover_jason_bourne.jpeg',    price_add: 0   },
      { key: 'ds', title: 'Doctor Strange',    time: 60+30, cover: 'cover_doctor_strange.jpeg',  price_add: 0   },
      { key: 'ss', title: 'Suicide Squad',     time: 60+45, cover: 'cover_suicide_squad.jpeg',   price_add: 0   },
      { key: 'st', title: 'Star Trek',         time: 60+35, cover: 'cover_star_trek.jpeg',       price_add: 0   },
      { key: 'dp', title: 'Deadpool',          time: 60+20, cover: 'cover_deadpool.jpeg',        price_add: 50  },
      { key: 'bs', title: 'Batman v Superman', time: 60+30, cover: 'cover_batman_superman.jpeg', price_add: 150 },
      { key: 'wc', title: 'Warcraft',          time: 60+35, cover: 'cover_warcraft.jpeg',        price_add: 50  }
    ],
    _defaultPrices =
      { disabled_adult: 800, disabled_child: 400, single_adult: 1200, single_child: 600, vip: 1600, double: 2500 }

function hallSeats(/*str*/ hallName) {
  if (hallName == _hallNames[0] || hallName == _hallNames[1])
    return '' +
      '..ii.ooooo.ii..' + '|' + // i - disabled
      '.ooooooooooooo.' + '|' +
      'ooooooooooooooo' + '|' +
      'ooooooooooooooo' + '|' +
      'oo...........oo' + '|' +
      'oo..vvvvvvv..oo' + '|' + // v - VIP
      'ooooooooooooooo' + '|' +
      'ooooooooooooooo' + '|' +
      'ooooooooooooooo' + '|' +
      'ooooooooooooooo'

  if (hallName == _hallNames[2] || hallName == _hallNames[3])
    return '' +
      '.oooooooooo.' + '|' +
      'oooooooooooo' + '|' +
      'oooooooooooo' + '|' +
      'oooooooooooo' + '|' +
      'oooooooooooo' + '|' +
      '..w.w..w.w..' // w - sofa seat
}

function hallShowTimes(/*str*/ hallName, /*boolean*/ weekend) {
  var timesWD = [ // working day
    [1000, 1200, 1400, 1800, 2000],
    [0900, 1100, 1300, 1530, 2030],
    [1000, 1200, 1830, 2030],
    [1000, 1200, 1800, 2000]
  ]
  var timesWE = [ // weekend
    [1000, 1200, 1400, 1600, 1800, 2000, 2200],
    [0900, 1100, 1300, 1530, 2030],
    [1000, 1200, 1400, 1600, 1830, 2030],
    [1000, 1200, 1400, 1600, 1800, 2000, 2200]
  ]
  var times = weekend ? timesWE : timesWD
      i = _hallNames.indexOf(hallName)
  return times[i]
}

function randomMovie() {
  var n = Math.floor(Math.random()*_movies.length)
  return _movies[n]
}

function addHallSeats(/*str*/ hallName) {
  var layout = hallSeats(hallName),
      seats = [],
      rowN = 1,
      seatN = 1,
      position = 1

  function makeSeat(/*char*/ c) {
    var type
    switch (c) {
      case 'i': type = 'disabled'; break
      case 'o': type = 'single';   break
      case 'v': type = 'vip';      break
      case 'w': type = 'double';   break
      case '.': position++;                      break
      case '|': rowN++; seatN = 1; position = 1; break
    }

    if (type) {
      seats.push({ type: type, row_n: rowN, seat_n: seatN, position: position, name: 'Seat '+rowN+'.'+seatN })
      seatN++
      position++
    }
  }

  for (var n = 0; n < layout.length; n++)
    makeSeat(layout[n])

  console.log('Westfield Cinema '+hallName+' has '+(seats.length)+' seats')

  var deferred = Q.defer()

  qu.find_forEach(_spaces, { place_id: _placeId, name: hallName },
    function onItem(hall) {
      var hallId = ''+hall._id.valueOf(),
          seatIds = [],
          n = seats.length

      qu.insert_n(_spaces, n,
        function newItem(i) {
          var seat = seats[i]
          return {
            place_id: _placeId,
            parent_space_id: hallId,
            name: seat.name,
            attributes: { prm1: { type: seat.type, seat_n: seat.row_n+'.'+seat.seat_n, position: seat.position }}
          }
        },
        function onItem(item) {
          var seatId = ''+item._id.valueOf()
          seatIds.push(seatId)
          _seatTypes[seatId] = item.attributes.prm1.type
        },
        function after(items) {
          _hallSeatIds[hallName] = seatIds

          console.log('Westfield Cinema '+hallName+' seats added')
          deferred.resolve()

//          qu.findAndModify_byId(_spaces, hallId, { $set: { spaces: seatIds }}, function callback() {
//            console.log('Westfield Cinema '+hallName+' seats added')
//            deferred.resolve()
//          }, true)

        })
    },
    function after() {})

  return deferred.promise
}

function addHallSlots(/*str*/ hallName) {
  var days = 7,
      today = du.todayDate(),
      timesWD = hallShowTimes(hallName, false),
      timesWE = hallShowTimes(hallName, true),
      arr = []

  console.log('Westfield Cinema '+hallName+' has '+(timesWD.length)+'/'+(timesWE.length)+' shows per day, '+days+' days from now')

  for (var day = 0; day < days; day++) {
    var date = parseInt(du.addDaysDate(today, day)),
        weekday = du.weekdayAsWord(date)
        times = weekday == 'Saturday' || weekday == 'Sunday' ? timesWE : timesWD

    for (var t = 0; t < times.length; t++) {
      var movie = randomMovie()
          timeFrom = times[t],
          timeTo = timeFrom+100-60+movie.time // 1000 -> 1145

      _hallSeatIds[hallName].map(function(seatId) {
        arr.push(su.addSlot(_placeId, seatId, 'Slot', date, date, timeFrom, timeTo, { prm1: { movie_key: movie.key } }))
      })
    }
  }

  var promises = [].concat.apply([], arr)
  return Q.all(promises)

}

function addSeatSlotPrices(/*json*/ movie, /*num*/ price_add, /*[str]*/ seatIds) {
  var deferred = Q.defer(),
      arr = []

  qu.find_forEach(_slots, { space_id: { $in: seatIds }, 'attributes.prm1.movie_key': movie.key },
    function onItem(slot) {
      var slotId = ''+slot._id.valueOf(),
          seatId = slot.space_id,
          seatType = _seatTypes[seatId],
          price1 = _defaultPrices[seatType],
          price2 = _defaultPrices[seatType+'_adult'],
          price3 = _defaultPrices[seatType+'_child']

      if (price1) arr.push(su.addPrice(_placeId, seatId, slotId, seatType+': '+movie.title,       price1+price_add+movie.price_add, 'GBP', { prm1: { age: 'none' } }))
      if (price2) arr.push(su.addPrice(_placeId, seatId, slotId, seatType+' Adult: '+movie.title, price2+price_add+movie.price_add, 'GBP', { prm1: { age: 'adult' } }))
      if (price3) arr.push(su.addPrice(_placeId, seatId, slotId, seatType+' Child: '+movie.title, price3+price_add+movie.price_add, 'GBP', { prm1: { age: 'child' } }))
    },
    function after(slots) {
      var promises = [].concat.apply([], arr)
      Q.all(promises).then(deferred.resolve)
    }
  )

  return deferred.promise
}

function addHallSeatPrices(/*str*/ hallName) {
  var price_add = hallName == _hallNames[0] || hallName == _hallNames[1] ? 0 : -100, // smaller screen is cheaper
      seatIds = _hallSeatIds[hallName]

  console.log('Westfield Cinema '+hallName+' pricing movies and seats')

  var arr =
    _movies.map(function(movie) {
      return addSeatSlotPrices(movie, price_add, seatIds)
    })

  var promises = [].concat.apply([], arr)
  return Q.all(promises)
}

function addSlots() {
  var arr =
    _hallNames.map(function(hallName) {
      return addHallSlots(hallName)
    })

  var promises = [].concat.apply([], arr)
  return Q.all(promises)
}

function addSeats() {
  var arr =
    _hallNames.map(function(hallName) {
      return addHallSeats(hallName)
    })

  var promises = [].concat.apply([], arr)
  return Q.all(promises)
}

function addPrices() {
  var arr =
    _hallNames.map(function(hallName) {
      return addHallSeatPrices(hallName)
    })

  var promises = [].concat.apply([], arr)
  return Q.all(promises)
}

function bookRandom() {
  var deferred = Q.defer()

  su.bookRandomSlots(_placeId, _profileIds['customer'], 30)
    .then(function() {
      console.log('Random slots booked in Westfield Cinema')
      deferred.resolve()
    })

  return deferred.promise
}

function addHalls() {
  var deferred = Q.defer(),
      n = _hallNames.length,
      spaceIds = []

  console.log('Westfield Cinema has '+n+' halls')

  qu.insert_n(_spaces, n,
    function newItem(i) {
      return { place_id: _placeId, name: _hallNames[i] }
    },
    function onItem(space) {
      spaceIds.push(''+space._id.valueOf())
    },
    function after(spaces) {
      console.log('Westfield Cinema halls added')
      deferred.resolve()

//      qu.findAndModify_byId(_places, _placeId, { $set: { spaces: spaceIds }}, function callback() {
//        console.log('Westfield Cinema halls added')
//        deferred.resolve()
//      }, true)

    }
  )

  return deferred.promise
}

function addPlace() {
  var deferred = Q.defer(),
      newPlace = {
        profile_id: _profileIds['support'],
        name: 'Westfield Cinema',
        attributes: { client_key: 'example', external_key: 'westfield-cinema' }
      }

  qu.insert_one(_places, newPlace, function onItem(item) {
    _placeId = ''+item._id.valueOf()
    _placeIds['westfield-cinema'] = _placeId
    console.log('Westfield Cinema place created')
    deferred.resolve()
  })

  return deferred.promise
}

function setupPlace() {
  console.log('Setting up place: Westfield Cinema')
  var deferred = Q.defer()

  addPlace()
    .then(addHalls)
    .then(addSeats)
    .then(addSlots)
    .then(addPrices)
    .then(bookRandom)
    .then(function() { users.updateBalance('tester', _placeId, 25000, 'GBP') })
    .then(function() {
      console.log('Westfield Cinema place set up')
      deferred.resolve()
    })

  return deferred.promise
}
