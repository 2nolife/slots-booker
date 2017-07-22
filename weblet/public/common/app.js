var app = angular.module('app', [
  'ngResource',
  'ngRoute',
  'ngCookies'
])

app.config(function($routeProvider, $httpProvider) {

  $routeProvider
    .when('/', { templateUrl: 'views/dummy.html', controller: 'dummyController' })
    .otherwise({ redirectTo: '/' })

  $httpProvider.interceptors.push('sb_authInterceptor')

})

app.constant('config', {

})

app.value('state', {

  userProfile: null, // signed in user profile
  accessToken: null  // token to access the API

})

app.run(function($injector, $rootScope, $cookies, $interval, state) {

  $rootScope.$on('api.authorized', function() {
    $injector.get('sb_loginService').refreshUser()
  })

  state.accessToken = $cookies.get('token')
  if (!state.accessToken) {
    $rootScope.$broadcast('api.unauthorized')
  } else {
    $injector.get('sb_loginService').setHttpAuthHeader()
    $injector.get('sb_loginService').refreshUser()
  }

  $interval(function() {
    $('[data-toggle="tooltip"]').tooltip()
  }, 1000)

})
