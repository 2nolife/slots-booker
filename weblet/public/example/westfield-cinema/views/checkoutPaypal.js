app.controller('checkoutPaypalController', function($scope, state, config, paypalService) {

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
    opts.env = 'sandbox'
    opts.appKeySandbox = config.paypal_sandbox_key
    opts.appKeyProduction = config.paypal_production_key
    opts.button = '#checkout-paypal-button'

    paypalService.checkout(opts)
  }

  function bookingFailed() {
    $scope.status = 'failed'
  }

  function bookingSuccess() {
    $scope.reference = state.checkout.reference
    $scope.status = 'success'
    state.checkout.complete = true
  }

})
