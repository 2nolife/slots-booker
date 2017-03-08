app.controller('footerController', function($scope, $rootScope, state) {

  $scope.user = state.userProfile

  $rootScope.$on('api.user.ok', function() {
    $scope.user = state.userProfile
  })

});
