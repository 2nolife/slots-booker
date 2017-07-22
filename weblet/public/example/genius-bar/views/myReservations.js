app.controller('myReservationsController', function($scope, placesService) {

  placesService.getPlace(function(/*Place*/ place) {
    $scope.myPlace = place
  })

  $scope.onSubmitBookedSlot = function(/*Space*/ space, /*Slot*/ slot) {
    $scope.mySpace = space
    $scope.mySlot = slot
  }

  $scope.onCancelBooking = function() {
    $scope.bookingCancelled = true
  }

  $scope.showMyReservationsFragment = function() {
    delete $scope.mySpace
    delete $scope.mySlot
    delete $scope.bookingCancelled
  }

});

app.directive('myReservationsSelectBooking', function() {

  var controller = function($scope, $timeout) {

    function MyPlace(/*Place*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function wrapSpaces() {
        _this.spaces = (source.spaces || []).map(function(space) { return new MySpace(space) })
      }

      this.refreshBookedSlots = function() {
        $scope.bookedSlots = []
        source.refresh('spaces', true, function(/*str*/ status) {
          if (status == 'success') {
            refreshSpacesInProgress = source.spaces.length
            wrapSpaces()
          }
        })
      }
    }

    function MySpace(/*Space*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.name = source.name
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function bookedSlotsByDate(/*num*/ daysInAdvance) {
        var today = parseInt(sb.utils.todayDate()),
            plus6 = parseInt(sb.utils.addDaysDate(today, daysInAdvance)),
            yesterday = parseInt(sb.utils.addDaysDate(today, -1))
        source.slotsFilter = { from: yesterday, to: plus6, booked: '' }
        source.refreshRetry('slots', true, function(/*str*/ status) {
          if (status == 'success') {
            wrapSlots()
            refreshSpaceReady()
          }
        })
      }

      function wrapSlots() {
        _this.slots = (source.slots || []).map(function(slot) {
          var mySlot = new MySlot(slot)
          mySlot.setParentSpace(_this)
          $scope.bookedSlots.push(mySlot)
          return mySlot
        })
      }

      bookedSlotsByDate(6)
    }

    function MySlot(/*Slot*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.dateFrom = source.dateFrom
        _this.dateTo = source.dateTo
        _this.timeFrom = source.timeFrom
        _this.timeTo = source.timeTo
        _this.status = slotStatus()
        _this.shortDate = shortDate()
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function slotStatus() {
        var now = new Date(),
            todayDatetime = sb.utils.datetime(sb.utils.todayDate(), now.getHours()*100+now.getMinutes()),
            slotDatetime = sb.utils.datetime(_this.dateFrom, _this.timeFrom),
            status = ''
       if (sb.utils.datetimeCompare(slotDatetime, todayDatetime) <= 0) status = 'late'

        return status
      }

      function shortDate() {
        return sb.utils.weekdayAsWord(_this.dateFrom)+', '+sb.utils.strToDate(_this.dateFrom).getDate()+' '+sb.utils.monthAsWord(_this.dateFrom)
      }

      this.setParentSpace = function(/*MySpace*/ space) {
        _this.parentSpace = space
      }
    }

    var refreshSpacesInProgress = 0

    function refreshSpaceReady() {
      if (--refreshSpacesInProgress <= 0)
        $timeout(function() { $scope.refreshSlotsSpin = false }, 1000)
    }

    $scope.refreshBookedSlots = function() {
      $scope.refreshSlotsSpin = true
      $scope.myPlace.refreshBookedSlots()
    }

    $scope.setMyPlace = function(/*Place*/ place) {
      $scope.myPlace = new MyPlace(place)
      $scope.refreshBookedSlots()
    }

    $scope.submitSlot = function(/*MySlot*/ slot) {
      $scope.onSubmit({ space: slot.parentSpace, slot: slot.source })
    }

  }

  return {

    restrict: 'E',

    scope: {
      place: '=', /*Place*/
      onSubmit: '&'
    },

    templateUrl: 'views/templates/myReservationsSelectBooking.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('place', function(newValue, oldValue) {
        if (newValue) scope.setMyPlace(newValue)
      })
    }

  }
})

app.directive('myReservationsBookingInfo', function() {

  var controller = function($scope, $timeout, sb_apiBookingService) {

    function MySlot(/*Slot*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.dateFrom = source.dateFrom
        _this.timeFrom = source.timeFrom
        _this.shortDate = shortDate()
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function shortDate() {
        return sb.utils.weekdayAsWord(_this.dateFrom)+', '+sb.utils.strToDate(_this.dateFrom).getDate()+' '+sb.utils.monthAsWord(_this.dateFrom)
      }

      this.toApiCancelEntity = function() {
        return {
          slot_ids: [_this.id]
        }
      }

    }

    function bookingCancelled() {
      $scope.status = 1
      $scope.onCancel()
    }

    function bookingFailed() {
      $scope.status = 2
    }

    $scope.cancelBooking = function() {
      sb_apiBookingService.cancel(
        $scope.mySlot.toApiCancelEntity(),
        bookingCancelled,
        function statusCallback(/*str*/ status) {
          if (status == 'error') bookingFailed()
          return true // handled error
        })
    }

    $scope.setMySlot = function(/*Slot*/ slot) {
      $scope.status = 0
      $scope.mySlot = new MySlot(slot)
    }

  }

  return {

    restrict: 'E',

    scope: {
      place: '=', /*Place*/
      space: '=', /*Space*/
      slot: '=', /*Slot*/
      onCancel: '&'
    },

    templateUrl: 'views/templates/myReservationsBookingInfo.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('slot', function(newValue, oldValue) {
        if (newValue) scope.setMySlot(newValue)
      })
    }

  }
})
