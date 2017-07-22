app.controller('statementsController', function($scope, $rootScope, placesService, $timeout, sb_paypalService, sb_apiPaymentsService) {

  var currency = 'GBP'

  placesService.getMyPlace(function(/*MyPlace*/ myPlace) {
    $scope.myPlace = myPlace
  })

  function initSelectableMonths() {
    $scope.selectableMonths = []
    for (var n = 0; n < 3; n++) {
      var date = sb.utils.monthMidDate(sb.utils.todayDate()),
          date = sb.utils.addDaysDate(date, -30*n),
          month = sb.utils.monthAsWord(date),
          year = date.substr(0, 4)
      $scope.selectableMonths.push({ date: date, month: month, year: year })
      $scope.selectedMonth = $scope.selectableMonths[0]
    }
  }
  initSelectableMonths()

  $scope.topupOptions = [
    { amount: 2800, currency: currency },
    { amount: 5600, currency: currency },
    { amount: 8400, currency: currency },
    { amount: 11200, currency: currency },
    { amount: 14000, currency: currency }
  ]

  $scope.selectedTopup = {}

  function wait() {
    if ($scope.myPlace && $scope.myPlace.activeSeason) {
      $scope.season = $scope.myPlace.activeSeason
      onSeasonReady()
    } else $timeout(wait, 10)
  }
  wait()

  function onSeasonReady() {
    getTransactionsForMonth()

    var user = $rootScope.userProfile,
        place = $scope.myPlace
    user.balanceFor(place.id, true, function() {
      var balance = user.balances[place.id]
      $scope.credit = balance.creditIn(currency)
    })
  }

  function getTransactionsForMonth(/*str*/ date) {
    $scope.season.transactionHistoryForMonth(date || sb.utils.todayDate(), true)
  }

  $scope.changeMonth = function(/*obj*/ month) {
    $scope.selectedMonth = month
    getTransactionsForMonth(month.date)
  }

  $scope.topupWithPaypal = function() {
    if ($scope.selectedTopup.option) {
      $scope.selectedTopup.confirmed = $scope.selectedTopup.option
      doTopUp()
    }
  }

  function doTopUp() {
    var opts = {
      total: sb.utils.numberX100($scope.selectedTopup.confirmed.amount, true),
      currency: $scope.selectedTopup.confirmed.currency,
      placeId: $scope.myPlace.id,
      profileId: $rootScope.userProfile.id
    }

    opts.onBefore = function() {
      $scope.status = 'loading'
    }
    opts.onReady = function() {
      $scope.$apply(function() {
        $scope.status = 'ready'
      })
    }
    opts.onSuccess = function() {
      $scope.$apply(function() {
        $scope.status = 'success'
      })
    }
    opts.onError = function() {
      $scope.$apply(function() {
        $scope.status = 'failed'
      })
    }
    opts.button = '#checkout-paypal-button'

    getAccount(opts.placeId, opts.currency, function(/*json*/ attrs) {
      opts.env = attrs.paypal_env
      opts.appKeySandbox = attrs.paypal_skey
      opts.appKeyProduction = attrs.paypal_pkey

      if (!opts.env) $scope.status = 'failed'
      else sb_paypalService.checkout(opts)
    })

  }

  function getAccount(/*str*/ placeId, /*str*/ currency, /*fn*/ callback) {
    sb_apiPaymentsService.getPlaceAccount(placeId, function(/*Account*/ account) {
      var attrs = account.currencyIn(currency).attributes
      callback(attrs)
    })
  }

})
