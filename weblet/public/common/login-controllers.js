app.controller('sb_loginDialogController', function($scope, sb_loginService, modalDialogService) {

  $scope.username = ''
  $scope.password = ''

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    $scope.password = ''
    sb_loginService.login(credentials)
  }

  var loginDialog = modalDialogService.registerDialog('#login-dialog')

  $('#login-dialog').on('hide.bs.modal', function(e) {
    $scope.submitForm()
  })

  $scope.$on('api.unauthorized', function() {
    loginDialog.show()
  })

})
