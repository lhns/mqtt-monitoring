package de.lhns.mqtt.monitoring

import cats.syntax.all.*
import com.comcast.ip4s.{Host, SocketAddress}
import de.lhns.mqtt.monitoring.Config.FilterConfig
import io.circe.Codec

import scala.collection.immutable.ListMap

case class Config(
                   server: SocketAddress[Host],
                   username: String,
                   password: String,
                   maxCardinality: Option[Int],
                   filters: List[FilterConfig]
                 )derives Codec

object Config {
  case class FilterConfig(
                           metricName: Option[String],
                           topics: List[TopicPattern],
                           discard: Option[Boolean],
                           topicLabel: Option[Boolean],
                           labelMatchers: Option[List[ExtendedGroupPattern]],
                           valueMappings: Option[ListMap[ExtendedGroupPattern, String]],
                           enableArrays: Option[Boolean],
                           ignoreWarnings: Option[Boolean]
                         )derives Codec {
    val metricNameOrDefault: String = metricName.getOrElse("mqtt.value")

    val discardOrDefault: Boolean = discard.getOrElse(false)

    val topicLabelOrDefault: Boolean = topicLabel.getOrElse(true)

    val labelMatchersOrDefault: List[ExtendedGroupPattern] = labelMatchers.getOrElse(List.empty)

    val valueMappingsOrDefault: ListMap[ExtendedGroupPattern, String] = valueMappings.getOrElse(ListMap.empty)

    val enableArraysOrDefault: Boolean = enableArrays.getOrElse(false)

    val ignoreWarningsOrDefault: Boolean = ignoreWarnings.getOrElse(false)
  }

  private given Codec[SocketAddress[Host]] = Codec.implied[String].imap(SocketAddress.fromString(_).get)(_.toString)

  def fromEnv: Config =
    sys.env.get("CONFIG")
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .flatMap(io.circe.config.parser.decode[Config](_))
      .toTry.get
}
