var app = angular.module('app', [
  'ngResource',
  'ngRoute',
  'ngCookies'
])

app.config(function($routeProvider, $httpProvider) {

  $routeProvider
    .when('/sign-in', { templateUrl: 'views/signIn.html', controller: 'signInController' })
    .when('/dashboard', { templateUrl: 'views/dashboard.html', controller: 'dashboardController' })
    .when('/add-reservation', { templateUrl: 'views/addReservation.html', controller: 'addReservationController' })
    .when('/my-reservations', { templateUrl: 'views/myReservations.html', controller: 'myReservationsController' })
    .otherwise({ redirectTo: '/dashboard' })

  $httpProvider.interceptors.push('sb_authInterceptor')

})

app.constant('config', {

  client_key: 'example',
  place_external_key: 'genius-bar'

})

app.value('state', {

  userProfile: null, // signed in user profile
  accessToken: null  // token to access the API

})

app.run(function($injector, $rootScope, $cookies, $timeout, $interval, state, $location) {

  $rootScope.$on('api.authorized', function() {
    $injector.get('sb_loginService').refreshUser(function(/*str*/ status) {
      if (status == 'success') $location.path('/dashboard')
    })
  })

  $rootScope.$on('api.unauthorized', function() {
    $location.path('/sign-in')
  })

  $timeout(function() {
    state.accessToken = $cookies.get('token')
    if (!state.accessToken) {
      $rootScope.$broadcast('api.unauthorized')
    } else {
      $injector.get('sb_loginService').setHttpAuthHeader()
      $injector.get('sb_loginService').refreshUser()
    }
  }, 10)

})
