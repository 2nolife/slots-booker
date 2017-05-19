app.service('placesService', function(sb_apiPlacesService, config) {
  var service = this

  service.loadPlaces = function(/*fn*/ callback, /*bool*/ force) {
    if (force || !service.cachedPlaces) {
      sb_apiPlacesService.findPlaces({ attributes: [{ 'client_key': config.client_key, 'external_key': config.place_external_key }] }, function(/*[Place]*/ places) {

          service.cachedPlaces = places
          callback(service.cachedPlaces)

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

  service.getPlace = function(/*fn*/ callback) {
    service.loadPlaces(function(/*[Place]*/ places) {
      sb.utils.assert(places.length == 1, 'Place not found: '+config.place_external_key)
      callback(places[0])
    })
  }

})
