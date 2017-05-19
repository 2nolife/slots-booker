app.controller('checkoutController', function($scope, state, sb_apiBookingService, sb_apiPaymentsService, sb_notifyService, $location) {

  if (state.checkout && !state.checkout.complete) {
    $scope.basket = state.checkout
    var price = state.checkout.prices[0]
    getCredit(price.placeId, price.currency)
  }

  function bookingFailed() {
    $scope.status = 'failed'
  }

  function bookingSuccess(/*Reference*/ reference) {
    $scope.reference = reference
    $scope.status = 'success'
    state.checkout.complete = true
  }

  function getQuote(/*fn*/ callback) {
    var slotsAnsPrices = $scope.basket.prices.map(function(/*CinemaPrice*/ price) {
      return { slot_id: price.cinemaSlot.id, price_id: price.id }
    })

    sb_apiBookingService.quote(
      { selected: slotsAnsPrices },
      function(/*Quote*/ quote) {
        if (quote.amount != state.checkout.amount) bookingFailed()
        else callback(quote)
      },
      function statusCallback(/*str*/ status) {
        if (status == 'error') bookingFailed()
        return true // handled error
      })
  }

  function bookSlots(/*Quote*/ quote, /*fn*/ callback) {
    sb_apiBookingService.book(
      { quote_id: quote.id },
      function(/*Reference*/ reference) {
        callback(reference)
      },
      function statusCallback(/*str*/ status) {
        if (status == 'error') bookingFailed()
        return true // handled error
      })
  }

  function payWithCredit(/*Reference*/ reference, /*fn*/ callback) {
    sb_apiPaymentsService.processReference(
      { ref: reference.ref },
      function() {
        callback(reference)
      },
      function statusCallback(/*str*/ status) {
        if (status == 'error') bookingFailed()
        return true // handled error
      })
  }

  $scope.bookWithCredit = function() {
    $scope.status = 'progress'

    getQuote(function(/*Quote*/ quote) {
      bookSlots(quote, function(/*Reference*/ reference) {
        payWithCredit(reference, bookingSuccess)
      })
    })
  }

  $scope.bookWithCard = function() {
    sb_notifyService.featureNotImplemented()
//    $scope.status = 'progress'
//
//    getQuote(function(/*Quote*/ quote) {
//      bookSlots(quote, function(/*Reference*/ reference) {
//        todo update balance and payWithCredit
//      })
//    })
  }

  $scope.bookWithPayPal = function() {
    $scope.status = 'progress'

    getQuote(function(/*Quote*/ quote) {
      bookSlots(quote, function(/*Reference*/ reference) {
        state.checkout.reference = reference
        $location.path('/checkout-paypal')
      })
    })
  }

  function getCredit(/*str*/ placeId, /*str*/ currency) {
    sb_apiPaymentsService.getUserBalance(placeId, null, function(/*Balance*/ balance) {
      $scope.credit = balance.creditIn(currency)
    })
  }

})

app.directive('checkoutShowTickets', function() {

  var controller = function($scope, state) {

    if (state.checkout && !state.checkout.complete) {
      $scope.basket = state.checkout
      var price = state.checkout.prices[0]
      $scope.cinemaHall = price.cinemaSlot.cinemaSeat.cinemaHall
      $scope.cinemaSlot = price.cinemaSlot
    }

  }

  return {

    restrict: 'E',

    templateUrl: 'views/templates/chekoutShowTickets.html',

    controller: controller
  }
})
