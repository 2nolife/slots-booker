PayPal sandbox instructions
===========================
  1) Login to "https://developer.paypal.com/developer/accounts/"
  2) Create a sandbox Business account
  3) Create REST API application "https://developer.paypal.com/developer/applications/"
     name it "slotsbooker" and link to the sandbox account
  4) Create a web hook for "Payment sale completed" event pointing
     to SB, eg. "https://sb.coldcore.com/api/paypal/events"
  5) Use REST app Client ID to send payments to the test account


Update DB with REST app details
===============================
Update "paypal_skey" property in the "ms-payments-accounts" collection with your app key


How to simulate "sale completed" event
======================================
  API: https://developer.paypal.com/docs/api/payments/#payment_create

  Enable "sandbox_mode" in "application.conf"
  Modify "event-sale-completed.json": change "id", "custom", "invoice"
    or get a payload from PayPal developer "Sandbox Webhooks Events"
  
  To send directly use port 8027, to send through Weblet use port 8080
  curl -v -X POST http://localhost:8027/paypal/events -H "Content-Type:application/json" -d @event-sale-completed.json

  Field     Description
  id        must be unique otherwise will be marked as duplicate
  custom    must contain IDs of a profile and a place, the payment will go to that balance
  invoice   optional ref, if set then the reference will be processed after the balance update
            can be used just once then PayPal will reject repeated value
