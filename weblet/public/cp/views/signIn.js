app.controller('signInController', function($scope, sb_loginService) {

  delete $scope.password

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    delete $scope.password

    sb_loginService.login(credentials)
  }

});
