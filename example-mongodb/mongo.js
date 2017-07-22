var MongoClient = require('mongodb').MongoClient,
    assert = require('assert'),
    Q = require('q')

var config = require('./config.json'),
    moduleGeniusBar = require('./genius-bar'),
    moduleWestfieldCinema = require('./westfield-cinema'),
    moduleDenhamWsc = require('./denham-wsc'),
    moduleUsers = require('./users'),
    qu = require('./query-utils')

var _db, _profileIds = {}, _placeIds = {}

function drop() {
  var deferred = Q.defer()
  _db.dropDatabase(function() {
    console.log('Database dropped')
    deferred.resolve()
  })
  return deferred.promise
}

function setupPlaces() {
  var arr = [
    moduleDenhamWsc.setupPlace,
    moduleGeniusBar.setupPlace,
    moduleWestfieldCinema.setupPlace
  ]

  var deferred = Q.defer(),
      promise = qu.sequentialPromise(arr)
  promise.then(function () {
    console.log('Places set up')
    deferred.resolve()
  })
  return deferred.promise
}

function connect() {
  var deferred = Q.defer()
  MongoClient.connect(config.mongodb_url, function(err, db) {
    assert.equal(null, err)
    console.log('Connected to server: '+config.mongodb_url)
    _db = db
    deferred.resolve()
  })
  return deferred.promise
}

connect()
  .then(function() {
    [moduleUsers, moduleGeniusBar, moduleWestfieldCinema, moduleDenhamWsc].forEach(function(module) {
      module.setVars(_db, _profileIds, _placeIds)
    })
  })
  .then(drop)
  .then(moduleUsers.setupUsers)
  .then(setupPlaces)
  .then(function() { _db.close() })
  .catch(console.err)
