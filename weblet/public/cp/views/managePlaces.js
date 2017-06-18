app.controller('managePlacesController', function($scope, $rootScope, placesService, sb_apiPlacesService, $timeout, $routeParams, $location, sb_notifyService) {

  var paramPlaceId = $routeParams.placeId

  function loadPlaces(/*bool*/ force, /*str*/ editPlaceId) {
    $scope.loadPlacesSpin = true
    placesService.loadPlaces(force, function(/*[EditedPlace]*/ places) {
      $timeout(function() { $scope.loadPlacesSpin = false }, 1000)
      
      $scope.listedPlaces = places
      if (editPlaceId) editPlaceById(editPlaceId)

    })
  }

  function editPlaceById(/*str*/ placeId) {
    delete $scope.editedPlace
    var p = $.grep($scope.listedPlaces, function(place) { return placeId == place.id })
    if (p.length) $scope.editedPlace = p[0]
  }

  function resetToListedPlaces() {
    delete $scope.newPlaceTrigger
    delete $scope.newPlaceGuideTrigger
    delete $scope.editedPlace
  }

  $scope.reloadPlaces = function(/*str*/ editPlaceId) {
    resetToListedPlaces()
    loadPlaces(true, editPlaceId)
  }

  $scope.showListedPlaces = function() {
    resetToListedPlaces()
    $location.path('/manage-places')
  }

  $scope.reloadEditedPlace = function() {
    $scope.loadEditedPlaceSpin = true
    $scope.editedPlace.refresh(function() {
      $rootScope.$broadcast('editedPlace.reset')
      $timeout(function() { $scope.loadEditedPlaceSpin = false }, 1000)
    })
  }

  $scope.deleteEditedPlace = function() {
    function confirmed() {
      sb_apiPlacesService.deletePlace($scope.editedPlace.id, function() {
        sb_notifyService.notify('Deleted', 'success')
        $scope.reloadPlaces()
      })
    }
    $rootScope.$broadcast('dialog.delete', { text: 'Delete place '+$scope.editedPlace.name, onConfirm: confirmed })
  }

  $scope.newPlaceGuide = function() {
    $scope.newPlaceGuideTrigger = Math.random()
  }

  $scope.addPlace = function(/*str*/ template) {
    var title = 'Add a new Place'
        name = ''

    if (template == 'waterski') {
      title = 'Add a new Water Ski Club'
      name = 'WSC'
    }

    $scope.dialogOptions = {
      title: title,
      onSave: saveNewPlace
    }
    $scope.newPlace = new cp.classes.NewPlace()
    $scope.newPlace.name = name
    $scope.newPlace.template = template
    $scope.newPlaceTrigger = Math.random()
  }

  function saveNewPlace() {
    sb_apiPlacesService.addPlace($scope.newPlace.toApiEntity(), function(/*Place*/ place) {
      sb_notifyService.notify('Created', 'success')
      $scope.reloadPlaces(place.id)
    })
  }

  loadPlaces(false, paramPlaceId)
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

  return cp.manageDirectives.editedPlaceDirective('managePlaces/editedPlaceProperties', controller)

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

  return cp.manageDirectives.editedPlaceDirective('managePlaces/editedPlaceAddress', controller)

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

    $scope.addPrice = function() {
      $scope.dialogOptions = {
        title: 'Add a new Price',
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

    $scope.addSpace = function(/*str*/ template) {
      var title = 'Add a new Space',
          name = ''

      if ($scope.editedPlace.template == 'waterski') {
        title = 'Add a new Lake'
        name = 'Lake'
        template = template || 'lake'
      }

      $scope.dialogOptions = {
        title: title,
        onSave: saveNewSpace
      }
      $scope.newSpace = new cp.classes.NewSpace()
      $scope.newSpace.name = name
      $scope.newSpace.template = template
      $scope.newSpaceTrigger = Math.random()
    }

    $scope.addRootSpace = function(/*str*/ template) {
      var title = 'Add a new Root Space',
          name = ''

      if ($scope.editedPlace.template == 'waterski') {
        title = 'Add a new Season'
        name = 'Season '+sb.utils.todayDate().substring(0,4)
        template = template || 'season'
      }

      $scope.dialogOptions = {
        title: title,
        onSave: saveNewRootSpace
      }
      $scope.newSpace = new cp.classes.NewSpace()
      $scope.newSpace.name = name
      $scope.newSpace.template = template
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

  return cp.manageDirectives.editedPlaceDirective('managePlaces/editedPlaceSpaces', controller, $rootScope)

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

    $scope.removeModerator = function(/*EditedUser*/ user) {
      var moderatorIds = [].concat($scope.editedPlace.source.moderatorIds)
      moderatorIds.splice(moderatorIds.indexOf(user.id), 1)
      patchAndRefresh(moderatorIds)
    }

    $scope.onEditedPlaceSet = function() {
      $scope.editedPlace.source.refresh('owner')
      $scope.editedPlace.source.refresh('moderators')
    }

  }

  return cp.manageDirectives.editedPlaceDirective('managePlaces/editedPlaceModerators', controller, $rootScope)

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
      var dialogHandle = cp.utils.modalDialog('.edited-place-edit-price-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('editedPrice', function(newValue, oldValue) { if (newValue) scope.onEditedPriceSet() })
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
      var dialogHandle = cp.utils.modalDialog('.edited-place-new-space-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('newSpace', function(newValue, oldValue) { if (newValue) scope.onNewSpaceSet() })
    }

  }
})

app.directive('newPlaceDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    $scope.onNewPlaceSet = function() {
    }

    $scope.submit = function() {
      $scope.onSave()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      newPlace: '=', /*NewPlace*/
      dialogTitle: '@',
      onSave: '&'
    },

    templateUrl: 'views/templates/managePlaces/newPlaceDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.new-place-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('newPlace', function(newValue, oldValue) { if (newValue) scope.onNewPlaceSet() })
    }

  }
})

app.directive('editedSpaceSlots', function($rootScope) {

  var controller = function($scope, sb_notifyService, sb_apiSpacesService, sb_apiSlotsService) {

    $scope.dateFilter = { from: parseInt(sb.utils.todayDate()), to: parseInt(sb.utils.todayDate()) }
    $scope.viewSlotsMode = 'calendar'

    function applyDateFilter(/*bool*/ force, /*fn*/ callback) {
      delete $scope.editedSlot
      delete $scope.slotsCalendar
      var dates = $scope.dateFilter
      $scope.editedSpace.refreshSlotsByDate(dates.from, dates.to, force, function() {
        $scope.slotsCalendar = new cp.classes.SlotsCalendar(dates.from, dates.to, $scope.editedSpace.slots)
        if (callback) callback()
      })
    }

    $scope.onDateFilterChange = function(/*num*/ from, /*num*/ to) {
      $scope.dateFilter = { from: from, to: to }
      applyDateFilter(true)
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

    $scope.addPrice = function() {
      $scope.dialogOptions = {
        title: 'Add a new Price',
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
        title: 'Add a new Slot',
        onSave: saveNewSlot
      }
      var slot = new cp.classes.NewSlot($scope.editedSpace.placeId, $scope.editedSpace.id),
          dates = $scope.dateFilter
      slot.dateFrom = dates.from
      slot.dateTo = dates.to
      $scope.newSlot = slot
      $scope.newSlotTrigger = Math.random()
    }

    function saveNewSlot() {
      sb_apiSlotsService.addSlot($scope.newSlot.toApiEntity(), function(/*Slot*/ slot) {
        sb_notifyService.notify('Saved', 'success')
        delete $scope.editedSlot
        $scope.dateFilter = { from: slot.dateFrom, to: slot.dateTo }
        applyDateFilter(true, function() {
          var slots = $.grep($scope.editedSpace.slots, function(s) { return slot.id == s.id })
          if (slots.length) $scope.editedSlot = slots[0]
        })
      })
    }

    $scope.deleteEditedSlot = function() {
      function confirmed() {
        sb_apiSlotsService.deleteSlot($scope.editedSlot.id, function() {
          sb_notifyService.notify('Deleted', 'success')
          applyDateFilter(true)
        })
      }
      $rootScope.$broadcast('dialog.delete', { text: 'Delete slot '+$scope.editedSlot.name, onConfirm: confirmed })
    }

    $scope.addSlotSchedule = function() {
      $scope.dialogOptions = {
        title: 'Setup a new Schedule',
        onSave: saveNewSlotSchedule
      }
      var schedule = new cp.classes.NewSlotSchedule($scope.editedSpace.placeId, $scope.editedSpace.id, sb_apiSlotsService),
          dates = $scope.dateFilter
      schedule.dateFrom = dates.from
      schedule.dateTo = dates.to
      $scope.newSlotSchedule = schedule
      $scope.newSlotScheduleTrigger = Math.random()
    }

    function saveNewSlotSchedule() {
      var schedule = $scope.newSlotSchedule
      schedule.ready()
      function confirmed() {
        schedule.createNewSlots(function() {
          sb_notifyService.notify('Saved', 'success')
          $scope.dateFilter = { from: schedule.dateFrom, to: schedule.dateFrom } // show the first day
          applyDateFilter(true)
        })
      }
      $rootScope.$broadcast('dialog.confirm', { text: 'A total of '+schedule.progress.total+' slots will be created', onConfirm: confirmed })
    }

    $scope.copySlots = function() {
      var editedSpace = $scope.editedSpace,
          template = editedSpace.template,
          slotCopyPaste = new cp.classes.SlotCopyPaste(sb_apiSlotsService),
          dates = $scope.dateFilter

      sb_apiSpacesService.findSpaces(editedSpace.placeId, { attributes: [{ 'prm0.template': template }] }, function(/*[Space]*/ spaces) {
        var trgSpaces = $.grep(spaces, function(space) { return space.id != editedSpace.id })
        trgSpaces.splice(0, 0, editedSpace)
        trgSpaces.forEach(function(space) { space.selected = true })

        $scope.dialogOptions = {
          title: 'Copy Slots',
          onSave: saveSlotCopyPaste
        }

        slotCopyPaste.copy.dateFrom = dates.from
        slotCopyPaste.copy.dateTo = dates.from
        slotCopyPaste.copy.space = editedSpace
        slotCopyPaste.paste.spaces = trgSpaces
        $scope.slotCopyPaste = slotCopyPaste
        $scope.slotCopyPasteTrigger = Math.random()
      })
    }

    function saveSlotCopyPaste() {
      var slotCopyPaste = $scope.slotCopyPaste
      slotCopyPaste.ready(function() {
        function confirmed() {
          slotCopyPaste.createNewSlots(function() {
            sb_notifyService.notify('Saved', 'success')
            $scope.dateFilter = { from: slotCopyPaste.paste.dateFrom, to: slotCopyPaste.paste.dateFrom } // show the first day
            applyDateFilter(true)
          })
        }
        $rootScope.$broadcast('dialog.confirm', { text: 'A total of '+slotCopyPaste.progress.total+' slots will be created or updated', onConfirm: confirmed })
      })
    }

    $scope.deleteSlots = function() {
      var slots = $.grep($scope.editedSpace.slots, function(slot) { return slot.selected }),
          n = slots.length
      function confirmed() {
        slots.forEach(function(/*EditedSlot*/ slot) {
          sb_apiSlotsService.deleteSlot(slot.id, function() {
            if (--n == 0) {
              sb_notifyService.notify('Deleted', 'success')
              applyDateFilter(true)
            }
          })
        })
      }
      $rootScope.$broadcast('dialog.delete', { text: 'Delete selected '+slots.length+' slots', onConfirm: confirmed })
    }

    $scope.onEditedSpaceSet = function() {
      applyDateFilter(false)
    }

  }

  return cp.manageDirectives.editedSpaceDirective('managePlaces/editedSpaceSlots', controller)

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
      var dialogHandle = cp.utils.modalDialog('.edited-place-new-slot-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('newSlot', function(newValue, oldValue) { if (newValue) scope.onNewSlotSet() })
    }

  }
})

app.directive('editedPlaceNewSlotScheduleDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    $scope.onNewSlotScheduleSet = function() {
      $scope.dateFrom = sb.utils.formatDate($scope.newSlotSchedule.dateFrom)
      $scope.dateTo = sb.utils.formatDate($scope.newSlotSchedule.dateTo)
      if (!$scope.timeFrom) $scope.timeFrom = '8:00'
      if (!$scope.timeTo) $scope.timeTo = '20:00'
      if (!$scope.timePeriod) $scope.timePeriod = '60'
    }

    $scope.submit = function() {
      var slotSchedule = $scope.newSlotSchedule
      slotSchedule.dateFrom = sb.utils.safeParseInt(sb.utils.parseDate($scope.dateFrom))
      slotSchedule.dateTo = sb.utils.safeParseInt(sb.utils.parseDate($scope.dateTo))
      slotSchedule.timeFrom = sb.utils.safeParseInt(sb.utils.parseTime($scope.timeFrom))
      slotSchedule.timeTo = sb.utils.safeParseInt(sb.utils.parseTime($scope.timeTo))
      slotSchedule.timePeriod = sb.utils.safeParseInt($scope.timePeriod < 5 ? 5 : $scope.timePeriod > 60*24 ? 60*24 : $scope.timePeriod) // minutes
      $scope.onSave()
    }

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      newSlotSchedule: '=', /*NewSlotSchedule*/
      dialogTitle: '@',
      onSave: '&'
    },

    templateUrl: 'views/templates/managePlaces/editedPlaceNewSlotScheduleDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.edited-place-new-slot-schedule-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('newSlotSchedule', function(newValue, oldValue) { if (newValue) scope.onNewSlotScheduleSet() })
    }

  }
})

app.directive('editedPlaceSlotCopyPasteDialog', function(sb_modalDialogService) {

  var controller = function($scope) {

    $scope.fixDates = function() {
      if (!$scope.slotCopyPaste) return

      var base = $scope.slotCopyPaste.base,
          from = sb.utils.parseDate($scope.copy.dateFrom),
          plus6 = sb.utils.addDaysDate(from, 6)
      if (base == 'day') $scope.copy.dateTo = $scope.copy.dateFrom
      if (base == 'week') $scope.copy.dateTo = sb.utils.formatDate(plus6)

      var to = sb.utils.parseDate($scope.copy.dateTo),
          diff = sb.utils.diffDaysDate(from, to)
          pasteFrom = sb.utils.addDaysDate(to, 1),
          paste6 = sb.utils.addDaysDate(to, 1+diff)
      $scope.paste.dateFrom = sb.utils.formatDate(pasteFrom)
      $scope.paste.dateTo = sb.utils.formatDate(paste6)
    }

    $scope.onSlotCopyPasteSet = function() {
      $scope.copy = {
        dateFrom: sb.utils.formatDate($scope.slotCopyPaste.copy.dateFrom),
        dateTo: sb.utils.formatDate($scope.slotCopyPaste.copy.dateTo)
      }
      $scope.paste = {}
      $scope.fixDates()
    }

    $scope.submit = function() {
      var slotCopyPaste = $scope.slotCopyPaste
      slotCopyPaste.copy.dateFrom = sb.utils.safeParseInt(sb.utils.parseDate($scope.copy.dateFrom))
      slotCopyPaste.copy.dateTo = sb.utils.safeParseInt(sb.utils.parseDate($scope.copy.dateTo))
      slotCopyPaste.paste.dateFrom = sb.utils.safeParseInt(sb.utils.parseDate($scope.paste.dateFrom))
      slotCopyPaste.paste.dateTo = sb.utils.safeParseInt(sb.utils.parseDate($scope.paste.dateTo))
      $scope.onSave()
    }

    $scope.$watch('copy.dateFrom', $scope.fixDates)
    $scope.$watch('copy.dateTo', $scope.fixDates)

  }

  return {

    restrict: 'E',

    scope: {
      trigger: '=', /*any*/
      slotCopyPaste: '=', /*SlotCopyPaste*/
      dialogTitle: '@',
      onSave: '&'
    },

    templateUrl: 'views/templates/managePlaces/editedPlaceSlotCopyPasteDialog.html',

    controller: controller,

    link: function(scope, element, attrs) {
      var dialogHandle = cp.utils.modalDialog('.edited-place-slot-copy-paste-dialog', element, sb_modalDialogService)
      scope.$watch('trigger', function(newValue, oldValue) { if (newValue) dialogHandle.show() })
      scope.$watch('slotCopyPaste', function(newValue, oldValue) { if (newValue) scope.onSlotCopyPasteSet() })
    }

  }
})
