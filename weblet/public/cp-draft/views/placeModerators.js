app.service('placeModeratorsService', function($http, notifyService, $q) {
  var service = this

  //todo service placeholder

})

app.directive('placeModerators', function(placeModeratorsService, notifyService, apiPlacesService) {

  var controller = function($scope) {

    function EditedPlace(/*Place*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id

        source.refresh('owner')
        source.refresh('moderators')
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

    }

    function EditedUser(/*User*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.username = source.username
        _this.fullName = source.fullName
        _this.isAssigned = isAssigned()
        _this.isOwner = isOwner()
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.applyChanges = applyChangesFromSource

      function isAssigned() {
        return findModeratorById(_this.id) != null
      }

      function isOwner() {
        return _this.id == $scope.place.owner.id
      }

    }

    var findModeratorById = function(/*str*/ id) {
      var users = $.grep($scope.place.moderators.concat($scope.place.owner), function(moderator) { return moderator.id == id })
      return users.length > 0 ? users[0] : null
    }

    var findModeratorByIdToEdit = function(/*str*/ id) {
      $scope.editedModerator = null

      var users = $.grep($scope.place.moderators.concat($scope.place.owner), function(moderator) { return moderator.id == id })
      if (users.length > 0) $scope.editedModerator = new EditedUser(users[0])

      return $scope.editedModerator
    }

    $scope.showAddModeratorFragment = function() {
      $scope.onSelectUser = addModerator
      $scope.userFinderTrigger = Math.random()
    }

    $scope.showEditModeratorFragment = function(/*str*/ id) {
      findModeratorByIdToEdit(id)
    }

    var refreshPlaceModerators = function(/*[str]*/ moderatorIds) {
      apiPlacesService.patchPlace($scope.place.id, { moderators: moderatorIds }, function() {
        $scope.place.moderatorIds = moderatorIds
        $scope.place.refresh('moderators', true, function() {
          if ($scope.editedModerator) $scope.editedModerator.applyChanges()
        })
      })
    }

    $scope.addEditedModerator = function() {
      var moderatorIds = [].concat($scope.place.moderatorIds)
      moderatorIds.push($scope.editedModerator.id)
      refreshPlaceModerators(moderatorIds)
    }

    $scope.removeEditedModerator = function() {
      var moderatorIds = [].concat($scope.place.moderatorIds)
      moderatorIds.splice(moderatorIds.indexOf($scope.editedModerator.id), 1)
      refreshPlaceModerators(moderatorIds)
    }

    $scope.showChangeOwnerFragment = function() {
      notifyService.featureNotImplemented()
    }

    var addModerator = function(/*User*/ user) {
      $scope.editedModerator = new EditedUser(user)
    }

    $scope.setEditedPlace = function() {
      $scope.editedPlace = new EditedPlace($scope.place)
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      place: '=' /*Place*/
    },

    templateUrl: 'views/templates/placeModerators.html',

    controller: controller,

    link: function(scope, element, attrs) {
      function trigger() {
        delete scope.editedModerator
        if (scope.place) scope.setEditedPlace()
      }

      scope.$watch('trigger', function(newValue, oldValue) {
        trigger()
      })
      scope.$watch('place', function(newValue, oldValue) {
        trigger()
      })
    }

  }
})

app.directive('placeModeratorsCollapse', function(modalDialogService) {
  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      place: '=' /*Place*/
    },

    templateUrl: 'views/templates/placeModeratorsCollapse.html'

  }
})
