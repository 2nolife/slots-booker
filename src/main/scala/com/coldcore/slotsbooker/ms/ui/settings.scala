package com.coldcore.slotsbooker.ms.ui

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import com.coldcore.slotsbooker.ms.config.CommonSettings

object Settings extends ExtensionKey[Settings]

class Settings(val system: ExtendedActorSystem) extends Extension with Constants with CommonSettings
