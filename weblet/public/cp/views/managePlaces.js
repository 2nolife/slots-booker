app.service('managePlacesService', function(sb_apiPlacesService) {
  var service = this

  service.loadPlaces = function(/*bool*/ force, /*fn*/ callback) {
    if (force || !service.cachedPlaces) {
      sb_apiPlacesService.getPlaces(function(/*[Place]*/ places) {

          service.cachedPlaces = places
          callback(service.cachedPlaces)

          places.forEach(function(place) {
            place.refresh('owner')
          })

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

})

app.controller('managePlacesController', function($scope, $rootScope, managePlacesService, $timeout, $routeParams, $location, sb_notifyService) {

  var paramPlaceId = $routeParams.placeId

  function loadPlaces(/*bool*/ force) {
    $scope.loadPlacesSpin = true
    managePlacesService.loadPlaces(force, function(/*[Place]*/ places) {
      $timeout(function() { $scope.loadPlacesSpin = false }, 1000)
      
      $scope.listedPlaces = places
      if (paramPlaceId) editPlaceByParam()

    })
  }

  function editPlaceByParam() {
    delete $scope.editedPlace
    var p = $.grep($scope.listedPlaces, function(place) { return paramPlaceId == place.id })
    if (p.length) $scope.editedPlace = new cp.classes.EditedPlace(p[0])
  }

  $scope.reloadPlaces = function() {
    loadPlaces(true)
  }

  $scope.showListedPlaces = function() {
    delete $scope.newPlaceTrigger
    delete $scope.editedPlace
    $location.path('/manage-places')
  }

  $scope.reloadEditedPlace = function() {
    $scope.loadEditedPlaceSpin = true
    $scope.editedPlace.refresh(function() {
      $rootScope.$broadcast('editedPlace.reset')
      $timeout(function() { $scope.loadEditedPlaceSpin = false }, 1000)
    })
  }

  $scope.dummy = function() {
    sb_notifyService.featureNotImplemented()
  }

  $scope.newPlace = function() {
    $scope.newPlaceTrigger = Math.random()
  }

  loadPlaces()
})

app.directive('editedPlaceProperties', function() {

  var controller = function($scope, sb_apiPlacesService, sb_notifyService) {

    $scope.save = function() {
      var entity = $scope.editedPlace.toApiAttributesEntity()
      entity.name = $scope.editedPlace.name
      sb_apiPlacesService.patchPlace($scope.editedPlace.id, entity, function() {
        $scope.editedPlace.refresh()
        sb_notifyService.notify('Saved', 'success')
      })
    }

    $scope.onEditedPlaceSet = function() {
      //$scope.editedPlace = $scope.place
    }

  }

  return cp.managePlaces.editedPlaceDirective('editedPlaceProperties', controller)

})

app.directive('editedPlaceAddress', function() {

  var controller = function($scope, sb_apiPlacesService, sb_notifyService) {

    $scope.save = function() {
      sb_apiPlacesService.patchPlace($scope.editedPlace.id, $scope.editedPlace.toApiAddressEntity(), function() {
        $scope.editedPlace.refresh()
        sb_notifyService.notify('Saved', 'success')
      })
    }

    $scope.onEditedPlaceSet = function() {
    }

  }

  return cp.managePlaces.editedPlaceDirective('editedPlaceAddress', controller)

})

app.directive('editedPlaceSpaces', function($rootScope) {

  var controller = function($scope, sb_apiSpacesService, sb_notifyService) {

    $scope.editSpace = function(/*EditedSpace*/ space) {
      $scope.editedSpace = space
      $scope.editedSpace.refreshSpaces()
      $scope.editedSpace.refreshPrices()
    }

    $scope.saveEditedSpaceProperties = function() {
      var entity = $scope.editedSpace.toApiAttributesEntity()
      entity.name = $scope.editedSpace.name
      sb_apiSpacesService.patchSpace($scope.editedSpace.placeId, $scope.editedSpace.id, entity, function() {
        $scope.editedSpace.refresh()
        sb_notifyService.notify('Saved', 'success')
      })
    }

    $scope.newPrice = function() {
      $scope.dialogOptions = {
        title: 'Add a New Price',
        onSave: saveNewPrice
      }
      $scope.editedPrice = new cp.classes.EditedPrice()
      $scope.editPriceTrigger = Math.random()
    }

    $scope.editPrice = function(/*EditedPrice*/ price) {
      $scope.dialogOptions = {
        title: 'Edit Price',
        onSave: saveEditedPrice,
        onDelete: deleteEditedPrice,
        hasDelete: true
      }
      $scope.editedPrice = price
      $scope.editPriceTrigger = Math.random()
    }

    function saveNewPrice() {
      sb_apiSpacesService.addPrice($scope.editedSpace.placeId, $scope.editedSpace.id, $scope.editedPrice.toApiEntity(), function() {
        sb_notifyService.notify('Saved', 'success')
        $scope.editedSpace.refreshPrices(true)
      })
    }

    function saveEditedPrice() {
      sb_apiSpacesService.patchPrice($scope.editedSpace.placeId, $scope.editedSpace.id, $scope.editedPrice.id, $scope.editedPrice.toApiEntity(), function() {
        sb_notifyService.notify('Saved', 'success')
        $scope.editedSpace.refreshPrices(true)
      })
    }

    function deleteEditedPrice() {
      sb_apiSpacesService.deletePrice($scope.editedSpace.placeId, $scope.editedSpace.id, $scope.editedPrice.id, function() {
        sb_notifyService.notify('Deleted', 'success')
        $scope.editedSpace.refreshPrices(true)
      })
    }

    $scope.addSpace = function() {
      $scope.dialogOptions = {
        title: 'Add a New Space',
        onSave: saveNewSpace
      }
      $scope.newSpace = new cp.classes.NewSpace()
      $scope.newSpaceTrigger = Math.random()
    }

    $scope.addRootSpace = function() {
      $scope.dialogOptions = {
        title: 'Add a New Root Space',
        onSave: saveNewRootSpace
      }
      $scope.newSpace = new cp.classes.NewSpace()
      $scope.newSpaceTrigger = Math.random()
    }

    function saveNewSpace() {
      sb_apiSpacesService.addSpace($scope.editedPlace.id, $scope.editedSpace.id, $scope.newSpace.toApiEntity(), function(/*Space*/ space) {
        sb_notifyService.notify('Saved', 'success')
        $scope.editedSpace.refreshSpaces(true, function() {
          var spaces = $.grep($scope.editedSpace.spaces, function(s) { return space.id == s.id })
          if (spaces.length) $scope.editedSpace = spaces[0]
        })
      })
    }

    function saveNewRootSpace() {
      sb_apiSpacesService.addSpace($scope.editedPlace.id, null, $scope.newSpace.toApiEntity(), function(/*Space*/ space) {
        sb_notifyService.notify('Saved', 'success')
        delete $scope.editedSpace
        $scope.editedPlace.refreshSpaces(true, function() {
          var spaces = $.grep($scope.editedPlace.spaces, function(s) { return space.id == s.id })
          if (spaces.length) $scope.editedSpace = spaces[0]
        })
      })
    }

    $scope.deleteEditedSpace = function() {
      function confirmed() {
        sb_apiSpacesService.deleteSpace($scope.editedSpace.placeId, $scope.editedSpace.id, function() {
          sb_notifyService.notify('Deleted', 'success')
          var parentSpace = $scope.editedSpace.parentSpace
          if (parentSpace) {
            $scope.editedSpace = parentSpace
            $scope.editedSpace.refreshSpaces(true)
          } else {
            delete $scope.editedSpace
            $scope.editedPlace.refreshSpaces(true)
          }
        })
      }    
      $rootScope.$broadcast('dialog.delete', { text: 'Delete space '+$scope.editedSpace.name, onConfirm: confirmed })
    }

    $scope.onEditedPlaceSet = function() {
      delete $scope.editedSpace
      $scope.editedPlace.refreshSpaces()
    }

  }

  return cp.managePlaces.editedPlaceDirective('editedPlaceSpaces', controller, $rootScope)

})

app.directive('editedPlaceModerators', function($rootScope) {

  var controller = function($scope, sb_apiPlacesService) {

    function patchAndRefresh(/*[str]*/ moderatorIds) {
      sb_apiPlacesService.patchPlace($scope.editedPlace.id, { moderators: moderatorIds }, function(/*Place*/ place) {
        $scope.editedPlace.source.moderatorIds = place.moderatorIds
        $scope.editedPlace.source.refresh('moderators', true)
      })
    }


    function addModerator(/*User*/ user) {
      var moderatorIds = [].concat($scope.editedPlace.source.moderatorIds)
      moderatorIds.push(user.id)
      patchAndRefresh(moderatorIds)
    }

    $scope.addModerator = function() {
      $scope.onUser = addModerator
      $scope.userFinderTrigger = Math.random()
    }

    $scope.removeModerator = function(/*User*/ user) {
      var moderatorIds = [].concat($scope.editedPlace.source.moderatorIds)
      moderatorIds.splice(moderatorIds.indexOf(user.id), 1)
      patchAndRefresh(moderatorIds)
    }

    $scope.onEditedPlaceSet = function() {
      $scope.editedPlace.source.refresh('owner')
      $scope.editedPlace.source.refresh('moderators')
    }

  }

  return cp.managePlaces.editedPlaceDirective('editedPlaceModerators', controller, $rootScope)

})

app.directive('editedPlaceEditPriceDialog', function(sb_modalDialogService) {

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

    $scope.onEditedPriceSet = function() {
      if (!$scope.selectedCurrency || $scope.editedPrice.currency) $scope.selectedCurrency = new SelectedCurrency($scope.editedPrice.currency)
      if (!$scope.editedAmount || $scope.editedPrice.amount >= 0) $scope.editedAmount = new EditedAmount($scope.editedPrice.amount)
    }

    $scope.submit = function() {
      $scope.editedPrice.amount = $scope.editedAmount.valueX100()
      $scope.editedPrice.currency = $scope.selectedCurrency.value
      $scope.onSave()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      editedPrice: '=', /*EditedPrice*/
      dialogTitle: '@',
      onSave: '&',
      onDelete: '&',
      hasDelete: '@'
    },

    templateUrl: 'views/templates/managePlaces/editedPlaceEditPriceDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.edited-place-edit-price-dialog')
      var dialogHandle = sb_modalDialogService.registerDialog('#'+sb.utils.elementID(dialogElement))

      function trigger() {
        if (scope.trigger && scope.editedPrice) {
          scope.onEditedPriceSet()
          dialogHandle.show()
        }
      }
      scope.$watch('trigger', trigger)
      scope.$watch('editedPrice', trigger)
    }

  }
})

app.directive('editedPlaceNewSpaceDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    $scope.onNewSpaceSet = function() {
    }

    $scope.submit = function() {
      $scope.onSave()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      newSpace: '=', /*NewSpace*/
      dialogTitle: '@',
      onSave: '&'
    },

    templateUrl: 'views/templates/managePlaces/editedPlaceNewSpaceDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.edited-place-new-space-dialog')
      var dialogHandle = sb_modalDialogService.registerDialog('#'+sb.utils.elementID(dialogElement))

      function trigger() {
        if (scope.trigger && scope.newSpace) {
          scope.onNewSpaceSet()
          dialogHandle.show()
        }
      }
      scope.$watch('trigger', trigger)
      scope.$watch('newSpace', trigger)
    }

  }
})

app.directive('editedSpaceSlots', function($rootScope) {

  var controller = function($scope, sb_notifyService, sb_apiSlotsService) {

    $scope.dateFilter = { from: sb.utils.formatDate(sb.utils.todayDate()), to: sb.utils.formatDate(sb.utils.todayDate()) }

    $scope.changeDateFilter = function(/*str*/ action) {
      var parsedDateFilter = parseDateFilter()
      var fromDate = parsedDateFilter.from, toDate = parsedDateFilter.to
      var day = parsedDateFilter.from, mid = sb.utils.monthMidDate(day), today = sb.utils.todayDate(), todayMid = sb.utils.monthMidDate(today)

      if (action == 'day') fromDate = toDate = today
      if (action == 'day-1') fromDate = toDate = sb.utils.addDaysDate(day, -1)
      if (action == 'day+1') fromDate = toDate = sb.utils.addDaysDate(day, +1)
      if (action == 'month') fromDate = sb.utils.monthFirstDate(todayMid), toDate = sb.utils.monthLastDate(todayMid)
      if (action == 'month-1') fromDate = sb.utils.monthFirstDate(sb.utils.addDaysDate(mid, -30)), toDate = sb.utils.monthLastDate(sb.utils.addDaysDate(mid, -30))
      if (action == 'month+1') fromDate = sb.utils.monthFirstDate(sb.utils.addDaysDate(mid, +30)), toDate = sb.utils.monthLastDate(sb.utils.addDaysDate(mid, +30))

      $scope.dateFilter.from = sb.utils.formatDate(fromDate)
      $scope.dateFilter.to = sb.utils.formatDate(toDate)
      $scope.applyDateFilter()
    }

    $scope.applyDateFilter = function() {
      delete $scope.editedSlot
      var parsedDateFilter = parseDateFilter()
      $scope.editedSpace.refreshSlotsByDate(parsedDateFilter.from, parsedDateFilter.to, true)
    }

    function parseDateFilter() {
      return {
        from: sb.utils.parseDate($scope.dateFilter.from || '') || sb.utils.todayDate(),
        to: sb.utils.parseDate($scope.dateFilter.to || '') || sb.utils.todayDate()
      }
    }

    $scope.editSlot = function(/*EditedSlot*/ slot) {
      $scope.editedSlot = slot
      $scope.editedSlot.refreshPrices()
    }

    $scope.unsetEditedSlot = function() {
      delete $scope.editedSlot 
    }

    $scope.saveEditedSlotProperties = function() {
      var entity = $scope.editedSlot.toApiAttributesEntity()
      entity.name = $scope.editedSlot.name
      sb_apiSlotsService.patchSlot($scope.editedSlot.id, entity, function() {
        $scope.editedSlot.refresh()
        sb_notifyService.notify('Saved', 'success')
      })
    }

    $scope.newPrice = function() {
      $scope.dialogOptions = {
        title: 'Add a New Price',
        onSave: saveNewPrice
      }
      $scope.editedPrice = new cp.classes.EditedPrice()
      $scope.editPriceTrigger = Math.random()
    }

    $scope.editPrice = function(/*EditedPrice*/ price) {
      $scope.dialogOptions = {
        title: 'Edit Price',
        onSave: saveEditedPrice,
        onDelete: deleteEditedPrice,
        hasDelete: true
      }
      $scope.editedPrice = price
      $scope.editPriceTrigger = Math.random()
    }

    function saveNewPrice() {
      sb_apiSlotsService.addPrice($scope.editedSlot.id, $scope.editedPrice.toApiEntity(), function() {
        sb_notifyService.notify('Saved', 'success')
        $scope.editedSlot.refreshPrices(true)
      })
    }

    function saveEditedPrice() {
      sb_apiSlotsService.patchPrice($scope.editedSlot.id, $scope.editedPrice.id, $scope.editedPrice.toApiEntity(), function() {
        sb_notifyService.notify('Saved', 'success')
        $scope.editedSlot.refreshPrices(true)
      })
    }

    function deleteEditedPrice() {
      sb_apiSlotsService.deletePrice($scope.editedSlot.id, $scope.editedPrice.id, function() {
        sb_notifyService.notify('Deleted', 'success')
        $scope.editedSlot.refreshPrices(true)
      })
    }

    $scope.addSlot = function() {
      $scope.dialogOptions = {
        title: 'Add a New Slot',
        onSave: saveNewSlot
      }
      var slot = new cp.classes.NewSlot($scope.editedSpace.placeId, $scope.editedSpace.id),
          parsedDateFilter = parseDateFilter()
      slot.dateFrom = parsedDateFilter.from
      slot.dateTo = parsedDateFilter.to
      $scope.newSlot = slot
      $scope.newSlotTrigger = Math.random()
    }

    function saveNewSlot() {
      sb_apiSlotsService.addSlot($scope.newSlot.toApiEntity(), function(/*Slot*/ slot) {
        sb_notifyService.notify('Saved', 'success')
        delete $scope.editedSlot
        $scope.dateFilter = { from: sb.utils.formatDate(slot.dateFrom), to: sb.utils.formatDate(slot.dateTo) }
        $scope.editedSpace.refreshSlotsByDate(slot.dateFrom, slot.dateTo, true, function() {
          var slots = $.grep($scope.editedSpace.slots, function(s) { return slot.id == s.id })
          if (slots.length) $scope.editedSlot = slots[0]
        })
      })
    }

    $scope.deleteEditedSlot = function() {
      function confirmed() {
        sb_apiSlotsService.deleteSlot($scope.editedSlot.id, function() {
          sb_notifyService.notify('Deleted', 'success')
          delete $scope.editedSlot
          $scope.applyDateFilter()
        })
      }
      $rootScope.$broadcast('dialog.delete', { text: 'Delete slot '+$scope.editedSlot.name, onConfirm: confirmed })
    }

    $scope.onEditedSpaceSet = function() {
      delete $scope.editedSlot
      var parsedDateFilter = parseDateFilter()
      $scope.editedSpace.refreshSlotsByDate(parsedDateFilter.from, parsedDateFilter.to)
    }

  }

  return cp.managePlaces.editedSpaceDirective('editedSpaceSlots', controller)

})

app.directive('editedPlaceNewSlotDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    $scope.onNewSlotSet = function() {
      $scope.dateFrom = sb.utils.formatDate($scope.newSlot.dateFrom)
      $scope.dateTo = sb.utils.formatDate($scope.newSlot.dateTo)
      if (!$scope.timeFrom) $scope.timeFrom = '0:00'
      if (!$scope.timeTo) $scope.timeTo = '24:00'
    }

    $scope.submit = function() {
      var slot = $scope.newSlot
      slot.dateFrom = sb.utils.safeParseInt(sb.utils.parseDate($scope.dateFrom))
      slot.dateTo = sb.utils.safeParseInt(sb.utils.parseDate($scope.dateTo))
      slot.timeFrom = sb.utils.safeParseInt(sb.utils.parseTime($scope.timeFrom))
      slot.timeTo = sb.utils.safeParseInt(sb.utils.parseTime($scope.timeTo))
      $scope.onSave()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      newSlot: '=', /*NewSlot*/
      dialogTitle: '@',
      onSave: '&'
    },

    templateUrl: 'views/templates/managePlaces/editedPlaceNewSlotDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogElement = element.find('.edited-place-new-slot-dialog')
      var dialogHandle = sb_modalDialogService.registerDialog('#'+sb.utils.elementID(dialogElement))

      function trigger() {
        if (scope.trigger && scope.newSlot) {
          scope.onNewSlotSet()
          dialogHandle.show()
        }
      }
      scope.$watch('trigger', trigger)
      scope.$watch('newSlot', trigger)
    }

  }
})

var cp = cp || {}

cp.managePlaces = {

  editedPlaceDirective: function(/*str*/ template, /*fn*/ controller, $rootScope) {
    return {

      restrict: 'E',
      replace: true,

      scope: {
        trigger: '=', /*any*/
        editedPlace: '=' /*EditedPlace*/
      },

      templateUrl: 'views/templates/managePlaces/'+template+'.html',

      controller: controller,

      link: function(scope, element, attrs) {
        function trigger() {
          scope.showContent = false
          if (scope.trigger && scope.editedPlace) {
            scope.onEditedPlaceSet()
            scope.showContent = true
          }
        }
        scope.$watch('trigger', trigger)
        scope.$watch('editedPlace', trigger)
        if ($rootScope) $rootScope.$on('editedPlace.reset', trigger)
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

      templateUrl: 'views/templates/managePlaces/'+template+'.html',

      controller: controller,

      link: function(scope, element, attrs) {
        function trigger() {
          scope.showContent = false
          if (scope.trigger && scope.editedSpace) {
            scope.onEditedSpaceSet()
            scope.showContent = true
          }
        }
        scope.$watch('trigger', trigger)
        scope.$watch('editedSpace', trigger)
        if ($rootScope) $rootScope.$on('editedPlace.reset', trigger)
        if ($rootScope) $rootScope.$on('editedSpace.reset', trigger)
      }

    }
  }

}

