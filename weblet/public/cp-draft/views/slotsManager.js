app.service('slotsManagerService', function(apiSlotsService, $timeout) {
  var service = this

  //todo service placeholder

})

app.directive('slotsManager', function(slotsManagerService, apiSlotsService, apiBookingService, notifyService) {

  var controller = function($scope) {

    function EditedSpace(/*Space*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.placeId = source.placeId
        _this.parentSpaceId = source.parentSpaceId
        _this.name = source.name
        _this.slots = source.slots || []
        _this.slotsFilter = source.slotsFilter || {}

        source.refresh('slots', false, wrapSlots)

        //updateDateFilter()
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function wrapSlots() {
        _this.slots = (source.slots || []).map(function(slot) { return new EditedSlot(slot) })
      }

      function updateDateFilter() {
        var src = _this.slotsFilter, trg = $scope.dateFilter
        trg.from = src.from ? formatDate(src.from) : null
        trg.to = src.to ? formatDate(src.to) : null
      }

      this.refreshSlots = function(/*fn*/ callback) {
        source.refresh('slots', true, callback)
      }

      this.slotsByDate = function(/*json*/ dateFilter, /*bool*/ force) {
        source.slotsFilter.from = dateFilter.from
        source.slotsFilter.to = dateFilter.to
        source.refreshRetry('slots', force)
      }

      this.refresh = function(/*fn*/ callback) {
        source.refresh('*', true, callback)
      }

    }

    function NewSlot(/*str*/ placeId, /*str*/ spaceId) {

      var _this = this

      var source = new Slot({ place_id: placeId, space_id: spaceId })
      this.source = source

      function applyChangesFromSource() {
        _this.placeId = source.placeId
        _this.spaceId = source.spaceId
        _this.name = source.name
        _this.dateFrom = source.dateFrom
        _this.dateTo = source.dateTo
        _this.timeFrom = source.timeFrom
        _this.timeTo = source.timeTo
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.updateFromDateFilter = function() {
        source.dateFrom = parseDateFilter().from
        source.dateTo = parseDateFilter().to
        source.applyChangesToSource()
      }

      this.toApiSlotEntity = function() {
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

    function EditedSlot(/*Slot*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.placeId = source.placeId
        _this.spaceId = source.spaceId
        _this.name = source.name
        _this.dateFrom = source.dateFrom
        _this.dateTo = source.dateTo
        _this.timeFrom = source.timeFrom
        _this.timeTo = source.timeTo
        _this.dateFromFormatted = formatDate(_this.dateFrom)
        _this.dateToFormatted = formatDate(_this.dateTo)
        _this.timeFromFormatted = formatTime(_this.timeFrom)
        _this.timeToFormatted = formatTime(_this.timeTo)
        _this.prices = source.prices || []
        _this.bookings = source.bookings || []

        source.refresh('prices')
        source.refresh('bookings', false, wrapBookings)

      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function wrapBookings() {
        _this.bookings = (source.bookings || []).map(function(booking) { return new EditedBooking(booking) })
      }

      this.refreshPrices = function() {
        source.refresh('prices', true)
      }

      this.refreshBookings = function() {
        source.refresh('bookings', true)
      }

      this.refresh = function(/*fn*/ callback) {
        source.refresh('*', true, callback)
      }

      this.toApiSlotEntity = function() {
        return {
          name: _this.name,
          date_from: safeParseInt(parseDate(_this.dateFromFormatted)),
          date_to: safeParseInt(parseDate(_this.dateToFormatted)),
          time_from: safeParseInt(parseTime(_this.timeFromFormatted)),
          time_to: safeParseInt(parseTime(_this.timeToFormatted))
        }
      }

    }

    function EditedPrice(/*Price|optional*/ source) {

      var _this = this

      source = source ? source : new Price({})
      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.slotId = source.slotId
        _this.name = source.name
        _this.amount = source.amount
        _this.currency = source.currency
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.toApiPriceEntity = function() {
        return {
          name: _this.name,
          amount: _this.amount,
          currency: _this.currency
        }
      }

    }

    function BookSlot(/*str*/ slotId) {

      var _this = this

      _this.slotId = slotId
      _this.user = null
      _this.attributes = JSON.stringify({}, null, 4)

      this.toApiEntity = function() {
        var entity = {
          slot_ids: [_this.slotId],
          profile_id: _this.user.id
        }

        if (_this.attributes) entity.attributes = JSON.parse(_this.attributes)

        return entity
      }

    }

    function EditedBooking(/*Booking*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.slotId = source.slotId
        _this.name = source.name
        _this.status = source.status
        _this.user = source.user
        _this.attributes = JSON.stringify(source.attributes, null, 4)

        _this.statusText = (function() {
          return _this.status == 0 ? 'Inactive' : _this.status == 1 ? 'Active' : 'Other ('+_this.status+')'
        })()

        source.refresh('user')
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.refresh = function(/*fn*/ callback) {
        source.refresh('*', true, callback)
      }

      this.toApiBookingEntity = function() {
        var entity = {
          name: _this.name
        }

        if (_this.attributes) entity.attributes = JSON.parse(_this.attributes)

        return entity
      }

      this.toApiCancelEntity = function() {
        return {
          slot_ids: [_this.slotId],
          profile_id: _this.user.id
        }
      }

    }

    var findSlotByIdToEdit = function(/*str*/ slotId) {
      delete $scope.editedSlot
      delete $scope.editedBooking
      delete $scope.newBooking

      var slots = $.grep($scope.editedSpace.slots, function(slot) { return slot.id == slotId })
      if (slots.length > 0) $scope.editedSlot = slots[0]

      return $scope.editedSlot
    }

    var findPriceByIdToEdit = function(/*str*/ priceId) {
      delete $scope.editedPrice

      var prices = $.grep($scope.editedSlot.prices, function(price) { return price.id == priceId })
      if (prices.length > 0) $scope.editedPrice = new EditedPrice(prices[0])

      return $scope.editedPrice
    }

    var findBookingByIdToEdit = function(/*str*/ bookingId) {
      delete $scope.editedBooking
      delete $scope.newBooking

      var bookings = $.grep($scope.editedSlot.bookings, function(booking) { return booking.id == bookingId })
      if (bookings.length > 0) $scope.editedBooking = bookings[0]

      return $scope.editedBooking
    }

    var parseDateFilter = function() {
      return {
        from: parseDate($scope.dateFilter.from || '') || todayDate(),
        to: parseDate($scope.dateFilter.to || '') || todayDate()
      }
    }

    $scope.dateFilter = { from: formatDate(todayDate()), to: formatDate(todayDate()) }

    $scope.changeDateFilter = function(/*str*/ action) {
      var parsedDateFilter = parseDateFilter()
      var fromDate = parsedDateFilter.from, toDate = parsedDateFilter.to
      var day = parsedDateFilter.from, mid = monthMidDate(day), today = todayDate(), todayMid = monthMidDate(today)

      if (action == 'day') fromDate = toDate = today
      if (action == 'day-1') fromDate = toDate = addDaysDate(day, -1)
      if (action == 'day+1') fromDate = toDate = addDaysDate(day, +1)
      if (action == 'month') fromDate = monthFirstDate(todayMid), toDate = monthLastDate(todayMid)
      if (action == 'month-1') fromDate = monthFirstDate(addDaysDate(mid, -30)), toDate = monthLastDate(addDaysDate(mid, -30))
      if (action == 'month+1') fromDate = monthFirstDate(addDaysDate(mid, +30)), toDate = monthLastDate(addDaysDate(mid, +30))

      $scope.dateFilter.from = formatDate(fromDate)
      $scope.dateFilter.to = formatDate(toDate)
      $scope.applyDateFilter()
    }

    $scope.applyDateFilter = function() {
      $scope.editedSpace.slotsByDate(parseDateFilter(), true)
    }

    $scope.showEditSlotFragment = function(/*str*/ slotId) {
      findSlotByIdToEdit(slotId)
    }

    $scope.showAddSlotFragment = function() {
      $scope.dialogOptions = {
        onSubmit: submitNewSlot
      }
      var slot = new NewSlot($scope.editedSpace.placeId, $scope.editedSpace.id)
      slot.updateFromDateFilter()
      $scope.newSlot = slot
    }

    $scope.submitEditedSlot = function() {
      apiSlotsService.patchSlot($scope.editedSlot.id, $scope.editedSlot.toApiSlotEntity(), function(/*Slot*/ slot) {
        notifyService.notify('Slot details updated', 'success')
        $scope.editedSlot.refresh()
      })
    }

    var submitNewSlot = function() {
      apiSlotsService.addSlot($scope.newSlot.toApiSlotEntity(), function(/*Slot*/ slot) {
        notifyService.notify('New Slot added', 'success')
        var editNewSlot = function() {
          findSlotByIdToEdit(slot.id)
        }
        $scope.editedSpace.refreshSlots(editNewSlot)
      })
    }

    $scope.deleteEditedSlot = function() {
      apiSlotsService.deleteSlot($scope.editedSlot.id, function() {
        notifyService.notify('Slot deleted', 'success')
        delete $scope.editedSlot
        delete $scope.editedBooking
        delete $scope.newBooking
        $scope.editedSpace.refreshSlots()
      })
    }

    $scope.showEditPriceFragment = function(/*str*/ priceId) {
      $scope.dialogOptions = {
        title: 'Edit Price Details',
        onSubmit: submitEditedPrice,
        onDelete: deleteEditedPrice,
        hasDelete: true
      }

      findPriceByIdToEdit(priceId)
    }

    $scope.showAddPriceFragment = function() {
      $scope.dialogOptions = {
        title: 'Add a New Price',
        onSubmit: submitNewPrice
      }
      $scope.editedPrice = new EditedPrice()
    }

    var submitEditedPrice = function() {
      apiSlotsService.patchPrice($scope.editedPrice.slotId, $scope.editedPrice.id, $scope.editedPrice.toApiPriceEntity(), function() {
        notifyService.notify('Price details updated', 'success')
        $scope.editedSlot.refreshPrices()
      })
    }

    var deleteEditedPrice = function() {
      apiSlotsService.deletePrice($scope.editedPrice.slotId, $scope.editedPrice.id, function() {
        notifyService.notify('Price deleted', 'success')
        $scope.editedSlot.refreshPrices()
      })
    }

    var submitNewPrice = function() {
      apiSlotsService.addPrice($scope.editedSlot.id, $scope.editedPrice.toApiPriceEntity(), function() {
        notifyService.notify('New price added', 'success')
        $scope.editedSlot.refreshPrices()
      })
    }

    $scope.showAddBookingFragment = function() {
      delete $scope.editedBooking
      delete $scope.newBooking
      $scope.newBooking = new BookSlot($scope.editedSlot.id)
    }

    $scope.showEditBookingFragment = function(/*str*/ bookingId) {
      findBookingByIdToEdit(bookingId)
    }

    $scope.submitNewBooking = function() {
      apiBookingService.bookSlots($scope.newBooking.toApiEntity(), function() {
        notifyService.notify('New booking added', 'success')
        delete $scope.editedBooking
        delete $scope.newBooking
        $scope.editedSlot.refreshBookings()
      })
    }

    $scope.submitEditedBooking = function() {
      apiSlotsService.patchBooking($scope.editedBooking.slotId, $scope.editedBooking.id, $scope.editedBooking.toApiBookingEntity(), function(/*Booking*/ booking) {
        notifyService.notify('Booking details updated', 'success')
        $scope.editedBooking.refresh()
      })
    }

    $scope.cancelEditedBooking = function() {
      apiBookingService.cancelSlots($scope.editedBooking.toApiCancelEntity(), function() {
        notifyService.notify('Booking cancelled', 'success')
        $scope.editedBooking.refresh()
      })
    }

    $scope.cancelNewBooking = function() {
      delete $scope.newBooking
    }

    $scope.showBookForUserFragment = function() {
      $scope.onSelectUser = bookForUser
      $scope.userFinderTrigger = Math.random()
    }

    var bookForUser = function(/*User*/ user) {
      $scope.newBooking.user = user
    }

    $scope.setEditedSpace = function() {
      delete $scope.editedSpace
      delete $scope.editedSlot
      delete $scope.editedBooking
      delete $scope.newBooking
      if ($scope.space) {
        $scope.editedSpace = new EditedSpace($scope.space)
        $scope.editedSpace.slotsByDate(parseDateFilter())
      }
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      space: '=' /*Space*/
    },

    templateUrl: 'views/templates/slotsManager.html',

    controller: controller,

    link: function(scope, element, attrs) {
      function trigger() {
        scope.setEditedSpace()
      }

      scope.$watch('trigger', function(newValue, oldValue) {
        trigger()
      })
      scope.$watch('space', function(newValue, oldValue) {
        trigger()
      })
    }

  }
})

app.directive('slotsManagerAddSlotDialog', function(modalDialogService) {

  var controller = function($scope) {

    $scope.setEditedSlot = function() {
      $scope.dateFromFormatted = formatDate($scope.slot.dateFrom)
      $scope.dateToFormatted = formatDate($scope.slot.dateTo)
      if (!$scope.timeFromFormatted) $scope.timeFromFormatted = '0:00'
      if (!$scope.timeToFormatted) $scope.timeToFormatted = '24:00'
    }

    $scope.updateAndSubmit = function() {
      var slot = $scope.slot
      slot.dateFrom = safeParseInt(parseDate($scope.dateFromFormatted))
      slot.dateTo = safeParseInt(parseDate($scope.dateToFormatted))
      slot.timeFrom = safeParseInt(parseTime($scope.timeFromFormatted))
      slot.timeTo = safeParseInt(parseTime($scope.timeToFormatted))

      $scope.slot.applyChangesToSource()
      $scope.onSubmit()
    }

  }

  return {

    restrict: 'E',

    scope: {
      slot: '=', /*Slot*/
      onSubmit: '&',
    },

    templateUrl: 'views/templates/slotsManagerAddSlotDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.slots-manager-add-slot-dialog')
      var dialogHandle = modalDialogService.registerDialog('#'+elementID(dialogElement))

      scope.$watch('slot', function(newValue, oldValue) {
        if (newValue) {
          scope.setEditedSlot()
          dialogHandle.show()
        }
      })
    }

  }
})
