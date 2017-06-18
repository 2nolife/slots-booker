package com.coldcore.slotsbooker
package ms.members

import akka.actor.{ExtendedActorSystem, Extension}
import ms.config.{CommonSettings, ExtensionKey}

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
}
