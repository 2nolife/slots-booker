# Slots Booker #

A flexible booking system for anything which can be thought of as a collection of time slots:
parking lots, hotel rooms, sport classes, restaurant tables, airplane seats, cinema seats, appointments etc.

Feedback email cftp@coldcore.com

## Technologies ##

* Microservices oriented, stateless, REST, OAuth2
* Backend: Scala, Akka HTTP, Akka Actors, MongoDB, SBT, Casbah, Spay JSON
* Frontend: AngularJS, Bootstrap, jQuery, Node JS

## Running the application ##

### Start the backend ###

* Install and run MongoDB
* Install [SBT](http://www.scala-sbt.org)
* From the project's root directory do `sbt run`

### Start the frontend ###

* Install [Node](http://www.scala-sbt.org)
* From the project's `weblet` directory do `npm install` and then `npm start`

### Demo ###

* From the project's `example-mongodb` directory do `npm install` and then `npm start` to generate sample data
(this will erase the existing database data)
* Open `http://localhost:8080/example` and choose an example application you want to run

## Testing ##

* Requires MongoDB up and running
* From the project's root directory do `sbt it:test`

## License ##

This code is open source software licensed under the [GNU Lesser General Public License v3](http://www.gnu.org/licenses/lgpl-3.0.en.html).

## Available features ##
* Authentication with OAuth2 token
* Multi tenancy and multiple clubs per user
* Hierarchy of spaces (e.g. halls, parking lots) and time slots per club
* Hierarchy of prices across spaces and slots
* Bookings which do or do not require payment
* Cancellation and full refunds for bookings
* Users balance per club in multiple currencies
* Expiration of unpaid bookings
* References for successful bookings and refunds
* Local timezone support per club
* Responses with full and partial JSON graph
* Genius Bar: the simple UI example to demo basic bookings
* Westfield Cinema: the advanced UI example to demo a cinema

## Outstanding work ##

### Major topics ###
* Control panel for users, admins and moderators
* Users must be able to credit clubs through PayPal
* Deals and promotions on slots (get 3 for the price of 2)
* Discount for club members and membership types
* Queue for booked slots in case if cancelled
* Future slots availability time periods
* Bookings cancel and refund (full or partial) time periods
* Example applications to demo all the features
* Audit logs
* Means to integrate with external parties
* Temporary reservations to complete later (plane seats reserved but not selected nor paid for)
* Optional payments (extra luggage, fancy 3D glasses)

### Other features ###
* Read only anonymous users support
* Better error responses with custom codes

