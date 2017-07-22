var app = angular.module('app', [
  'ngResource',
  'ngRoute',
  'ngCookies'
])

app.config(function($routeProvider, $httpProvider) {

  $routeProvider
    .when('/day', { templateUrl: 'views/day.html', controller: 'dayController' })
    .when('/day/:date', { templateUrl: 'views/day.html', controller: 'dayController' })
    .when('/checkout', { templateUrl: 'views/checkout.html', controller: 'checkoutController', signedIn: true })
    .when('/refund', { templateUrl: 'views/refund.html', controller: 'refundController', signedIn: true })
    .when('/diary', { templateUrl: 'views/diary.html', controller: 'diaryController', signedIn: true })
    .when('/statements', { templateUrl: 'views/statements.html', controller: 'statementsController', signedIn: true })
    .when('/welcome', { templateUrl: 'views/welcome.html', controller: 'welcomeController' })
    .otherwise({ redirectTo: '/day' })

  $httpProvider.interceptors.push('sb_authInterceptor')

})

app.constant('config', {

  client_key: 'example',
  place_external_key: 'denham-wsc',
  max_bookings_per_day: 2

})

app.value('state', {

  userProfile: null, // signed in user profile
  accessToken: null,  // token to access the API

  checkoutBasket: null,
  refundBasket: null

})

app.run(function($injector, $rootScope, $cookies, $interval, state, $location) {

  $rootScope.$on('$routeChangeStart', function(event, next) {
    if (!$rootScope.userProfile && next.signedIn) $location.path('/day')
  })

  $rootScope.$on('api.authorized', function() {
    $injector.get('sb_loginService').refreshUser()
  })

  $rootScope.$on('api.unauthorized', function() {
    $cookies.remove('token')
    delete $rootScope.userProfile
  })

  $rootScope.$on('api.user.ok', function() {
    $rootScope.userProfile = new MyUser(state.userProfile)
    $rootScope.$broadcast('api.user.set')
  })

  state.accessToken = $cookies.get('token')
  if (state.accessToken) {
    $injector.get('sb_loginService').setHttpAuthHeader()
    $injector.get('sb_loginService').refreshUser()
  }

})
