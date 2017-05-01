var assert = require('assert'),
    Q = require('q'),
    ObjectID = require('mongodb').ObjectID

var du = require('./date-utils'),
    su = require('./slot-utils')

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

  console.log('Genius Bar module set up')
}

function finderById(/*str*/ id) {
  return { _id: ObjectID.createFromHexString(id) }
}

function sortById() {
  return [['_id', 'asc']]
}

function addSlots() {
  var deferred = Q.defer()
  var times = [0900, 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700],
      days = 14
  console.log('Has '+(times.length)+' slots per day of 60 minutes each, '+days+' days from now')

  _spaces.find({ place_id: _placeId }).toArray(function(err, items) {
    assert.equal(null, err)

    var arr = items.map(function(space) {
      var spaceId = ''+space._id.valueOf(),
          xs = []
      for (var day = 0; day < days; day++)
        for (var n = 1; n <= times.length; n++) {
          var today = du.todayDate(),
              date = parseInt(du.addDaysDate(today, day)),
              timeFrom = times[n-1],
              timeTo = timeFrom+100
          xs.push(su.addSlot(_placeId, spaceId, 'Slot '+n, date, date, timeFrom, timeTo))
        }
      return xs
    })
    var promises = [].concat.apply([], arr)

    Q.all(promises).then(deferred.resolve)
  })

  return deferred.promise
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

function addSpaces() {
  var deferred = Q.defer()
  var names = ['Regent Street', 'White City', 'Leicester Square', 'Canary Wharf', 'Covent Garden'],
      n = names.length,
      spaceIds = []
  console.log('Has '+n+' branches')

  names.map(function(name) {
    _spaces.insert(
      {
        place_id: _placeId,
        name: name
      },
      function(err, item) {
        assert.equal(null, err)
        spaceIds.push(''+item.ops[0]._id.valueOf())

        if (--n == 0) {
          console.log('Branches added')
          deferred.resolve()
        }
      })
  })

  return deferred.promise
}

function addPlace() {
  var deferred = Q.defer()

  _places.insert(
    {
      profile_id: _profileIds['support'],
      name: 'Genius Bar Support',
      attributes: { client_key: 'example', external_key: 'genius-bar' }
    },
    function(err, item) {
      assert.equal(null, err)
      _placeId = ''+item.ops[0]._id.valueOf()
      _placeIds['genius-bar'] = _placeId
      console.log('Place created')
      deferred.resolve()
    })

  return deferred.promise
}

function setupPlace() {
  console.log('Setting up place: Genius Bar')
  var deferred = Q.defer()

  addPlace()
    .then(addSpaces)
    .then(addSlots)
    .then(bookRandom)
    .then(function() {
      console.log('Genius Bar place set up')
      deferred.resolve()
    })

  return deferred.promise
}
