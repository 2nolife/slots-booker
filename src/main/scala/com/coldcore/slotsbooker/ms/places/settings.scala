package com.coldcore.slotsbooker
package ms.places

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import ms.attributes.Types.VoAttributes
import ms.config.CommonSettings

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val voAttributes: VoAttributes = collectVoAttributes("place", "space", "price")
}
