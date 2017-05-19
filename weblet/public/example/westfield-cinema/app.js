var app = angular.module('app', [
  'ngResource',
  'ngRoute',
  'ngCookies'
])

app.config(function($routeProvider, $httpProvider) {

  $routeProvider
    .when('/sign-in', { templateUrl: 'views/signIn.html', controller: 'signInController' })
    .when('/featuring', { templateUrl: 'views/featuring.html', controller: 'featuringController' })
    .when('/movie/:movieKey', { templateUrl: 'views/movie.html', controller: 'movieController' })
    .when('/hall/:hallId', { templateUrl: 'views/hallSchedule.html', controller: 'hallScheduleController' })
    .when('/hall/:hallId/:slotId', { templateUrl: 'views/hallSeats.html', controller: 'hallSeatsController' })
    .when('/checkout', { templateUrl: 'views/checkout.html', controller: 'checkoutController' })
    .when('/checkout-paypal', { templateUrl: 'views/checkoutPaypal.html', controller: 'checkoutPaypalController' })
    .when('/refund', { templateUrl: 'views/refund.html', controller: 'refundController' })
    .when('/my-tickets', { templateUrl: 'views/myTickets.html', controller: 'myTicketsController' })
    .otherwise({ redirectTo: '/featuring' })

  $httpProvider.interceptors.push('sb_authInterceptor')

})

app.constant('config', {

  client_key: 'example',
  place_external_key: 'westfield-cinema',

  all_movies: [ // copy paste from westfield-cinema.js
    { key: 'ro', title: 'Rogue One',         time: 60+45, cover: 'cover_rogue_one.jpeg',       price_add: 150 },
    { key: 'ca', title: 'Captain America',   time: 60+35, cover: 'cover_captain_america.jpeg', price_add: 150 },
    { key: 'jb', title: 'Jason Bourne',      time: 60+15, cover: 'cover_jason_bourne.jpeg',    price_add: 0   },
    { key: 'ds', title: 'Doctor Strange',    time: 60+30, cover: 'cover_doctor_strange.jpeg',  price_add: 0   },
    { key: 'ss', title: 'Suicide Squad',     time: 60+45, cover: 'cover_suicide_squad.jpeg',   price_add: 0   },
    { key: 'st', title: 'Star Trek',         time: 60+35, cover: 'cover_star_trek.jpeg',       price_add: 0   },
    { key: 'dp', title: 'Deadpool',          time: 60+20, cover: 'cover_deadpool.jpeg',        price_add: 50  },
    { key: 'bs', title: 'Batman v Superman', time: 60+30, cover: 'cover_batman_superman.jpeg', price_add: 150 },
    { key: 'wc', title: 'Warcraft',          time: 60+35, cover: 'cover_warcraft.jpeg',        price_add: 50  }
  ]

})

app.value('state', {

  userProfile: null, // signed in user profile
  accessToken: null, // token to access the API

  checkout: null,
  refund: null

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
