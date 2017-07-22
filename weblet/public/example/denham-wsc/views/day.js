app.controller('dayController', function($scope, placesService, $routeParams, $timeout, $rootScope, $location, state, config) {

  var paramDate = $routeParams.date,
      dateFilter = {},
      handlers = {},
      myBookingsCount = 0

  paramDate = paramDate || state.lastDate
  state.lastDate = paramDate

  placesService.getMyPlace(function(/*MyPlace*/ myPlace) {
    $scope.myPlace = myPlace
  })

  function initDateFilter() {
    var date = parseInt(sb.utils.safeParseInt(paramDate) ? paramDate : sb.utils.todayDate()),
        str = ''+date
    dateFilter.date = date
    dateFilter.print = sb.utils.weekdayAsWord(date)+' '+str.substr(6, 2)+' '+sb.utils.monthAsWord(date)+' '+str.substr(0, 4)
  }
  initDateFilter()

  $scope.printDate = dateFilter.print
  $scope.selectedDate = sb.utils.formatDate(dateFilter.date)

  function wait() {
    if ($scope.myPlace && $scope.myPlace.activeSeason && $scope.myPlace.activeSeason.firstLake) {
      $scope.firstLake = $scope.myPlace.activeSeason.firstLake
      onLakesReady()
    } else $timeout(wait, 10)
  }
  wait()

  function onLakesReady() {
    slotsByDates()
  }

  function onUserReady() {
    if (!$scope.firstLake) return
    slotsByDates()
  }

  function slotsByDates() {
    $scope.firstLake.refreshSlotsByDate(dateFilter.date, dateFilter.date, true, function() {
      var flake = $scope.firstLake,
          fslot = flake.firstSlot,
          user = $rootScope.userProfile

      if (fslot) fslot.refreshEffectiveBookBounds(false, function() {
        fslot.refreshEffectiveCancelBounds(false, function() {
          flake.onSlotBounds()
        })
      })
    
      if (user) flake.slots.forEach(function(/*MySlot*/ slot) {
        if (slot.bookStatus > 0) slot.refreshActiveBooking(false, function() {
          if (slot.activeBooking) {
            slot.activeBooking.onSingedInUser($rootScope.userProfile)
            slot.onActiveBooking()
            if (slot.custom.mine) myBookingsCount++
          }
        })
      })

    })
  }

  handlers.onuser = $rootScope.$on('api.user.set', function() {
    onUserReady()
  })

  $scope.changeDay = function(/*num*/ add) {
    var day = sb.utils.addDaysDate(dateFilter.date, add)
    $location.path('/day/'+day)
  }

  $scope.$watch('selectedDate', function(newValue, oldValue) {
    if (newValue && newValue != sb.utils.formatDate(dateFilter.date)) {
      var day = sb.utils.parseDate(newValue)
      $location.path('/day/'+day)
    }
  })

  $scope.selectDateTrigger = function() {
    angular.element('#view-day input.calendar').focus()
  }

  $scope.selectedSlotsCount = 0

  $scope.toggleSelectSlot = function(/*MySlot*/ slot) {
    var user = $rootScope.userProfile
        bookable = user && slot.custom.bookable,
        cancellable = user && slot.custom.cancellable

    if (bookable) {
      slot.selected = !slot.selected
      $scope.selectedSlotsCount += slot.selected ? 1 : -1
    }

    if (cancellable)
      refund([slot])
  }

  $scope.unselectAllSlots = function() {
    var slots = ($scope.firstLake || {}).slots || []
    slots.forEach(function(/*MySlot*/ slot) {
      delete slot.selected
    })
    $scope.selectedSlotsCount = 0
  }

  $scope.checkout = function() {
    var slots = ($scope.firstLake || {}).slots || [],
        selected = slots.filter(function(/*MySlot*/ slot) { return slot.selected }),
        bookingsCount = myBookingsCount+selected.length

    if (selected.length && bookingsCount <= config.max_bookings_per_day) {
      state.checkoutBasket = {
        slots: selected,
        place: $scope.myPlace,
        user: $rootScope.userProfile,
        lake: $scope.firstLake
      }
      $location.path('/checkout')
    }

    if (bookingsCount > config.max_bookings_per_day) {
      $scope.limitExceeded = true
    }
  }

  function refund(/*[MySlot]*/ selected) {
    state.refundBasket = {
      slots: selected,
      place: $scope.myPlace,
      user: $rootScope.userProfile,
      lake: $scope.firstLake
    }
    $location.path('/refund')
  }

  $scope.$on('$destroy', function() {
    handlers.onuser()
  })

});
