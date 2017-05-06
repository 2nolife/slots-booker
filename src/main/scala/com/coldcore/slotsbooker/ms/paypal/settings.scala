package com.coldcore.slotsbooker
package ms.paypal

import akka.actor.{ExtendedActorSystem, Extension}
import ms.config.{CommonSettings, ExtensionKey}

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
  val paymentsBaseUrl: String = readString("payments_base_url")
  val liveEventIp: String = readString("live_event_ip")
}
