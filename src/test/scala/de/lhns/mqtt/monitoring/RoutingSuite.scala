package de.lhns.mqtt.monitoring

import de.lhns.mqtt.monitoring.Config.FilterConfig
import munit.FunSuite

class RoutingSuite extends FunSuite {
  private val mqttTopic = "home/livingroom/sensor"

  private def filter(
                      metric: String,
                      patterns: Seq[String],
                      discard: Boolean = false,
                      enableArrays: Boolean = false,
                      ignoreWarnings: Boolean = false
                    ) = FilterConfig(
    metricName = Some(metric),
    topics = patterns.toList.map(TopicPattern.fromString),
    discard = Some(discard),
    topicLabel = None,
    labels = None,
    labelMatchers = None,
    valueMappings = None,
    enableArrays = Some(enableArrays),
    ignoreWarnings = Some(ignoreWarnings)
  )

  private val noMapping: FilterConfig => Topic => String => Option[Double] = _ => _ => _ => None

  private def route(filters: List[FilterConfig], payload: String, topic: String = mqttTopic) =
    Routing.route(filters, Topic.fromString(topic), payload, noMapping)

  // metric -> value topics (relative to the mqtt topic) -> value
  private def exported(result: Routing.Result, topic: String = mqttTopic): Map[String, Map[String, Double]] =
    result.exports.map((f, values) =>
      f.metricNameOrDefault -> values.map((k, v) => k.toString.stripPrefix(topic).stripPrefix("/") -> v)
    ).toMap

  test("later #> filter exports what an earlier one did not select") {
    val filters = List(
      filter("pressure", Seq("home/+/sensor/#>/pressure")),
      filter("temperature", Seq("home/+/sensor/#>/temperature"))
    )
    assertEquals(
      exported(route(filters, """{"temperature": 21.5}""")),
      Map("temperature" -> Map("#>/temperature" -> 21.5))
    )
  }

  test("each #> filter exports its own field") {
    val filters = List(
      filter("pressure", Seq("home/+/sensor/#>/pressure")),
      filter("temperature", Seq("home/+/sensor/#>/temperature"))
    )
    assertEquals(
      exported(route(filters, """{"temperature": 21.5, "pressure": 1000}""")),
      Map(
        "pressure" -> Map("#>/pressure" -> 1000.0),
        "temperature" -> Map("#>/temperature" -> 21.5)
      )
    )
  }

  test("#> discard filter claims fields before a catch-all") {
    val filters = List(
      filter("discarded", Seq("home/+/sensor/#>/battery/#"), discard = true),
      filter("rest", Seq("#"))
    )
    assertEquals(
      exported(route(filters, """{"temperature": 21.5, "battery": {"level": 80}}""")),
      Map("rest" -> Map("#>/temperature" -> 21.5))
    )
  }

  test("plain discard takes the whole message") {
    val filters = List(
      filter("discarded", Seq("home/debug/#"), discard = true),
      filter("rest", Seq("#"))
    )
    assertEquals(exported(route(filters, "1", topic = "home/debug/x"), "home/debug/x"), Map.empty[String, Map[String, Double]])
  }

  test("plain filter takes the message before a catch-all") {
    val filters = List(
      filter("power", Seq("home/+/power")),
      filter("rest", Seq("#"))
    )
    assertEquals(
      exported(route(filters, "5", topic = "home/x/power"), "home/x/power"),
      Map("power" -> Map("" -> 5.0))
    )
  }

  test("plain filter takes the message even for fields it skips") {
    val filters = List(
      filter("plain", Seq("home/+/sensor"), enableArrays = false),
      filter("arrays", Seq("home/+/sensor/#>/readings/+"), enableArrays = true)
    )
    assertEquals(exported(route(filters, """{"readings": [1, 2]}""")), Map.empty[String, Map[String, Double]])
  }

  test("later #> filter sees fields an earlier one skipped") {
    val filters = List(
      filter("first", Seq("home/+/sensor/#>/temperature"), enableArrays = false),
      filter("rest", Seq("home/+/sensor/#>/#"), enableArrays = true)
    )
    assertEquals(
      exported(route(filters, """{"temperature": 21.5, "humidity": 40, "readings": [1, 2]}""")),
      Map(
        "first" -> Map("#>/temperature" -> 21.5),
        "rest" -> Map("#>/humidity" -> 40.0, "#>/readings/0" -> 1.0, "#>/readings/1" -> 2.0)
      )
    )
  }

  test("warns when a json pattern selects nothing") {
    val filters = List(filter("pressure", Seq("home/+/sensor/#>/pressure")))
    val result = route(filters, """{"temperature": 21.5}""")
    assertEquals(result.warnNothingSelected, true)
    assertEquals(route(filters, """{"pressure": 1}""").warnNothingSelected, false)
  }

  test("no warning when ignoreWarnings is set or nothing matched") {
    val ignoring = List(filter("pressure", Seq("home/+/sensor/#>/pressure"), ignoreWarnings = true))
    assertEquals(route(ignoring, """{"temperature": 21.5}""").warnNothingSelected, false)
    val other = List(filter("pressure", Seq("other/#>/pressure")))
    assertEquals(route(other, """{"temperature": 21.5}""").warnNothingSelected, false)
  }
}
