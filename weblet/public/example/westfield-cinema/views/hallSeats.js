app.controller('hallSeatsController', function($scope, $routeParams, placesService, miscService) {

  var handles = {},
      paramHallId = $routeParams.hallId,
      paramSlotId = $routeParams.slotId

  /** CinemaPlace on change callback */
  function cinemaPlaceChange() {
    console.log('invoked: cinemaPlaceChange')
    var cinemaPlace = $scope.cinemaPlace
    if (!cinemaPlace) return

    if (!$scope.cinemaHall) { // find selected hall
      var cinemaHalls = $.grep(cinemaPlace.halls, function(/*CinemaHall*/ hall) { return hall.id == paramHallId })
      if (cinemaHalls.length) {
        $scope.cinemaHall = cinemaHalls[0]
        cinemaHallChange()
        handles.cinemaHallChange = cinemaHalls[0].onChangeCallback.add(cinemaHallChange)
      }
    }
  }

  /** CinemaHall on change callback */
  function cinemaHallChange() {
    console.log('invoked: cinemaHallChange')
    var cinemaHall = $scope.cinemaHall
    if (!cinemaHall) return

    cinemaHall.refreshWeekSchedule(function() {
      miscService.appendDataToSlots(cinemaHall.weekSchedule.slots)
    })

    cinemaHall.refreshSeats()

    if (!$scope.cinemaSlot && cinemaHall.weekSchedule) { // find selected slot
      var cinemaSlots = $.grep(cinemaHall.weekSchedule.slots, function(/*CinemaSlot*/ slot) { return slot.id == paramSlotId })
      if (cinemaSlots.length) $scope.cinemaSlot = cinemaSlots[0]
    }

    if (!$scope.seatMatrix && cinemaHall.seats.length && $scope.cinemaSlot)
      createSeatMatrix()
  }

  function createSeatMatrix() {
    console.log('invoked: createSeatMatrix')
    var cinemaHall = $scope.cinemaHall

    var matrix = [[]],
        maxrow = 0,
        maxcol = 0
    cinemaHall.seats.forEach(function(seat) {
      var r = seat.rowNum,
          c = seat.colNum
      maxrow = Math.max(r, maxrow)
      maxcol = Math.max(c, maxcol)
    })

    for (var r = 0; r < maxrow; r++)
      for (var c = 0; c < maxcol; c++) {
        matrix[r] = matrix[r] || []
        matrix[r][c] = { id: Math.random(), type: 'gap' }
      }

    cinemaHall.seats.forEach(function(seat) {
      var r = seat.rowNum,
          c = seat.colNum
      matrix[r-1][c-1] = seat
    })

    var dateFrom = $scope.cinemaSlot.dateFrom,
        timeFrom = $scope.cinemaSlot.timeFrom

      cinemaHall.refreshSlotsByDateTime(dateFrom, timeFrom, function() {
        var slots = cinemaHall.seats.map(function(seat) { return seat.slot })
        miscService.appendDataToSlots(slots)
      })

    $scope.seatMatrix = matrix
  }

  placesService.getCinemaPlace(function(/*CinemaPlace*/ cinemaPlace) {
    $scope.cinemaPlace = cinemaPlace
    cinemaPlaceChange()
    handles.cinemaPlaceChange = cinemaPlace.onChangeCallback.add(cinemaPlaceChange)
  })

  $scope.$on('$destroy', function() {
    if ($scope.cinemaPlace) $scope.cinemaPlace.onChangeCallback.remove(handles.cinemaPlaceChange)
    if ($scope.cinemaHall) $scope.cinemaHall.onChangeCallback.remove(handles.cinemaHallChange)
  })

  $scope.selectedSeats = []

  $scope.toggleSeat = function(/*CinemaSeat*/ seat) {
    var seats = $scope.selectedSeats,
        i = seats.indexOf(seat)

    if (i >= 0) seats.splice(i, 1)
    else if (seat.status == 'available') seats.push(seat)
  }

  $scope.isSeatToggled = function(/*CinemaSeat*/ seat) {
    return $scope.selectedSeats.indexOf(seat) >= 0
  }

});

app.directive('hallSeatsSelectTickets', function() {

  var controller = function($scope, state, $location) {

    function calculateTotal() {
      var arr = [],
          sum = 0,
          sign = null
      $scope.selectedSeats.forEach(function(seat) {
        var priceId = seat.slot.selectedPriceId,
            prices = $.grep(seat.slot.prices, function(price) { return price.id == priceId })
        if (prices.length) {
          arr.push(prices[0])
          sum += prices[0].amount
          sign = prices[0].sign
        }
      })

      $scope.total = { sign: sign, amount: sum, prices: arr }
    }

    $scope.$watchCollection('selectedSeats', function(newValue, oldValue) {
      $scope.selectedSeats.forEach(function(seat) {
        seat.slot.refreshPrices(calculateTotal)
      })
      calculateTotal()
    })

    $scope.priceChanged = function(/*CinemaPrice*/ price) {
      calculateTotal()
    }

    $scope.checkout = function() {
      state.checkout = $scope.total
      $location.path('/checkout')
    }

  }

  return {

    restrict: 'E',

    scope: {
      selectedSeats: '=' /*[CinemaSeats]*/
    },

    templateUrl: 'views/templates/hallSeatsSelectTickets.html',

    controller: controller
  }
})
