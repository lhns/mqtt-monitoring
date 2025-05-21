import de.lhns.mqtt.monitoring.{Topic, TopicPattern}
import munit.FunSuite

class TopicMatcherSuite extends FunSuite {
  private val tests: Seq[(String, String, Boolean, Boolean)] = Seq(
    // Exact matches
    ("a/b/c", "a/b/c", true, true),
    ("a/b", "a/b", true, true),

    // Anchored vs non-anchored mismatch
    ("a/b", "a/b/c", true, false),
    ("a/b", "a/b/c", false, false),
    ("a/b/c", "a/b", true, false),
    ("a/b/c", "a/b", false, true),

    // Single-level wildcard +
    ("a/+/c", "a/b/c", true, true),
    ("a/+/c", "a/x/c", true, true),
    ("a/+/c", "a/x/d", true, false),
    ("+/+/+", "a/b/c", true, true),
    ("+/b/+", "a/b/c", true, true),
    ("a/b/+", "a/b/c", true, true),
    ("a/b/+", "a/b", true, false), // segment missing
    ("+/+", "a/b", true, true),
    ("+/+", "a", true, false), // missing
    ("+/+", "a/b/c", true, false), // extra

    // Wrong segment count with +
    ("a/+/c", "a/c", true, false),
    ("a/+/c", "a/b/c/d", true, false),

    // Multi-level wildcard #
    ("a/b/#", "a/b", true, true), // # matches zero or more
    ("a/b/#", "a/b/c", true, true),
    ("a/b/#", "a/x/c", true, false),
    ("a/b/#", "a/b/c/d/e", true, true),
    ("#", "a", true, true),
    ("#", "a/b/c", true, true),
    ("#", "/", true, true),
    ("#", "//", true, true),

    // # cannot match middle segments
    ("a/#/c", "a/b/c", true, false), // Invalid pattern: # not last
    ("a/#/c", "a/x/y/c", true, false),

    // Pattern longer than topic
    ("a/b/c/d", "a/b/c", true, false),
    ("a/b/+", "a/b", true, false),

    // Topic longer than pattern, non-anchored
    ("a/b", "a/b/c", false, false),
    ("a/b/c", "a/b/c/d", false, false),

    // Empty topic and pattern
    ("", "", true, true),
    ("#", "", true, true),
    ("+", "", true, true),

    // Empty pattern vs non-empty topic
    ("", "a", true, false),
    ("", "a", false, false),

    // Trailing slashes
    ("a/b", "a/b/", true, false), // segment mismatch: b vs b + empty
    ("a/b/", "a/b/", true, true),
    ("a/b/", "a/b", true, false),

    // Topic with empty segments
    ("a//c", "a//c", true, true),
    ("a/+/c", "a//c", true, true),
    ("a/+/c", "a/c", true, false),

    // Non-anchored match when pattern is shorter
    ("a/b", "a/b/c", false, false),

    // Too long pattern, anchored match
    ("a/b/c", "a/b", true, false)
  )

  test("mqtt topic pattern matching works as expected") {
    tests.zipWithIndex.foreach {
      case (pattern, topic, anchored, expected) -> i =>
        val r = TopicPattern.fromString(pattern).matches(Topic.fromString(topic), anchored)
        assertEquals(r, expected, s"[$i] pattern='$pattern' topic='$topic' anchored=$anchored")
    }
  }
}
