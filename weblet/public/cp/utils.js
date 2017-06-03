var cp = cp || {}

cp.utils = {

  modalDialog: function(selector, element, sb_modalDialogService) {
    var dialogElement = element.find(selector),
        dialogHandle = sb_modalDialogService.registerDialog('#'+sb.utils.elementID(dialogElement))
    return dialogHandle
  }

}
