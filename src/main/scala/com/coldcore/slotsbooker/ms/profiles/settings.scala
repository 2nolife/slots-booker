package com.coldcore.slotsbooker
package ms.profiles

import akka.actor.{ExtendedActorSystem, Extension}
import ms.attributes.Types.VoAttributes
import ms.config.{CommonSettings, ExtensionKey}

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings {
  val voAttributes: VoAttributes = collectVoAttributes("profile")
}
