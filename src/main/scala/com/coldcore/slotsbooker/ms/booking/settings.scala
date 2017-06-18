package com.coldcore.slotsbooker
package ms.booking

import akka.actor.{ExtendedActorSystem, Extension}
import ms.config.{CommonSettings, ExtensionKey}

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
  val slotsBaseUrl: String = readString("slots_base_url")
  val membersBaseUrl: String = readString("members_base_url")
}
