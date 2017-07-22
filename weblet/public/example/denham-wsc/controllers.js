app.controller('signInController', function($scope, $rootScope, sb_loginService, $location) {

  delete $scope.password

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    delete $scope.password

    sb_loginService.login(credentials, function(/*str*/ status) {
      if (status == 'success') $location.path('/welcome')
    })
  }

});

app.controller('footerController', function($scope) {

});
