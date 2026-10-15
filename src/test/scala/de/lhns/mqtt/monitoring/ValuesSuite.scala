package de.lhns.mqtt.monitoring

import munit.FunSuite

class ValuesSuite extends FunSuite {
  private val t = Topic.fromString("t")

  private val noMapping: Topic => String => Option[Double] = _ => _ => None

  private val onOffMapping: Topic => String => Option[Double] = _ => {
    case "on" => Some(1.0)
    case _ => None
  }

  test("plain number") {
    assertEquals(Values.valuesFromString("42", t, noMapping, enableArrays = false), Map(t -> 42.0))
  }

  test("array with duplicate elements keeps all entries") {
    assertEquals(
      Values.valuesFromString("[0,0,1]", t, noMapping, enableArrays = true),
      Map(
        t / "#>" / "0" -> 0.0,
        t / "#>" / "1" -> 0.0,
        t / "#>" / "2" -> 1.0
      )
    )
  }

  test("arrays are ignored when disabled") {
    assertEquals(Values.valuesFromString("[0,0,1]", t, noMapping, enableArrays = false), Map.empty[Topic, Double])
  }

  test("nested object") {
    assertEquals(
      Values.valuesFromString("""{"a":{"b":true}}""", t, noMapping, enableArrays = false),
      Map(t / "#>" / "a" / "b" -> 1.0)
    )
  }

  test("non-numeric non-JSON string uses mapping") {
    assertEquals(Values.valuesFromString("on", t, onOffMapping, enableArrays = false), Map(t -> 1.0))
    assertEquals(Values.valuesFromString("off", t, onOffMapping, enableArrays = false), Map.empty[Topic, Double])
  }
}
