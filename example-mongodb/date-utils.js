module.exports = {
  todayDate: todayDate,
  addDaysDate: addDaysDate,
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

/** 20170123 -> Wednesday */
function weekdayAsWord(/*str*/ value) {
  var date = strToDate(value),
      weekday = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  return weekday[date.getDay()]
}
