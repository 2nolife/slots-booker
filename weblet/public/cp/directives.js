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
      '  <li ng-class="{active: selectedIndex == $index}" ng-repeat="name in names" ng-show="name">' +
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
          scope.names = newValue.split(',').map(function(v) { return $.trim(v) })

          scope.select = function(/*num*/ index) {
            scope.selectedIndex = index
            scope.$parent[scope.indexVar] = index
          }

        }
      })

      scope.$watch('resetTrigger', function(newValue, oldValue) {
        scope.select(-1)
      })
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

app.directive('userFinderDialog', function(sb_apiUsersService, sb_modalDialogService) {

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
          $scope.foundUsers = users
        })
    }

    $scope.selectUser = function(/*User*/ user) {
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

app.directive('slotsCalendar', function(sb_apiUsersService, sb_modalDialogService) {

  var controller = function($scope) {

    $scope.onCalendarSet = function() {

    }

  }

  return {

    restrict: 'E',
    replace: true,

    scope: {
      calendar: '=' /*SlotsCalendar*/
    },

    templateUrl: 'views/templates/slotsCalendar.html',

    controller: controller,

    link: function(scope, element, attrs) {
      scope.$watch('calendar', function(newValue, oldValue) {
        if (newValue) scope.onCalendarSet()
      })
    }

  }
})
