var assert = require('assert'),
    Q = require('q')

var qu = require('./query-utils')

module.exports = {
  setVars: setVars,
  setupUsers: setupUsers,
  updateBalance: updateBalance
}

var _db, _profileIds, _placeIds
var _users, _profiles, _balances

function setVars(/*obj*/ db, /*{}*/ profileIds, /*{}*/ placeIds) {
  _db = db

  _profileIds = profileIds
  _placeIds = placeIds

  _users = db.collection('ms-auth-users'),
  _profiles = db.collection('ms-profiles')
  _balances = db.collection('ms-payments-balances')

  console.log('Users module set up')
}

function addUser(/*str*/ username, /*[str]*/ roles, /*str*/ firstName, /*str*/ lastName) {
  var deferredA = Q.defer(),
      deferredB = Q.defer()

  qu.insert_one(_users, { username: username, password: username }, function onItem() {
    deferredA.resolve()
  })

  var newProfile = {
    username: username,
    email: username+'@example.org',
    roles: roles,
    attributes: { first_name: firstName, last_name: lastName }
  }
  qu.insert_one(_profiles, newProfile, function onItem(item) {
      _profileIds[username] = ''+item._id.valueOf()
      deferredB.resolve()
  })

  return Q.all([deferredA.promise, deferredB.promise])
}

function updateBalance(/*str*/ username, /*str*/ placeId, /*num*/ amount, /*str*/ currency) {
  var deferred = Q.defer(),
      newBalance = {
        place_id: placeId,
        profile_id: _profileIds[username],
        credit: [{
          amount: amount,
          currency: currency
        }]
      }
  qu.insert_one(_balances, newBalance, function onItem() {
    deferred.resolve()
  })
  return deferred.promise
}

function setupUsers() {
  var promises = [
    addUser('admin', ['ADMIN'], 'Admin', 'Admin'), // admin
    addUser('support', [], 'Support', 'Support'), // creates and owns places
    addUser('moderator', [], 'Moderator', 'Moderator'), // places moderator
    addUser('customer', [], 'Customer', 'Customer'), // creates bookings
    addUser('tester', [], 'Tester', 'Tester') // login as tester
  ]

  var deferred = Q.defer()
  Q.all(promises).then(function() {
    console.log('Users set up')
    deferred.resolve()
  })
  return deferred.promise
}
