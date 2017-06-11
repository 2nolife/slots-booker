app.service('placesService', function(sb_apiPlacesService) {
  var service = this

  service.loadPlaces = function(/*bool*/ force, /*fn*/ callback) {
    if (force || !service.cachedPlaces) {
      sb_apiPlacesService.getPlaces(function(/*[Place]*/ places) {

        service.cachedPlaces = places.map(function(place) { return new cp.classes.EditedPlace(place) })
        callback(service.cachedPlaces)

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

})

app.service('bookingService', function(sb_apiBookingService, sb_apiPaymentsService) {
  var service = this

  function groupSlotsByProfile(/*[EditedSlot]*/ slots, /*fn*/ callback) {
    var group = {},
        n = slots.length

    slots.forEach(function(slot) {
      slot.refreshActiveBooking(false, function() {
        var profileId = (slot.activeBooking || {}).profileId
        if (profileId) (group[profileId] = group[profileId] || []).push(slot)
        if (--n == 0) callback(group)
      })
    })
  }

  service.creditReferences = function(/*[References]*/ references, /*fn*/ callback) {
    var n = references.length

    references.forEach(function(reference) {
      sb_apiPaymentsService.processReference({ ref: reference.ref, as_profile_id: reference.profileId }, function() {
        if (--n == 0) callback(references)
      })
    })
  }

  service.calculateRefund = function(/*[EditedSlot]*/ slots, /*fn*/ callback) {
    var refunds = [],
        n = slots.length

    groupSlotsByProfile(slots, function(/*{}*/ group) {

      Object.keys(group).forEach(function(/*str*/ profileId) {
        var grouped = group[profileId], /*[EditedSlot]*/
            slotIds = grouped.map(function(slot) { return slot.id })

        sb_apiBookingService.refund({ slot_ids: slotIds, as_profile_id: profileId }, function(/*Refund*/ refund) {
          refunds.push({
            refund: refund,
            slots: grouped,
            profileId: profileId
          })
          n -= grouped.length
          if (n == 0) callback(refunds)
        })

      })

    })
  }

  service.refundSlots = function(/*[Refund]*/ refunds, /*fn*/ callback) {
    var references = [],
        n = refunds.length

    refunds.forEach(function(refund) {
      sb_apiBookingService.cancel({ refund_id: refund.id, as_profile_id: refund.profileId }, function(/*Reference*/ reference) {
        references.push(reference)
        if (--n == 0) callback(references)
      })
    })
  }

  service.refundSlotsAndCredit = function(/*[Refund]*/ refunds, /*fn*/ referenceCallback, /*fn*/ creditCallback) {
    service.refundSlots(refunds, function(/*[Reference]*/ references) {
      if (referenceCallback) referenceCallback()
      service.creditReferences(references, function() {
        if (creditCallback) creditCallback()
      })
    })
  }

  service.calculateQuote = function(/*[EditedSlot]*/ slots, /*str*/ profileId, /*fn*/ callback) {
    var slotsAndPrices = slots.map(function(slot) {
      var price = slot.selectedPrice
      return price ? { slot_id: slot.id, price_id: price.id } : { slot_id: slot.id }
    })

    sb_apiBookingService.quote({ selected: slotsAndPrices, as_profile_id: profileId }, function(/*Quote*/ quote) {
      var hash = {
        quote: quote,
        slots: slots,
        profileId: profileId
      }
      callback(hash)
    })
  }

  service.bookSlots = function(/*Quote*/ quote, /*fn*/ callback) {
    sb_apiBookingService.book({ quote_id: quote.id, as_profile_id: quote.profileId }, function(/*Reference*/ reference) {
      callback(reference)
    })
  }

  service.bookSlotsWithCredit = function(/*Quote*/ quote, /*fn*/ referenceCallback, /*fn*/ creditCallback) {
    service.bookSlots(quote, function(/*Reference*/ reference) {
      if (referenceCallback) referenceCallback()
      service.creditReferences([reference], function() {
        if (creditCallback) creditCallback()
      })
    })
  }

})
