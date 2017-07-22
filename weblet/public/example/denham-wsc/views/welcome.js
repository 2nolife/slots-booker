app.controller('welcomeController', function($scope, $location) {

  $scope.ok = function() {
    $location.path('/day')
  }

})
