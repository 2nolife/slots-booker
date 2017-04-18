var MongoClient = require('mongodb').MongoClient,
    assert = require('assert'),
    Q = require('q')

var config = require('./config.json'),
    moduleGeniusBar = require('./genius-bar'),
    moduleWestfieldCinema = require('./westfield-cinema'),
    moduleUsers = require('./users')

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
  var promises = [
    moduleGeniusBar.setupPlace(),   
    moduleWestfieldCinema.setupPlace()
  ]

  var deferred = Q.defer()
  Q.all(promises).then(function() {
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
    [moduleUsers, moduleGeniusBar, moduleWestfieldCinema].forEach(function(module) {
      module.setVars(_db, _profileIds, _placeIds)
    })
  })
  .then(drop)
  .then(moduleUsers.setupUsers)
  .then(setupPlaces)
  .then(function() { _db.close() })
  .catch(console.err)
