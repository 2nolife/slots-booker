app.service('placePropertiesService', function($http, notifyService, $q) {
  var service = this

  //todo service placeholder

})

app.directive('placeProperties', function(placePropertiesService, notifyService, apiPlacesService) {

  var controller = function($scope) {

    function EditedPlace(/*Place*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.name = source.name
        _this.attributes = source.attributes
        _this.url = _this.attributes.url
        _this.email = _this.attributes.email
        _this.primaryNumber = _this.attributes.primary_number
        _this.address = source.address
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.refresh = function(/*fn*/ callback) {
        source.refresh('*', true, callback)
      }

      this.toApiPlaceEntity = function() {
        return {
          name: _this.name,
          attributes: {
            url: _this.url,
            email: _this.email,
            primary_number: _this.primaryNumber
          },
          address: {
            line1: _this.address.line1,
            line2: _this.address.line2,
            line3: _this.address.line3,
            postcode: _this.address.postcode,
            town: _this.address.town,
            country: _this.address.country
          }
        }
      }

    }

    $scope.saveEditedPlace = function() {
      apiPlacesService.patchPlace($scope.editedPlace.id, $scope.editedPlace.toApiPlaceEntity(), function() {
        $scope.editedPlace.refresh()
      })
    }

    $scope.setEditedPlace = function() {
      $scope.editedPlace = new EditedPlace($scope.place)
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      place: '=' /*Place*/
    },

    templateUrl: 'views/templates/placeProperties.html',

    controller: controller,

    link: function(scope, element, attrs) {
      function trigger() {
        delete scope.editedPlace
        if (scope.place) scope.setEditedPlace()
      }

      scope.$watch('trigger', function(newValue, oldValue) {
        trigger()
      })
      scope.$watch('place', function(newValue, oldValue) {
        trigger()
      })
    }

  }
})

app.directive('placePropertiesCollapse', function(modalDialogService) {
  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      place: '=' /*Place*/
    },

    templateUrl: 'views/templates/placePropertiesCollapse.html'

  }
})
