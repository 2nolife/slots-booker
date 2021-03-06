package com.coldcore.slotsbooker
package ms.slots

import akka.actor.{ExtendedActorSystem, Extension}
import ms.config.{CommonSettings, ExtensionKey}
import ms.attributes.Types.VoAttributes

object Settings extends ExtensionKey[Settings] 

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val placesBaseUrl: String = readString("places_base_url")
  val voAttributes: VoAttributes = collectVoAttributes("booking", "slot", "price")
}
