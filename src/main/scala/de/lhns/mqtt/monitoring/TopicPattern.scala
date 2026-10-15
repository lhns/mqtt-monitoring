package de.lhns.mqtt.monitoring

import cats.syntax.all.*
import io.circe.Codec

case class TopicPattern(segments: List[String]) {
  override def toString: String = segments.mkString("/")

  def matches(topic: Topic): Boolean = {
    @annotation.tailrec
    def loop(pattern: List[String], topic: List[String]): Boolean = (pattern, topic) match {
      case ("#" :: "#>" :: _) -> ts =>
        // '#' directly before '#>' spans the mqtt levels; '#>' only occurs where Values inserts it
        ts.dropWhile(_ != "#>") match {
          case Nil => false
          case rest => loop(pattern.tail, rest)
        }

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

      case _ =>
        false
    }

    loop(segments, topic.segments)
  }

  /*
  A non-standard way to specify the structure of a json payload.
  Example: mqtt/topic/+/test/#>/json/#/value
  */
  def hasJsonPattern: Boolean = segments.contains("#>")

  def stripJsonPattern: TopicPattern =
    TopicPattern(segments.takeWhile(_ != "#>"))
}

object TopicPattern {
  def fromString(pattern: String): TopicPattern = TopicPattern(pattern.split("/", -1).toList)

  given Codec[TopicPattern] = Codec.implied[String].imap(fromString)(_.toString)
}