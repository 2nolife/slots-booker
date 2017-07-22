app.controller('checkoutController', function($scope, state, sb_apiBookingService, sb_apiPaymentsService, $location) {

  var basket, user, place, balance, currency, lake, member, prices, credit

  if (state.checkoutBasket && !state.checkoutBasket.complete) {
    basket = state.checkoutBasket
    user = basket.user
    place = basket.place
    lake = basket.lake

    $scope.basket = basket
    $scope.lake = lake
    $scope.user = user

    makePrintDatetme()

    user.memberFor(place.id, false, function() {
      user.balanceFor(place.id, true, function() {
        lake.refreshEffectivePrices(false, function() {
          makePreData()
        })
      })
    })
  }

  function makePrintDatetme() {
    var date = basket.slots[0].dateFrom,
        str = ''+date
    $scope.printDatetime =
      'on ' +sb.utils.weekdayAsWord(date)+' '+str.substr(6, 2)+' '+sb.utils.monthAsWord(date)+' '+str.substr(0, 4)+
      ' at ' + basket.slots.map(function(/*MySlot*/ slot) { return sb.utils.formatTime(slot.timeFrom) }).join(' and ')
  }

  function makePreData() {
    member = user.members[place.id]
    balance = user.balances[place.id]

    var hash = {}
    lake.effectivePrices.forEach(function(/*Price*/ price) {
      if (price.memberLevel <= member.level) {
        var existing = hash[price.name]
        if (!existing || existing.memberLevel < member.level) hash[price.name] = price
      }
    })

    prices = Object.keys(hash).map(function(key) { return hash[key] })
    prices.sort(function(a, b) { return a.name < b.name ? -1 : a.name > b.name ? 1 : 0 })
    currency = prices[0].currency
    credit = balance.creditIn(currency)

    var minPrice
    prices.forEach(function(/*Price*/ price) {
      if (!minPrice || minPrice.amount > price.amount) minPrice = price
    })
    lake.selectedPrice = minPrice

    $scope.currency = currency
    $scope.credit = credit
    $scope.prices = prices
    $scope.member = member
  }

  $scope.completeBooking = function() {
     var price = lake.selectedPrice,
         expectedAmount = price.amount*basket.slots.length
    if (credit.amount < expectedAmount) bookingFailed2('Not enough credit to pay for the booking')
    else doBooking()
  }

  function doBooking() {
    $scope.status = 'progress'

    getQuote(function(/*Quote*/ quote) {
      bookSlots(quote, function(/*Reference*/ reference) {
        payWithCredit(reference, bookingSuccess)
      })
    })
  }

  function getQuote(/*fn*/ callback) {
    var price = lake.selectedPrice,
        slotsAnsPrices = basket.slots.map(function(/*MySlot*/ slot) {
          return { slot_id: slot.id, price_id: price.id }
        }),
        expectedAmount = price.amount*basket.slots.length

    sb_apiBookingService.quote(
      { selected: slotsAnsPrices },
      function(/*Quote*/ quote) {
        if (quote.amount != expectedAmount) bookingFailed2('Bug, the quote did not match the price')
        else callback(quote)
      },
      function statusCallback(/*str*/ status, /*obj*/ response) {
        if (status == 'error') bookingFailed(response)
        return true // handled error
      })
  }

  function bookSlots(/*Quote*/ quote, /*fn*/ callback) {
    sb_apiBookingService.book(
      { quote_id: quote.id },
      function(/*Reference*/ reference) {
        callback(reference)
      },
      function statusCallback(/*str*/ status, /*obj*/ response) {
        if (status == 'error') bookingFailed(response)
        return true // handled error
      })
  }

  function payWithCredit(/*Reference*/ reference, /*fn*/ callback) {
    sb_apiPaymentsService.processReference(
      { ref: reference.ref },
      function() {
        callback(reference)
      },
      function statusCallback(/*str*/ status, /*obj*/ response) {
        if (status == 'error') bookingFailed(response)
        return true // handled error
      })
  }

  function bookingFailed(/*obj*/ response) {
    var apiCode = sb.utils.apiCodeFromResponse(response)
    $scope.status = 'failed'
    $scope.statusText = apiCode.text
  }

  function bookingFailed2(/*str*/ code) {
    $scope.status = 'failed'
    $scope.statusText = code
  }

  function bookingSuccess(/*Reference*/ reference) {
    $scope.status = 'success'
    $scope.reference = reference
    basket.complete = true
  }

  $scope.cancel = function() {
    $location.path('/day')
  }

  $scope.backToDay = function() {
    $location.path('/day')
  }

})
