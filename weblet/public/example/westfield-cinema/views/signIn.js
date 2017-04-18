app.controller('signInController', function($scope, loginService) {

  delete $scope.password

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    delete $scope.password

    loginService.login(credentials)
  }

});
