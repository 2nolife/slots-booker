var app = angular.module('app', [
  'ngResource',
  'ngRoute'
]);

app.config(function($routeProvider) {
  $routeProvider
    .when('/', { templateUrl: 'pages/dummy.html', controller: 'dummyController' })
    .when('/dummy', { templateUrl: 'pages/dummy.html', controller: 'dummyController' })
    .when('/login', { templateUrl: 'pages/login.html', controller: 'loginController' })
    .otherwise({ redirectTo: '/' });
});
