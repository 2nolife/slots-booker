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
    var result = '0'
    if (value) {
      var pound = Math.floor(value/100), pence = value-pound*100
      var paddedPence = pence > 0 || alwaysShowPence ? '.'+('00'+pence).slice(-2) : ''
      result = pound+paddedPence
    }
    return result
  }
})

app.filter('sb_numberAsDate', function() {
  return function(/*num*/ value) {
    // 20161230 -> 12/30/2016
    var result = ''
    if (value) {
      var str = ''+value
      var year = str.substr(0, 4), month = str.substr(4, 2), day = str.substr(6, 2)
      result = day+'/'+month+'/'+year
    }
    return result
  }
})

app.filter('sb_numberAsTime', function() {
  return function(/*num*/ value) {
    // 1259 -> 12:59
    var result = ''
    if (value) {
      var str = ('0000'+value).slice(-4)
      var hours = str.substr(0, 2), minutes = str.substr(2, 2)
      result = hours+':'+minutes
    }
    return result
  }
})
