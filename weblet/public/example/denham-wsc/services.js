app.service('placesService', function(sb_apiPlacesService, config) {
  var service = this

  service.loadMyPlaces = function(/*fn*/ callback, /*bool*/ force) {
    if (force || !service.cachedPlaces) {
      sb_apiPlacesService.findPlaces({ attributes: [{ 'client_key': config.client_key, 'external_key': config.place_external_key }] }, function(/*[Place]*/ places) {

          service.cachedPlaces = places.map(function(place) { return new MyPlace(place) })
          callback(service.cachedPlaces)

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

  service.getMyPlace = function(/*fn*/ callback) {
    service.loadMyPlaces(function(/*[MyPlace]*/ places) {
      sb.utils.assert(places.length == 1, 'Place not found: '+config.place_external_key)
      callback(places[0])
    })
  }

})
