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

      _this.url = _this.attributes.url
      _this.email = _this.attributes.email
      _this.primaryNumber = _this.attributes.primary_number
      _this.clientKey = _this.attributes.client_key
      _this.externalKey = _this.attributes.external_key
      _this.negativeBalance = _this.attributes.negative_balance

      makeAttributesArray()

      if (key == '*') {
        delete _this.spaces
      }
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
        { key: 'negative_balance', name: 'Negative balance', value: f(a.negative_balance), type: 'bool',  write: true  }
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
      source.refresh('spaces', force, wrapSpaces.bind(null, force, callback))
    }

    function wrapSpaces(/*bool*/ force, /*fn*/ callback, /*str*/ status) {
      if (sb.utils.apiStatusOK(status) && (force || !(_this.spaces || []).length)) {
        var spaces = (source.spaces || []).map(function(space) { return new cp.classes.EditedSpace(space) })
        spaces.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
        _this.spaces = spaces
        _this.onChangeCallback.trigger('spaces-loaded', _this)
      }
      if (callback) callback()
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

      makeAttributesArray()

      if (key == '*') {
        delete _this.spaces
        delete _this.prices
        delete _this.slots
      }
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
      source.refresh('prices', force, wrapPrices.bind(null, force, callback))
    }

    function wrapPrices(/*bool*/ force, /*fn*/ callback, /*str*/ status) {
      if (sb.utils.apiStatusOK(status) && (force || !(_this.prices || []).length)) {
        var prices = (source.prices || []).map(function(price) { return new cp.classes.EditedPrice(price) })
        prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
        _this.prices = prices
        _this.onChangeCallback.trigger('prices-loaded', _this)
      }
      if (callback) callback()
    }

    this.refreshSpaces = function(/*bool*/ force, /*fn*/ callback) {
      source.refresh('spaces', force, wrapSpaces.bind(null, force, callback))
    }

    function wrapSpaces(/*bool*/ force, /*fn*/ callback, /*str*/ status) {
      if (sb.utils.apiStatusOK(status) && (force || !(_this.spaces || []).length)) {
        var spaces = (source.spaces || []).map(function(space) { return new cp.classes.EditedSpace(space, _this) })
        spaces.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
        _this.spaces = spaces
        _this.onChangeCallback.trigger('spaces-loaded', _this)
      }
      if (callback) callback()
    }

    this.refreshSlotsByDateTime = function(/*str|num*/ date, /*str|num*/ time, /*fn*/ callback) {
      slotsByDateTime(date, time, callback)
    }

    this.refreshSlotsByDate = function(/*str|num*/ from, /*str|num*/ to, /*bool*/ force, /*fn*/ callback) {
      source.slotsFilter = { from: from ? parseInt(from) : sb.utils.todayDate(), to: to ? parseInt(to) : sb.utils.todayDate() }
      source.refreshRetry('slots', force, function(/*str*/ status) {
        wrapSlots(force, callback, status)
      })
    }

    function wrapSlots(/*bool*/ force, /*fn*/ callback, /*str*/ status) {
      if (sb.utils.apiStatusOK(status) && (force || !(_this.slots || []).length)) {
        var slots = (source.slots || []).map(function(slot) { return new cp.classes.EditedSlot(slot, _this) })
        _this.slots = slots
        _this.onChangeCallback.trigger('slots-loaded', _this)
      }
      if (callback) callback()
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
        attributes: cp.classes.utils.toApiAttributesEntity(_this.attributesArray).attributes
      }
    }

  },

  NewSpace: function() {

    var _this = this

    this.name = null

    this.toApiEntity = function() {
      return {
        name: _this.name
      }
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

      if (key == '*') {
        delete _this.prices
      }
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    function formattedDateTime() {
      return {
        dateFrom: sb.utils.formatDate(_this.dateFrom),
        dateTo: sb.utils.formatDate(_this.dateTo),
        timeFrom: sb.utils.formatTime(_this.timeFrom),
        timeTo: sb.utils.formatTime(_this.timeTo)
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
      source.refresh('prices', force, wrapPrices.bind(null, force, callback))
    }

    function wrapPrices(/*bool*/ force, /*fn*/ callback, /*str*/ status) {
      if (sb.utils.apiStatusOK(status) && (force || !(_this.prices || []).length)) {
        var prices = (source.prices || []).map(function(price) { return new cp.classes.EditedPrice(price) })
        prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name < b.name ? 1 : 0 })
        _this.prices = prices
        _this.onChangeCallback.trigger('prices-loaded', _this)
      }
      if (callback) callback()
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

    this.toApiEntity = function() {
      return {
        place_id: _this.placeId,
        space_id: _this.spaceId,
        name: _this.name,
        date_from: _this.dateFrom,
        date_to: _this.dateTo,
        time_from: _this.timeFrom,
        time_to: _this.timeTo
      }
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
