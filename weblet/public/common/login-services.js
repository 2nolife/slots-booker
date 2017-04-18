app.service('authInterceptor', function($q, $rootScope) {
  var service = this

  service.responseError = function(response) {
    if (response.status == 401)
      $rootScope.$broadcast('api.unauthorized')
    return $q.reject(response)
  }

})

app.service('loginService', function($rootScope, $cookies, $http, state, notifyService) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    var apiCode = apiCodeFromResponse(response)
    notifyService.notify('<strong>'+response.status+'</strong> '+apiCode.text, 'danger')
  }

  service.setHttpAuthHeader = function() {
    service.removeHttpAuthHeader()
    $http.defaults.headers.common['Authorization'] = 'Bearer '+state.accessToken
  }

  service.removeHttpAuthHeader = function() {
     delete $http.defaults.headers.common['Authorization']
  }

  service.login = function(/*json*/ credentials, /*fn*/ statusCallback) {
    $cookies.remove('token')
    $http.post('/api/auth/token', credentials)
      .then(
        function successCallback(response) {
          state.accessToken = response.data.access_token
          $cookies.put('token', state.accessToken)
          service.setHttpAuthHeader()
          $rootScope.$broadcast('api.authorized')
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) notifyResponseError(response)
        })
  }

  service.refreshUser = function(/*fn*/ statusCallback) {
    $http.get('/api/profiles/me')
      .then(
        function successCallback(response) {
          state.userProfile = new User(response.data)
          assert(state.userProfile.id, 'Invalid user profile')
          $rootScope.$broadcast('api.user.ok')
          if (statusCallback) statusCallback('success')
        },
        function errorCallback(response) {
          if (!statusCallback || !statusCallback('error', response)) notifyResponseError(response)
        })
  }

})
