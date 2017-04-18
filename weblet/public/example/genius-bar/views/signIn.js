app.controller('signInController', function($scope, loginService, notifyService) {

  delete $scope.password

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    delete $scope.password

    loginService.login(credentials)
  }

  function notifyError(/*str*/ msg) {
    notifyService.notify(msg, 'danger')
  }

});
