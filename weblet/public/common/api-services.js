app.service('sb_apiPlacesService', function($http, sb_apiHelper, $q, sb_apiClassWrap) {
  var service = this

  service.findPlaces = function(/*json*/ searchOptions, /*fn*/ callback, /*fn*/ statusCallback) {
    var attrs0 = (searchOptions.attributes || []).map(function(attr) {
      return Object.keys(attr).map(function(key) {
        return key+'='+attr[key]
      })
    })
    var attrs = [].concat.apply([], attrs0).join('&')

    var query = '?'+(searchOptions.join || 'and')+(attrs.length > 0 ? '&' : '')+attrs

    $http.get('/api/places/search'+query)
      .then(
        function successCallback(response) {
          var places = response.data
          callback(places.map(function(place) { return sb_apiClassWrap.wrap(place, 'place') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getPlaces = function(/*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places')
      .then(
        function successCallback(response) {
          var places = response.data
          callback(places.map(function(place) { return sb_apiClassWrap.wrap(place, 'place') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getPlace = function(/*str*/ placeId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId)
      .then(
        function successCallback(response) {
          var place = response.data
          callback(sb_apiClassWrap.wrap(place, 'place'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.patchPlace = function(/*str*/ placeId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/places/'+placeId, entity)
      .then(
        function successCallback(response) {
          var place = response.data
          callback(sb_apiClassWrap.wrap(place, 'place'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.addPlace = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/places', entity)
      .then(
        function successCallback(response) {
          var place = response.data
          callback(sb_apiClassWrap.wrap(place, 'place'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.deletePlace = function(/*str*/ placeId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/places/'+placeId)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

})

app.service('sb_apiSpacesService', function($http, sb_apiHelper, $q, sb_apiClassWrap) {
  var service = this

  service.patchPrice = function(/*str*/ placeId, /*str*/ spaceId, /*str*/ priceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/places/'+placeId+'/spaces/'+spaceId+'/prices/'+priceId, entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(sb_apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.deletePrice = function(/*str*/ placeId, /*str*/ spaceId, /*str*/ priceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/places/'+placeId+'/spaces/'+spaceId+'/prices/'+priceId)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.addPrice = function(/*str*/ placeId, /*str*/ spaceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/places/'+placeId+'/spaces/'+spaceId+'/prices', entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(sb_apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getPrice = function(/*str*/ placeId, /*str*/ spaceId, /*str*/ priceId, /*fn*/ callback, /*fn*/ statusCallback) {   
    $http.get('/api/places/'+placeId+'/spaces/'+spaceId+'/prices/'+priceId)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(sb_apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.patchSpace = function(/*str*/ placeId, /*str*/ spaceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/places/'+placeId+'/spaces/'+spaceId, entity)
      .then(
        function successCallback(response) {
          var space = response.data
          callback(sb_apiClassWrap.wrap(space, 'space'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.deleteSpace = function(/*str*/ placeId, /*str*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/places/'+placeId+'/spaces/'+spaceId)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.addSpace = function(/*str*/ placeId, /*str(optional)*/ parentSpaceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/places/'+placeId+'/spaces'+(parentSpaceId ? '/'+parentSpaceId : ''), entity)
      .then(
        function successCallback(response) {
          var space = response.data
          callback(sb_apiClassWrap.wrap(space, 'space'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getSpace = function(/*str*/ placeId, /*str*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId+'/spaces/'+spaceId)
      .then(
        function successCallback(response) {
          var space = response.data
          callback(sb_apiClassWrap.wrap(space, 'space'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.findSpaces = function(/*str*/ placeId, /*json*/ searchOptions, /*fn*/ callback, /*fn*/ statusCallback) {
    var attrs0 = (searchOptions.attributes || []).map(function(attr) {
      return Object.keys(attr).map(function(key) {
        return key+'='+attr[key]
      })
    })
    var attrs = [].concat.apply([], attrs0).join('&')

    var query = '?'+(searchOptions.join || 'and')+(attrs.length > 0 ? '&' : '')+attrs

    $http.get('/api/places/'+placeId+'/spaces/search'+query)
      .then(
        function successCallback(response) {
          var spaces = response.data
          callback(spaces.map(function(space) { return sb_apiClassWrap.wrap(space, 'space') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.refreshSpaces = function(/*str*/ placeId, /*str(optional)*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback, /*json*/ options) {
    options = $.extend(true, {}, options)

    var query = ''
    if (options.limit != undefined) query += '&limit='+options.limit
    if (query) query = '?'+query.substring(1)

    $http.get('/api/places/'+placeId+(spaceId ? '/spaces/'+spaceId : '')+'/spaces'+query)
      .then(
        function successCallback(response) {
          var spaces = response.data
          callback(spaces.map(function(space) { return sb_apiClassWrap.wrap(space, 'space') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.refreshPrices = function(/*str*/ placeId, /*str*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId+'/spaces/'+spaceId+'/prices')
      .then(
        function successCallback(response) {
          var prices = response.data
          callback(prices.map(function(price) { return sb_apiClassWrap.wrap(price, 'price') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

})

app.service('sb_apiSlotsService', function($http, sb_apiHelper, $q, sb_apiClassWrap) {
  var service = this

  service.patchPrice = function(/*str*/ slotId, /*str*/ priceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/slots/'+slotId+'/prices/'+priceId, entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(sb_apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.deletePrice = function(/*str*/ slotId, /*str*/ priceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/slots/'+slotId+'/prices/'+priceId)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.addPrice = function(/*str*/ slotId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/slots/'+slotId+'/prices', entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(sb_apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getPrice = function(/*str*/ slotId, /*str*/ priceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'/prices/'+priceId)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(sb_apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.patchBooking = function(/*str*/ slotId, /*str*/ bookingId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/slots/'+slotId+'/bookings/'+bookingId, entity)
      .then(
        function successCallback(response) {
          var booking = response.data
          callback(sb_apiClassWrap.wrap(booking, 'booking'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getBooking = function(/*str*/ slotId, /*str*/ bookingId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'/bookings/'+bookingId)
      .then(
        function successCallback(response) {
          var booking = response.data
          callback(sb_apiClassWrap.wrap(booking, 'booking'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.patchSlot = function(/*str*/ slotId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/slots/'+slotId, entity)
      .then(
        function successCallback(response) {
          var slot = response.data
          callback(sb_apiClassWrap.wrap(slot, 'slot'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.deleteSlot = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/slots/'+slotId)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.addSlot = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/slots', entity)
      .then(
        function successCallback(response) {
          var slot = response.data
          callback(sb_apiClassWrap.wrap(slot, 'slot'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getSlot = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId)
      .then(
        function successCallback(response) {
          var slot = response.data
          callback(sb_apiClassWrap.wrap(slot, 'slot'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.refreshBookings = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback, /*json*/ options) {
    options = $.extend(true, {}, options)

    var query = ''
    if (options.active != undefined) query += '&active'
    if (query) query = '?'+query.substring(1)

    $http.get('/api/slots/'+slotId+'/bookings'+query)
      .then(
        function successCallback(response) {
          var bookings = response.data
          callback(bookings.map(function(booking) { return sb_apiClassWrap.wrap(booking, 'booking') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.refreshPrices = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'/prices')
      .then(
        function successCallback(response) {
          var prices = response.data
          callback(prices.map(function(price) { return sb_apiClassWrap.wrap(price, 'price') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.findSlots = function(/*json*/ searchOptions, /*fn*/ callback, /*fn*/ statusCallback) {
    var defaultOptions = { from: '19700101', to: '99990101', inner: false }
    searchOptions = $.extend(true, {}, defaultOptions, searchOptions)

    var query = '?place_id='+searchOptions.placeId+'&space_id='+searchOptions.spaceId+'&from='+searchOptions.from+'&to='+searchOptions.to+'&inner='+searchOptions.inner
    if (searchOptions.booked != undefined) query += '&booked='+searchOptions.booked
    if (searchOptions.paid != undefined) query += '&paid='+searchOptions.paid

    $http.get('/api/slots/search'+query)
      .then(
        function successCallback(response) {
          var slots = response.data
          callback(slots.map(function(slot) { return sb_apiClassWrap.wrap(slot, 'slot') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

})

app.service('sb_apiBookingService', function($http, sb_apiHelper, $q, sb_apiClassWrap) {
  var service = this

  service.book = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/booking/book', entity)
      .then(
        function successCallback(response) {
          var reference = response.data
          callback(sb_apiClassWrap.wrap(reference, 'reference'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.cancel = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/booking/cancel', entity)
      .then(
        function successCallback(response) {
          var reference = response.data
          callback(sb_apiClassWrap.wrap(reference, 'reference'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.quote = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/booking/quote', entity)
      .then(
        function successCallback(response) {
          var quote = response.data
          callback(sb_apiClassWrap.wrap(quote, 'quote'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.refund = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/booking/refund', entity)
      .then(
        function successCallback(response) {
          var refund = response.data
          callback(sb_apiClassWrap.wrap(refund, 'refund'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

})

app.service('sb_apiUsersService', function($http, sb_apiHelper, $q, sb_apiClassWrap) {
  var service = this

  service.getUser = function(/*str*/ userId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/profiles/'+userId)
      .then(
        function successCallback(response) {
          var user = response.data
          callback(sb_apiClassWrap.wrap(user, 'user'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getUsers = function(/*[str]*/ userIds, /*fn*/ callback, /*fn*/ statusCallback) {
    var promises = userIds.map(function(userId) {
      return $http.get('/api/profiles/'+userId)
    })

    if (promises.length == 0) {
      callback([])
      if (statusCallback) statusCallback('success')
    }

    $q.all(promises).then(
      function successCallback(responses) {
        var users = responses.map(function(response) {
          return response.data
        })
        callback(users.map(function(user) { return sb_apiClassWrap.wrap(user, 'user') }))
        if (statusCallback) statusCallback('success')
      },
      function errorCallback(responses) {
        if (!statusCallback || !statusCallback('error', responses)) sb_apiHelper.notifyResponseError(responses[0])
      })
  }

  service.findUsers = function(/*json*/ searchOptions, /*fn*/ callback, /*fn*/ statusCallback) {
    var attrs0 = (searchOptions.attributes || []).map(function(attr) {
      return Object.keys(attr).map(function(key) {
        return key+'='+attr[key]
      })
    })
    var attrs = [].concat.apply([], attrs0).join('&')

    var query = '?'+(searchOptions.join || 'and')+(attrs.length > 0 ? '&' : '')+attrs

    $http.get('/api/profiles/search'+query)
      .then(
        function successCallback(response) {
          var users = response.data
          callback(users.map(function(user) { return sb_apiClassWrap.wrap(user, 'user') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.patchUser = function(/*str*/ id, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/profiles/'+id, entity)
      .then(
        function successCallback(response) {
          var user = response.data
          callback(sb_apiClassWrap.wrap(user, 'user'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.deleteUser = function(/*str*/ id, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/profiles/'+id)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.signOutUser = function(/*str*/ id, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.delete('/api/profiles/'+id+'/token')
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.addUser = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/profiles/register', entity)
      .then(
        function successCallback(response) {
          var id = response.data.profile_id
          service.patchUser(id, entity, callback)
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

})

app.service('sb_apiPaymentsService', function($http, sb_apiHelper, $q, sb_apiClassWrap) {
  var service = this

  service.processReference = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/payments/reference/process', entity)
      .then(
        function successCallback(response) {
          var balance = response.data
          callback(sb_apiClassWrap.wrap(balance, 'balance'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getReference = function(/*str*/ ref, /*str|optional*/ profileId, /*fn*/ callback, /*fn*/ statusCallback) { //todo
    $http.get('/api/payments/reference?ref='+ref+(profileId ? '&profile_id='+profileId : ''))
      .then(
        function successCallback(response) {
          var reference = response.data
          callback(sb_apiClassWrap.wrap(reference, 'reference'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

  service.getUserBalance = function(/*str*/ placeId, /*str|optional*/ profileId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/payments/balance?place_id='+placeId+(profileId ? '&profile_id='+profileId : ''))
      .then(
        function successCallback(response) {
          var balance = response.data
          callback(sb_apiClassWrap.wrap(balance, 'balance'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  },

  service.getPlaceAccount = function(/*str*/ placeId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/payments/account?place_id='+placeId)
      .then(
        function successCallback(response) {
          var account = response.data
          callback(sb_apiClassWrap.wrap(account, 'account'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) sb_apiHelper.notifyResponseError(response)
        })
  }

})

app.service('sb_apiClassWrap', function($injector) {
  var service = this

  var sc = null

  function initServiceContainer() {
    if (!sc) sc = {
      apiUsersService: $injector.get('sb_apiUsersService'),
      apiPlacesService: $injector.get('sb_apiPlacesService'),
      apiSpacesService: $injector.get('sb_apiSpacesService'),
      apiSlotsService: $injector.get('sb_apiSlotsService'),
      apiPaymentsService: $injector.get('sb_apiPaymentsService'),
      apiClassService: $injector.get('sb_apiClassService')
    }
  }

  service.wrap = function(/*json*/ json, /*str*/ className) {
    initServiceContainer()

    var clz
    switch (className) {
      case 'user':
        clz = new sb.classes.User(json, sc)
        break
      case 'place':
        clz = new sb.classes.Place(json, sc)
        break
      case 'space':
        clz = new sb.classes.Space(json, sc)
        break
      case 'slot':
        clz = new sb.classes.Slot(json, sc)
        break
      case 'price':
        clz = new sb.classes.Price(json, sc)
        break
      case 'booking':
        clz = new sb.classes.Booking(json, sc)
        break
      case 'quote':
        clz = new sb.classes.Quote(json, sc)
        break
      case 'refund':
        clz = new sb.classes.Refund(json, sc)
        break
      case 'reference':
        clz = new sb.classes.Reference(json, sc)
        break
      case 'balance':
        clz = new sb.classes.Balance(json, sc)
        break
      case 'account':
        clz = new sb.classes.Account(json, sc)
        break
    }

    return clz
  }

})

app.service('sb_apiClassService', function($timeout) {
  var service = this

  /** retry forced 'refresh' function on 'source' in case of 'locked' status */
  service.refreshRetry = function(/*obj*/ source, /*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
    retries = retries || 3
    var retryN = 0 , retryFn = source.refresh.bind(null, target, true)

    function retryCallback(/*str*/ status) {
      if (status == 'locked') {

        if (retryN < retries) {
          retryN++
          console.log('target '+target+', status '+status+', retrying '+retryN+' of '+retries)
          $timeout(function() { retryFn(retryCallback) }, 100*retryN) // 100ms 200ms 300ms ...
        } else {
          console.log('target '+target+', status '+status+', max reties reached, giving up')
          if (callback) callback(status)
        }

      } else {
        if (callback) callback(status)
      }
    }

    source.refresh(target, force, retryCallback)
  }

})

app.service('sb_apiHelper', function(sb_notifyService) {
  var service = this

  service.notifyResponseError = function(/*obj*/ response) {
    var apiCode = sb.utils.apiCodeFromResponse(response)
    sb_notifyService.notify('<strong>'+response.status+'</strong> '+apiCode.text, 'danger')
  }

})
