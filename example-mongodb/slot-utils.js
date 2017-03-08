var assert = require('assert'),
    Q = require('q'),
    ObjectID = require('mongodb').ObjectID

module.exports = {
  setDb: setDb,
  addSlot: addSlot,
  bookRandomSlots: bookRandomSlots
}

var _slots, _booked, _bookings

function setDb(/*obj*/ db) {
  _slots = db.collection('ms-slots')
  _booked = db.collection('ms-slots-booked')
  _bookings = db.collection('ms-slots-bookings')
}

function finderById(/*str*/ id) {
  return { _id: ObjectID.createFromHexString(id) }
}

function sortById() {
  return [['_id', 'asc']]
}

function addSlot(/*str*/ placeId, /*str*/ spaceId, /*str*/ name, /*num*/ dateFrom, /*num*/ dateTo, /*num*/ timeFrom, /*num*/ timeTo) {
  var deferred = Q.defer()

  _slots.insert(
    {
      place_id: placeId,
      space_id: spaceId,
      name: name,
      date_from: dateFrom,
      date_to: dateTo,
      time_from: timeFrom,
      time_to: timeTo,
      book_status: 0
    },
    function(err, item) {
      assert.equal(null, err)
      deferred.resolve()
    })

  return deferred.promise
}

function bookRandomSlots(/*str*/ placeId, /*str*/ profileId, /*num*/ chance) {
  var deferred = Q.defer()

  _slots.find({ place_id: placeId }).toArray(function(err, items) {
    assert.equal(null, err)

    var arr = items.map(function(slot) {
      var slotId = ''+slot._id.valueOf(),
          xs = []
      if ((Math.floor(Math.random()*100)+1) <= chance) // 1 to 100
        xs.push(bookSlot(slotId))
      return xs
    })
    var promises = [].concat.apply([], arr)

    Q.all(promises).then(function() { deferred.resolve() })
  })

  return deferred.promise
}

function bookSlot(/*str*/ slotId, /*str*/ profileId) { //todo proper booking as in MongoVerify
  var deferred = Q.defer()

  _slots.findAndModify(
    finderById(slotId),
    sortById(),
    { $set: { book_status: 1 }},
    function(err, result) {
      assert.equal(null, err)
      assert(result.value != null, 'Slot not found')
      deferred.resolve()
    })

  return deferred.promise
}
