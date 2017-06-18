var app = angular.module('app', [
  'ngResource',
  'ngRoute',
  'ngCookies'
])

app.config(function($routeProvider, $httpProvider) {

  $routeProvider
    .when('/sign-in', { templateUrl: 'views/signIn.html', controller: 'signInController' })
    .when('/dashboard', { templateUrl: 'views/dashboard.html', controller: 'dashboardController' })
    .when('/manage-places', { templateUrl: 'views/managePlaces.html', controller: 'managePlacesController' })
    .when('/manage-places/:placeId', { templateUrl: 'views/managePlaces.html', controller: 'managePlacesController' })
    .when('/manage-bookings', { templateUrl: 'views/manageBookings.html', controller: 'manageBookingsController' })
    .when('/manage-bookings/:placeId', { templateUrl: 'views/manageBookings.html', controller: 'manageBookingsController' })
    .when('/manage-members', { templateUrl: 'views/manageMembers.html', controller: 'manageBookingsController' })
    .when('/manage-members/:placeId', { templateUrl: 'views/manageMembers.html', controller: 'manageMembersController' })
    .otherwise({ redirectTo: '/dashboard' })

  $httpProvider.interceptors.push('sb_authInterceptor')

})

app.constant('config', {

    all_user_roles: ['ADMIN', 'MODERATOR'],
    allowed_login_user_roles: ['ADMIN', 'MODERATOR']

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
    delete $rootScope.userLoggedIn
    $location.path('/sign-in')
  })

  $rootScope.$on('api.user.ok', function() {
    $rootScope.userLoggedIn = true
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

  $interval(function() {
    $('[data-toggle="tooltip"]').tooltip()
  }, 1000)

})
