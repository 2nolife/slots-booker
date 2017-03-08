app.controller('managePlacesController', function($scope, managePlacesService, apiPlacesService, apiSpacesService, apiUsersService,
                                                  notifyService, $timeout, modalDialogService, config) {

  function NewPlace() {

    var _this = this

    this.name = null

    this.template = {
      rootSpaces: 1,
      innerSpaces: 5
    }

    this.toApiPlaceEntity = function() {
      return {
        name: _this.name
      }
    }

    this.toApiRootSpaceEntities = function() {
      var entities = []
      for (var n = 1; n <= _this.template.rootSpaces; n++)
        entities.push({ name: _this.name+'. Space '+n })
      return entities
    }

    this.toApiInnerSpaceEntities = function(/*Space*/ parentSpace) {
      var entities = []
      for (var n = 1; n <= _this.template.innerSpaces; n++)
        entities.push({ name: parentSpace.name+'.'+n })
      return entities
    }

  }

  function EditedPlace(/*Place*/ source) {

    var _this = this

    this.source = source

    function applyChangesFromSource() {
      _this.id = source.id
    }

    applyChangesFromSource()

    source.onChangeCallback.add(applyChangesFromSource)

    this.refresh = function(/*fn*/ callback) {
      source.refresh('*', true, callback)
    }

  }

  $scope.loadPlaces = function(/*bool*/ force) {
    $scope.loadPlacesSpin = true
    managePlacesService.loadPlaces(function(/*[Place]*/ places) {

      $scope.managedPlaces = places

      $timeout(function() { $scope.loadPlacesSpin = false }, 1000)

    }, force)
  }

  $scope.showAddPlaceFragment = function() {
    $scope.newPlace = new NewPlace()
    addPlaceDialog.show()
  }

  $scope.submitNewPlace = function() {
    managePlacesService.addPlace($scope.newPlace, function() {
      notifyService.notify('New place added', 'success')
      $scope.loadPlaces(true);
    });
  }

  var findPlaceByIdToEdit = function(/*str*/ id) {
    $scope.editedPlace = null

    var places = $.grep($scope.managedPlaces, function(place) { return place.id == id })
    if (places.length > 0) $scope.editedPlace = new EditedPlace(places[0])

    return $scope.editedPlace
  }

  $scope.showEditPlaceFragment = function(/*str*/ placeId) {
    findPlaceByIdToEdit(placeId)
  }

  $scope.showManagePlacesFragment = function() {
    delete $scope.editedPlace
  }

  $scope.refreshEditedPlace = function() {
    $scope.refreshEditedPlaceSpin = true

    $scope.editedPlace.refresh(function() {

      findPlaceByIdToEdit($scope.editedPlace.id)
      $timeout(function() { $scope.refreshEditedPlaceSpin = false }, 1000)

    })
  }

  $scope.spaceManagerListener = function(/*str*/ key, /*any*/ arg) {
    //console.log('Event received: '+key)
    switch (key) {
      case 'space.selected':
        $scope.selectedSpace = arg
        break
    }
  }

  var addPlaceDialog = modalDialogService.registerDialog('#manage-places-add-dialog')

  $scope.loadPlaces()

})

app.service('managePlacesService', function($http, apiPlacesService, notifyService, $q) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.loadPlaces = function(/*fn*/ callback, /*bool*/ force) {
    if (force || !service.cachedPlaces) {
      apiPlacesService.findPlaces(function(/*[Place]*/ places) {

          service.cachedPlaces = places
          callback(service.cachedPlaces)

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

  service.addPlace = function(/*obj*/ newPlace, /*fn*/ callback) {
    $http.post('/api/places', newPlace.toApiPlaceEntity())
      .then(
        function successCallback(response) {
          var place = response.data

          createRootSpaces(place.place_id, newPlace, function(/*[json]*/ spaces) {
            createInnerSpaces(place.place_id, spaces, newPlace, function() {
              callback()
            })
          })

        },
        function errorCallback(response) {
          notifyResponseError(response)
        })

    var createRootSpaces = function(/*str*/ placeId, /*obj*/ newPlace, /*fn*/ callback) {
      var promises = newPlace.toApiRootSpaceEntities().map(function(entity) {
        return $http.post('/api/places/'+placeId+'/spaces', entity)
      })

      $q.all(promises).then(
        function successCallback(responses) {
          var spaces = responses.map(function(response) {
            return response.data
          })
          callback(spaces)
        },
        function errorCallback(responses) {
          notifyResponseError(responses[0])
        })
    }

    var createInnerSpaces = function(/*str*/ placeId, /*[str]*/ spaces, /*obj*/ newPlace, /*fn*/ callback) {
      var promises = spaces.map(function(space) {
        return newPlace.toApiInnerSpaceEntities(space).map(function(entity) {
          return $http.post('/api/places/'+placeId+'/spaces/'+space.space_id, entity)
        })
      })

      $q.all([].concat.apply([], promises)).then(
        function successCallback(responses) {
          var spaces = responses.map(function(response) {
            return response.data
          })
          callback(spaces)
        },
        function errorCallback(responses) {
          notifyResponseError(responses[0])
        })
    }
  }

})
