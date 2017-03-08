app.controller('loginDialogController', function($scope, loginService, modalDialogService) {

  $scope.username = ''
  $scope.password = ''

  $scope.submitForm = function() {
    var credentials = {
      username: $scope.username,
      password: $scope.password
    }
    $scope.password = ''
    loginService.login(credentials)
  }

  var loginDialog = modalDialogService.registerDialog('#login-dialog')

  $('#login-dialog').on('hide.bs.modal', function(e) {
    $scope.submitForm()
  })

  $scope.$on('api.unauthorized', function() {
    loginDialog.show()
  })

})

app.controller('deniedDialogController', function($scope, modalDialogService) {

  var deniedDialog = modalDialogService.registerDialog('#denied-dialog')

  $scope.$on('api.user.denied', function() {
    deniedDialog.show()
  })

})
