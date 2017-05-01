app.controller('movieController', function($scope, $routeParams, config, placesService) {

  var handles = {},
      paramMovieKey = $routeParams.movieKey

  $scope.movieKey = paramMovieKey

  function selectMovie() {
    var movies = $.grep(config.all_movies, function(movie) { return movie.key == paramMovieKey })
    if (movies.length) $scope.selectedMovie = movies[0]
  }

  /** CinemaPlace on change callback */
  function cinemaPlaceChange() {
    console.log('invoked: cinemaPlaceChange')
    var cinemaPlace = $scope.cinemaPlace
    if (!cinemaPlace) return

    cinemaPlace.halls.forEach(function(hall) {
      hall.refreshWeekSchedule(function() {

      })
    })
  }

  placesService.getCinemaPlace(function(/*CinemaPlace*/ cinemaPlace) {
    $scope.cinemaPlace = cinemaPlace
    cinemaPlaceChange()
    handles.cinemaPlaceChange = cinemaPlace.onChangeCallback.add(cinemaPlaceChange)
  })

  $scope.$on('$destroy', function() {
    if ($scope.cinemaPlace) $scope.cinemaPlace.onChangeCallback.remove(handles.cinemaPlaceChange)
  })

  selectMovie()

});
