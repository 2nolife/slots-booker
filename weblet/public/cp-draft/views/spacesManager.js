app.service('spacesManagerService', function(apiSpacesService) {
  var service = this

  //todo service placeholder

})

app.directive('spacesManager', function(spacesManagerService, apiSpacesService, notifyService) {

  var controller = function($scope) {

    function EditedPlace(/*Place*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id

        source.refresh('spaces')
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

    }

    function NewSpace(/*str*/ parentSpaceId) {

      var _this = this

      var source = new Space({ parent_space_id: parentSpaceId })
      this.source = source

      function applyChangesFromSource() {
        _this.parentSpaceId = source.parentSpaceId
        _this.name = source.name
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.toApiSpaceEntity = function() {
        return {
          name: _this.name
        }
      }

    }

    function EditedSpace(/*Space*/ source) {

      var _this = this

      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.placeId = source.placeId
        _this.parentSpaceId = source.parentSpaceId
        _this.name = source.name
        _this.prices = source.prices || []
        _this.spaces = source.spaces || []
        _this.parentSpaces = getParentSpaces()

        source.refresh('spaces')
        source.refresh('prices')
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      function getParentSpaces() {
        var parents = [], parentSpace = findSpaceById(_this.parentSpaceId)
        for (var n = 1; n <= 100; n++)
          if (parentSpace) {
            parents.unshift(parentSpace)
            parentSpace = findSpaceById(parentSpace.parentSpaceId)
          }
        return parents
      }

      this.refreshPrices = function() {
        source.refresh('prices', true)
      }

      this.refreshSpaces = function(/*fn*/ callback) {
        source.refresh('spaces', true, callback)
      }

      this.refresh = function(/*fn*/ callback) {
        source.refresh('*', true, callback)
      }

      this.toApiSpaceEntity = function() {
        return {
          name: _this.name
        }
      }

    }

    function EditedPrice(/*Price|optional*/ source) {

      var _this = this

      source = source ? source : new Price({})
      this.source = source

      function applyChangesFromSource() {
        _this.id = source.id
        _this.placeId = source.placeId
        _this.spaceId = source.spaceId
        _this.name = source.name
        _this.amount = source.amount
        _this.currency = source.currency
      }

      applyChangesFromSource()

      source.onChangeCallback.add(applyChangesFromSource)

      this.toApiPriceEntity = function() {
        return {
          name: _this.name,
          amount: _this.amount,
          currency: _this.currency
        }
      }

    }

    function event(/*str*/ key, /*any*/ arg) {
      if ($scope.listener) $scope.listener()(key, arg)
      //console.log('Event sent: '+key)
    }

    var refreshRootSpaces = function(/*fn*/ callback) {
      apiSpacesService.refreshSpaces($scope.place.id, null, function(/*[Space]*/ spaces) {
        $scope.place.spaces = spaces
        $scope.place.applyChangesToSource()
        if (callback) callback()
      })
    }

    var flatSpaces = function(/*[Space]*/ spaces) {
      var innerSpaces = spaces.map(function(space) {
        return (space.spaces || []).length > 0 ? flatSpaces(space.spaces) : []
      })
      return [].concat.apply(spaces, innerSpaces)
    }

    var findSpaceById = function(/*str*/ spaceId) {
      var spaces = $.grep(flatSpaces($scope.place.spaces), function(space) {
        return space.id == spaceId
      })
      return spaces.length > 0 ? spaces[0] : null
    }

    var findSpaceByIdToEdit = function(/*str*/ spaceId) {
      delete $scope.editedSpace

      var spaces = $.grep(flatSpaces($scope.place.spaces), function(space) { return space.id == spaceId })
      if (spaces.length > 0) $scope.editedSpace = new EditedSpace(spaces[0])

      event('space.selected', $scope.editedSpace ? $scope.editedSpace.source : null)

      return $scope.editedSpace
    }

    var findPriceByIdToEdit = function(/*str*/ priceId) {
      delete $scope.editedPrice

      var prices = $.grep($scope.editedSpace.prices, function(price) { return price.id == priceId })
      if (prices.length > 0) $scope.editedPrice = new EditedPrice(prices[0])

      return $scope.editedPrice
    }

    $scope.showEditSpaceFragment = function(/*str*/ spaceId) {
      findSpaceByIdToEdit(spaceId)
    }

    $scope.showAddRootSpaceFragment = function() {
      $scope.dialogOptions = {
        onSubmit: submitNewSpace
      }
      $scope.newSpace = new NewSpace()
    }

    $scope.showAddSpaceFragment = function() {
      $scope.dialogOptions = {
        onSubmit: submitNewSpace
      }
      $scope.newSpace = new NewSpace($scope.editedSpace.id)
    }

    $scope.showEditPriceFragment = function(/*str*/ priceId) {
      $scope.dialogOptions = {
        title: 'Edit Price Details',
        onSubmit: submitEditedPrice,
        onDelete: deleteEditedPrice,
        hasDelete: true
      }

      findPriceByIdToEdit(priceId)
    }

    $scope.showAddPriceFragment = function() {
      $scope.dialogOptions = {
        title: 'Add a New Price',
        onSubmit: submitNewPrice
      }
      $scope.editedPrice = new EditedPrice()
    }

    var submitEditedPrice = function() {
      apiSpacesService.patchPrice($scope.editedPrice.placeId, $scope.editedPrice.spaceId, $scope.editedPrice.id, $scope.editedPrice.toApiPriceEntity(), function() {
        notifyService.notify('Price details updated', 'success')
        $scope.editedSpace.refreshPrices()
      })
    }

    var deleteEditedPrice = function() {
      apiSpacesService.deletePrice($scope.editedPrice.placeId, $scope.editedPrice.spaceId, $scope.editedPrice.id, function() {
        notifyService.notify('Price deleted', 'success')
        $scope.editedSpace.refreshPrices()
      })
    }

    var submitNewPrice = function() {
      apiSpacesService.addPrice($scope.editedSpace.placeId, $scope.editedSpace.id, $scope.editedPrice.toApiPriceEntity(), function() {
        notifyService.notify('New price added', 'success')
        $scope.editedSpace.refreshPrices()
      })
    }

    $scope.submitEditedSpace = function() {
      apiSpacesService.patchSpace($scope.editedSpace.placeId, $scope.editedSpace.id, $scope.editedSpace.toApiSpaceEntity(), function(/*Space*/ space) {
        notifyService.notify('Space details updated', 'success')
        $scope.editedSpace.refresh()
      })
    }

    $scope.deleteEditedSpace = function() {
      apiSpacesService.deleteSpace($scope.editedSpace.placeId, $scope.editedSpace.id, function() {
        notifyService.notify('Space deleted', 'success')
        if ($scope.editedSpace.parentSpaceId) {
          var parentSpaceId = $scope.editedSpace.parentSpaceId
          findSpaceByIdToEdit(parentSpaceId)
          $scope.editedSpace.refreshSpaces()
        } else {
          delete $scope.editedSpace
          refreshRootSpaces()
          event('space.selected', null)
        }
      })
    }

    var submitNewSpace = function() {
      apiSpacesService.addSpace($scope.place.id, $scope.newSpace.parentSpaceId, $scope.newSpace.toApiSpaceEntity(), function(/*Space*/ space) {
        notifyService.notify('New space added', 'success')
        var editNewSpace = function() {
          findSpaceByIdToEdit(space.id)
        }
        if ($scope.editedSpace) $scope.editedSpace.refreshSpaces(editNewSpace)
        else refreshRootSpaces(editNewSpace)
      })
    }

    $scope.setEditedPlace = function() {
      delete $scope.editedSpace
      event('space.selected', null)

      if ($scope.place) $scope.editedPlace = new EditedPlace($scope.place)
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      place: '=', /*Place*/
      listener: '&'
    },

    templateUrl: 'views/templates/spacesManager.html',

    controller: controller,

    link: function(scope, element, attrs) {
      function trigger() {
        scope.setEditedPlace()
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

app.directive('spacesManagerEditPriceDialog', function(modalDialogService) {

  var controller = function($scope) {

    function SelectedCurrency(/*str*/ value) {

      this.currencies = {
        'GBP': '£',
        'EUR': '€',
        'USD': '$'
      }
      this.value = null
      this.sign = null

      this.change = function(/*str*/ value) {
        var sign = this.currencies[value]
        if (sign) {
          this.sign = sign
          this.value = value
        }
      }

      this.change(value)

    }

    function EditedAmount(/*num(x100)*/ value) {

      this.pound = 0
      this.pence = 0

      if (value) {
        this.pound = Math.floor(value/100)
        this.pence = value-this.pound*100
      }

      this.valueX100 = function() {
        return this.pound*100+this.pence
      }

    }

    $scope.setEditedPrice = function() {
      if (!$scope.selectedCurrency || $scope.price.currency) $scope.selectedCurrency = new SelectedCurrency($scope.price.currency)
      if (!$scope.editedAmount || $scope.price.amount >= 0) $scope.editedAmount = new EditedAmount($scope.price.amount)
    }

    $scope.updateAndSubmit = function() {
      $scope.price.amount = $scope.editedAmount.valueX100()
      $scope.price.currency = $scope.selectedCurrency.value
      $scope.price.applyChangesToSource()
      $scope.onSubmit()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      price: '=', /*Price*/
      dialogTitle: '@',
      onSubmit: '&',
      onDelete: '&',
      hasDelete: '@'
    },

    templateUrl: 'views/templates/spacesManagerEditPriceDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.spaces-manager-edit-price-dialog')
      var dialogHandle = modalDialogService.registerDialog('#'+elementID(dialogElement))

      function trigger() {
        delete scope.editedPrice
        if (scope.price) {
          scope.setEditedPrice()
          dialogHandle.show()
        }
      }

      scope.$watch('trigger', function(newValue, oldValue) {
        trigger()
      })
      scope.$watch('price', function(newValue, oldValue) {
        trigger()
      })
    }

  }
})

app.directive('spacesManagerAddSpaceDialog', function(modalDialogService) {

  var controller = function($scope) {

    $scope.updateAndSubmit = function() {
      $scope.space.applyChangesToSource()
      $scope.onSubmit()
    }

  }

  return {

    restrict: 'E',

    scope: {
      space: '=', /*Space*/
      onSubmit: '&',
    },

    templateUrl: 'views/templates/spacesManagerAddSpaceDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.spaces-manager-add-space-dialog')
      var dialogHandle = modalDialogService.registerDialog('#'+elementID(dialogElement))

      scope.$watch('space', function(newValue, oldValue) {
        if (newValue) dialogHandle.show()
      })
    }

  }
})

app.directive('spacesManagerCollapse', function(modalDialogService) {
  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      place: '=', /*Place*/
      listener: '&'
    },

    templateUrl: 'views/templates/spacesManagerCollapse.html'

  }
})
