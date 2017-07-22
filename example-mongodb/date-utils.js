module.exports = {
  todayDate: todayDate,
  addDaysDate: addDaysDate,
  addMinutesTime: addMinutesTime,
  weekdayAsWord : weekdayAsWord
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

/** 1230,45 -> 1315, 22:00,125 -> 0005 */
function addMinutesTime(/*str|num*/ value, /*num*/ add) {
  var str = ('0000'+value).slice(-4),
      minutes = parseInt(str.substr(0,2))*60+parseInt(str.substr(2,2))+add,
      result = Math.floor(minutes/60)*100+Math.floor(minutes%60)
  while (result >= 2400) result -= 2400
  return ('0000'+result).slice(-4)
}

/** 20170123 -> Wednesday */
function weekdayAsWord(/*str*/ value) {
  var date = strToDate(value),
      weekday = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  return weekday[date.getDay()]
}
