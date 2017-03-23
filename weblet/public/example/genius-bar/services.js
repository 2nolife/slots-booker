app.service('placesService', function(apiPlacesService, notifyService, config) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.loadPlaces = function(/*fn*/ callback, /*bool*/ force) {
    if (force || !service.cachedPlaces) {
      apiPlacesService.findPlaces({ attributes: [{ 'client_key': config.client_key, 'external_key': config.place_external_key }] }, function(/*[Place]*/ places) {

          service.cachedPlaces = places
          callback(service.cachedPlaces)

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

  service.getPlace = function(/*fn*/ callback) {
    service.loadPlaces(function(/*[Place]*/ places) {
      assert(places.length == 1, 'Place not found: '+config.place_external_key)
      callback(places[0])
    })
  }

})
