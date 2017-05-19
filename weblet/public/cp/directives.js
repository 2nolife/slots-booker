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
      '  <li ng-class="{active: selectedIndex == $index}" ng-repeat="name in names">' +
      '    <a href ng-click="select($index)">{{name}}</a>' +
      '  </li>' +
      '</ul>',
      
    restrict: 'E',
    replace: true,

    scope: {
      tabs: '@', /*csv*/
      indexVar: '@', /*str*/
      class: '@' /*str*/
    },

    link: function(scope, element, attrs) {
      var unwatch = scope.$watch('tabs', function(newValue, oldValue) {
        if (newValue) {
          unwatch()

          scope.selectedIndex = -1
          scope.names = newValue.split(',')

          scope.select = function(/*num*/ index) {
            scope.selectedIndex = index
            scope.$parent[scope.indexVar] = index
          }

        }
      })
    }

  }
})

app.directive('confirmDialog', function(sb_modalDialogService, $rootScope) {

  return {

    restrict: 'E',

    templateUrl: 'views/templates/confirmDialog.html',

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.confirm-dialog')
      var dialogHandle = sb_modalDialogService.registerDialog('#'+sb.utils.elementID(dialogElement))

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
      var dialogElement = element.find('.user-finder-dialog')
      var dialogHandle = sb_modalDialogService.registerDialog('#'+sb.utils.elementID(dialogElement))

      scope.$watch('trigger', function(newValue, oldValue) {
        if (newValue) dialogHandle.show()
      })
    }

  }
})
