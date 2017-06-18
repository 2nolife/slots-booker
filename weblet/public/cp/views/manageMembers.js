app.controller('manageMembersController', function($scope, $rootScope, placesService, $timeout, $routeParams, $location, sb_notifyService) {

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
    $location.path('/manage-members')
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
