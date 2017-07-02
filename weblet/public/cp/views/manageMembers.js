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

app.directive('editedPlaceMembers', function($rootScope) {

  var controller = function($scope, sb_apiPlacesService, sb_apiMembersService) {

    function refreshMembers(/*bool*/ force) {
      var editedPlace = $scope.editedPlace
      editedPlace.refreshMembers(force, function() {
        editedPlace.members.forEach(function(/*EditedMember*/ member) {
          member.refreshUser()
        })
      })
    }

    function addMember(/*EditedUser*/ user) {
      user.memberFor($scope.editedPlace.id, true, function() {
        var member = user.members[$scope.editedPlace.id] /*EditedMember*/
        member.level = member.level || 1
        sb_apiMembersService.patchPlaceMember(member.toApiEntity(), function() {
          refreshMembers(true)
        })
      })
    }

    $scope.addMember = function() {
      $scope.onUser = addMember
      $scope.userFinderTrigger = Math.random()
    }

    $scope.editMember = function(/*EditedMember*/ member) {
      $scope.editedMember = member
      $scope.editMemberTrigger = Math.random()
    }

    $scope.onMemberSave = function() {
      sb_apiMembersService.patchPlaceMember($scope.editedMember.toApiEntity(), function() {
        refreshMembers(true)
      })
    }

    $scope.onEditedPlaceSet = function() {
      refreshMembers(false)
    }

  }

  return cp.manageDirectives.editedPlaceDirective('manageMembers/editedPlaceMembers', controller)

})

app.directive('editedPlaceEditMemberDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    $scope.onEditedMemberSet = function() {
      $scope.level = $scope.editedMember.level
    }

    $scope.submit = function() {
      $scope.editedMember.level = parseInt($scope.level)
      $scope.onSave()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      editedMember: '=', /*EditedMember*/
      onSave: '&'
    },

    templateUrl: 'views/templates/manageMembers/editedPlaceEditMemberDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.edited-place-edit-member-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('editedMember', function(newValue, oldValue) { if (newValue) scope.onEditedMemberSet() })
    }

  }
})
