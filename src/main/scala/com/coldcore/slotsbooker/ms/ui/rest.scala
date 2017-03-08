package com.coldcore.slotsbooker
package ms.ui.rest

import akka.actor.ActorSystem
import ms.rest.BaseRestService
import ms.ui.Constants._
import akka.http.scaladsl.server.Directives._

class UiRestService(hostname: String, port: Int)(implicit system: ActorSystem)
  extends BaseRestService(hostname, port, null) {

  bind(staticRoute, name = "UI")

  def staticRoute =
    pathSingleSlash {
      getFromResource(s"$MS/web/index.html")
    } ~ {
      getFromResourceDirectory(s"$MS/web")
    }

}
