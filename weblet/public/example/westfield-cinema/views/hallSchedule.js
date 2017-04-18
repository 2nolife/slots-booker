app.controller('hallScheduleController', function($scope, $routeParams, placesService, miscService) {

  var handles = {},
      paramHallId = $routeParams.hallId

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
  }

  placesService.getCinemaPlace(function(/*CinemaPlace*/ cinemaPlace) {
    $scope.cinemaPlace = cinemaPlace
    cinemaPlaceChange()
    handles.cinemaPlaceChange = cinemaPlace.onChangeCallback.add(cinemaPlaceChange)
  })

  $scope.$on('$destroy', function() {
    var cinemaPlace = $scope.cinemaPlace
    cinemaPlace.onChangeCallback.remove(handles.cinemaPlaceChange)
    cinemaPlace.onChangeCallback.remove(handles.cinemaHallChange)
  })

});
