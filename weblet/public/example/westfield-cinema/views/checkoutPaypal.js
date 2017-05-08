app.controller('checkoutPaypalController', function($scope, state, config, paypalService, apiPaymentsService) {

  var opts = {}

  if (state.checkout && !state.checkout.complete) {
    $scope.basket = state.checkout

    opts = {
      total: numberX100(state.checkout.amount, true),
      currency: state.checkout.prices[0].currency,
      placeId: state.checkout.prices[0].placeId,
      profileId: state.userProfile.id,
      ref: state.checkout.reference.ref
    }

    console.log(opts)
    doCheckout()
  }

  function doCheckout() {
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
        bookingSuccess()
      })
    }
    opts.onError = function() {
      $scope.$apply(function() {
        bookingFailed()
      })
    }
    opts.button = '#checkout-paypal-button'

    getAccount(opts.placeId, opts.currency, function(/*json*/ attrs) {
      opts.env = attrs.paypal_env
      opts.appKeySandbox = attrs.paypal_skey
      opts.appKeyProduction = attrs.paypal_pkey

      if (!opts.env) bookingFailed()
      else paypalService.checkout(opts)
    })

  }

  function bookingFailed() {
    $scope.status = 'failed'
  }

  function bookingSuccess() {
    $scope.reference = state.checkout.reference
    $scope.status = 'success'
    state.checkout.complete = true
  }

  function getAccount(/*str*/ placeId, /*str*/ currency, /*fn*/ callback) {
    apiPaymentsService.getPlaceAccount(placeId, function(/*Account*/ account) {
      var attrs = account.currencyIn(currency).attributes
      callback(attrs)
    })
  }

})
