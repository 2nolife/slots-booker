app.service('modalDialogService', function($timeout) {
  var service = this

  var dialogs = {}

  service.registerDialog = function(/*selector*/ selector) {
    dialogs[selector] = {
      hidden: true
    }

    $(selector).on('hidden.bs.modal', function() {
      dialogs[selector].hidden = true
    })
    $(selector).on('show.bs.modal', function() {
      dialogs[selector].hidden = false
    })

    return {
      show: function() {
        service.showDialog(selector)
      }
    }
  }

  service.showDialog = function(/*selector*/ selector) {
    var f = function() {
      $(selector).modal({
        backdrop: 'static'
      })
    }
    if (dialogs[selector].hidden) f()
    else $timeout(f, 500)
  }

})

app.service('notifyService', function($timeout) {
  var service = this

  var defaultOptions = {
  }

  var defaultSettings = {
    newest_on_top: true,
    placement: {
      align: 'center'
    }
  }

  service.notify = function(/*json|str*/ options, /*json|str*/ settings) {
    options = $.extend(true, {}, defaultOptions, typeof options === 'object' ? options : { message: options })
    settings = $.extend(true, {}, defaultSettings, typeof settings === 'object' ? settings : { type: settings })
    $.notify(options, settings)
  }

  service.featureNotImplemented = function() {
    service.notify('This feature is not implemented!', 'danger')
  }

})
