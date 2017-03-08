app.controller('loginController', function($scope, $http) {

  $scope.username = "";
  $scope.password = "";

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }

    $scope.password = "";

    $http.post("http://localhost:8022/auth/token", JSON.stringify(credentials)).success(function() { alert("GOOD"); })
  }

});
