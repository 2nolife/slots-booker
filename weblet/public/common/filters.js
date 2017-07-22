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
    return value || value == 0 ? sb.utils.numberX100(value, alwaysShowPence) : ''
  }
})

app.filter('sb_formatDate', function() {
  return function(/*num|str*/ value) {
    // 20161230 -> 30/12/2016
    return value ? sb.utils.formatDate(value) : ''
  }
})

app.filter('sb_formatDateFull', function() {
  return function(/*num|str*/ value) {
    // 20161230 -> 30 December 2016
    return value ? sb.utils.formatDateFull(value) : ''
  }
})

app.filter('sb_formatTime', function() {
  return function(/*num|str*/ value) {
    // 1259 -> 12:59
    return value ? sb.utils.formatTime(value) : ''
  }
})

app.filter('sb_weekdayAsWord', function() {
  return function(/*str*/ value) {
    // 20161230 -> Friday
    return value ? sb.utils.weekdayAsWord(value) : ''
  }
})

app.filter('sb_currencySign', function() {
  return function(/*str*/ value) {
    // USD -> $
    return value ? sb.utils.currencySign(value) : ''
  }
})

app.filter('sb_memberLevelAsWord', function() {
  return function(/*num*/ value) {
    // 1 -> Standard
    return value || value == 0 ? sb.utils.memberLevelAsWord(value) : ''
  }
})

app.filter('sb_memberLevelAsWordSimple', function() {
  return function(/*num*/ value) {
    // 1 -> Standard
    return value || value == 0 ? sb.utils.memberLevelAsWordSimple(value) : ''
  }
})

app.filter('sb_formatTime', function() {
  return function(/*num|str*/ value) {
    // 1200 -> 12:00
    return value ? sb.utils.formatTime(value) : ''
  }
})
