app.controller('manageBookingsController', function($scope, $rootScope, placesService, $timeout, $routeParams, $location, sb_notifyService) {

  var paramPlaceId = $routeParams.placeId

  function loadPlaces(/*bool*/ force, /*str*/ editPlaceId) {
    $scope.loadPlacesSpin = true
    placesService.loadPlaces(force, function(/*[EditedPlace]*/ places) {
      $timeout(function() { $scope.loadPlacesSpin = false }, 1000)

      $scope.listedPlaces = places
      if (editPlaceId) editPlaceById(editPlaceId)

    })
  }

  function editPlaceById(/*str*/ placeId) {
    delete $scope.editedPlace
    var p = $.grep($scope.listedPlaces, function(place) { return placeId == place.id })
    if (p.length) $scope.editedPlace = p[0]
  }

  function resetToListedPlaces() {
    delete $scope.editedPlace
  }

  $scope.reloadPlaces = function(/*str*/ editPlaceId) {
    resetToListedPlaces()
    loadPlaces(true, editPlaceId)
  }

  $scope.showListedPlaces = function() {
    resetToListedPlaces()
    $location.path('/manage-bookings')
  }

  $scope.reloadEditedPlace = function() {
    $scope.loadEditedPlaceSpin = true
    $scope.editedPlace.refresh(function() {
      $rootScope.$broadcast('editedPlace.reset')
      $timeout(function() { $scope.loadEditedPlaceSpin = false }, 1000)
    })
  }

  loadPlaces(false, paramPlaceId)
})

app.directive('mbEditedPlaceSpaces', function($rootScope) {

  var controller = function($scope, sb_apiSpacesService, sb_notifyService) {

    $scope.editSpace = function(/*EditedSpace*/ space) {
      $scope.editedSpace = space
      $scope.editedSpace.refreshSpaces()
    }

    $scope.onEditedPlaceSet = function() {
      delete $scope.editedSpace
      $scope.editedPlace.refreshSpaces()
    }

  }

  return cp.manageDirectives.editedPlaceDirective('manageBookings/editedPlaceSpaces', controller, $rootScope)

})

app.directive('mbEditedSpaceSlots', function($rootScope) {

  var controller = function($scope, sb_notifyService, sb_apiSpacesService, sb_apiSlotsService, bookingService) {

    $scope.dateFilter = { from: parseInt(sb.utils.todayDate()), to: parseInt(sb.utils.todayDate()) }
    $scope.viewSlotsMode = 'calendar'

    function applyDateFilter(/*bool*/ force, /*fn*/ callback) {
      delete $scope.editedSlot
      delete $scope.slotsCalendar
      var dates = $scope.dateFilter
      $scope.editedSpace.refreshSlotsByDate(dates.from, dates.to, force, function() {
        $scope.slotsCalendar = new cp.classes.SlotsCalendar(dates.from, dates.to, $scope.editedSpace.slots)
        if (callback) callback()
      })
    }

    $scope.onDateFilterChange = function(/*num*/ from, /*num*/ to) {
      $scope.dateFilter = { from: from, to: to }
      applyDateFilter(true)
    }

    function refreshBookings(/*EditedSlot*/ slot, /*bool*/ force) {
      slot.refreshActiveBooking(force, function() {
        var booking = slot.activeBooking /*EditedBooking*/
        if (booking) {
          booking.refreshUser()
          booking.refreshReference()
        }
      })
      slot.refreshBookings(force, function() {
        slot.bookings.forEach(function(/*EditedBooking*/ booking) {
          booking.refreshUser()
        })
      })
    }

    $scope.editSlot = function(/*EditedSlot*/ slot) {
      $scope.editedSlot = slot
      refreshBookings($scope.editedSlot)
    }

    function refreshHashSlotsWithBookings(/*{}*/ hash) {
      hash.slots.forEach(function(/*EditedSlot*/ slot) {
        slot.refresh(function() {
          refreshBookings(slot, true)
        })})
    }

    function refund(/*[EditedSlot]*/ slots) {
      bookingService.calculateRefund(slots, function(/*[{}]*/ refunds) { // step 1
        var amount = refunds[0].refund.currency+' '+sb.utils.numberX100(refunds.map(function(hash) { return hash.refund.amount }).reduce((a, b) => a+b, 0), true)
        $rootScope.$broadcast('dialog.confirm', { text: 'Refund '+slots.length+' slots for '+amount, onConfirm: refundSlotsAndCredit.bind(null, refunds) })
      })
      function refundSlotsAndCredit(/*[{}]*/ refunds) { // step 2
        bookingService.refundSlotsAndCredit(
          refunds.map(function(hash) { return hash.refund }),
          function referenceCallback() {
            refunds.forEach(function(hash) {
              refreshHashSlotsWithBookings(hash)
            })
          },
          function creditCallback() {
            sb_notifyService.notify('Refunded', 'success')
          })
      }
    }

    $scope.refundSingle = function() {
      var slots = [$scope.editedSlot]
      if (slots.length) refund(slots)
    }

    $scope.refundSelected = function() {
      var slots = $.grep($scope.editedSpace.slots, function(/*EditedSlot*/ slot) { return slot.selected })
      if (slots.length) refund(slots)
    }

    function book(/*[EditedSlot]*/ slots) {
      var selectedUser = null
      function selectUser() { // step 1 (dialog)
        $scope.selectedSlots = slots
        $scope.userFinderTrigger = Math.random()
        $scope.onUserSubmit = function(/*EditedUser*/ user) {
          delete $scope.onUserSubmit
          delete $scope.selectedSlots
          selectedUser = user
          slotsPrices()
        }
      }
      function slotsPrices() { // step 2 (dialog)
        $scope.selectedSlots = slots
        $scope.selectedUser = selectedUser
        $scope.slotsPricesTrigger = Math.random()
        $scope.onSlotsPricesSubmit = function() {
          delete $scope.onSlotsPricesSubmit
          delete $scope.selectedSlots
          delete $scope.selectedUser
          bookSlots()
        }
      }
      function bookSlots() { // step 3
        bookingService.calculateQuote(slots, user.id, function(/*{}*/ hash) {
          var amount = hash.quote.currency+' '+sb.utils.numberX100(hash.quote.amount, true)
          $rootScope.$broadcast('dialog.confirm', { text: 'Book '+slots.length+' slots for '+amount, onConfirm: bookSlotsWithCredit.bind(null, hash) })
        })
      }
      function bookSlotsWithCredit(/*{}*/ hash) { // step 4
        bookingService.bookSlotsWithCredit(
          hash.quote,
          function referenceCallback() {
            refreshHashSlotsWithBookings(hash)
          },
          function creditCallback() {
            sb_notifyService.notify('Booked', 'success')
          })
      }
      if (slots.length) selectUser()
    }

    $scope.bookSingle = function() {
      var slots = [$scope.editedSlot]
      if (slots.length) book(slots)
    }

    $scope.bookSelected = function() {
      var slots = $.grep($scope.editedSpace.slots, function(/*EditedSlot*/ slot) { return slot.selected }) 
      if (slots.length) book(slots)
    }

    $scope.unsetEditedSlot = function() {
      delete $scope.editedSlot
    }

    $scope.onEditedSpaceSet = function() {
      applyDateFilter(false)
    }

  }

  return cp.manageDirectives.editedSpaceDirective('manageBookings/editedSpaceSlots', controller)

})
