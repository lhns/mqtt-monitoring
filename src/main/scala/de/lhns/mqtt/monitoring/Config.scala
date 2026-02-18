package de.lhns.mqtt.monitoring

import cats.syntax.all.*
import com.comcast.ip4s.{Host, SocketAddress}
import de.lhns.mqtt.monitoring.Config.FilterConfig
import io.circe.{Codec, JsonNumber}

import scala.collection.immutable.ListMap
import scala.concurrent.duration.{Duration, FiniteDuration}

case class Config(
                   server: SocketAddress[Host],
                   username: String,
                   password: String,
                   maxCardinality: Option[Int],
                   exportInterval: Option[FiniteDuration],
                   filters: List[FilterConfig]
                 ) derives Codec

object Config {
  case class FilterConfig(
                           metricName: Option[String],
                           topics: List[TopicPattern],
                           discard: Option[Boolean],
                           topicLabel: Option[Boolean],
                           labels: Option[ListMap[String, String]],
                           labelMatchers: Option[List[ExtendedGroupPattern]],
                           valueMappings: Option[ListMap[ExtendedGroupPattern, ExtendedGroupPattern.Replacement]],
                           enableArrays: Option[Boolean],
                           ignoreWarnings: Option[Boolean]
                         ) derives Codec {
    val metricNameOrDefault: String = metricName.getOrElse("mqtt.value")

    val discardOrDefault: Boolean = discard.getOrElse(false)

    val topicLabelOrDefault: Boolean = topicLabel.getOrElse(true)

    val labelsOrDefault: ListMap[String, String] = labels.getOrElse(ListMap.empty)

    val labelMatchersOrDefault: List[ExtendedGroupPattern] = labelMatchers.getOrElse(List.empty)

    val valueMappingsOrDefault: ListMap[ExtendedGroupPattern, ExtendedGroupPattern.Replacement] = valueMappings.getOrElse(ListMap.empty)

    val enableArraysOrDefault: Boolean = enableArrays.getOrElse(false)

    val ignoreWarningsOrDefault: Boolean = ignoreWarnings.getOrElse(false)
  }

  private given Codec[SocketAddress[Host]] = Codec.implied[String].imap(SocketAddress.fromString(_).get)(_.toString)

  private given Codec[FiniteDuration] = Codec.implied[String].imap(Duration(_) match {
    case duration: FiniteDuration => duration
    case e => throw new RuntimeException(s"Unsupported duration: $e")
  })(_.toString)

  private given Codec[BigDecimal] = Codec.implied[JsonNumber].imap(e =>
    e.toBigDecimal.getOrElse(throw new RuntimeException(s"Invalid decimal: $e"))
  )(e =>
    JsonNumber.fromDecimalStringUnsafe(e.toString)
  )

  def fromEnv: Config =
    sys.env.get("CONFIG")
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .flatMap(io.circe.config.parser.decode[Config](_))
      .toTry.get
}
