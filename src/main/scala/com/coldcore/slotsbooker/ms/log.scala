package com.coldcore.slotsbooker.ms

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}

trait Logger {

  private var _log: LoggingAdapter = _

  def log: LoggingAdapter = _log

  def initLoggingAdapter(implicit system: ActorSystem) =
    _log = Logging.getLogger(system, this)

}
