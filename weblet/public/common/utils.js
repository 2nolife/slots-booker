function randomID(/*str*/ prefix, /*str*/ postfix) {
  return (prefix ? prefix : 'id_')+(''+Math.random()).substring(2)+(postfix ? postfix : '')
}

function randomScopeID() {
  return randomID('id_{{$id}}_')
}

function chunkArray(/*array*/ arr, /*num*/ chunkSize) {
  var chunks = []
  for (var n = 0; n < arr.length; n += chunkSize)
    chunks.push(arr.slice(n, chunkSize))
  return chunks;
}

function elementID(/*obj*/ element) {
  var id = element.attr('id')
  if (!id) {
    id = randomID()
    element.attr('id', id)
  }
  return id
}

function replaceInternals(/*obj*/ obj, /*obj*/ copyFrom) {
  Object.keys(obj).forEach(function(key) {
    delete obj[key]
  })
  $.extend(true, obj, copyFrom)
}

function attributeNamesValues(/*array*/ attrNames, /*array*/ attrValues, /*bool*/ like, /*bool*/ filter) {
  like = like || true, filter = filter || true

  var tokens = attrValues.map(function(v) {
    return v+(like && !v.endsWith('*') ? '*' : '')
  })
  var filteredTokens = $.grep(tokens, function(v) {
    return filter ? v.length >= 3+1 : true
  })
  var namesValues = filteredTokens.map(function(v) {
    return attrNames.map(function(name) {
      var nv = {}
      nv[name] = v
      return nv
    })
  })
  return [].concat.apply([], namesValues)
}

function assert(condition, message) {
  if (!condition) {
    message = message || "Assertion failed"
    if (typeof Error !== "undefined") throw new Error(message)
    throw message
  }
}

function safeParseInt(/*str*/ value) {
  return value ? parseInt(value) : null
}

/** 20161230 -> 30/12/2016 */
function formatDate(/*num|str*/ value) {
  var str = ''+value
  var year = str.substr(0, 4), month = str.substr(4, 2), day = str.substr(6, 2)
  return day+'/'+month+'/'+year
}

/** 1259 -> 12:59 */
function formatTime(/*num|str*/ value) {
  var str = ('0000'+value).slice(-4)
  var hours = str.substr(0, 2), minutes = str.substr(2, 2)
  return hours+':'+minutes
}

/** 30/12/2016 -> 20161230   3/12/2016 -> 20161203 */
function parseDate(/*str*/ value) {
  if (value.length == 9) value = '0'+value
  var year = value.substr(6, 4), month = value.substr(3, 2), day = value.substr(0, 2)
  var num = parseInt(year+month+day)
  return value.length == 10 && num >= 10000000 && num <= 99990000 ? ''+num : null
}

/** 12:59 -> 1259   2:30 -> 230 */
function parseTime(/*str*/ value) {
  if (value.length == 4) value = '0'+value
  var hours = '0'+value.substr(0, 2), minutes = value.substr(3, 2)
  var num = parseInt(hours+minutes)
  return value.length == 5 && num >= 0 && num <= 2400 ? ''+num : null
}


/** args -> UTC Date (no daylight saving) */
function dateUTC(/*num*/ year, /*num*/ month, /*num*/ day) {
  return new Date(Date.UTC(year, month, day))
}

/** 20170123 -> Date */
function strToDate(/*str|num*/ value) {
  var str = ''+value
  var year = str.substr(0, 4), month = str.substr(4, 2), day = str.substr(6, 2)
  return dateUTC(parseInt(year), parseInt(month-1), parseInt(day))
}

/** Date -> 20170123 */
function dateToStr(/*Date*/ date) {
  return date.toJSON().slice(0,10).replace(/-/g,'')
}

/** 20170123 */
function todayDate() {
  return dateToStr(new Date())
}

/** 20170123,1 -> 20170124 */
function addDaysDate(/*str*/ value, /*num*/ add) {
  var date = strToDate(value)
  var result = dateUTC(date.getFullYear(), date.getMonth(), date.getDate()+add)
  return dateToStr(result)
}

/** 20170123 -> 20170101 */
function monthFirstDate(/*str*/ value) {
  var date = strToDate(value)
  var result = dateUTC(date.getFullYear(), date.getMonth(), 1)
  return dateToStr(result)
}

/** 20170123 -> 20170131 */
function monthLastDate(/*str*/ value) {
  var date = strToDate(value)
  var nextMonth = dateUTC(date.getFullYear(), date.getMonth()+1, 1)
  var result = dateUTC(nextMonth.getFullYear(), nextMonth.getMonth(), nextMonth.getDate()-1)
  return dateToStr(result)
}

/** 20170123 -> 20170115 */
function monthMidDate(/*str*/ value) {
  var date = strToDate(value)
  var result = dateUTC(date.getFullYear(), date.getMonth(), 15)
  return dateToStr(result)
}


/** 20170123 -> Wednesday */
function weekdayAsWord(/*str*/ value) {
  var date = strToDate(value),
      weekday = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  return weekday[date.getDay()]
}

/** 20170123 -> January */
function monthAsWord(/*str*/ value) {
  var date = strToDate(value),
      month = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
  return month[date.getMonth()]
}

/** Compare dates with time (201701231220), value1 is compared with value2, return -1 or 0 or 1 */
function datetimeCompare(/*str*/ value1, /*str*/ value2) {
  var date1 = parseInt(value1.substr(0, 8)),
      time1 = parseInt(value1.substr(8, 4)),
      date2 = parseInt(value2.substr(0, 8)),
      time2 = parseInt(value2.substr(8, 4))
  return date1 < date2 ? -1 : date1 > date2 ? 1 : time1 < time2 ? -1 : time1 > time2 ? 1 : 0
}


function apiCode(/*obj*/ response) {
  var arr = (response.headers('x-api-code') || '').split(",")
  return $.grep(arr, function(v) { return v })
}

function hasApiCode(/*obj*/ response, /*str*/ code) {
  return apiCode(response).indexOf(code) != -1
}

function apiCodeText(/*str*/ code) {
  var s = 'No description'
  switch (code) {
    case 'ms-auth-1': s = 'Invalid username or password'; break;
    case 'ms-auth-2': s = 'Token has expired'; break;

    case 'ms-profiles-1': s = 'Action forbidden'; break;
    case 'ms-profiles-2': s = 'Profile not found'; break;
    case 'ms-profiles-3': s = 'Trying to update forbidden attributes'; break;
    case 'ms-profiles-4': s = 'Trying to update forbidden fields'; break;
    case 'ms-profiles-5': s = 'Username already exists'; break;
    case 'ms-profiles-6': s = 'Email already exists'; break;
    case 'ms-profiles-7': s = 'External service failed'; break;
  }
  return s
}

function apiCodeFromResponse(/*obj*/ response) {
  var arr = apiCode(response).map(function(code) {
    return { code: code, text: apiCodeText(code) }
  })
  return {
    code: arr.length ? arr[0].code : '',
    text: arr.length ? arr[0].text : '',
    codes: arr
  }
}

function numberX100(/*num*/ value, /*bool*/ alwaysShowPence) {
  // 230 -> 2.30
  var result = '0'
  if (value) {
    var pound = Math.floor(value/100), pence = value-pound*100
    var paddedPence = pence > 0 || alwaysShowPence ? '.'+('00'+pence).slice(-2) : ''
    result = pound+paddedPence
  }
  return result
}
