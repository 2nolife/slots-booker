app.service('sb_modalDialogService', function($timeout) {
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

app.service('sb_notifyService', function($timeout) {
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

app.service('sb_paypalService', function() {
  var service = this

  var checkoutScriptLoaded = false

  service.loadCheckoutScript = function(/*fn*/ callback) {
    if (checkoutScriptLoaded) callback()
    else $.getScript('https://www.paypalobjects.com/api/checkout.js', function() {
      checkoutScriptLoaded = true
      callback()
    })
  }

  service.checkout = function(/*obj*/ options) {
    ['onBefore',
     'onReady',
     'env',
     'appKeyProduction',
     'appKeySandbox',
     'total',
     'currency',
     'placeId',
     'profileId',
     'onSuccess',
     'onError',
     'button'
    ].forEach(function(v) {
      sb.utils.assert(options[v], 'Required but missing: '+v)
    })

    var render = {
      env: options.env, // 'production|sandbox' environment

      client: { // API key for PayPal REST App
        sandbox:    options.appKeySandbox,
        production: options.appKeyProduction
      },

      payment: function() {
          var env    = this.props.env
          var client = this.props.client

          return paypal.rest.payment.create(env, client, {
            transactions: [
              {
                amount: { total: options.total, currency: options.currency },   // Note: must match the PayPal account currency
                custom: 'place='+options.placeId+',profile='+options.profileId, // Required: Place ID and Profile ID
                invoice_number: (options.ref ? 'ref='+options.ref : null)       // Optional: must be unique, can be processed by PayPal only once
              }
            ]
          })
      },

      commit: true, // Optional: show a 'Pay Now' button in the checkout flow

      onAuthorize: function(data, actions) {
        // Optional: display a confirmation page here
        return actions.payment.execute()
          .then(options.onSuccess) // Show a success page to the buyer
          .catch(options.onError)  // Show an error page to the buyer
      }
    }

    options.onBefore()
    service.loadCheckoutScript(function() {
      paypal.Button.render(render, options.button)
      options.onReady()
    })

  }

})
