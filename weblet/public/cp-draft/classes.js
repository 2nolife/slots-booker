function Callbacks() {
  
  var callbacks = []
  
  this.add = function(/*fn*/ callback) {
    callbacks.push(callback)
  }
  
  this.trigger = function(/*str*/ key, /*obj*/ src, /*any*/ arg) {
    callbacks.forEach(function(callback) {
      callback(key, src, arg)
    })
  }
  
}

function Locks() {

  var xs = []

  this.lock = function(/*str*/ key) {
    var locked = xs[key]
    if (!locked) xs[key] = true
    //else console.log('Lock already exists: '+key)
    return !locked
  }

  this.unlock = function(/*str*/ key) {
    xs[key] = false
  }

  this.has = function(/*str*/ key) {
    return xs[key]
  }

  /** try to acquire a 'key' lock and execute the 'f' function, respond via the 'callback' */
  this.exec = function(/*str*/ key, /*fn(callback)*/ f, /*fn|optional*/ callback) {
    function unlockCallback(/*any*/ arg) {
      this.unlock(key)
      if (callback) callback(arg)
    }

    if (this.lock(key)) f(unlockCallback.bind(this))
    else if (callback) callback('locked')
  }
}

function ChangesToSource(/*class*/ c, /*json*/ source) {

  /** update source '_' field and notify the listeners */
  this.update = function(/*str*/ key) {
    source['_'+key] = c[key]
    c.onChangeCallback.trigger(key, c)
  }

 /** check if source field changed, test '_' and the original source value */
  this.changed = function(/*str*/ key, /*any*/ defaultValue) {
    defaultValue = defaultValue || null
    return (source['_'+key] || source[key] || defaultValue) != (c[key] || defaultValue)
  }

  this.field = function(/*str*/ key, /*any*/ defaultValue) {
    if (this.changed(key, defaultValue)) this.update(key)
  }

}

function Place(/*json*/ source, /*services*/ sc) {

  var _this = this

  this.source = source

  this.onChangeCallback = new Callbacks()

  function applyChangesFromSource() {
    _this.id = source.place_id
    _this.name = source._name || source.name
    _this.address = source._address || source.address
    _this.owner = source._owner
    _this.spaces = source._spaces
    _this.moderatorIds = source._moderatorIds || source.moderators || []
    _this.moderators = source._moderators
    _this.attributes = source._attributes || source.attributes || {}
  }

  applyChangesFromSource()

  var locks = new Locks()
  var changesToSource = new ChangesToSource(_this, source)

  /** get place owner from API (expands the source object) */
  function refreshOwner(/*bool*/ force, /*fn*/ callback) {
    if (!_this.owner || force) {

      sc.apiUsersService.getUser(source.profile_id,
        function(/*User*/ user) {
          _this.owner = user
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  /** get place moderators from API (expands the source object) */
  function refreshModerators(/*bool*/ force, /*fn*/ callback) {
    if (!_this.moderators || force) {

      sc.apiUsersService.getUsers(_this.moderatorIds,
        function(/*[User]*/ users) {
          _this.moderators = users
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  /** get space inner spaces from API (expands the source object) */
  function refreshSpaces(/*bool*/ force, /*fn*/ callback) {
    if (!_this.spaces || force) {

      var f = force ?
        sc.apiSpacesService.refreshSpaces.bind(null, _this.id, null) :
        sc.apiSpacesService.expandSpaces.bind(null, source.spaces || [])

      f(function(/*[Space]*/ spaces) {
          _this.spaces = spaces
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
    sc.apiPlacesService.getPlace(_this.id,
      function(/*Place*/ place) {
        _this.copyFrom(place)
      },
      callback)
  }

  this.refresh = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    var f, lock = target

    switch (target) {
      case '*':
        f = refreshThis
        break

      case 'owner':
        f = refreshOwner
        break

      case 'moderators':
        f = refreshModerators
        break

      case 'spaces':
        f = refreshSpaces
        break

      default:
        console.log('Unknown refresh target: '+target)
    }

    if (f) locks.exec(lock, f.bind(this, force), callback)
  }

  this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
    sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
  }

  this.applyChangesToSource = function() {
    changesToSource.field('name')
    changesToSource.field('address')
    changesToSource.field('spaces')
    changesToSource.field('owner')
    changesToSource.field('attributes', {})

    if (source._moderators != _this.moderators) {
      source._moderatorIds = _this.moderatorIds = _this.moderators.map(function(moderator) { return moderator.id })
      changesToSource.update('moderators')
    }
  }

  this.copyFrom = function(/*json|Place*/ src) {
    var json = src.source ? src.source : src
    replaceInternals(source, json)
    applyChangesFromSource()
    _this.onChangeCallback.trigger('*', _this)
  }

}

function Space(/*json*/ source, /*services*/ sc) {

  var _this = this

  this.source = source

  this.onChangeCallback = new Callbacks()

  function applyChangesFromSource() {
    _this.id = source.space_id
    _this.placeId = source.place_id
    _this.parentSpaceId = source.parent_space_id
    _this.name = source._name || source.name
    _this.prices = source._prices
    _this.spaces = source._spaces
    _this.slots = source._slots
    _this.slotsFilter = source._slotsFilter || defaultSlotFilter()
  }

  applyChangesFromSource()

  var locks = new Locks()
  var changesToSource = new ChangesToSource(_this, source)

  function defaultSlotFilter() {
    return { from: todayDate(), to: todayDate() }
  }

  /** get space prices from API (expands the source object) */
  function refreshPrices(/*bool*/ force, /*fn*/ callback) {
    if (!_this.prices || force) {

      var f = force ?
        sc.apiSpacesService.refreshPrices.bind(null, _this.placeId, _this.id) :
        sc.apiSpacesService.expandPrices.bind(null, source.prices || [])

      f(function(/*[Price]*/ prices) {
          _this.prices = prices
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  /** get space inner spaces from API (expands the source object) */
  function refreshSpaces(/*bool*/ force, /*fn*/ callback) {
    if (!_this.spaces || force) {

      var f = force ?
        sc.apiSpacesService.refreshSpaces.bind(null, _this.placeId, _this.id) :
        sc.apiSpacesService.expandSpaces.bind(null, source.spaces || [])

      f(function(/*[Space]*/ spaces) {
          _this.spaces = spaces
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  /** get space slots from API (expands the source object) */
  function refreshSlots(/*bool*/ force, /*fn*/ callback) {
    var datesChanged = function() {
      var slotsFilter = source._slotsFilter || defaultSlotFilter
      return _this.slotsFilter.from != slotsFilter.from || _this.slotsFilter.to != slotsFilter.to
    }

    if (!_this.slots || force || datesChanged()) {

      var searchOptions = $.extend(true, { placeId: _this.placeId, spaceId: _this.id }, _this.slotsFilter)

      sc.apiSlotsService.findSlots(searchOptions,
        function(/*[Slot]*/ slots) {
          _this.slots = slots
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
    sc.apiSpacesService.getSpace(_this.placeId, _this.id,
      function(/*Space*/ space) {
        _this.copyFrom(space)
      },
      callback)
  }

  this.refresh = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    var f, lock = target

    switch (target) {
      case '*':
        f = refreshThis
        break

      case 'spaces':
        f = refreshSpaces
        break

      case 'prices':
        f = refreshPrices
        break

      case 'slots':
        f = refreshSlots
        break

      default:
        console.log('Unknown refresh target: '+target)
    }

    if (f) locks.exec(lock, f.bind(this, force), callback)
  }

  this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
    sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
  }

  this.applyChangesToSource = function() {
    changesToSource.field('name')
    changesToSource.field('spaces')
    changesToSource.field('prices')

    if (source._slots != _this.slots) {
      source._slotsFilter = $.extend(true, {}, _this.slotsFilter)
      changesToSource.update('slots')
    }
  }

  this.copyFrom = function(/*json|Space*/ src) {
    var json = src.source ? src.source : src
    replaceInternals(source, json)
    applyChangesFromSource()
    _this.onChangeCallback.trigger('*', _this)
  }

}

function Slot(/*json*/ source, /*services*/ sc) {

  var _this = this

  this.source = source

  this.onChangeCallback = new Callbacks()

  function applyChangesFromSource() {
    _this.id = source.slot_id
    _this.placeId = source.place_id
    _this.spaceId = source.space_id
    _this.name = source._name || source.name
    _this.dateFrom = source._date_from || source.date_from
    _this.dateTo = source._date_to || source.date_to
    _this.timeFrom = source._time_from || source.time_from
    _this.timeTo = source._time_to || source.time_to
    _this.prices = source._prices
    _this.bookings = source._bookings
    _this.attributes = source._attributes || source.attributes || {}
  }

  applyChangesFromSource()

  var locks = new Locks()
  var changesToSource = new ChangesToSource(_this, source)

  /** get slot prices from API (expands the source object) */
  function refreshPrices(/*bool*/ force, /*fn*/ callback) {
    if (!_this.prices || force) {

      var f = force ?
        sc.apiSlotsService.refreshPrices.bind(null, _this.id) :
        sc.apiSlotsService.expandPrices.bind(null, source.prices || [])

      f(function(/*[Price]*/ prices) {
          _this.prices = prices
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  /** get slot bookings from API (expands the source object) */
  function refreshBookings(/*bool*/ force, /*fn*/ callback) {
    if (!_this.bookings || force) {

      var f = force ?
        sc.apiSlotsService.refreshBookings.bind(null, _this.id) :
        sc.apiSlotsService.expandBookings.bind(null, source.bookings || [])

      f(function(/*[Booking]*/ bookings) {
          _this.bookings = bookings
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
    sc.apiSlotsService.getSlot(_this.id,
      function(/*Slot*/ slot) {
        _this.copyFrom(slot)
      },
      callback)
  }

  this.refresh = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    var f, lock = target

    switch (target) {
      case '*':
        f = refreshThis
        break

      case 'prices':
        f = refreshPrices
        break

      case 'bookings':
        f = refreshBookings
        break

      default:
        console.log('Unknown refresh target: '+target)
    }

    if (f) locks.exec(lock, f.bind(this, force), callback)
  }

  this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    sc.apiClassService.refreshRetry(_this, target, force, callback)
  }

  this.applyChangesToSource = function() {
    changesToSource.field('name')
    changesToSource.field('bookings')
    changesToSource.field('prices')
    changesToSource.field('attributes', {})

    if (
      (source._date_from || source.date_from) != _this.dateFrom ||
      (source._date_to   || source.date_to)   != _this.dateTo   ||
      (source._time_from || source.time_from) != _this.timeFrom ||
      (source._time_to   || source.time_to)   != _this.timeTo
    ) {
      source._date_from = _this.dateFrom
      source._date_To   = _this.dateTo
      source._time_from = _this.timeFrom
      source._time_to   = _this.timeTo
      _this.onChangeCallback.trigger('date-time', _this)
    }
  }

  this.copyFrom = function(/*json|Slot*/ src) {
    var json = src.source ? src.source : src
    replaceInternals(source, json)
    applyChangesFromSource()
    _this.onChangeCallback.trigger('*', _this)
  }

}

function Price(/*json*/ source, /*services*/ sc) {

  var _this = this

  this.source = source

  this.onChangeCallback = new Callbacks()

  function applyChangesFromSource() {
    _this.id = source.price_id

    // belongs to a Space
    _this.placeId = source.place_id
    _this.spaceId = source.space_id

    // belongs to a Slot
    _this.slotId = source.slot_id

    _this.name = source._name || source.name
    _this.amount = source._amount || source.amount
    _this.currency = source._currency || source.currency
  }

  applyChangesFromSource()

  var locks = new Locks()
  var changesToSource = new ChangesToSource(_this, source)

  function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
    if (_this.spaceId) {

      sc.apiSpacesService.getPrice(_this.placeId, _this.spaceId, _this.id,
        function(/*Price*/ price) {
          _this.copyFrom(price)
        },
        callback)

    }
    if (_this.slotId) {

      sc.apiSlotsService.getPrice(_this.slotId, _this.id,
        function(/*Price*/ price) {
          _this.copyFrom(price)
        },
        callback)

    }
  }

  this.refresh = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    var f, lock = target

    switch (target) {
      case '*':
        f = refreshThis
        break

      default:
        console.log('Unknown refresh target: '+target)
    }

    if (f) locks.exec(lock, f.bind(this, force), callback)
  }

  this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
    sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
  }

  this.applyChangesToSource = function() {
    changesToSource.field('name')
    changesToSource.field('amount')
    changesToSource.field('currency')
  }

  this.copyFrom = function(/*json|Price*/ src) {
    var json = src.source ? src.source : src
    replaceInternals(source, json)
    applyChangesFromSource()
    _this.onChangeCallback.trigger('*', _this)
  }

}

function Booking(/*json*/ source, /*services*/ sc) {

  var _this = this

  this.source = source

  this.onChangeCallback = new Callbacks()

  function applyChangesFromSource() {
    _this.id = source.booking_id
    _this.slotId = source.slot_id
    _this.name = source._name || source.name
    _this.status = source._status || source.status
    _this.user = source._user
    _this.attributes = source._attributes || source.attributes || {}
  }

  applyChangesFromSource()

  var locks = new Locks()
  var changesToSource = new ChangesToSource(_this, source)

  /** get booking user from API (expands the source object) */
  function refreshUser(/*bool*/ force, /*fn*/ callback) {
    if (!_this.user || force) {

      sc.apiUsersService.getUser(source.profile_id,
        function(/*User*/ user) {
          _this.user = user
          _this.applyChangesToSource()
        },
        callback)

    } else callback('noop')
  }

  function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
    sc.apiSlotsService.getBooking(_this.slotId, _this.id,
      function(/*Booking*/ booking) {
        _this.copyFrom(booking)
      },
      callback)
  }

  this.refresh = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    var f, lock = target

    switch (target) {
      case '*':
        f = refreshThis
        break

      case 'user':
        f = refreshUser
        break

      default:
        console.log('Unknown refresh target: '+target)
    }

    if (f) locks.exec(lock, f.bind(this, force), callback)
  }

  this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
    sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
  }

  this.applyChangesToSource = function() {
    changesToSource.field('name')
    changesToSource.field('status')
    changesToSource.field('user')
    changesToSource.field('attributes', {})
  }

  this.copyFrom = function(/*json|Booking*/ src) {
    var json = src.source ? src.source : src
    replaceInternals(source, json)
    applyChangesFromSource()
    _this.onChangeCallback.trigger('*', _this)
  }

}

function User(/*json*/ source, /*services*/ sc) {

  var _this = this

  this.source = source

  this.onChangeCallback = new Callbacks()

  function applyChangesFromSource() {
    _this.id = source.profile_id
    _this.username = source._username || source.username
    _this.email = source._email || source.email
    _this.roles = source._roles || source.roles || []
    _this.metadata = source._metadata || source.metadata || {}
    _this.attributes = source._attributes || source.attributes || {}

    _this.fullName = (function() {
      var firstName = _this.attributes.first_name, lastName = _this.attributes.last_name
      return firstName || lastName ? (firstName ? firstName : '')+' '+(lastName ? lastName : '') : source.username
    })()
  }

  applyChangesFromSource()

  var locks = new Locks()
  var changesToSource = new ChangesToSource(_this, source)

  function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
    sc.apiUsersService.getUser(_this.id,
      function(/*User*/ user) {
        _this.copyFrom(user)
      },
      callback)
  }

  this.refresh = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
    var f, lock = target

    switch (target) {
      case '*':
        f = refreshThis
        break

      default:
        console.log('Unknown refresh target: '+target)
    }

    if (f) locks.exec(lock, f.bind(this, force), callback)
  }

  this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
    sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
  }

  this.applyChangesToSource = function() {
    changesToSource.field('username')
    changesToSource.field('email')
    changesToSource.field('attributes', {})
    changesToSource.field('metadata', {})
    changesToSource.field('roles', [])
  }

  this.copyFrom = function(/*json|User*/ src) {
    var json = src.source ? src.source : src
    replaceInternals(source, json)
    applyChangesFromSource()
    _this.onChangeCallback.trigger('*', _this)
  }

}