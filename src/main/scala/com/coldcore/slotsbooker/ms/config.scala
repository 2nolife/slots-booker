package com.coldcore.slotsbooker
package ms.config

import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import Constants._
import ms.attributes.Types.VoAttributes
import ms.attributes.{Util => au}

trait DefaultSettingsReader {
  self: {
    val system: ExtendedActorSystem
    val MS: String
  } =>

  private val config: Config = system.settings.config

  private def optional[T](readSetting: => T): Option[T] =
    try {
      Some(readSetting)
    } catch {
      case _: Missing => None
    }

  def readString(setting: String): String =
    optional(config.getString(s"$APP.$MS.$setting")) getOrElse config.getString(s"$APP.$setting")

  def readInt(setting: String): Int =
    optional(config.getInt(s"$APP.$MS.$setting")) getOrElse config.getInt(s"$APP.$setting")

  def readBoolean(setting: String): Boolean =
    optional(config.getBoolean(s"$APP.$MS.$setting")) getOrElse config.getBoolean(s"$APP.$setting")

}

trait CommonSettings extends DefaultSettingsReader {
  self: {
    val system: ExtendedActorSystem
    val MS: String
  } =>

  val systemToken: String = readString("shared_system_token")

  val hostname: String = readString("http_bind_hostname")
  val port: Int = readInt("http_bind_port")

  val mongoDbHostname: String = readString("mongodb_hostname")
  val mongoDbPort: Int = readInt("mongodb_port")
  val mongoDbName: String = readString("mongodb_name")

  val authBaseUrl: String = readString("auth_base_url")
  val profilesBaseUrl: String = readString("profiles_base_url")

  val restConnPerRoute: Int = readInt("rest_conn_per_route")
  val restConnMaxTotal: Int = readInt("rest_conn_max_total")

  def collectVoAttributes(names: String*): VoAttributes =
    names.map(name => name -> au.parse(readString(s"vo_attributes_$name"))).toMap

  val getDeepFields: Boolean = readBoolean("get_deep_fields")
}

object Constants {
  val APP = "slotsbooker"
}
