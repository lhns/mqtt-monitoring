package de.lhns.mqtt.monitoring

case class TopicJsonPattern(topicPattern: TopicPattern, jsonPatternSegments: List[String]) {
  override def toString: String = s"$topicPattern/#>/${jsonPatternSegments.mkString("/")}"
}

object TopicJsonPattern {
  // mqtt/topic/+/test/#>/json/#/value
  def fromString(pattern: String): TopicJsonPattern = {
    val segments = pattern.split("/", -1).toList
    val (topicPatternSegments, jsonPatternSegments) = segments.span(_ != "#>")
    val topicPattern = TopicPattern(topicPatternSegments)
    TopicJsonPattern(topicPattern, jsonPatternSegments)
  }
}
