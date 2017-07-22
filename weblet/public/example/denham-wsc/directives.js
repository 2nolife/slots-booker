app.directive('menuTabs', function($location) {

  return {

    template:
      '<ul class="nav nav-tabs {{class}}">' +
      '  <li ng-repeat="name in names" >' +
      '    <a href ng-click="go($index)">{{name}}</a>' +
      '  </li>' +
      '</ul>',
      
    restrict: 'E',
    replace: true,

    scope: {
      tabs: '@', /*csv*/
      pages: '@', /*csv*/
      class: '@' /*str*/
    },

    link: function(scope, element, attrs) {
      scope.$watch('tabs', function(newValue, oldValue) {
        if (newValue) {
          scope.selectedIndex = -1
          scope.names = newValue.split(',')
        }
      })

      var pages = []

      scope.$watch('pages', function(newValue, oldValue) {
        if (newValue) {
          pages = newValue.split(',')
        }
      })

      scope.go = function(/*num*/ index) {
        $location.path('/'+pages[index])
      }
    }

  }
})
