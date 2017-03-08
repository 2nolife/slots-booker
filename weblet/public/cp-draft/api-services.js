app.service('apiPlacesService', function($http, notifyService, $q, apiClassWrap) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.findPlaces = function(/*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places?deep=false')
      .then(
        function successCallback(response) {
          var places = response.data
          callback(places.map(function(place) { return apiClassWrap.wrap(place, 'place') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.getPlace = function(/*str*/ placeId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId+'?deep=false')
      .then(
        function successCallback(response) {
          var place = response.data
          callback(apiClassWrap.wrap(place, 'place'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.patchPlace = function(/*str*/ placeId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/places/'+placeId, entity)
      .then(
        function successCallback(response) {
          var place = response.data
          callback(apiClassWrap.wrap(place, 'place'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

})

app.service('apiSpacesService', function($http, notifyService, $q, apiClassWrap) {
  var service = this

  var notifyResponseError = function(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.patchPrice = function(/*str*/ placeId, /*str*/ spaceId, /*str*/ priceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/places/'+placeId+'/spaces/'+spaceId+'/prices/'+priceId, entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.addPrice = function(/*str*/ placeId, /*str*/ spaceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/places/'+placeId+'/spaces/'+spaceId+'/prices', entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.getPrice = function(/*str*/ placeId, /*str*/ spaceId, /*str*/ priceId, /*fn*/ callback, /*fn*/ statusCallback) {   
    $http.get('/api/places/'+placeId+'/spaces/'+spaceId+'/prices/'+priceId)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.patchSpace = function(/*str*/ placeId, /*str*/ spaceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/places/'+placeId+'/spaces/'+spaceId, entity)
      .then(
        function successCallback(response) {
          var space = response.data
          callback(apiClassWrap.wrap(space, 'space'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.addSpace = function(/*str*/ placeId, /*str(optional)*/ parentSpaceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/places/'+placeId+'/spaces'+(parentSpaceId ? '/'+parentSpaceId : ''), entity)
      .then(
        function successCallback(response) {
          var space = response.data
          callback(apiClassWrap.wrap(space, 'space'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.getSpace = function(/*str*/ placeId, /*str*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId+'/spaces/'+spaceId+'?deep=false')
      .then(
        function successCallback(response) {
          var space = response.data
          callback(apiClassWrap.wrap(space, 'space'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.expandSpaces = function(/*[json]*/ spaces, /*fn*/ callback, /*fn*/ statusCallback) {
    var promises = spaces.map(function(space) {
      return $http.get('/api/places/'+space.place_id+'/spaces/'+space.space_id+'?deep=false')
    })

    if (promises.length == 0) {
      callback([])
      if (statusCallback) statusCallback('success')
    }

    $q.all(promises).then(
      function successCallback(responses) {
        var spaces = responses.map(function(response) {
          return response.data
        })
        callback(spaces.map(function(space) { return apiClassWrap.wrap(space, 'space') }))
        if (statusCallback) statusCallback('success')
      },
      function errorCallback(responses) {
        notifyResponseError(responses[0])
        if (statusCallback) statusCallback('error')
      })
  }

  service.expandPrices = function(/*[json]*/ prices, /*fn*/ callback, /*fn*/ statusCallback) {
    var promises = prices.map(function(price) {
      return $http.get('/api/places/'+price.place_id+'/spaces/'+price.space_id+'/prices/'+price.price_id)
    })

    if (promises.length == 0) {
      callback([])
      if (statusCallback) statusCallback('success')
    }

    $q.all(promises).then(
      function successCallback(responses) {
        var prices = responses.map(function(response) {
          return response.data
        })
        callback(prices.map(function(price) { return apiClassWrap.wrap(price, 'price') }))
        if (statusCallback) statusCallback('success')
      },
      function errorCallback(responses) {
        notifyResponseError(responses[0])
        if (statusCallback) statusCallback('error')
      })
  }

  service.refreshSpaces = function(/*str*/ placeId, /*str(optional)*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId+(spaceId ? '/spaces/'+spaceId : '')+'?deep=false')
      .then(
        function successCallback(response) {
          var spaces = response.data.spaces
          if (spaces) service.expandSpaces(spaces, callback, statusCallback)
          else {
            callback([])
            if (statusCallback) statusCallback('success')
          }
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.refreshPrices = function(/*str*/ placeId, /*str*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/places/'+placeId+'/spaces/'+spaceId+'?deep=false')
      .then(
        function successCallback(response) {
          var prices = response.data.prices
          if (prices) service.expandPrices(prices, callback, statusCallback)
          else {
            callback([])
            if (statusCallback) statusCallback('success')
          }
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

})

app.service('apiSlotsService', function($http, notifyService, $q, apiClassWrap) {
  var service = this

  var notifyResponseError = function(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.patchPrice = function(/*str*/ slotId, /*str*/ priceId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/slots/'+slotId+'/prices/'+priceId, entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.addPrice = function(/*str*/ slotId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/slots/'+slotId+'/prices', entity)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.getPrice = function(/*str*/ slotId, /*str*/ priceId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'/prices/'+priceId)
      .then(
        function successCallback(response) {
          var price = response.data
          callback(apiClassWrap.wrap(price, 'price'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.patchBooking = function(/*str*/ slotId, /*str*/ bookingId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/slots/'+slotId+'/bookings/'+bookingId, entity)
      .then(
        function successCallback(response) {
          var booking = response.data
          callback(apiClassWrap.wrap(booking, 'booking'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.getBooking = function(/*str*/ slotId, /*str*/ bookingId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'/bookings/'+bookingId)
      .then(
        function successCallback(response) {
          var booking = response.data
          callback(apiClassWrap.wrap(booking, 'booking'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.patchSlot = function(/*str*/ slotId, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/slots/'+slotId, entity)
      .then(
        function successCallback(response) {
          var slot = response.data
          callback(apiClassWrap.wrap(slot, 'slot'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.addSlot = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/slots', entity)
      .then(
        function successCallback(response) {
          var slot = response.data
          callback(apiClassWrap.wrap(slot, 'slot'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.getSlot = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'?deep=false')
      .then(
        function successCallback(response) {
          var slot = response.data
          callback(apiClassWrap.wrap(slot, 'slot'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.expandBookings = function(/*[json]*/ bookings, /*fn*/ callback, /*fn*/ statusCallback) {
    var promises = bookings.map(function(booking) {
      return $http.get('/api/slots/'+booking.slot_id+'/bookings/'+booking.booking_id)
    })

    if (promises.length == 0) {
      callback([])
      if (statusCallback) statusCallback('success')
    }

    $q.all(promises).then(
      function successCallback(responses) {
        var bookings = responses.map(function(response) {
          return response.data
        })
        callback(bookings.map(function(booking) { return apiClassWrap.wrap(booking, 'booking') }))
        if (statusCallback) statusCallback('success')
      },
      function errorCallback(responses) {
        notifyResponseError(responses[0])
        if (statusCallback) statusCallback('error')
      })
  }

  service.expandPrices = function(/*[json]*/ prices, /*fn*/ callback, /*fn*/ statusCallback) {
    var promises = prices.map(function(price) {
      return $http.get('/api/slots/'+price.slot_id+'/prices/'+price.price_id)
    })

    if (promises.length == 0) {
      callback([])
      if (statusCallback) statusCallback('success')
    }

    $q.all(promises).then(
      function successCallback(responses) {
        var prices = responses.map(function(response) {
          return response.data
        })
        callback(prices.map(function(price) { return apiClassWrap.wrap(price, 'price') }))
        if (statusCallback) statusCallback('success')
      },
      function errorCallback(responses) {
        notifyResponseError(responses[0])
        if (statusCallback) statusCallback('error')
      })
  }

  service.refreshBookings = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'?deep=false')
      .then(
        function successCallback(response) {
          var bookings = response.data.bookings
          if (bookings) service.expandBookings(bookings, callback, statusCallback)
          else {
            callback([])
            if (statusCallback) statusCallback('success')
          }
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.refreshPrices = function(/*str*/ slotId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/slots/'+slotId+'?deep=false')
      .then(
        function successCallback(response) {
          var prices = response.data.prices
          if (prices) service.expandPrices(prices, callback, statusCallback)
          else {
            callback([])
            if (statusCallback) statusCallback('success')
          }
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.refreshSlotsBySpace = function(/*str*/ placeId, /*str*/ spaceId, /*fn*/ callback, /*fn*/ statusCallback) {   //todo may be deprecated, use "findSlots" instead
    $http.get('/api/slots/space/'+spaceId+'?place_id='+placeId+'&deep=false')
      .then(
        function successCallback(response) {
          var slots = response.data
          callback(slots.map(function(slot) { return apiClassWrap.wrap(slot, 'slot') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.findSlots = function(/*json*/ searchOptions, /*fn*/ callback, /*fn*/ statusCallback) {
    var defaultOptions = { from: '19700101', to: '99990101' }
    searchOptions = $.extend(true, {}, defaultOptions, searchOptions)

    $http.get('/api/slots/search?place_id='+searchOptions.placeId+'&space_id='+searchOptions.spaceId+'&from='+searchOptions.from+'&to='+searchOptions.to+'&inner=false&deep=false')
      .then(
        function successCallback(response) {
          var slots = response.data
          callback(slots.map(function(slot) { return apiClassWrap.wrap(slot, 'slot') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

})

app.service('apiBookingService', function($http, notifyService, $q, apiClassWrap) {
  var service = this

  var notifyResponseError = function(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.bookSlots = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/booking/slots', entity)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.cancelSlots = function(/*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.post('/api/booking/cancel', entity)
      .then(
        function successCallback(response) {
          callback()
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

})

app.service('apiUsersService', function($http, notifyService, $q, apiClassWrap) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.getUser = function(/*str*/ userId, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.get('/api/profiles/'+userId)
      .then(
        function successCallback(response) {
          var user = response.data
          callback(apiClassWrap.wrap(user, 'user'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
        callback(users.map(function(user) { return apiClassWrap.wrap(user, 'user') }))
        if (statusCallback) statusCallback('success')
      },
      function errorCallback(responses) {
        notifyResponseError(responses[0])
        if (statusCallback) statusCallback('error')
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
          callback(users.map(function(user) { return apiClassWrap.wrap(user, 'user') }))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

  service.patchUser = function(/*str*/ id, /*json*/ entity, /*fn*/ callback, /*fn*/ statusCallback) {
    $http.patch('/api/profiles/'+id, entity)
      .then(
        function successCallback(response) {
          var user = response.data
          callback(apiClassWrap.wrap(user, 'user'))
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
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
          notifyResponseError(response)
          if (statusCallback) statusCallback('error')
        })
  }

})

app.service('apiClassWrap', function($injector) {
  var service = this

  var sc = null

  function initServiceContainer() {
    if (!sc) sc = {
      apiUsersService: $injector.get('apiUsersService'),
      apiPlacesService: $injector.get('apiPlacesService'),
      apiSpacesService: $injector.get('apiSpacesService'),
      apiSlotsService: $injector.get('apiSlotsService'),
      apiClassService: $injector.get('apiClassService')
    }
  }

  service.wrap = function(/*json*/ json, /*str*/ className) {
    initServiceContainer()

    var clz
    switch (className) {
      case 'user':
        clz = new User(json, sc)
        break
      case 'place':
        clz = new Place(json, sc)
        break
      case 'space':
        clz = new Space(json, sc)
        break
      case 'slot':
        clz = new Slot(json, sc)
        break
      case 'price':
        clz = new Price(json, sc)
        break
      case 'booking':
        clz = new Booking(json, sc)
        break
    }

    return clz
  }

})

app.service('apiClassService', function($timeout) {
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
