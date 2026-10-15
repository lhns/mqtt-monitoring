package de.lhns.mqtt.monitoring

import com.comcast.ip4s.{Host, SocketAddress}
import de.lhns.mqtt.monitoring.Config.FilterConfig
import io.circe.Codec

import scala.collection.immutable.ListMap
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Success, Try}

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

    // Patterns whose mqtt part matches the topic; a plain one among them selects every value.
    def matchedPatterns(mqttTopic: Topic): List[TopicPattern] =
      topics.filter(_.stripJsonPattern.matches(mqttTopic))

    def valueSelector(mqttTopic: Topic): Option[Topic => Boolean] = {
      val matched = matchedPatterns(mqttTopic)
      if (matched.isEmpty) None
      else Some(valueTopic => matched.exists(p => !p.hasJsonPattern || p.matches(valueTopic)))
    }
  }

  private given Codec[SocketAddress[Host]] =
    Codec.implied[String].iemap(e => SocketAddress.fromString(e).toRight(s"Invalid server address: $e"))(_.toString)

  private given Codec[FiniteDuration] =
    Codec.implied[String].iemap(e =>
      Try(Duration(e)) match {
        case Success(duration: FiniteDuration) => Right(duration)
        case _ => Left(s"Invalid finite duration: $e")
      }
    )(_.toString)

  def fromEnv: Config =
    sys.env.get("CONFIG")
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .flatMap(io.circe.config.parser.decode[Config](_))
      .toTry.get
}
