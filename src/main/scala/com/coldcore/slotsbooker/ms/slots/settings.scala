package com.coldcore.slotsbooker
package ms.slots

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import ms.config.CommonSettings
import ms.attributes.Types.VoAttributes

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
  val voAttributes: VoAttributes = collectVoAttributes("booking", "slot", "price")
}
