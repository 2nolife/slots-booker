var assert = require('assert'),
    Q = require('q'),
    ObjectID = require('mongodb').ObjectID

module.exports = {
  find_forEach: find_forEach,
  insert_n: insert_n,
  insert_one: insert_one,
  findAndModify_byId: findAndModify_byId
}

function finderById(/*str*/ id) {
  return { _id: ObjectID.createFromHexString(id) }
}

function sortById() {
  return [['_id', 'asc']]
}

function find_forEach(collection, query, /*fn*/ onItem, /*fn*/ after) {
  collection.find(query).toArray(function(err, items) {
    assert.equal(null, err)

    if (onItem) items.forEach(function(item) { onItem(item) })
    if (after) after(items)
  })
}

function insert_n(collection, /*num*/ n, /*fn*/ newItem, /*fn*/ onItem, /*fn*/ after) {
  var items = [],
      newItems = []

  for (var i = 0; i < n; i++)
    newItems.push(newItem(i))

  newItems.map(function(newItem) {
    collection.insert(newItem, function(err, result) {
      assert.equal(null, err)

      var item = result.ops[0]
      items.push(item)
      if (onItem) onItem(item)

      if (--n == 0 && after) after(items)
    })
  })
}

function insert_one(collection, /*json*/ newItem, /*fn*/ onItem) {
  collection.insert(newItem, function(err, result) {
    assert.equal(null, err)

    var item = result.ops[0]
    if (onItem) onItem(item)
  })
}

function findAndModify_byId(collection, /*str*/ id, update, /*fn*/ callback, /*bool*/ assertExists) {
  collection.findAndModify(
    finderById(id),
    sortById(),
    update,
    function(err, result) {
      assert.equal(null, err)

      if (assertExists) assert(result.value != null, 'Item not found')
      if (callback) callback(result)
    })
}
