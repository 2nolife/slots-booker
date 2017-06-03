app.filter('sb_chunkArray', function() {
  return function(/*array*/ arr, /*num*/ chunkSize) {
    var chunks = []
    if (arr)
      for (var n = 0; n < arr.length; n += chunkSize)
        chunks.push(arr.slice(n, chunkSize))
    return chunks
  }
})

app.filter('sb_csvArray', function() {
  return function(/*array*/ arr) {
    return arr ? arr.join(', ') : []
  }
})

app.filter('sb_numberX100', function() {
  return function(/*num*/ value, /*bool*/ alwaysShowPence) {
    // 230 -> 2.30
    return value ? sb.utils.numberX100(value, alwaysShowPence) : ''
  }
})

app.filter('sb_numberAsDate', function() {
  return function(/*num*/ value) {
    // 20161230 -> 12/30/2016
    return value ? sb.utils.formatDate(value) : ''
  }
})

app.filter('sb_numberAsTime', function() {
  return function(/*num*/ value) {
    // 1259 -> 12:59
    return value ? sb.utils.formatTime(value) : ''
  }
})

app.filter('sb_numberAsWeekday', function() {
  return function(/*num*/ value) {
    // 20161230 -> Friday
    return value ? sb.utils.weekdayAsWord(value) : ''
  }
})
