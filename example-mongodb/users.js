var assert = require('assert'),
    Q = require('q')

module.exports = {
  setVars: setVars,
  setupUsers: setupUsers
}

var _db, _profileIds, _placeIds
var _users, _profiles

function setVars(/*obj*/ db, /*{}*/ profileIds, /*{}*/ placeIds) {
  _db = db

  _profileIds = profileIds
  _placeIds = placeIds

  _users = db.collection('ms-auth-users'),
  _profiles = db.collection('ms-profiles')

  console.log('Users module set up')
}

function addUser(/*str*/ username, /*[str]*/ roles, /*str*/ firstName, /*str*/ lastName) {
  var deferredA = Q.defer(),
      deferredB = Q.defer()

  _users.insert({ username: username, password: username }, function(err, item) {
    assert.equal(null, err)
    deferredA.resolve()
  })

  _profiles.insert(
    { username: username,
      email: username+'@example.org',
      roles: roles,
      attributes: { first_name: firstName, last_name: lastName }
    },
    function(err, item) {
      assert.equal(null, err)
      _profileIds[username] = ''+item.ops[0]._id.valueOf()
      deferredB.resolve()
    })

  return Q.all([deferredA.promise, deferredB.promise])
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
