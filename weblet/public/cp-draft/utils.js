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
