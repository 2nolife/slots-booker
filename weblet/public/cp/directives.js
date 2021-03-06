app.directive('topHeader', function() {

  return {

    template: '<h1 id="top-header">{{header || "&nbsp;"}}</h1>',
    restrict: 'E',
    replace: true,

    link: function(scope, element, attrs) {
      scope.$on('$viewContentLoaded', function() {
        scope.header = $('h1.top').text()
      })
    }

  }
})

app.directive('triggeredNavTabs', function() {

  return {

    template:
      '<ul class="nav nav-tabs {{class}}">' +
      '  <li ng-class="{active: selectedIndex == $index}" ng-repeat="name in names" ng-show="nan(name)">' +
      '    <a href ng-click="select($index)">{{name}}</a>' +
      '  </li>' +
      '</ul>',
      
    restrict: 'E',
    replace: true,

    scope: {
      tabs: '@', /*csv*/
      indexVar: '@', /*str*/
      class: '@', /*str*/
      resetTrigger: '=' /*any*/
    },

    link: function(scope, element, attrs) {
      var unwatch = scope.$watch('tabs', function(newValue, oldValue) {
        if (newValue) {
          unwatch()

          scope.selectedIndex = -1
          scope.names = newValue.split(',').map(function(v,n) {
            v = $.trim(v)
            return v ? v : n
          })

          scope.select = function(/*num*/ index) {
            scope.selectedIndex = index
            scope.$parent[scope.indexVar] = index
          }

        }
      })

      scope.$watch('resetTrigger', function(newValue, oldValue) {
        scope.select(-1)
      })

      scope.nan = function(v) {
        return isNaN(v)
      }
    }

  }
})

app.directive('confirmDialog', function(sb_modalDialogService, $rootScope) {

  return {

    restrict: 'E',

    templateUrl: 'views/templates/confirmDialog.html',

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.confirm-dialog', element, sb_modalDialogService)

      function show(/*obj*/ data) {
        scope.text = data.text
        scope.onConfirm = data.onConfirm
        dialogHandle.show()
      }

      $rootScope.$on('dialog.confirm', function(event, data) {
        scope.mode = 'confirm'
        scope.title = 'Confirm'
        show(data)
      })
      $rootScope.$on('dialog.delete', function(event, data) {
        scope.mode = 'delete'
        scope.title = 'Delete'
        show(data)
      })
      $rootScope.$on('dialog.info', function(event, data) {
        scope.mode = 'info'
        scope.title = 'Info'
        show(data)
      })
    }

  }
})

app.directive('userFinderDialog', function(sb_apiUsersService, sb_apiMembersService, sb_modalDialogService) {

  var controller = function($scope) {

    $scope.searchUser = function() {
      delete $scope.selectedUser
      
      var attrs = sb.utils.attributeNamesValues(['first_name', 'last_name'], ($scope.searchValue || '').split(' '))
      if (attrs.length > 0)
        sb_apiUsersService.findUsers({
          attributes: attrs,
          join: 'or'
        },
        function(/*[User]*/ users) {
          $scope.foundUsers = users.map(function(user) { return new cp.classes.EditedUser(user) })

          if ($scope.placeId) {
            $scope.foundUsers.forEach(function(user) {
              user.memberFor($scope.placeId)
            })
          }

        })
    }

    $scope.selectUser = function(/*EditedUser*/ user) {
      $scope.selectedUser = user
    }

    $scope.submit = function() {
      if ($scope.selectedUser) $scope.onUser({ user: $scope.selectedUser })
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=',
      placeId: '=', /*str*/
      onUser: '&'
    },

    templateUrl: 'views/templates/userFinderDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.user-finder-dialog', element, sb_modalDialogService)

      scope.$watch('trigger', function(newValue, oldValue) {
        if (newValue) dialogHandle.show()
      })
    }

  }
})

app.directive('slotsCalendar', function() {

  var controller = function($scope) {

    $scope.clicked = function(/*Slot*/ slot) {
      slot.selected = true
      if ($scope.onSlot) $scope.onSlot({ slot: slot })
    }

    $scope.onCalendarSet = function() {

    }

  }

  return {

    restrict: 'E',
    replace: true,

    scope: {
      calendar: '=', /*SlotsCalendar*/
      onSlot: '&'
    },

    templateUrl: 'views/templates/slotsCalendarFragment.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('calendar', function(newValue, oldValue) {
        if (newValue) scope.onCalendarSet()
      })
    }

  }
})

app.directive('slotsTable', function() {

  var controller = function($scope) {

    $scope.toggle = function() {
      $scope.slots.forEach(function(slot) {
        slot.selected = $scope.toggled
      })
    }

    $scope.clicked = function(/*Slot*/ slot) {
      if ($scope.onSlot) $scope.onSlot({ slot: slot })
    }

  }

  return {

    restrict: 'E',
    replace: true,

    scope: {
      slots: '=', /*[Slot]*/
      onSlot: '&'
    },

    templateUrl: 'views/templates/slotsTableFragment.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('slots', function(newValue, oldValue) {
        delete scope.toggled
      })
    }

  }
})

app.directive('listedPlaces', function() {

  var controller = function($scope) {

    var handles = {}

    function placeChange(/*str*/ key, /*EditedPlace|Place*/ place) {
      //console.log('place change invoked: '+place.id+' '+key)
      var obj = place instanceof cp.classes.EditedPlace ? place.source : place
      obj.refresh('owner')
    }

    function removePlaceChangeHandle(/*EditedPlace*/ place) {
      var key = 'placeChange_'+place.id
      if (handles[key]) place.onChangeCallback.remove(handles[key])
    }

    function setPlaceChangeHandle(/*EditedPlace*/ place) {
      var key = 'placeChange_'+place.id
      removePlaceChangeHandle(key)
      handles[key] = place.onChangeCallback.add(placeChange)
    }

    $scope.$on('$destroy', function() {
      ($scope.places || []).forEach(function(place) {
        removePlaceChangeHandle(place)
      })
    })

    $scope.onPlacesSet = function() {
      $scope.places.forEach(function(place) {
        placeChange('?', place)
        setPlaceChangeHandle(place)
      })
    }

  }

  return {

    restrict: 'E',
    replace: true,

    scope: {
      places: '=', /*[EditedPlace]*/
      goLink: '@', /*str*/
      goTitle: '@' /*str*/
    },

    templateUrl: 'views/templates/listedPlacesFragment.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('places', function(newValue, oldValue) {
        if (newValue) scope.onPlacesSet()
      })
    }

  }
})

app.directive('dateFilter', function() {

  var controller = function($scope) {

    $scope.searchPeriod = 'day'

    function parse() {
      return {
        from: sb.utils.parseDate($scope.dates.from || '') || sb.utils.todayDate(),
        to: sb.utils.parseDate($scope.dates.to || '') || sb.utils.todayDate()
      }
    }

    $scope.change = function(/*str*/ action) {
      var parsed = parse()
      var fromDate = parsed.from, toDate = parsed.to
      var day = parsed.from, mid = sb.utils.monthMidDate(day), today = sb.utils.todayDate(), todayMid = sb.utils.monthMidDate(today)

      if (action == 'day') fromDate = toDate = today
      if (action == 'day-1') fromDate = toDate = sb.utils.addDaysDate(day, -1)
      if (action == 'day+1') fromDate = toDate = sb.utils.addDaysDate(day, +1)
      if (action == 'month') fromDate = sb.utils.monthFirstDate(todayMid), toDate = sb.utils.monthLastDate(todayMid)
      if (action == 'month-1') fromDate = sb.utils.monthFirstDate(sb.utils.addDaysDate(mid, -30)), toDate = sb.utils.monthLastDate(sb.utils.addDaysDate(mid, -30))
      if (action == 'month+1') fromDate = sb.utils.monthFirstDate(sb.utils.addDaysDate(mid, +30)), toDate = sb.utils.monthLastDate(sb.utils.addDaysDate(mid, +30))
      if (action == 'week') fromDate = sb.utils.weekFirstDate(today), toDate = sb.utils.weekLastDate(today)
      if (action == 'week-1') fromDate = sb.utils.weekFirstDate(sb.utils.addDaysDate(day, -7)), toDate = sb.utils.weekLastDate(sb.utils.addDaysDate(day, -7))
      if (action == 'week+1') fromDate = sb.utils.weekFirstDate(sb.utils.addDaysDate(day, +7)), toDate = sb.utils.weekLastDate(sb.utils.addDaysDate(day, +7))

      $scope.dates.from = sb.utils.formatDate(fromDate)
      $scope.dates.to = sb.utils.formatDate(toDate)
      $scope.submit()
    }

    $scope.submit = function() {
      var parsed = parse()
      $scope.onChange({ from: parsed.from, to: parsed.to })
    }

    $scope.onDatesSet = function(/*str|num*/ from, /*str|num*/ to) {
      $scope.dates = { from: sb.utils.formatDate($scope.from), to: sb.utils.formatDate($scope.to) }
    }

  }

  return {

    restrict: 'E',
    replace: true,

    scope: {
      from: '=', /*str|num*/
      to: '=', /*str|num*/
      onChange: '&'
    },

    templateUrl: 'views/templates/dateFilterFragment.html',

    controller: controller,

    link: function(scope, element, attrs) {
      function trigger() { if (scope.from && scope.to) scope.onDatesSet() }
      scope.$watch('from', trigger)
      scope.$watch('to', trigger)
    }

  }
})

app.directive('slotsPricesDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    function refreshPrices() {
      var slots = $scope.slots,
          n = slots.length
      slots.forEach(function(/*EditedSlot*/ slot) {
        slot.refreshEffectivePrices(false, function() {
          var selected = $.grep(slot.effectivePrices, function(price) { return price == slot.selectedPrice })
          if (!selected.length && slot.effectivePrices.length) slot.selectedPrice = slot.effectivePrices[0]
        })
      })
    }

    $scope.onSlotsSet = function() {
      refreshPrices()
    }

    $scope.onUserOrPlaceSet = function() {
      delete $scope.member
      if ($scope.user && $scope.placeId) {
        $scope.user.memberFor($scope.placeId, false, function() {
          $scope.member = $scope.user.members[$scope.placeId]
        })
        $scope.user.balanceFor($scope.placeId, false, function() {
          $scope.balance = $scope.user.balances[$scope.placeId]
        })
      }
    }

    $scope.submit = function() {
      $scope.onSubmit()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=',
      slots: '=', /*[EditedSlot]*/
      user: '=', /*[EditedUser]*/
      placeId: '=', /*str*/
      onSubmit: '&'
    },

    templateUrl: 'views/templates/slotsPricesDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.slots-prices-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('slots', function(newValue, oldValue) { if (newValue) scope.onSlotsSet() })
      scope.$watch('user', function(newValue, oldValue) { if (newValue) scope.onUserOrPlaceSet() })
      scope.$watch('placeId', function(newValue, oldValue) { if (newValue) scope.onUserOrPlaceSet() })
    }

  }
})


var cp = cp || {}

cp.manageDirectives = {

  editedPlaceDirective: function(/*str*/ template, /*fn*/ controller, $rootScope) {
    return {

      restrict: 'E',
      replace: true,

      scope: {
        trigger: '=', /*any*/
        editedPlace: '=' /*EditedPlace*/
      },

      templateUrl: 'views/templates/'+template+'.html',

      controller: controller,

      link: function(scope, element, attrs) {
        function hide() { scope.showContent = false }
        function show() { scope.showContent = true  }
        scope.$watch('trigger', function(newValue, oldValue) { if (newValue) show(); else hide() })
        scope.$watch('editedPlace.rid', function(newValue, oldValue) { if (newValue) scope.onEditedPlaceSet() })
        if ($rootScope) $rootScope.$on('editedPlace.reset', hide)
      }

    }
  },

  editedSpaceDirective: function(/*str*/ template, /*fn*/ controller, $rootScope) {
    return {

      restrict: 'E',
      replace: true,

      scope: {
        trigger: '=', /*any*/
        editedSpace: '=' /*EditedSpace*/
      },

      templateUrl: 'views/templates/'+template+'.html',

      controller: controller,

      link: function(scope, element, attrs) {
        function hide() { scope.showContent = false }
        function show() { scope.showContent = true  }
        scope.$watch('trigger', function(newValue, oldValue) { if (newValue) show(); else hide() })
        scope.$watch('editedSpace.rid', function(newValue, oldValue) { if (newValue) scope.onEditedSpaceSet() })
        if ($rootScope) $rootScope.$on('editedPlace.reset', hide)
        if ($rootScope) $rootScope.$on('editedSpace.reset', hide)
      }

    }
  }

}
