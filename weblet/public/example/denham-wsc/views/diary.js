app.controller('diaryController', function($scope, placesService, $timeout) {

  placesService.getMyPlace(function(/*MyPlace*/ myPlace) {
    $scope.myPlace = myPlace
  })

  function wait() {
    if ($scope.myPlace && $scope.myPlace.activeSeason) {
      $scope.season = $scope.myPlace.activeSeason
      onSeasonReady()
    } else $timeout(wait, 10)
  }
  wait()

  function onSeasonReady() {
    $scope.season.refreshBookedPaidSlotsFromToday(true)
  }

})
