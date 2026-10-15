package de.lhns.mqtt.monitoring

import munit.FunSuite

class TopicMatcherSuite extends FunSuite {
  private val tests: Seq[(String, String, Boolean)] = Seq(
    // Exact matches
    ("a/b/c", "a/b/c", true),
    ("a/b", "a/b", true),

    // Topic and pattern length mismatch
    ("a/b", "a/b/c", false),
    ("a/b/c", "a/b", false),

    // Single-level wildcard +
    ("a/+/c", "a/b/c", true),
    ("a/+/c", "a/x/c", true),
    ("a/+/c", "a/x/d", false),
    ("+/+/+", "a/b/c", true),
    ("+/b/+", "a/b/c", true),
    ("a/b/+", "a/b/c", true),
    ("a/b/+", "a/b", false), // segment missing
    ("+/+", "a/b", true),
    ("+/+", "a", false), // missing
    ("+/+", "a/b/c", false), // extra

    // Wrong segment count with +
    ("a/+/c", "a/c", false),
    ("a/+/c", "a/b/c/d", false),

    // Multi-level wildcard #
    ("a/b/#", "a/b", true), // # matches zero or more
    ("a/b/#", "a/b/c", true),
    ("a/b/#", "a/x/c", false),
    ("a/b/#", "a/b/c/d/e", true),
    ("#", "a", true),
    ("#", "a/b/c", true),
    ("#", "/", true),
    ("#", "//", true),

    // # cannot match middle segments
    ("a/#/c", "a/b/c", false), // Invalid pattern: # not last
    ("a/#/c", "a/x/y/c", false),

    // Pattern longer than topic
    ("a/b/c/d", "a/b/c", false),
    ("a/b/+", "a/b", false),

    // # directly before #> spans the mqtt levels
    ("a/#/#>/x", "a/b/c/#>/x", true),
    ("a/#/#>/x", "a/#>/x", true),
    ("a/#/#>/x", "a/b/c", false),
    ("a/#/#>/x", "a/b/#>/y", false),

    // Empty topic and pattern
    ("", "", true),
    ("#", "", true),
    ("+", "", true),

    // Empty pattern vs non-empty topic
    ("", "a", false),

    // Trailing slashes
    ("a/b", "a/b/", false), // segment mismatch: b vs b + empty
    ("a/b/", "a/b/", true),
    ("a/b/", "a/b", false),

    // Topic with empty segments
    ("a//c", "a//c", true),
    ("a/+/c", "a//c", true),
    ("a/+/c", "a/c", false),

    // Too long pattern
    ("a/b/c", "a/b", false)
  )

  test("mqtt topic pattern matching works as expected") {
    tests.zipWithIndex.foreach {
      case (pattern, topic, expected) -> i =>
        val r = TopicPattern.fromString(pattern).matches(Topic.fromString(topic))
        assertEquals(r, expected, s"[$i] pattern='$pattern' topic='$topic'")
    }
  }
}
