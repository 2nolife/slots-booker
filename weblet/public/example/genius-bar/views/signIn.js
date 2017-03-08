app.controller('signInController', function($scope, loginService, notifyService) {

  delete $scope.password

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    delete $scope.password

    loginService.login(credentials, function(/*str*/ status, /*obj*/ response) {
      if (status == 'error')
        switch (response.status) {
          case 401:
            notifyError('Invalid username or password')
            break
          default:
            notifyError('Response code: '+response.status)
        }
    })
  }

  function notifyError(/*str*/ msg) {
    notifyService.notify(msg, 'danger')
  }

});
