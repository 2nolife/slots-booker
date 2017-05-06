package com.coldcore.slotsbooker
package ms.attributes

import ms.vo.{Attributes, ProfileRemote}
import ms.utils.StringUtil._
import spray.json._
import Types._

case class ConfiguredAttribute(name: String, permissions: Seq[Permission] = Nil, value: Option[JsValue] = None)

object Permission {

  def apply(c: Char): Permission =
    (ReadPermission :: WritePermission :: PublicPermission :: OncePermission :: Nil).find(_.char == c).get

  def defaultRead(profile: ProfileRemote, readCondition: HasPermission = _ => false): HasPermission =
    (attr: ConfiguredAttribute) => attr.permissions.contains(PublicPermission) || profile.isSuper ||
      (if (attr.permissions.contains(ReadPermission)) readCondition(attr) else false)

  def defaultWrite(profile: ProfileRemote, writeCondition: HasPermission = _ => false): HasPermission =
    (attr: ConfiguredAttribute) => profile.isSuper ||
      (if (attr.permissions.contains(WritePermission) || attr.permissions.contains(OncePermission)) writeCondition(attr) else false)

  def defaultPublic: HasPermission =
    (attr: ConfiguredAttribute) => attr.permissions.contains(PublicPermission)

}

sealed trait Permission {
  val char: Char
}
case object ReadPermission extends Permission {
  override val char = 'r'
}
case object WritePermission extends Permission {
  override val char = 'w'
}
case object PublicPermission extends Permission { // public read
  override val char = 'p'
}
case object OncePermission extends Permission { // write once
  override val char = 'o'
}

object Types {
  type VoAttributes = Map[String, Seq[ConfiguredAttribute]]
  type HasPermission = ConfiguredAttribute => Boolean
  type AttributedObject = { val attributes: Option[Attributes] }
}

object Util {

  def parse(csv: String): Seq[ConfiguredAttribute] =
    parseCSV(csv).map { str =>
      val parts = str.split(' ')
      val name = parts.head
      val chunks = parts.tail
        .map {
          case part if part.matches("[rwpo]+") => 'permissions -> part
          case part => throw new IllegalArgumentException(s"Attribute $name argument $part")
        }.toMap
      ConfiguredAttribute(name, chunks.get('permissions).map(_.map(Permission.apply)).getOrElse(Nil))
    }

  def permit[A <: AttributedObject](obj: A, configured: Seq[ConfiguredAttribute], writeable: HasPermission): (Boolean, Seq[String]) =
    obj.attributes
      .map(_.value.fields.keys)
      .getOrElse(Nil)
      .map(name => configured.find(_.name == name).getOrElse(ConfiguredAttribute(name)))
      .collect { case attr if !writeable(attr) => attr.name } match {
        case seq => seq.isEmpty -> seq.toSeq
      }

  def definedFields[A <: AttributedObject](obj: A, configured: Seq[ConfiguredAttribute]): Map[String, JsValue] =
    obj.attributes
      .map(_.value.fields)
      .getOrElse(Nil)
      .map { case (name, value) => configured.find(_.name == name).getOrElse(ConfiguredAttribute(name)).copy(value = Some(value)) }
      .collect { case attr if attr.value.isDefined => attr.name -> attr.value.get }
      .toMap

  def expose[A <: AttributedObject](obj: A, configured: Seq[ConfiguredAttribute], readable: HasPermission): Option[Attributes] =
    obj.attributes
      .map(_.value.fields)
      .getOrElse(Nil)
      .map { case (name, value) => configured.find(_.name == name).getOrElse(ConfiguredAttribute(name)).copy(value = Some(value)) }
      .filter(readable)
      .collect { case attr if attr.value.isDefined => attr.name -> attr.value.get }.toList match {
        case Nil => None
        case values => Some(Attributes(values: _*))
      }

  /** Returns the same object after setting exposed attributes */
  def exposeClass[A <: AttributedObject](obj: A, configured: Seq[ConfiguredAttribute], readable: HasPermission): A = {
    expose(obj, configured, readable) match {
      case Some(Attributes(value)) => obj.attributes.get.set(value)
      case None => obj.attributes.foreach(_.set(Attributes().value))
    }
    obj.attributes.foreach(_.exposed = true)
    obj
  }

  def extractAttributedMembers(p: Product): Iterator[Attributes] =
    p.productIterator.flatMap { c =>
      c.getClass.getDeclaredFields.flatMap { f =>
        val noAccess = !f.isAccessible
        if (noAccess) f.setAccessible(true)
        try {
          f.get(c) match {
            case v if v.isInstanceOf[Seq[_]] =>
              v.asInstanceOf[Seq[_]] match {
                case Seq(x, _*) if x.isInstanceOf[Product] => v.asInstanceOf[Seq[Product]].flatMap(extractAttributedMembers)
                case _ => Nil
              }
            case v if v.isInstanceOf[Attributes] => v.asInstanceOf[Attributes] :: Nil
            case v if v.isInstanceOf[Product] => extractAttributedMembers(v.asInstanceOf[Product])
            case _ => Nil
          }
        } finally {
          if (noAccess) f.setAccessible(false)
        }
      }
    }

  def assertExposed(p: Product) =
    extractAttributedMembers(p).find(!_.exposed).map(a => throw new IllegalStateException(s"Attributes exposed check failed: $a in $p"))

}

