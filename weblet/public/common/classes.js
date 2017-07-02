var sb = sb || {}

sb.classes = {

  Place: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.id = source.place_id
      _this.name = _this.name || source.name
      _this.address = _this.address || source.address
      _this.owner = _this.owner
      _this.spaces = _this.spaces
      _this.moderatorIds = _this.moderatorIds || source.moderators || []
      _this.moderators = _this.moderators
      _this.attributes = _this.attributes || source.attributes || {}
      _this.members = _this.members
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

    /** get place owner from API */
    function refreshOwner(/*bool*/ force, /*fn*/ callback) {
      if (!_this.owner || force) {

        sc.apiUsersService.getUser(source.profile_id,
          function(/*User*/ user) {
            _this.owner = user
            _this.onChangeCallback.trigger('owner')
          },
          callback)

      } else callback('noop')
    }

    /** get place moderators from API */
    function refreshModerators(/*bool*/ force, /*fn*/ callback) {
      if (!_this.moderators || force) {

        sc.apiUsersService.getUsers(_this.moderatorIds,
          function(/*[User]*/ users) {
            _this.moderators = users
            _this.moderatorIds = _this.moderators.map(function(moderator) { return moderator.id })
            _this.onChangeCallback.trigger('moderators')
          },
          callback)

      } else callback('noop')
    }

    /** get space inner spaces from API */
    function refreshSpaces(/*bool*/ force, /*fn*/ callback) {
      if (!_this.spaces || force) {

        sc.apiSpacesService.refreshSpaces(_this.id, null,
          function(/*[Space]*/ spaces) {
            _this.spaces = spaces
            _this.onChangeCallback.trigger('spaces')
          },
          callback)

      } else callback('noop')
    }

    /** get place members from API */
    function refreshMembers(/*bool*/ force, /*fn*/ callback) {
      if (!_this.members || force) {

        sc.apiMembersService.refreshMembers(_this.id,
          function(/*[Member]*/ members) {
            _this.members = members
            _this.onChangeCallback.trigger('members')
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

        case 'members':
          f = refreshMembers
          break

        default:
          console.log('Unknown refresh target: '+target)
      }

      if (f) locks.exec(lock, f.bind(this, force), callback)
    }

    this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
      sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
    }

    this.copyFrom = function(/*json|Place*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  Space: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.id = source.space_id
      _this.placeId = source.place_id
      _this.parentSpaceId = source.parent_space_id
      _this.name = _this.name || source.name
      _this.prices = _this.prices
      _this.spaces = _this.spaces
      _this.firstSpace = _this.firstSpace
      _this.slots = _this.slots
      _this.slotsFilter = _this.slotsFilter || _this._slotsFilter || defaultSlotsFilter()
      _this.attributes = _this.attributes || source.attributes || {}
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

    function defaultSlotsFilter() {
      return { from: sb.utils.todayDate(), to: sb.utils.todayDate(), inner: false }
    }

    /** get space prices from API */
    function refreshPrices(/*bool*/ force, /*fn*/ callback) {
      if (!_this.prices || force) {

        sc.apiSpacesService.refreshPrices(_this.placeId, _this.id,
          function(/*[Price]*/ prices) {
            _this.prices = prices
            _this.onChangeCallback.trigger('prices')
          },
          callback)

      } else callback('noop')
    }

    /** get space effective prices from API */
    function refreshEffectivePrices(/*bool*/ force, /*fn*/ callback) {
      if (!_this.effectivePrices || force) {

        sc.apiSpacesService.refreshPrices(_this.placeId, _this.id,
          function(/*[Price]*/ prices) {
            _this.effectivePrices = prices
            _this.onChangeCallback.trigger('effectivePrices')
          },
          callback,
          { effective: '' })

      } else callback('noop')
    }

    /** get space inner spaces first space from API */
    function refreshFirstSpace(/*bool*/ force, /*fn*/ callback) {
      if (!_this.spaces || force) {

        sc.apiSpacesService.refreshSpaces(_this.placeId, _this.id,
          function(/*[Space]*/ spaces) {
            _this.firstSpace = spaces.length ? spaces[0] : null
            _this.onChangeCallback.trigger('firstSpace')
          },
          callback,
          { limit: 1 })

      } else callback('noop')
    }

    /** get space inner spaces from API */
    function refreshSpaces(/*bool*/ force, /*fn*/ callback) {
      if (!_this.spaces || force) {

        sc.apiSpacesService.refreshSpaces(_this.placeId, _this.id,
          function(/*[Space]*/ spaces) {
            _this.spaces = spaces
            _this.onChangeCallback.trigger('spaces')
          },
          callback)

      } else callback('noop')
    }

    /** get space slots from API */
    function refreshSlots(/*bool*/ force, /*fn*/ callback) {
      var slotsFilterChanged = function() {
        var slotsFilter = _this._slotsFilter
        return _this.slotsFilter.from != slotsFilter.from || _this.slotsFilter.to != slotsFilter.to ||
               _this.slotsFilter.inner != slotsFilter.inner ||
               _this.slotsFilter.booked != slotsFilter.booked || _this.slotsFilter.paid != slotsFilter.paid
      }

      if (!_this.slots || force || slotsFilterChanged()) {

        var searchOptions = $.extend(true, { placeId: _this.placeId, spaceId: _this.id }, _this.slotsFilter)

        sc.apiSlotsService.findSlots(searchOptions,
          function(/*[Slot]*/ slots) {
            _this.slots = slots
            _this._slotsFilter = $.extend(true, {}, _this.slotsFilter)
            _this.onChangeCallback.trigger('slots')
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

        case 'firstSpace':
          f = refreshFirstSpace
          break

        case 'spaces':
          f = refreshSpaces
          break

        case 'prices':
          f = refreshPrices
          break

        case 'effectivePrices':
          f = refreshEffectivePrices
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

    this.copyFrom = function(/*json|Space*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  Slot: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.id = source.slot_id
      _this.placeId = source.place_id
      _this.spaceId = source.space_id
      _this.name = _this.name || source.name
      _this.dateFrom = _this.date_from || source.date_from
      _this.dateTo = _this.date_to || source.date_to
      _this.timeFrom = _this.time_from || source.time_from
      _this.timeTo = _this.time_to || source.time_to
      _this.prices = _this.prices
      _this.effectivePrices = _this.effectivePrices
      _this.bookings = _this.bookings
      _this.activeBookings = _this.activeBookings
      _this.attributes = _this.attributes || source.attributes || {}
      _this.bookStatus = source.book_status
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

    /** get slot prices from API */
    function refreshPrices(/*bool*/ force, /*fn*/ callback) {
      if (!_this.prices || force) {

        sc.apiSlotsService.refreshPrices(_this.id,
          function(/*[Price]*/ prices) {
            _this.prices = prices
            _this.onChangeCallback.trigger('prices')
          },
          callback)

      } else callback('noop')
    }

    /** get slot effective prices from API  */
    function refreshEffectivePrices(/*bool*/ force, /*fn*/ callback) {
      if (!_this.effectivePrices || force) {

        sc.apiSlotsService.refreshPrices(_this.id,
          function(/*[Price]*/ prices) {
            _this.effectivePrices = prices
            _this.onChangeCallback.trigger('effectivePrices')
          },
          callback,
          { effective: '' })

      } else callback('noop')
    }

    /** get slot bookings from API */
    function refreshBookings(/*bool*/ force, /*fn*/ callback) {
      if (!_this.bookings || force) {

        sc.apiSlotsService.refreshBookings(_this.id,
          function(/*[Booking]*/ bookings) {
            _this.bookings = bookings
            _this.onChangeCallback.trigger('bookings')
          },
          callback)

      } else callback('noop')
    }

    /** get slot active bookings from API */
    function refreshActiveBookings(/*bool*/ force, /*fn*/ callback) {
      if (!_this.activeBookings || force) {

        sc.apiSlotsService.refreshBookings(_this.id,
          function(/*[Booking]*/ bookings) {
            _this.activeBookings = bookings
            _this.onChangeCallback.trigger('activeBookings')
          },
          callback,
          { active: '' })

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

        case 'effectivePrices':
          f = refreshEffectivePrices
          break

        case 'bookings':
          f = refreshBookings
          break

        case 'activeBookings':
          f = refreshActiveBookings
          break

        default:
          console.log('Unknown refresh target: '+target)
      }

      if (f) locks.exec(lock, f.bind(this, force), callback)
    }

    this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback) {
      sc.apiClassService.refreshRetry(_this, target, force, callback)
    }

    this.copyFrom = function(/*json|Slot*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  Price: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.id = source.price_id
      _this.placeId = source.place_id
      _this.spaceId = source.space_id

      // belongs to a Slot
      _this.slotId = source.slot_id

      _this.name = _this.name || source.name
      _this.amount = _this.amount || source.amount
      _this.currency = _this.currency || source.currency
      _this.memberLevel = _this.memberLevel || source.member_level || 0
      _this.attributes = _this.attributes || source.attributes || {}
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

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

    this.copyFrom = function(/*json|Price*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  Booking: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.id = source.booking_id
      _this.slotId = source.slot_id
      _this.profileId = source.profile_id
      _this.name = _this.name || source.name
      _this.status = _this.status || source.status
      _this.user = _this.user
      _this.attributes = _this.attributes || source.attributes || {}
      _this.price = _this.price
      _this.reference = _this.reference
      _this.referenceCancel = _this.referenceCancel
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

    /** get booking user from API */
    function refreshUser(/*bool*/ force, /*fn*/ callback) {
      if (!_this.user || force) {

        sc.apiUsersService.getUser(source.profile_id,
          function(/*User*/ user) {
            _this.user = user
            _this.onChangeCallback.trigger('user')
          },
          callback)

      } else callback('noop')
    }

    /** get booking price from API */
    function refreshPrice(/*bool*/ force, /*fn*/ callback) {
      if ((!_this.price || force) && _this.attributes.price_id) {

        sc.apiSlotsService.getPrice(_this.slotId, _this.attributes.price_id,
          function(/*Price*/ price) {
            _this.price = price
            _this.onChangeCallback.trigger('price')
          },
          callback)

          //todo price from space if not found on slot

      } else callback('noop')
    }

    /** get booking reference from API */
    function refreshReference(/*bool*/ force, /*fn*/ callback) {
      if ((!_this.reference || force) && _this.attributes.ref) {

        sc.apiPaymentsService.getReference(_this.attributes.ref, source.profile_id,
          function(/*Reference*/ reference) {
            _this.reference = reference
            _this.onChangeCallback.trigger('reference')
          },
          callback)

      } else callback('noop')
    }

    /** get booking reference from API */
    function refreshReferenceCancel(/*bool*/ force, /*fn*/ callback) {
      if ((!_this.referenceCancel || force) && _this.attributes.ref_cancel) {

        sc.apiPaymentsService.getReference(_this.attributes.ref_cancel, source.profile_id,
          function(/*Reference*/ reference) {
            _this.referenceCancel = reference
            _this.onChangeCallback.trigger('referenceCancel')
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

        case 'price':
          f = refreshPrice
          break

        case 'reference':
          f = refreshReference
          break

        case 'referenceCancel':
          f = refreshReferenceCancel
          break

        default:
          console.log('Unknown refresh target: '+target)
      }

      if (f) locks.exec(lock, f.bind(this, force), callback)
    }

    this.refreshRetry = function(/*str*/ target, /*bool*/ force, /*fn*/ callback, /*num*/ retries) {
      sc.apiClassService.refreshRetry(_this, target, force, callback, retries)
    }

    this.copyFrom = function(/*json|Booking*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  User: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.id = source.profile_id
      _this.username = _this.username || source.username
      _this.email = _this.email || source.email
      _this.roles = _this.roles || source.roles || []
      _this.metadata = _this.metadata || source.metadata || {}
      _this.attributes = _this.attributes || source.attributes || {}

      _this.fullName = (function() {
        var firstName = _this.attributes.first_name, lastName = _this.attributes.last_name
        return firstName || lastName ? (firstName ? firstName : '')+' '+(lastName ? lastName : '') : source.username
      })()
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

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

    this.copyFrom = function(/*json|User*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  Member: function(/*json*/ source, /*services*/ sc) {

    var _this = this

    this.sc = sc
    this.source = source

    this.onChangeCallback = new sb.classes.inner.Callbacks(_this)

    function applyChangesFromSource() {
      _this.profileId = source.profile_id
      _this.placeId = source.place_id
      _this.user = _this.user
      _this.level = _this.level || source.level || 0
    }

    applyChangesFromSource()

    var locks = new sb.classes.inner.Locks()

    /** get user from API (expands the source object) */
    function refreshUser(/*bool*/ force, /*fn*/ callback) {
      if (!_this.user || force) {

        sc.apiUsersService.getUser(source.profile_id,
          function(/*User*/ user) {
            _this.user = user
            _this.onChangeCallback.trigger('user')
          },
          callback)

      } else callback('noop')
    }

    function refreshThis(/*bool|redundant*/ force, /*fn*/ callback) {
      sc.apiMembersService.getPlaceMember(_this.placeId, _this.profileId,
        function(/*Member*/ member) {
          _this.copyFrom(member)
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

    this.copyFrom = function(/*json|Member*/ src) {
      var json = src.source ? src.source : src
      sb.utils.replaceInternals(source, json)
      applyChangesFromSource()
      _this.onChangeCallback.trigger('*')
    }

  },

  Quote: function(/*json*/ source, /*services*/ sc) { // read-only

    var _this = this

    this.sc = sc
    this.source = source

    this.id = source.quote_id
    this.profileId = source.profile_id
    this.status = source.status
    this.amount = source.amount
    this.currency = source.currency
    this.prices = (source.prices || []).map(function(/*json*/ p) { return new sb.classes.Price(p) })

    this.priceFor = function(/*str*/ slotId) {
      var found = $.grep(_this.prices, function(/*Price*/ price) { return price.slotId == slotId })
      return found.length ? found[0] : null
    }

  },

  Refund: function(/*json*/ source, /*services*/ sc) { // read-only

    this.sc = sc
    this.source = source

    this.id = source.refund_id
    this.profileId = source.profile_id
    this.status = source.status
    this.amount = source.amount
    this.currency = source.currency
    this.prices = (source.prices || []).map(function(/*json*/ p) { return new sb.classes.Price(p) })

    this.priceFor = function(/*str*/ slotId) {
      var found = $.grep(_this.prices, function(/*Price*/ price) { return price.slotId == slotId })
      return found.length ? found[0] : null
    }

  },

  Reference: function(/*json*/ source, /*services*/ sc) { // read-only

    this.sc = sc
    this.source = source

    this.id = source.reference_id
    this.profileId = source.profile_id
    this.ref = source.ref
    this.quote = source.quote ? new sb.classes.Quote(source.quote) : null
    this.refund = source.refund ? new sb.classes.Refund(source.refund) : null

  },

  Balance: function(/*json*/ source, /*services*/ sc) { // read-only

    var _this = this

    this.sc = sc
    this.source = source

    this.credit = source.credit || []

    this.creditIn = function(/*str*/ currency) {
      var arr = $.grep(_this.credit, function(c) { return c.currency == currency })
      return arr.length ? arr[0] : { currency: currency, amount: 0 }
    }

  },

  Account: function(/*json*/ source, /*services*/ sc) { // read-only

    var _this = this

    this.sc = sc
    this.source = source

    this.currencies = source.currencies || []

    this.currencyIn = function(/*str*/ currency) {
      var arr = $.grep(_this.currencies, function(c) { return c.currency == currency })
      return arr.length ? arr[0] : { currency: currency, attributes: {} }
    }

  }

}

sb.classes.inner = {

  Callbacks: function(/*obj*/ src) {

    var callbacks = {},
        index = 0

    this.add = function(/*fn*/ callback) {
      var handle = ++index+''
      callbacks[handle] = callback
      return handle
    }

    this.remove = function(/*str*/ handle) {
      if (!callbacks[handle]) console.log('No handle found to remove: '+handle)
      else delete callbacks[handle]
    }

    this.trigger = function(/*str*/ key, /*any*/ arg) {
      Object.values(callbacks).forEach(function(callback) {
        callback(key, src, arg)
      })
    }

  },

  Locks: function() {

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

}
