app.controller('addReservationController', function($scope, placesService) {

  placesService.getPlace(function(/*Place*/ place) {
    $scope.myPlace = place
  })

  $scope.onSubmitSpace = function(/*Space*/ space) {
    $scope.mySpace = space
  }

  $scope.onSubmitSlot = function(/*Slot*/ slot) {
    $scope.mySlot = slot
  }

  $scope.onBookedSlot = function() {
    $scope.slotBooked = true
  }

  $scope.showSelectStoreFragment = function() {
    delete $scope.mySpace
    delete $scope.mySlot
    delete $scope.slotBooked
  }

});

app.directive('addReservationSelectStore', function() {

  var controller = function($scope) {

    function MyPlace(/*Place*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.spaces = source.spaces || []

        source.refresh('spaces')
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

    }

    $scope.setMyPlace = function(/*Place*/ place) {
      $scope.myPlace = new MyPlace(place)
    }

    $scope.submitSpace = function(/*Space*/ space) {
      $scope.onSubmit({ space: space })
    }

  }

  return {

    restrict: 'E',

    scope: {
      place: '=', /*Place*/
      onSubmit: '&'
    },

    templateUrl: 'views/templates/addReservationSelectStore.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('place', function(newValue, oldValue) {
        if (newValue) scope.setMyPlace(newValue)
      })
    }

  }
})

app.directive('addReservationSelectSlot', function() {

  var controller = function($scope, $timeout) {

    function MySpace(/*Space*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function slotsByDate(/*num*/ daysInAdvance, /*fn*/ callback) {
        var today = parseInt(sb.utils.todayDate()),
            plus6 = parseInt(sb.utils.addDaysDate(today, daysInAdvance))
        source.slotsFilter = { from: today, to: plus6 }
        source.refreshRetry('slots', true, function(/*str*/ status) {
          if (status == 'success') {
            wrapSlots()
            slotsByDay(daysInAdvance)
            if (callback) callback()
          }
        })
      }

      function wrapSlots() {
        _this.slots = (source.slots || []).map(function(slot) { return new MySlot(slot) })
      }

      function slotsByDay(/*num*/ daysInAdvance) {
        var today = parseInt(sb.utils.todayDate())
            array = []
        for (var n = 0; n < daysInAdvance; n++) {
          var date = parseInt(sb.utils.addDaysDate(today, n)),
              slots = $.grep(_this.slots, function(slot) { return slot.dateFrom == date }),
              day = sb.utils.weekdayAsWord(date),
              short = sb.utils.strToDate(date).getDate()+' '+sb.utils.monthAsWord(date)

          array.push({ short: short, day: day, slots: slots })
        }

        _this.daySlots = array
      }

      this.refreshSlots = function(/*fn*/ callback) {
        slotsByDate(6, callback)
      }
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
        _this.bookStatus = source.bookStatus
        _this.status = slotStatus()
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function slotStatus() {
        var now = new Date(),
            todayDatetime = sb.utils.todayDate()+(now.getHours()*100+now.getMinutes())
            slotDatetime = ''+_this.dateFrom+_this.timeFrom,
            status = 'free'
        if (sb.utils.datetimeCompare(slotDatetime, todayDatetime) <= 0) status = 'late'
        if (_this.bookStatus != 0) status = 'booked'

        return status
      }

    }

    $scope.refreshSlots = function() {
      $scope.refreshSlotsSpin = true
      $scope.mySpace.refreshSlots(function() {

        $timeout(function() { $scope.refreshSlotsSpin = false }, 1000)

      })
    }

    $scope.setMySpace = function(/*Space*/ space) {
      $scope.mySpace = new MySpace(space)
      $scope.refreshSlots()
    }

    $scope.submitSlot = function(/*MySlot*/ slot) {
      $scope.onSubmit({ slot: slot.source })
    }

  }

  return {

    restrict: 'E',

    scope: {
      space: '=', /*Space*/
      onSubmit: '&'
    },

    templateUrl: 'views/templates/addReservationSelectSlot.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('space', function(newValue, oldValue) {
        if (newValue) scope.setMySpace(newValue)
      })
    }

  }
})

app.directive('addReservationBookSlot', function() {

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

      this.toApiBookEntity = function() {
        return {
          slot_ids: [_this.id]
        }
      }

    }

    function slotBooked() {
      $scope.status = 1
      $scope.onSuccess()
    }

    function slotFailed() {
      $scope.status = 2
    }

    function book() {
      sb_apiBookingService.book(
        $scope.mySlot.toApiBookEntity(),
        slotBooked,
        function statusCallback(/*str*/ status) {
          if (status == 'error') slotFailed()
          return true // handled error
        })
    }

    $scope.setMySlot = function(/*Slot*/ slot) {
      $scope.status = 0
      $scope.mySlot = new MySlot(slot)
      book()
    }

  }

  return {

    restrict: 'E',

    scope: {
      place: '=', /*Place*/
      space: '=', /*Space*/
      slot: '=', /*Slot*/
      onSuccess: '&'
    },

    templateUrl: 'views/templates/addReservationBookSlot.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('slot', function(newValue, oldValue) {
        if (newValue) scope.setMySlot(newValue)
      })
    }

  }
})
