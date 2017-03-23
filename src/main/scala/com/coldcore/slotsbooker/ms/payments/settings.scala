package com.coldcore.slotsbooker
package ms.payments

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import ms.attributes.Types.VoAttributes
import ms.config.CommonSettings

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
  val bookingBaseUrl: String = readString("booking_base_url")
}
