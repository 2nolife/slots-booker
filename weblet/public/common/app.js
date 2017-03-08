var app = angular.module('app', [
  'ngResource',
  'ngRoute',
  'ngCookies'
])

app.config(function($routeProvider, $httpProvider) {

  $routeProvider
    .when('/', { templateUrl: 'views/dummy.html', controller: 'dummyController' })
    .when('/manage-users', { templateUrl: 'views/manageUsers.html', controller: 'manageUsersController' })
    .when('/manage-places', { templateUrl: 'views/managePlaces.html', controller: 'managePlacesController' })
    .otherwise({ redirectTo: '/' })

  $httpProvider.interceptors.push('authInterceptor')

})

app.constant('config', {

    all_user_roles: ['ADMIN', 'MODERATOR'],
    allowed_login_user_roles: ['ADMIN', 'MODERATOR']

})

app.value('state', {

  userProfile: null, // signed in user profile
  accessToken: null  // token to access the API

})

app.run(function($injector, $rootScope, $cookies, $timeout, $interval, state) {

  $rootScope.$on('api.authorized', function() {
    $injector.get('loginService').refreshUser()
  })

  $timeout(function() {
    state.accessToken = $cookies.get('token')
    if (state.accessToken == null) {
      $rootScope.$broadcast('api.unauthorized')
    } else {
      $injector.get('loginService').setHttpAuthHeader()
      $injector.get('loginService').refreshUser()
    }
  }, 10)

  $interval(function() {
    $('[data-toggle="tooltip"]').tooltip()
  }, 1000)

})
