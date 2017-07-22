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

  removeKeys: function(/*obj*/ obj, /*[str]*/ keys) {
    keys.forEach(function(key) {
      delete obj[key]
    })
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

  /** 20161230 -> 30 December 2016 */
  formatDateFull: function(/*num|str*/ value) {
    var str = ''+value
    return str.substr(6, 2)+' '+sb.utils.monthAsWord(str)+' '+str.substr(0, 4)
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

  /** 20170317,845 -> 201703170845 */
  datetime: function(/*num|str*/ date, /*num|str*/ time) {
    var sdate = ''+date,
        stime = ('0000'+time).slice(-4)
    return sdate+stime
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

  /** 20170105 -> 20170102 (monday) */
  weekFirstDate: function(/*str*/ value) {
    var date = sb.utils.strToDate(value)
        weekday = date.getDay(),
        add = weekday == 0 ? -6 : -weekday+1
    return sb.utils.addDaysDate(value, add)
  },

  /** 20170105 -> 20170108 (sunday) */
  weekLastDate: function(/*str*/ value) {
    var date = sb.utils.strToDate(value)
        weekday = date.getDay(),
        add = weekday == 0 ? 0 : -weekday+7
    return sb.utils.addDaysDate(value, add)
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

  /** 20170123,20170125 -> 2 */
  diffDaysDate: function(/*str*/ value1, /*str*/ value2) {
    var curDate = value1
        diff = 0
    while (parseInt(curDate) < parseInt(value2)) {
      diff++
      curDate = sb.utils.addDaysDate(curDate, 1)
    }
    return diff
  },

  /** 1230,45 -> 1315, 22:00,125 -> 0005 */
  addMinutesTime: function(/*str|num*/ value, /*num*/ add) {
    var str = ('0000'+value).slice(-4),
        minutes = parseInt(str.substr(0,2))*60+parseInt(str.substr(2,2))+add,
        result = Math.floor(minutes/60)*100+Math.floor(minutes%60)
    while (result >= 2400) result -= 2400
    return ('0000'+result).slice(-4)
  },

  apiCode: function(/*obj*/ response) {
    var arr = (response.headers('x-api-code') || '').split(",")
    return $.grep(arr, function(v) { return v })
  },

  hasApiCode: function(/*obj*/ response, /*str*/ code) {
    return sb.utils.apiCode(response).indexOf(code) != -1
  },

  apiCodeText: function(/*str*/ code) {
    return sb.utils.text.apiCode[code] || 'No description'
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
    value = value || 0
    var pound = Math.floor(value/100), pence = value-pound*100,
        paddedPence = pence > 0 || alwaysShowPence ? '.'+('00'+pence).slice(-2) : '',
        result = pound+paddedPence
    return result
  },

  apiStatusOK: function(/*str*/ status) {
    return status == 'success' || status == 'noop'
  },

  percent: function(/*num*/ now, /*num*/ total) {
    return Math.round(((now / total)*100))
  },

  currencySign: function(/*str*/ currency) {
    var sign = currency
    switch (currency) {
      case 'USD': sign = '$'; break
      case 'GBP': sign = '£'; break //todo EUR
    }
    return sign
  },

  memberLevelAsWord: function(/*num*/ value) {
    var word = value
    switch (value) {
      case 0: word = 'No'; break
      case 1: word = 'Standard'; break
      case 2: word = 'Exclusive'; break
    }
    return word
  },

  memberLevelAsWordSimple: function(/*num*/ value) {
    return value == 0 ? 'Non-member' : value > 0 ? 'Member' : value
  }

}

sb.utils.text = {

  apiCode: {
    'ms-auth-1': 'Invalid username or password',
    'ms-auth-2': 'Token has expired',

    'ms-profiles-1': 'Action forbidden',
    'ms-profiles-2': 'Profile not found',
    'ms-profiles-3': 'Trying to update forbidden attributes',
    'ms-profiles-4': 'Trying to update forbidden fields',
    'ms-profiles-5': 'Username already exists',
    'ms-profiles-6': 'Email already exists',
    'ms-profiles-7': 'External service failed',

    'ms-payments-1': 'Not enough credit',
    'ms-payments-2': 'Invalid quote status',
    'ms-payments-3': 'Invalid refund status', 
    'ms-payments-4': 'Required field is missing: reason'
  }
}