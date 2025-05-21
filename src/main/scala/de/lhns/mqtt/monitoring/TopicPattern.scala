package de.lhns.mqtt.monitoring

case class TopicPattern(segments: List[String]) {
  override def toString: String = segments.mkString("/")

  def matches(topic: Topic, anchored: Boolean): Boolean = {
    @annotation.tailrec
    def loop(pattern: List[String], topic: List[String]): Boolean = (pattern, topic) match {
      case ("#" :: _) -> _ =>
        // '#' matches anything remaining, must be last in pattern
        pattern.tail.isEmpty

      case ("+" :: ps) -> (_ :: ts) =>
        loop(ps, ts)

      case (p :: ps) -> (t :: ts) if p == t =>
        loop(ps, ts)

      case (Nil, Nil) =>
        true

      case (Nil, _ :: _) =>
        // Pattern is shorter than topic
        false

      case (_ :: _, Nil) if !anchored =>
        // Pattern is longer than topic
        true

      case _ =>
        false
    }

    loop(segments, topic.segments)
  }
}

object TopicPattern {
  def fromString(pattern: String): TopicPattern = TopicPattern(pattern.split("/", -1).toList)
}