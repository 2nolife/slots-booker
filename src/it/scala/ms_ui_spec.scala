package com.coldcore.slotsbooker
package test

import org.apache.http.HttpStatus._
import org.scalatest._

@deprecated
abstract class BaseMsUiSpec extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter with Matchers with HelperObjects {

  override protected def beforeAll() {
    systemStart()

    mongoDropDatabase()
  }

  override protected def afterAll() {
    systemStop()
  }

}

class MsUiSpec extends BaseMsUiSpec {

  "GET to /" should "return index.html content" in {
//    val rc = (When getTo uiBaseUrl expect() code SC_OK).withBody[ms.vo.ResponseContent]
//    rc.content.trim should startWith ("<!DOCTYPE html>")
  }

}
