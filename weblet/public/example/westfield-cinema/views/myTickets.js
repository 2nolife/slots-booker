app.controller('myTicketsController', function($scope, placesService, miscService, state) {

  var handles = {},
      refundComplete = (state.refund || {}).complete

  delete state.refund

  function fetchBookedSlots() {
    var cinemaPlace = $scope.cinemaPlace,
        hallsRemaining = cinemaPlace.halls.length

    cinemaPlace.halls.forEach(function(hall) {
      hall.refreshBookedSlots(refundComplete, function() {
        hall.bookedSlots.forEach(function(slot) {

          slot.refreshSeat()
          slot.refreshActiveBooking(function() {
            slot.activeBooking.refreshPrice()
            //slot.activeBooking.refreshReference() // instead use 'paid=true' in 'refreshBookedSlots'
          })

        })

        miscService.appendDataToSlots(hall.bookedSlots)
        if (hall.bookedSlots.length || --hallsRemaining == 0) $scope.bookedSlots = ($scope.bookedSlots || []).concat(hall.bookedSlots)
      })
    })
  }

  /** CinemaPlace on change callback */
  function cinemaPlaceChange() {
    console.log('invoked: cinemaPlaceChange')
    var cinemaPlace = $scope.cinemaPlace
    if (!cinemaPlace) return

    if (!$scope.bookedSlots && cinemaPlace.halls.length) fetchBookedSlots()
  }

  placesService.getCinemaPlace(function(/*CinemaPlace*/ cinemaPlace) {
    $scope.cinemaPlace = cinemaPlace
    cinemaPlaceChange()
    handles.cinemaPlaceChange = cinemaPlace.onChangeCallback.add(cinemaPlaceChange)
  })

  $scope.$on('$destroy', function() {
    if ($scope.cinemaPlace) $scope.cinemaPlace.onChangeCallback.remove(handles.cinemaPlaceChange)
  })

  $scope.onCancelBooking = function() {

  }

  $scope.selectedSlots = []

  $scope.toggleSlot = function(/*CinemaSlot*/ slot) {
    var slots = $scope.selectedSlots,
        i = slots.indexOf(slot)

    if (i >= 0) slots.splice(i, 1)
    else slots.push(slot)
  }

})

app.directive('myTicketsSelectTickets', function() {

  var controller = function($scope, state, $location) {

    $scope.refund = function() {
      state.refund = {
        slots: $scope.selectedSlots
      }
      $location.path('/refund')
    }

  }

  return {

    restrict: 'E',

    scope: {
      selectedSlots: '=' /*[CinemaSlots]*/
    },

    templateUrl: 'views/templates/myTicketsSelectTickets.html',

    controller: controller
  }
})
