package com.coldcore.slotsbooker
package ms.payments.vo

import spray.json.{DefaultJsonProtocol, JsObject}
import ms.vo.Attributes

case class ProcessReference(ref: String, as_profile_id: Option[String])
object ProcessReference extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class UpdateCredit(profile_id: String, place_id: String, amount: Int, currency: String, source: JsObject)
object UpdateCredit extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

case class Credit(amount: Option[Int], currency: Option[String])
object Credit extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class Balance(profile_id: String, place_id: String, credit: Option[Seq[Credit]])
object Balance extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class UpdateCurrencyAccount(place_id: String, currency: String, attributes: Option[Attributes])
object UpdateCurrencyAccount extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

case class CurrencyAccount(currency: Option[String], attributes: Option[Attributes])
object CurrencyAccount extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

case class Account(place_id: String, currencies: Option[Seq[CurrencyAccount]])
object Account extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

/** External JSON objects from other micro services. */
package ext {

  case class Place(place_id: String, profile_id: String,
                   name: Option[String], moderators: Option[Seq[String]], attributes: Option[Attributes])
  object Place extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

  case class SlotPrice(slot_id: String, price_id: Option[String], name: Option[String], amount: Option[Int], currency: Option[String])
  object SlotPrice extends DefaultJsonProtocol { implicit val format = jsonFormat5(apply) }

  case class Quote(quote_id: String, place_id: String, profile_id: Option[String],
                   amount: Option[Int], currency: Option[String],
                   status: Option[Int], prices: Option[Seq[SlotPrice]])
  object Quote extends DefaultJsonProtocol { implicit val format = jsonFormat7(apply) }

  case class Refund(refund_id: String, place_id: String, profile_id: Option[String],
                    amount: Option[Int], currency: Option[String],
                    status: Option[Int], prices: Option[Seq[SlotPrice]])
  object Refund extends DefaultJsonProtocol { implicit val format = jsonFormat7(apply) }

  case class Reference(reference_id: String, place_id: String, ref: Option[String], profile_id: Option[String],
                       quote: Option[Quote], refund: Option[Refund])
  object Reference extends DefaultJsonProtocol { implicit val format = jsonFormat6(apply) }

  case class ReferencePaid(ref: String, profile_id: String)
  object ReferencePaid extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class CancelSlots(slot_ids: Option[Seq[String]], as_profile_id: Option[String], attributes: Option[Attributes])
  object CancelSlots extends DefaultJsonProtocol { implicit val format = jsonFormat3(apply) }

}

object Implicits {

  implicit class PlaceExt(obj: ext.Place) {

    def isModerator(profileId: String): Boolean =
      obj.moderators.getOrElse(Nil).exists(profileId ==) || obj.profile_id == profileId

 }

}