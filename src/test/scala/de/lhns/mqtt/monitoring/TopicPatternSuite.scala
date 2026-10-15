package de.lhns.mqtt.monitoring

import munit.FunSuite

class TopicPatternSuite extends FunSuite {
  private def pattern(string: String) = TopicPattern.fromString(string)

  test("hasJsonPattern") {
    assert(pattern("a/+/b/#>/x").hasJsonPattern)
    assert(pattern("a/#>").hasJsonPattern)
    assert(!pattern("a/+/b").hasJsonPattern)
    assert(!pattern("a/#").hasJsonPattern)
  }

  test("stripJsonPattern removes the json part") {
    assertEquals(pattern("a/+/b/#>/x/+").stripJsonPattern, pattern("a/+/b"))
    assertEquals(pattern("a/+/b").stripJsonPattern, pattern("a/+/b"))
  }

  test("stripped pattern matches the mqtt topic only") {
    val p = pattern("home/+/sensor/#>/battery/+")
    assert(p.stripJsonPattern.matches(Topic.fromString("home/livingroom/sensor")))
    assert(!p.stripJsonPattern.matches(Topic.fromString("home/livingroom/sensor/x")))
    assert(!p.matches(Topic.fromString("home/livingroom/sensor")))
  }
}
