package com.coldcore.slotsbooker
package ms.utils

object StringUtil {

  /** Parse a comma separated values. Return a sequence of trimmed strings. Inputs: text.
      ['foo, bar, goo' -> [foo bar goo] */
  def parseCSV(s: String): List[String] = {
    val arr = s split "," //split text using a separator (as regex)
    arr map(_ trim) filter(_.length > 0) toList
  }

  /** Parse a comma separated map values. Return a map with trimmed keys and values. Inputs: text.
      ['foo=F, bar=B, goo=G' -> {foo A, bar B, goo G} */
  def parseCSVMap(s: String): Map[String, String] = {
    val r =
      for {
        x <- parseCSV(s)
        i = x indexOf "="
        a = x.substring(0, i).trim
        b = x.substring(i + 1).trim
      } yield (a -> b)
    r toMap
  }

  /** Convert a string with precision into a whole number or a whole number back into a string with precision.
    * ['123.56' 2] -> 12356   ['123.56' 4] -> 1235600
    * [12356 2] -> '123.56'   [1235600 4] -> '123.56' */
  def toXPrec(s: String, prec: Int): Long = {
    val mult = scala.math.pow(10, prec) toLong
    val zero = mult.toString substring 1
    val neg = s startsWith "-"
    val pos = s startsWith "+"
    val val0 = "0" + (if (neg || pos) s substring (1) else s) replace(",", ".")
    val val1 = (if (val0 contains ".") val0 else val0 + ".") + zero
    val arr = val1 split "\\."
    val sign = if (neg) -1L else 1L
    (arr.head.toLong * mult + arr.last.substring(0, zero length).toLong) * sign
  }

  def toXPrec(n: Long, prec: Int): String = {
    val mult = scala.math.pow(10, prec) toLong
    val z = scala.math.abs(n)
    val a = z / mult
    val b = z - a * mult
    val d = (1 until (mult.toString.length - b.toString.length)).map(_ => "0").mkString
    val s = a + "." + d + b
    val sign = if (n < 0) "-" else ""
    sign + s
  }

  /** Convert a string or number with a precision of 2.
    * ['123.56' 2] -> 12356   [12356 2] -> '123.56' */
  def toX100(x: String): Long =
    toXPrec(x, 2)

  def toX100(x: Long): String =
    toXPrec(x, 2)

}