app.filter('cinemaDayScheduleHasMovieKey', function() {
  return function(/*[CinemaSeat.weekSchedule.daily]*/ dailies, /*str*/ movieKey) {
    var arr = []
    if (dailies)
      dailies.forEach(function(daily) {
        for (var n = 0; n < daily.slots.length; n++)
          if (daily.slots[n].movieKey == movieKey) {
            arr.push(daily.slots[n])
            break
          }
      })
    return arr
  }
})
