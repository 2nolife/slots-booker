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

app.filter('cinemaPaidBookings', function() {
  return function(/*[CinemaSlot]*/ slots) {
    var arr = [],
        _ = {}
    if (slots)
      slots.forEach(function(slot) {
        var quote = ((slot.activeBooking || _).reference || _).quote || _
        if (quote.status == 1) arr.push(slot)
      })
    return arr
  }
})
