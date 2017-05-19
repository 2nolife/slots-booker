app.service('placesService', function(sb_apiPlacesService, config) {
  var service = this

  service.loadCinemaPlaces = function(/*fn*/ callback, /*bool*/ force) {
    if (force || !service.cachedPlaces) {
      sb_apiPlacesService.findPlaces({ attributes: [{ 'client_key': config.client_key, 'external_key': config.place_external_key }] }, function(/*[Place]*/ places) {

          service.cachedPlaces = places.map(function(place) { return new CinemaPlace(place) })
          callback(service.cachedPlaces)

      })
    } else {
      callback(service.cachedPlaces)
    }
  }

  service.getCinemaPlace = function(/*fn*/ callback) {
    service.loadCinemaPlaces(function(/*[CinemaPlace]*/ places) {
      sb.utils.assert(places.length == 1, 'Place not found: '+config.place_external_key)
      callback(places[0])
    })
  }

})

app.service('miscService', function(config) {
  var service = this

  service.findMovieByKey = function(/*str*/ movieKey) {
    var movies = $.grep(config.all_movies, function(movie) { return movie.key == movieKey })
    return movies.length ? movies[0] : { key: '?', title: '?', time: 0, cover: 'cover_no.jpeg' }
  }

  service.appendDataToSlots = function(/*CinemaSlot*/ slots) {
    slots.forEach(function(slot) {
      slot.movie = service.findMovieByKey(slot.movieKey)
    })
  }

})
