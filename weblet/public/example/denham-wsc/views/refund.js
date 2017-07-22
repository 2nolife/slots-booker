app.controller('refundController', function($scope, state, sb_apiBookingService, sb_apiPaymentsService, sb_notifyService, $location) {

  var basket, user, lake

  if (state.refundBasket && !state.refundBasket.complete) {
    basket = state.refundBasket
    user = basket.user
    lake = basket.lake

    $scope.basket = basket
    $scope.lake = lake
    $scope.user = user

    makePrintDatetme()

    getRefund()
  }

  function makePrintDatetme() {
    var date = basket.slots[0].dateFrom,
        str = ''+date
    $scope.printDatetime =
      'on ' +sb.utils.weekdayAsWord(date)+' '+str.substr(6, 2)+' '+sb.utils.monthAsWord(date)+' '+str.substr(0, 4)+
      ' at ' + basket.slots.map(function(/*MySlot*/ slot) { return sb.utils.formatTime(slot.timeFrom) }).join(' and ')
  }

  function getRefund() {
    $scope.status = 'count'
    var slotIds = basket.slots.map(function(slot) { return slot.id })

    sb_apiBookingService.refund(
      { slot_ids: slotIds },
      function(/*Refund*/ refund) {
        $scope.refund = refund
        delete $scope.status
      },
      function statusCallback(/*str*/ status, /*obj*/ response) {
        if (status == 'error') refundFailed(response)
        return true // handled error
      })
  }

  $scope.completeRefund = function() {
    doRefund()
  }

  function doRefund() {
    $scope.status = 'progress'

    refundSlots($scope.refund, function(/*Reference*/ reference) {
      refundToCredit(reference, refundSuccess)
    })
  }

  function refundSlots(/*Refund*/ refund, /*fn*/ callback) {
    sb_apiBookingService.cancel(
      { refund_id: refund.id },
      function(/*Reference*/ reference) {
        callback(reference)
      },
      function statusCallback(/*str*/ status, /*obj*/ response) {
        if (status == 'error') refundFailed(response)
        return true // handled error
      })
  }

  function refundToCredit(/*Reference*/ reference, /*fn*/ callback) {
    sb_apiPaymentsService.processReference(
      { ref: reference.ref },
      function() {
        callback(reference)
      },
      function statusCallback(/*str*/ status, /*obj*/ response) {
        if (status == 'error') refundFailed(response)
        return true // handled error
      })
  }

  function refundFailed(/*obj*/ response) {
    var apiCode = sb.utils.apiCodeFromResponse(response)
    $scope.status = 'failed'
    $scope.statusCode = apiCode.code
    $scope.statusText = apiCode.code
  }

  function refundFailed2(/*str*/ code) {
    $scope.status = 'failed'
    $scope.statusCode = code
    $scope.statusText = code
  }

  function refundSuccess(/*Reference*/ reference) {
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
