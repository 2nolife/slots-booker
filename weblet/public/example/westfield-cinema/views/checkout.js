app.controller('checkoutController', function($scope, state, apiBookingService, apiPaymentsService, notifyService) {

  if (state.checkout && !state.checkout.complete) {
    $scope.basket = state.checkout
    var price = state.checkout.prices[0]
    $scope.cinemaHall = price.cinemaSlot.cinemaSeat.cinemaHall
    $scope.cinemaSlot = price.cinemaSlot

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

    apiBookingService.quote(
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
    apiBookingService.book(
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
    apiPaymentsService.processReference(
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
    notifyService.featureNotImplemented()
//    $scope.status = 'progress'
//
//    getQuote(function(/*Quote*/ quote) {
//      bookSlots(quote, function(/*Reference*/ reference) {
//        todo update balance and payWithCredit
//      })
//    })
  }

  function getCredit(/*str*/ placeId, /*str*/ currency) {
    apiPaymentsService.getUserBalance(placeId, null, function(/*Balance*/ balance) {
      $scope.credit = balance.creditIn(currency)
    })
  }

})
