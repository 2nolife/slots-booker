package com.coldcore.slotsbooker.ms.booking

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import com.coldcore.slotsbooker.ms.config.CommonSettings

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
  val slotsBaseUrl: String = readString("slots_base_url")
}
