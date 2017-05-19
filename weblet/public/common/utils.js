var sb = sb || {}

sb.utils = {

  randomID: function(/*str*/ prefix, /*str*/ postfix) {
    return (prefix ? prefix : 'id_')+(''+Math.random()).substring(2)+(postfix ? postfix : '')
  },

  randomScopeID: function() {
    return sb.utils.randomID('id_{{$id}}_')
  },
  
  chunkArray: function(/*array*/ arr, /*num*/ chunkSize) {
    var chunks = []
    for (var n = 0; n < arr.length; n += chunkSize)
      chunks.push(arr.slice(n, chunkSize))
    return chunks;
  },

  elementID: function(/*obj*/ element) {
    var id = element.attr('id')
    if (!id) {
      id = sb.utils.randomID()
      element.attr('id', id)
    }
    return id
  },

  replaceInternals: function(/*obj*/ obj, /*obj*/ copyFrom) {
    Object.keys(obj).forEach(function(key) {
      delete obj[key]
    })
    $.extend(true, obj, copyFrom)
  },

  attributeNamesValues: function(/*array*/ attrNames, /*array*/ attrValues, /*bool*/ like, /*bool*/ filter) {
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
  },

  assert: function(condition, message) {
    if (!condition) {
      message = message || "Assertion failed"
      if (typeof Error !== "undefined") throw new Error(message)
      throw message
    }
  },

  safeParseInt: function(/*str*/ value) {
    return value ? parseInt(value) : null
  },

  /** 20161230 -> 30/12/2016 */
  formatDate: function(/*num|str*/ value) {
    var str = ''+value
    var year = str.substr(0, 4), month = str.substr(4, 2), day = str.substr(6, 2)
    return day+'/'+month+'/'+year
  },

  /** 1259 -> 12:59 */
  formatTime: function(/*num|str*/ value) {
    var str = ('0000'+value).slice(-4)
    var hours = str.substr(0, 2), minutes = str.substr(2, 2)
    return hours+':'+minutes
  },

  /** 30/12/2016 -> 20161230   3/12/2016 -> 20161203 */
  parseDate: function(/*str*/ value) {
    if (value.length == 9) value = '0'+value
    var year = value.substr(6, 4), month = value.substr(3, 2), day = value.substr(0, 2)
    var num = parseInt(year+month+day)
    return value.length == 10 && num >= 10000000 && num <= 99990000 ? ''+num : null
  },

  /** 12:59 -> 1259   2:30 -> 230 */
  parseTime: function(/*str*/ value) {
    if (value.length == 4) value = '0'+value
    var hours = '0'+value.substr(0, 2), minutes = value.substr(3, 2)
    var num = parseInt(hours+minutes)
    return value.length == 5 && num >= 0 && num <= 2400 ? ''+num : null
  },


  /** args -> UTC Date (no daylight saving) */
  dateUTC: function(/*num*/ year, /*num*/ month, /*num*/ day) {
    return new Date(Date.UTC(year, month, day))
  },

  /** 20170123 -> Date */
  strToDate: function(/*str|num*/ value) {
    var str = ''+value
    var year = str.substr(0, 4), month = str.substr(4, 2), day = str.substr(6, 2)
    return sb.utils.dateUTC(parseInt(year), parseInt(month-1), parseInt(day))
  },

  /** Date -> 20170123 */
  dateToStr: function(/*Date*/ date) {
    return date.toJSON().slice(0,10).replace(/-/g,'')
  },

  /** 20170123 */
  todayDate: function() {
    return sb.utils.dateToStr(new Date())
  },

  /** 20170123,1 -> 20170124 */
  addDaysDate: function(/*str*/ value, /*num*/ add) {
    var date = sb.utils.strToDate(value)
    var result = sb.utils.dateUTC(date.getFullYear(), date.getMonth(), date.getDate()+add)
    return sb.utils.dateToStr(result)
  },

  /** 20170123 -> 20170101 */
  monthFirstDate: function(/*str*/ value) {
    var date = sb.utils.strToDate(value)
    var result = sb.utils.dateUTC(date.getFullYear(), date.getMonth(), 1)
    return sb.utils.dateToStr(result)
  },

  /** 20170123 -> 20170131 */
  monthLastDate: function(/*str*/ value) {
    var date = sb.utils.strToDate(value)
    var nextMonth = sb.utils.dateUTC(date.getFullYear(), date.getMonth()+1, 1)
    var result = sb.utils.dateUTC(nextMonth.getFullYear(), nextMonth.getMonth(), nextMonth.getDate()-1)
    return sb.utils.dateToStr(result)
  },

  /** 20170123 -> 20170115 */
  monthMidDate: function(/*str*/ value) {
    var date = sb.utils.strToDate(value)
    var result = sb.utils.dateUTC(date.getFullYear(), date.getMonth(), 15)
    return sb.utils.dateToStr(result)
  },


  /** 20170123 -> Wednesday */
  weekdayAsWord: function(/*str*/ value) {
    var date = sb.utils.strToDate(value),
        weekday = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
    return weekday[date.getDay()]
  },

  /** 20170123 -> January */
  monthAsWord: function(/*str*/ value) {
    var date = sb.utils.strToDate(value),
        month = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
    return month[date.getMonth()]
  },

  /** Compare dates with time (201701231220), value1 is compared with value2, return -1 or 0 or 1 */
  datetimeCompare: function(/*str*/ value1, /*str*/ value2) {
    var date1 = parseInt(value1.substr(0, 8)),
        time1 = parseInt(value1.substr(8, 4)),
        date2 = parseInt(value2.substr(0, 8)),
        time2 = parseInt(value2.substr(8, 4))
    return date1 < date2 ? -1 : date1 > date2 ? 1 : time1 < time2 ? -1 : time1 > time2 ? 1 : 0
  },


  apiCode: function(/*obj*/ response) {
    var arr = (response.headers('x-api-code') || '').split(",")
    return $.grep(arr, function(v) { return v })
  },

  hasApiCode: function(/*obj*/ response, /*str*/ code) {
    return sb.utils.apiCode(response).indexOf(code) != -1
  },

  apiCodeText: function(/*str*/ code) {
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
  },

  apiCodeFromResponse: function(/*obj*/ response) {
    var arr = sb.utils.apiCode(response).map(function(code) {
      return { code: code, text: sb.utils.apiCodeText(code) }
    })
    return {
      code: arr.length ? arr[0].code : '',
      text: arr.length ? arr[0].text : '',
      codes: arr
    }
  },

  numberX100: function(/*num*/ value, /*bool*/ alwaysShowPence) {
    // 230 -> 2.30
    var result = '0'
    if (value) {
      var pound = Math.floor(value/100), pence = value-pound*100
      var paddedPence = pence > 0 || alwaysShowPence ? '.'+('00'+pence).slice(-2) : ''
      result = pound+paddedPence
    }
    return result
  },

  apiStatusOK: function(/*str*/ status) {
    return status == 'success' || status == 'noop'
  }

}
