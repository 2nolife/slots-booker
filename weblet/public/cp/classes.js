var cp = cp || {}

cp.classes = {

  EditedPlace: function(/*Place*/ source) {

    var _this = this

    this.source = source

    this.onChangeCallback = source.onChangeCallback

    function applyChangesFromSource(/*str*/ key) {
      _this.id = source.id
      _this.name = source.name
      _this.attributes = source.attributes 
      _this.address = source.address || {}
      _this.owner = source.owner
      _this.moderators = source.moderators
      _this.owner = source.owner

      _this.template = ((_this.attributes || {}).prm0 || {}).template || 'default'

      makeAttributesArray()

      if (key == '*' || !_this.rid) _this.rid = Math.random()
      if (key == 'spaces') wrapSpaces()
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    function makeAttributesArray() {
      var a = _this.attributes,
          f = cp.classes.utils.attributeValueAsString
      _this.attributesArray = [ // same as "vo_attributes_place"
        { key: 'client_key',       name: 'Client key',       value: f(a.client_key),       type: 'text',  write: false },
        { key: 'external_key',     name: 'External key',     value: f(a.external_key),     type: 'text',  write: true  },
        { key: 'url',              name: 'URL',              value: f(a.url),              type: 'text',  write: true  },
        { key: 'email',            name: 'Email',            value: f(a.email),            type: 'email', write: true  },
        { key: 'primary_number',   name: 'Primary number',   value: f(a.primary_number),   type: 'text',  write: true  },
        { key: 'negative_balance', name: 'Negative balance', value: f(a.negative_balance), type: 'bool',  write: true  },
        { key: 'prm0', name: 'Parameter 0', value: f(a.prm0), type: 'text', write: true  },
        { key: 'prm1', name: 'Parameter 1', value: f(a.prm1), type: 'text', write: true  }
      ]
    }

    this.refresh = function(/*fn*/ callback) {
      source.refresh('*', true, callback)
    }

    this.toApiAddressEntity = function() {
      return {
        address: {
          line1: _this.address.line1,
          line2: _this.address.line2,
          line3: _this.address.line3,
          postcode: _this.address.postcode,
          town: _this.address.town,
          country: _this.address.country
        }
      }
    }

    this.toApiAttributesEntity = function() {
      return cp.classes.utils.toApiAttributesEntity(_this.attributesArray)
    }

    this.refreshSpaces = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('spaces', force, callback)
    }

    function wrapSpaces() {
      var spaces = source.spaces.map(function(space) { return new cp.classes.EditedSpace(space) })
      spaces.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
      _this.spaces = spaces
      _this.onChangeCallback.trigger('spaces-wrap', _this)
    }

  },

  EditedSpace: function(/*Space*/ source, /*EditedSpace*/ parentSpace) {

    var _this = this

    this.source = source
    this.parentSpace = parentSpace

    this.onChangeCallback = source.onChangeCallback

    function applyChangesFromSource(/*str*/ key) {
      _this.id = source.id
      _this.placeId = source.placeId
      _this.parentSpaceId = source.parentSpaceId
      _this.name = source.name
      _this.parentSpaces = getParentSpaces()
      _this.attributes = source.attributes

      _this.template = ((_this.attributes || {}).prm0 || {}).template || 'default'

      makeAttributesArray()

      if (key == '*' || !_this.rid) _this.rid = Math.random()
      if (key == 'spaces') wrapSpaces()
      if (key == 'prices') wrapPrices()
      if (key == 'effectivePrices') wrapEffectivePrices()
      if (key == 'slots') wrapSlots()
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    function getParentSpaces() {
      var parents = [],
          parent = _this.parentSpace
      while (parent) {
        parents.push(parent)
        parent = parent.parentSpace
      }
      return parents.reverse()
    }

    function makeAttributesArray() {
      var a = _this.attributes,
          f = cp.classes.utils.attributeValueAsString
      _this.attributesArray = [ // same as "vo_attributes_space"
        { key: 'prm0', name: 'Parameter 0', value: f(a.prm0), type: 'text', write: true  },
        { key: 'prm1', name: 'Parameter 1', value: f(a.prm1), type: 'text', write: true  }
      ]
    }

    this.refreshPrices = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('prices', force, callback)
    }

    this.refreshEffectivePrices = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('effectivePrices', force, callback)
    }

    function wrapPrices() {
      var prices = source.prices.map(function(price) { return new cp.classes.EditedPrice(price) })
      prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
      _this.prices = prices
      _this.onChangeCallback.trigger('prices-wrap', _this)
    }

    function wrapEffectivePrices() {
      var prices = source.effectivePrices.map(function(price) { return new cp.classes.EditedPrice(price) })
      prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
      _this.effectivePrices = prices
      _this.onChangeCallback.trigger('effective-prices-wrap', _this)
    }

    this.refreshSpaces = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('spaces', force, callback)
    }

    function wrapSpaces() {
      var spaces = source.spaces.map(function(space) { return new cp.classes.EditedSpace(space, _this) })
      spaces.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
      _this.spaces = spaces
      _this.onChangeCallback.trigger('spaces-wrap', _this)
    }

    this.refreshSlotsByDateTime = function(/*str|num*/ date, /*str|num*/ time, /*fn*/ callback) {
      slotsByDateTime(date, time, callback)
    }

    this.refreshSlotsByDate = function(/*str|num*/ from, /*str|num*/ to, /*bool*/ force, /*fn*/ callback) {
      source.slotsFilter = { from: from ? parseInt(from) : sb.utils.todayDate(), to: to ? parseInt(to) : sb.utils.todayDate() }
      source.refreshRetry('slots', force, callback)
    }

    function wrapSlots() {
      var slots = source.slots.map(function(slot) { return new cp.classes.EditedSlot(slot, _this) })
      _this.slots = slots
      _this.onChangeCallback.trigger('slots-wrap', _this)
    }

    this.refresh = function(/*fn*/ callback) {
      source.refresh('*', true, callback)
    }

    this.toApiAttributesEntity = function() {
      return cp.classes.utils.toApiAttributesEntity(_this.attributesArray)
    }

  },

  EditedPrice: function(/*Price*/ source) {

    var _this = this

    source = source ? source : new sb.classes.Price({})
    this.source = source

    function applyChangesFromSource() {
      _this.id = source.id
      _this.placeId = source.placeId
      _this.spaceId = source.spaceId // belongs to Space
      _this.slotId = source.slotId // belongs to Slot
      _this.name = source.name
      _this.amount = source.amount
      _this.currency = source.currency
      _this.attributes = source.attributes
      _this.memberLevel = source.memberLevel

      makeAttributesArray()
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    function makeAttributesArray() {
      var a = _this.attributes,
          f = cp.classes.utils.attributeValueAsString
      _this.attributesArray = [ // same as "vo_attributes_space"
        { key: 'prm0', name: 'Parameter 0', value: f(a.prm0), type: 'text', write: true  },
        { key: 'prm1', name: 'Parameter 1', value: f(a.prm1), type: 'text', write: true  }
      ]
    }

    this.toApiEntity = function() {
      return {
        name: _this.name,
        amount: _this.amount,
        currency: _this.currency,
        member_level: parseInt(_this.memberLevel),
        attributes: cp.classes.utils.toApiAttributesEntity(_this.attributesArray).attributes
      }
    }

  },

  NewPlace: function() {

    var _this = this

    this.name = null
    this.template = null

    this.toApiEntity = function() {
      var entity = { name: _this.name }
      if (_this.template) entity.attributes = { prm0: { template: _this.template }}
      return entity
    }

  },

  NewSpace: function() {

    var _this = this

    this.name = null
    this.template = null

    this.toApiEntity = function() {
      var entity = { name: _this.name }
      if (_this.template) entity.attributes = { prm0: { template: _this.template }}
      return entity
    }

  },

  EditedSlot: function(/*Slot*/ source) {

    var _this = this

    this.source = source

    this.onChangeCallback = source.onChangeCallback

    function applyChangesFromSource(/*str*/ key) {
      _this.id = source.id
      _this.placeId = source.placeId
      _this.spaceId = source.spaceId
      _this.name = source.name
      _this.dateFrom = source.dateFrom
      _this.dateTo = source.dateTo
      _this.timeFrom = source.timeFrom
      _this.timeTo = source.timeTo
      _this.formatted = formattedDateTime()
      _this.attributes = source.attributes
      _this.bookStatus = source.bookStatus

      makeAttributesArray()

      if (key == '*' || !_this.rid) _this.rid = Math.random()
      if (key == 'prices') wrapPrices()
      if (key == 'effectivePrices') wrapEffectivePrices()
      if (key == 'bookings') wrapBookings()
      if (key == 'activeBookings') wrapActiveBooking()
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    function formattedDateTime() {
      return {
        dateFrom: sb.utils.formatDate(_this.dateFrom),
        dateTo: sb.utils.formatDate(_this.dateTo),
        timeFrom: sb.utils.formatTime(_this.timeFrom),
        timeTo: sb.utils.formatTime(_this.timeTo),
        weekday: sb.utils.weekdayAsWord(_this.dateFrom)
      }
    }

    function makeAttributesArray() {
      var a = _this.attributes,
          f = cp.classes.utils.attributeValueAsString
      _this.attributesArray = [ // same as "vo_attributes_space"
        { key: 'prm0', name: 'Parameter 0', value: f(a.prm0), type: 'text', write: true  },
        { key: 'prm1', name: 'Parameter 1', value: f(a.prm1), type: 'text', write: true  }
      ]
    }

    this.refreshPrices = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('prices', force, callback)
    }

    this.refreshEffectivePrices = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('effectivePrices', force, callback)
    }

    function wrapPrices() {
      var prices = source.prices.map(function(price) { return new cp.classes.EditedPrice(price) })
      prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
      _this.prices = prices
      _this.onChangeCallback.trigger('prices-wrap', _this)
    }

    function wrapEffectivePrices() {
      var prices = source.effectivePrices.map(function(price) { return new cp.classes.EditedPrice(price) })
      prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
      _this.effectivePrices = prices
      _this.onChangeCallback.trigger('effective-prices-wrap', _this)
    }

    this.refreshBookings = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('bookings', force, callback)
    }

    function wrapBookings() {
      var bookings = source.bookings.map(function(booking) { return new cp.classes.EditedBooking(booking) })
      _this.bookings = bookings.reverse()
      _this.onChangeCallback.trigger('bookings-wrap', _this)
    }

    this.refreshActiveBooking = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('activeBookings', force, callback)
    }

    function wrapActiveBooking() {
      _this.activeBooking = source.activeBookings.length ? new cp.classes.EditedBooking(source.activeBookings[0]) : null
      _this.onChangeCallback.trigger('active-booking-wrap', _this)
    }

    this.refresh = function(/*fn*/ callback) {
      source.refresh('*', true, callback)
    }

    this.toApiAttributesEntity = function() {
      return cp.classes.utils.toApiAttributesEntity(_this.attributesArray)
    }

  },

  NewSlot: function(/*str*/ placeId, /*str*/ spaceId) {

    var _this = this

    this.placeId = placeId
    this.spaceId = spaceId
    this.name = null
    this.dateFrom = null
    this.dateTo = null
    this.timeFrom = null
    this.timeTo = null
    this.attributes = null
    this.prices = null

    this.toApiEntity = function() {
      var entity = {
        place_id: _this.placeId,
        space_id: _this.spaceId,
        name: _this.name,
        date_from: _this.dateFrom,
        date_to: _this.dateTo,
        time_from: _this.timeFrom,
        time_to: _this.timeTo
      }
      if (_this.attributes) entity.attributes = _this.attributes
      return entity
    }

  },

  NewSlotPrice: function(/*str*/ slotId) {

    var _this = this

    this.slotId = slotId
    this.name = null
    this.amount = null
    this.currency = null
    this.attributes = null

    this.toApiEntity = function() {
      var entity = {
        name: _this.name,
        amount: _this.amount,
        currency: _this.currency
      }
      if (_this.attributes) entity.attributes = _this.attributes
      return entity
    }

  },

  NewSlotSchedule: function(/*str*/ placeId, /*str*/ spaceId, sb_apiSlotsService) {

    var _this = this

    this.placeId = placeId
    this.spaceId = spaceId
    this.name = null
    this.dateFrom = null /*num*/
    this.dateTo = null /*num*/
    this.timeFrom = null /*num*/
    this.timeTo = null /*num*/
    this.timePeriod = null // minutes

    this.newSlots = null
    this.progress = {
      now: 0,
      total: 0,
      percent: 0
    }

    function makeNewSlots() {
      var slots = [],
          curDate = _this.dateFrom
      while (curDate <= _this.dateTo) {

        var curTime = _this.timeFrom
        while (curTime < _this.timeTo) {

          var plusTime = parseInt(sb.utils.addMinutesTime(curTime, _this.timePeriod))
          if (plusTime <= curTime || plusTime == 0000) plusTime = 2400

          var slot = new cp.classes.NewSlot(_this.placeId, _this.spaceId)
          slot.name = _this.name
          slot.dateFrom = slot.dateTo = curDate
          slot.timeFrom = curTime
          slot.timeTo = plusTime
          slots.push(slot)

          curTime = plusTime
        }

        curDate = parseInt(sb.utils.addDaysDate(curDate, 1))
      }

      return slots
    }

    this.ready = function() {
      _this.newSlots = makeNewSlots()
      _this.progress.total = _this.newSlots.length
    }

    this.createNewSlots = function(/*fn*/ callback) {
      var n = _this.progress.total
      _this.newSlots.forEach(function(/*NewSlot*/ newSlot) {
        sb_apiSlotsService.addSlot(newSlot.toApiEntity(), function() {
          _this.progress.now++
          _this.progress.percent = sb.utils.percent(_this.progress.now, _this.progress.total)
          if (--n == 0) callback()
        })
      })
    }

  },

  SlotCopyPaste: function(sb_apiSlotsService) {

    var _this = this

    this.base = 'day' // day|week|range
    this.copy = {
      space: null, /*Space*/
      dateFrom: null, /*num*/
      dateTo: null /*num*/
    }
    this.paste = {
      spaces: null, /*[Space]*/
      dateFrom: null, /*num*/
      dateTo: null /*num*/
    }

    this.newSlots = null
    this.progress = {
      now: 0,
      total: 0,
      percent: 0,
      created: 0,
      updated: 0
    }

    function fetchSlotsByDate(/*str*/ placeId, /*str*/ spaceId, /*num*/ dateFrom, /*num*/ dateTo, /*fn*/ callback) {
      var searchOptions = {
        placeId: placeId,
        spaceId: spaceId,
        from: dateFrom,
        to: dateTo
      }
      sb_apiSlotsService.findSlots(searchOptions,
        function(/*[Slot]*/ slots) {
          var n = slots.length
          if (n == 0) callback([])
          slots.forEach(function(slot) {
            slot.refresh('prices', true, function() {
              if (--n == 0) callback(slots)
            })
          })
        })
    }

    function filterSlotsByDate(/*num*/ date, /*Slot|NewSlot*/ slots) {
      return $.grep(slots, function(slot) { return slot.dateFrom == date })
    }

    function filterSlotsByTime(/*num*/ time, /*Slot|NewSlot*/ slots) {
      return $.grep(slots, function(slot) { return slot.timeFrom == time })
    }

    function filterSlotsByWeekday(/*num*/ date, /*Slot|NewSlot*/ slots) {
      var weekday = sb.utils.weekdayAsWord(date),
          mixed = $.grep(slots, function(slot) { return sb.utils.weekdayAsWord(slot.dateFrom) == weekday }),
          weekdayDate = mixed.length ? mixed[0].dateFrom : null
      return filterSlotsByDate(weekdayDate, slots)
    }

    function makeNewSlots(/*[Slot]*/ srcSlots) {
      var trgSlots = [],
          srcDate = _this.copy.dateFrom,
          trgDate = _this.paste.dateFrom

      while (trgDate <= _this.paste.dateTo) {
        var daySlots = []
        if (_this.base == 'day') daySlots = filterSlotsByDate(srcDate, srcSlots)
        if (_this.base == 'week') daySlots = filterSlotsByWeekday(trgDate, srcSlots)
        if (_this.base == 'range') {
          daySlots = filterSlotsByDate(srcDate, srcSlots)
          srcDate = parseInt(sb.utils.addDaysDate(srcDate, 1))
          if (srcDate > _this.copy.dateTo) srcDate = _this.copy.dateFrom
        }

        daySlots.forEach(function(srcSlot) {
          var slot = new cp.classes.NewSlot()
          slot.name = srcSlot.name
          slot.dateFrom = slot.dateTo = trgDate
          slot.timeFrom = srcSlot.timeFrom
          slot.timeTo = srcSlot.timeTo
          slot.attributes = srcSlot.attributes
          slot.prices = srcSlot.prices.map(function(/*Price*/ srcPrice) {
            var price = new cp.classes.NewSlotPrice()
            price.name = srcPrice.name
            price.amount = srcPrice.amount
            price.currency = srcPrice.currency
            price.attributes = srcPrice.attributes
            return price
          })
          trgSlots.push(slot)
        })

        trgDate = parseInt(sb.utils.addDaysDate(trgDate, 1))
      }

      return trgSlots
    }

    function createOrUpdateSlot(/*Space*/ space, /*NewSlot*/ newSlot, /*Slot*/ oldSlot, /*fn*/ callback) {
      var f = oldSlot ?
        sb_apiSlotsService.patchSlot.bind(null, oldSlot.id) :
        sb_apiSlotsService.addSlot.bind(null)
      var entity = $.extend(true, newSlot.toApiEntity(), { place_id: space.placeId, space_id: space.id })

      f(entity, function(/*Slot*/ slot) {
        if (oldSlot) _this.progress.updated++
        else _this.progress.created++

        if (oldSlot)
          oldSlot.prices.forEach(function(/*Price*/ price) {
            sb_apiSlotsService.deletePrice(price.slotId, price.id, function() {})
          })

        newSlot.prices.forEach(function(/*NewSlotPrice*/ price) {
          sb_apiSlotsService.addPrice(slot.id, price.toApiEntity(), function() {})
        })

        callback()
      })
    }

    this.ready = function(/*fn*/ callback) {
      fetchSlotsByDate(_this.copy.space.placeId, _this.copy.space.id, _this.copy.dateFrom, _this.copy.dateTo, function(/*[Slot]*/ slots) {
        var trgSpaces = $.grep(_this.paste.spaces, function(/*Space*/ space) { return space.selected })
        _this.newSlots = makeNewSlots(slots)
        _this.progress.total = _this.newSlots.length * trgSpaces.length
        callback()
      })
    }

    this.createNewSlots = function(/*fn*/ callback) {
      var trgSlots = _this.newSlots,
          trgDate = _this.paste.dateFrom,
          trgSpaces = $.grep(_this.paste.spaces, function(/*Space*/ space) { return space.selected }),
          n = _this.progress.total

      while (trgDate <= _this.paste.dateTo) {

        (function scope() {
          var daySlots = filterSlotsByDate(trgDate, trgSlots)
          trgSpaces.forEach(function(/*Space*/ trgSpace) {
            fetchSlotsByDate(trgSpace.placeId, trgSpace.id, trgDate, trgDate, function(/*[Slot]*/ oldSlots) {

              daySlots.forEach(function(/*NewSlot*/ newSlot) {
                var slots = filterSlotsByTime(newSlot.timeFrom, oldSlots),
                    oldSlot = slots.length ? slots[0] : null
                createOrUpdateSlot(trgSpace, newSlot, oldSlot, function() {
                  _this.progress.now++
                  _this.progress.percent = sb.utils.percent(_this.progress.now, _this.progress.total)
                  if (--n == 0) callback()
                })
              })

            })
          })
        })()

        trgDate = parseInt(sb.utils.addDaysDate(trgDate, 1))
      }
    }

  },

  SlotsCalendar: function(/*num*/ dateFrom, /*num*/ dateTo, /*[Slot]*/ slots) {

    var _this = this

    this.from = parseInt(sb.utils.weekFirstDate(dateFrom)) // start of the week
    this.to = parseInt(sb.utils.weekLastDate(dateTo)) // end of the week
    this.weeks = []

    {
      var group = {},
          weeks = [],
          curDate = _this.from

      slots.forEach(function(slot) {
        group[slot.dateFrom] = group[slot.dateFrom] || []
        group[slot.dateFrom].push(slot)
      })

      while (curDate <= _this.to) {
        if (curDate == parseInt(sb.utils.weekFirstDate(curDate))) weeks.push([])
        weeks[weeks.length-1].push({ date: curDate, slots: group[curDate] || [], include: dateInRange(curDate) })
        curDate = parseInt(sb.utils.addDaysDate(curDate, 1))
      }

      _this.weeks = weeks
    }

    function dateInRange(/*num*/ date) {
      return date >= dateFrom && date <= dateTo
    }

  },

  EditedBooking: function(/*Booking*/ source) {

    var _this = this

    this.source = source

    this.onChangeCallback = source.onChangeCallback

    function applyChangesFromSource() {
      _this.id = source.id
      _this.profileId = source.profileId
      _this.user = source.user
      _this.reference = source.reference
      _this.status = source.status
      _this.attributes = source.attributes
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    this.refreshUser = function() {
      source.refresh('user')
    }

    this.refreshReference = function() {
      source.refresh('reference')
    }

  },

  EditedUser: function(/*Booking*/ source) {

    var _this = this

    this.sc = source.sc
    this.source = source

    this.onChangeCallback = source.onChangeCallback

    function applyChangesFromSource(/*str*/ key) {
      _this.id = source.id
      _this.username = source.username
      _this.fullName = source.fullName
      _this.attributes = source.attributes

      if (key == '*' || !_this.rid) {
        _this.rid = Math.random()
        delete _this.members
        delete _this.balances
      }

      _this.members = _this.members || {}
      _this.balances = _this.balances || {}
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    this.refresh = function(/*fn*/ callback) {
      source.refresh('*', true, callback)
    }

    this.memberFor = function(/*str*/ placeId, /*bool*/ force, /*fn*/ callback) {
      if (force) delete _this.members[placeId]
      if (!_this.members[placeId])
        _this.sc.apiMembersService.getPlaceMember(placeId, _this.id, function(/*Member*/ member) {
          _this.members[placeId] = member
          if (callback) callback()
        })
      else if (callback) callback()
    }

    this.balanceFor = function(/*str*/ placeId, /*bool*/ force, /*fn*/ callback) {
      if (force) delete _this.balances[placeId]
      if (!_this.balances[placeId])
        _this.sc.apiPaymentsService.getUserBalance(placeId, _this.id, function(/*Balance*/ balance) {
          _this.balances[placeId] = balance
          if (callback) callback()
        })
      else if (callback) callback()
    }

  }

}

cp.classes.utils = {

  toApiAttributesEntity: function(/*[{?}]*/ attributesArray) {
    var attrs = {},
        f = cp.classes.utils.attributeValueAsJson
    $.grep(attributesArray, function(a) { return a.write })
      .forEach(function(a) { attrs[a.key] = f(a.value) })
    return {
      attributes: attrs
    }
  },

  attributeValueAsString: function(/*any*/ value) {
    return typeof value === 'object' ? JSON.stringify(value) : value
  },

  attributeValueAsJson: function(/*any*/ value) {
    return ($.trim(value)+' ')[0] == "{" ? JSON.parse(value) : value
  }

}
