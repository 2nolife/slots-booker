app.controller('refundController', function($scope, state, apiBookingService, apiPaymentsService) {

  if (state.refund && !state.refund.complete) {
    $scope.basket = state.refund
    getRefund()
  }

  function refundFailed() {
    $scope.status = 'failed'
  }

  function refundSuccess(/*Reference*/ reference) {
    $scope.reference = reference
    $scope.status = 'success'
    state.refund.complete = true
  }

  function getRefund() {
    $scope.status = 'count'
    var slotIds = state.refund.slots.map(function(slot) { return slot.id })

    apiBookingService.refund(
      { slot_ids: slotIds },
      function(/*Refund*/ refund) {
        $scope.total = {
          refund: refund,
          amount: refund.amount,
          currency: refund.currency,
          count: slotIds.length
        }
        delete $scope.status
      },
      function statusCallback(/*str*/ status) {
        if (status == 'error') refundFailed()
        return true // handled error
      })
  }

  function refundSlots(/*Refund*/ refund, /*fn*/ callback) {
    apiBookingService.cancel(
      { refund_id: refund.id },
      function(/*Reference*/ reference) {
        callback(reference)
      },
      function statusCallback(/*str*/ status) {
        if (status == 'error') refundFailed()
        return true // handled error
      })
  }

  function refundToCredit(/*Reference*/ reference, /*fn*/ callback) {
    apiPaymentsService.processReference(
      { ref: reference.ref },
      function() {
        callback(reference)
      },
      function statusCallback(/*str*/ status) {
        if (status == 'error') refundFailed()
        return true // handled error
      })
  }

  $scope.confirm = function() {
    $scope.status = 'progress'

    refundSlots($scope.total.refund, function(/*Reference*/ reference) {
      refundToCredit(reference, refundSuccess)
    })

  }

})
