package de.lhns.mqtt.monitoring

import cats.data.OptionT
import cats.effect.Sync
import cats.effect.std.Env
import com.comcast.ip4s.{Host, SocketAddress}
import de.lhns.mqtt.monitoring.Config.FilterConfig
import cats.syntax.all.*
import io.circe.Codec

case class Config(
                   server: SocketAddress[Host],
                   username: String,
                   password: String,
                   filters: List[FilterConfig]
                 )derives Codec

object Config {
  case class FilterConfig(
                           metricName: Option[String],
                           topics: List[String],
                           labelPatterns: Option[List[String]],
                           valueMappings: Option[Map[String, String]],
                           enableArrays: Option[Boolean],
                           warnInvalidValues: Option[Boolean]
                         )derives Codec {
    val metricNameOrDefault: String = metricName.getOrElse("mqtt.value")

    val labelPatternsOrDefault: List[String] = labelPatterns.getOrElse(List.empty)

    val valueMappingsOrDefault: Map[String, String] = valueMappings.getOrElse(Map.empty)

    val enableArraysOrDefault: Boolean = enableArrays.getOrElse(false)

    val warnInvalidValuesOrDefault: Boolean = warnInvalidValues.getOrElse(true)
  }
  
  private given Codec[SocketAddress[Host]] = Codec.implied[String].imap(SocketAddress.fromString(_).get)(_.toString)

  def fromEnv: Config =
    sys.env.get("CONFIG")
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .flatMap(io.circe.config.parser.decode[Config](_))
      .toTry.get
}
