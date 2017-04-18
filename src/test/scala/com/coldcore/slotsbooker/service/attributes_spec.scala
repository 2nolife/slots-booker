package com.coldcore.slotsbooker
package ms.attributes

import ms.vo.Attributes
import ms.attributes.{Util => au}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest._
import spray.json.{JsNumber, JsObject, JsString}

class AttributesUtilSpec extends FlatSpec with MockitoSugar with Matchers with BeforeAndAfter {

  case class MyClassA(id: String, attributes: Option[Attributes])
  case class MyClassB(id: Long, abc: String, attributes: Option[Attributes])
  case class MyWrapper(classA: Option[MyClassA], classB: Option[Seq[MyClassB]], attributes: Option[Attributes], abc: Option[Attributes])

  var attrA: Attributes = _
  var attrB: Attributes = _
  var attrC: Attributes = _

  var classA: MyClassA = _
  var classB: MyClassB = _
  var wrapper: MyWrapper = _

  before {
    attrA = Attributes(JsObject("a" -> JsString("a")))
    attrB = Attributes(JsObject("b" -> JsNumber(1)))
    attrC = Attributes(JsObject("c" -> JsObject()))

    classA = MyClassA("id", Some(attrA))
    classB = MyClassB(1, "abc", Some(attrB))
    wrapper = MyWrapper(Some(classA), Some(Seq(classB)), Some(attrC), None)
  }

  "extractAttributedMembers" should "extract optional attributes from case classes" in {
    au.extractAttributedMembers(classA).size shouldBe 1
    au.extractAttributedMembers(classA).toSeq.head shouldBe attrA

    au.extractAttributedMembers(classB).size shouldBe 1
    au.extractAttributedMembers(classB).toSeq.head shouldBe attrB

    au.extractAttributedMembers(wrapper).size shouldBe 3
    au.extractAttributedMembers(wrapper).toSeq should contain allOf(attrA, attrB, attrC)
  }

  "assertExposed" should "throw exception if attributes were not exposed" in {
    intercept[IllegalStateException] { au.assertExposed(classA) }
  }

  "assertExposed" should "not throw exception if attributes were exposed" in {
    au.exposeClass(classA, Nil, _ => false)
    au.assertExposed(classA)
  }

  "assertExposed" should "throw exception if attributes of an inner object were not exposed" in {
    intercept[IllegalStateException] {
      au.exposeClass(wrapper, Nil, _ => false)
      au.exposeClass(classB, Nil, _ => false)
      au.assertExposed(wrapper)
    }
  }

  "assertExposed" should "throw exception if attributes of an inner sequence were not exposed" in {
    intercept[IllegalStateException] {
      au.exposeClass(wrapper, Nil, _ => false)
      au.exposeClass(classA, Nil, _ => false)
      au.assertExposed(wrapper)
    }
  }

  "assertExposed" should "not throw exception if attributes of inner objects were exposed" in {
    au.exposeClass(wrapper, Nil, _ => false)
    au.exposeClass(classA, Nil, _ => false)
    au.exposeClass(classB, Nil, _ => false)
    au.assertExposed(wrapper)
  }

  "parse" should "parse string into configured attributes" in {
    val seq = au.parse("key_rw rw, key_rwp rwp")

    seq.size shouldBe 2
    seq should contain allOf(
      ConfiguredAttribute("key_rw", Seq(ReadPermission, WritePermission)),
      ConfiguredAttribute("key_rwp", Seq(ReadPermission, WritePermission, PublicPermission)))
  }

}
