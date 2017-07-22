# Slots Booker #

The flexible booking system for anything which can be thought of as a collection of time slots:
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

## Default configuration ##

* MongoDB is running on port 27017
* Microservices ports: 8021 to 8028
* UI port 8080

## License ##

This code is open source software licensed under the [GNU Lesser General Public License v3](http://www.gnu.org/licenses/lgpl-3.0.en.html).

## Available features ##
* Multi tenancy and multiple clubs per user
* Hierarchy of spaces (e.g. halls, parking lots) and time slots per club
* Hierarchy of prices across spaces and slots
* Bookings which do or do not require payment
* Cancellation and full refunds for bookings
* Expiration of unpaid bookings
* References for successful bookings and refunds
* Time periods to book and cancel with hierarchy across spaces and slots
* Users balance per club in multiple currencies
* Users can credit clubs and pay through PayPal
* Local timezone support per club
* Discount for club members and membership types
* Authentication with OAuth2 token
* Read only anonymous users support
* Responses with full and partial JSON graph
* Genius Bar: the simple UI example to demo basic bookings
* Westfield Cinema: the advanced UI example to demo a cinema
* Denham WSC: the advanced UI example to demo a water ski club

## Outstanding work ##

### Major topics ###
* Control panel for users, admins and moderators (partially available)
* 3rd party payment support
* Deals and promotions on slots (get 3 for the price of 2)
* Notifications in case if booked slots cancelled
* Time periods for partial refunds with hierarchy across spaces and slots
* Audit logs
* Means to integrate with external parties
* Temporary reservations to complete later (plane seats reserved but not selected nor paid for)
* Optional payments (extra luggage, fancy 3D glasses)

### Other features ###
* Error responses with custom codes (available for 409 status code)
* User payment and refund statements
