app.directive('onAdminRole', function(state) {

  function userInRole(/*str*/ role) {
    return state.userProfile != null && state.userProfile.roles.indexOf(role) != -1
  }

  return {

    restrict: 'A',

    link: function(scope, element, attrs) {
      if (userInRole('ADMIN')) element.css('display', 'none')
    }

  }
})

app.directive('randomScopeID', function(state) {

  return {

    restrict: 'A',

    link: function(scope, element, attrs) {
      attrs.$set('id', randomScopeID())
    }

  }
})

app.directive('datepicker', function () {
  return {

    restrict: 'A',
    require: 'ngModel',

    link: function (scope, element, attrs, ctrl) {
      element.datepicker({
        dateFormat: 'dd/mm/yy',
        onSelect: function (date) {
          ctrl.$setViewValue(date)
          scope.$apply()
        }
      })
    }

  }
})

app.directive('includeReplace', function () {
  return {

    restrict: 'A',
    require: 'ngInclude',

    link: function (scope, element, attrs) {
      element.replaceWith(element.children())
    }

  }
})

