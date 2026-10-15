package de.lhns.mqtt.monitoring

import de.lhns.mqtt.monitoring.Config.FilterConfig
import munit.FunSuite

import scala.collection.mutable

class ValueSelectionSuite extends FunSuite {
  private val payload = """{"temperature": 21.5, "humidity": 40, "battery": {"level": 80, "low": false}, "readings": [1, 2]}"""

  private val mqttTopic = "home/livingroom/sensor"

  private val noMapping: Topic => String => Option[Double] = _ => _ => None

  private def filter(patterns: String*) = FilterConfig(
    metricName = None,
    topics = patterns.toList.map(TopicPattern.fromString),
    discard = None,
    topicLabel = None,
    labels = None,
    labelMatchers = None,
    valueMappings = None,
    enableArrays = None,
    ignoreWarnings = None
  )

  // the value topics relative to the mqtt topic, as strings
  private def exported(
                        patterns: Seq[String],
                        topic: String = mqttTopic,
                        payload: String = payload,
                        enableArrays: Boolean = true,
                        mapping: Topic => String => Option[Double] = noMapping
                      ): Map[String, Double] = {
    val t = Topic.fromString(topic)
    filter(patterns*).valueSelector(t) match {
      case None => fail(s"no selector for topic $topic")
      case Some(select) =>
        Values.valuesFromString(payload, t, mapping, select, enableArrays)
          .map((k, v) => k.toString.stripPrefix(topic).stripPrefix("/") -> v)
    }
  }

  private val all = Map(
    "#>/temperature" -> 21.5,
    "#>/humidity" -> 40.0,
    "#>/battery/level" -> 80.0,
    "#>/battery/low" -> 0.0,
    "#>/readings/0" -> 1.0,
    "#>/readings/1" -> 2.0
  )

  test("plain pattern exports all fields") {
    assertEquals(exported(Seq("home/+/sensor")), all)
  }

  test("single field") {
    assertEquals(exported(Seq("home/+/sensor/#>/temperature")), Map("#>/temperature" -> 21.5))
  }

  test("object fields with +") {
    assertEquals(
      exported(Seq("home/+/sensor/#>/battery/+")),
      Map("#>/battery/level" -> 80.0, "#>/battery/low" -> 0.0)
    )
  }

  test("top level fields with + skip objects and arrays") {
    assertEquals(
      exported(Seq("home/+/sensor/#>/+")),
      Map("#>/temperature" -> 21.5, "#>/humidity" -> 40.0)
    )
  }

  test("# after #> selects all fields") {
    assertEquals(exported(Seq("home/+/sensor/#>/#")), all)
  }

  test("array elements need enableArrays") {
    assertEquals(
      exported(Seq("home/+/sensor/#>/readings/+")),
      Map("#>/readings/0" -> 1.0, "#>/readings/1" -> 2.0)
    )
    assertEquals(exported(Seq("home/+/sensor/#>/readings/+"), enableArrays = false), Map.empty[String, Double])
  }

  test("selecting an object yields nothing") {
    assertEquals(exported(Seq("home/+/sensor/#>/battery")), Map.empty[String, Double])
  }

  test("unknown field yields nothing") {
    assertEquals(exported(Seq("home/+/sensor/#>/pressure")), Map.empty[String, Double])
  }

  test("# directly before #> spans the mqtt levels") {
    assertEquals(exported(Seq("home/#/#>/temperature")), Map("#>/temperature" -> 21.5))
    assertEquals(
      exported(Seq("home/#/#>/temperature"), topic = "home", payload = """{"temperature": 1}"""),
      Map("#>/temperature" -> 1.0)
    )
  }

  test("plain number payload is not selected by a json pattern") {
    assertEquals(
      exported(Seq("home/+/sensor/#>/temperature"), payload = "21.5"),
      Map.empty[String, Double]
    )
  }

  test("plain number payload is exported by a plain pattern") {
    assertEquals(
      exported(Seq("home/+/temp"), topic = "home/livingroom/temp", payload = "21.5"),
      Map("" -> 21.5)
    )
  }

  test("top level boolean") {
    assertEquals(exported(Seq("t/#>/#"), topic = "t", payload = "true"), Map("#>" -> 1.0))
    assertEquals(exported(Seq("t/#>/x"), topic = "t", payload = "true"), Map.empty[String, Double])
  }

  test("only patterns matching the mqtt topic are used") {
    assertEquals(
      exported(Seq("a/b/c", "c/d/e/#>/x"), topic = "c/d/e", payload = """{"x":1,"y":2}"""),
      Map("#>/x" -> 1.0)
    )
  }

  test("a matching plain pattern wins over a json pattern") {
    assertEquals(
      exported(Seq("c/d/e", "c/d/e/#>/x"), topic = "c/d/e", payload = """{"x":1,"y":2}"""),
      Map("#>/x" -> 1.0, "#>/y" -> 2.0)
    )
  }

  test("no selector for a non-matching topic") {
    assertEquals(filter("home/+/sensor/#>/temperature").valueSelector(Topic.fromString("other/topic")).isDefined, false)
  }

  test("mapping is not called for unselected string fields") {
    val called = mutable.Buffer.empty[String]
    val mapping: Topic => String => Option[Double] = topic => string => {
      called += topic.toString
      Some(1.0)
    }
    assertEquals(
      exported(Seq("t/#>/t"), topic = "t", payload = """{"t": 1, "s": "on"}""", mapping = mapping),
      Map("#>/t" -> 1.0)
    )
    assertEquals(called.toList, Nil)
    assertEquals(
      exported(Seq("t/#>/s"), topic = "t", payload = """{"t": 1, "s": "on"}""", mapping = mapping),
      Map("#>/s" -> 1.0)
    )
    assertEquals(called.toList, List("t/#>/s"))
  }
}
