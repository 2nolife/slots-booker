app.service('userFinderService', function($http, notifyService, $q) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

})

app.directive('userFinderDialog', function(userFinderService, notifyService, apiUsersService, modalDialogService) {

  var controller = function($scope) {

    $scope.searchUser = function() {
      var attrs = attributeNamesValues(['first_name', 'last_name'], ($scope.searchValue || '').split(' '))
      if (attrs.length > 0)
        apiUsersService.findUsers({
          attributes: attrs,
          join: 'or'
        },
        function(/*[User]*/ users) {
          $scope.foundUsers = users
        })
    }

    $scope.selectUserById = function(/*str*/ id) {
      $scope.selectedUser = null

      var users = $.grep($scope.foundUsers, function(user) { return user.Id == id })
      if (users.length > 0) $scope.selectedUser = users[0]
    }

    $scope.submitSelectedUser = function() {
      if ($scope.selectedUser) $scope.onSubmit({ user: $scope.selectedUser })
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=',
      onSubmit: '&'
    },

    templateUrl: 'views/templates/userFinderDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.user-finder-dialog')
      var dialogHandle = modalDialogService.registerDialog('#'+elementID(dialogElement))

      scope.$watch('trigger', function(newValue, oldValue) {
        if (newValue) dialogHandle.show()
      })
    }

  }
})
