app.directive('sbOnAdminRole', function(state) {

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

app.directive('sbRandomScopeID', function(state) {

  return {

    restrict: 'A',

    link: function(scope, element, attrs) {
      attrs.$set('id', randomScopeID())
    }

  }
})

app.directive('sbDatepicker', function () {
  return {

    restrict: 'A',
    require: 'ngModel',

    link: function (scope, element, attrs, ctrl) {
      element.datepicker({
        dateFormat: 'dd/mm/yy',
        firstDay: 1,
        onSelect: function (date) {
          ctrl.$setViewValue(date)
          scope.$apply()
        }
      })
    }

  }
})

app.directive('sbIncludeReplace', function () {
  return {

    restrict: 'A',
    require: 'ngInclude',

    link: function (scope, element, attrs) {
      element.replaceWith(element.children())
    }

  }
})

