package com.coldcore.slotsbooker
package ms.auth

import akka.actor.{ExtendedActorSystem, Extension}
import ms.config.{CommonSettings, ExtensionKey}

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings
