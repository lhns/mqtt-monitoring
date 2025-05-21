package de.lhns.mqtt.monitoring

import com.comcast.ip4s.{Host, SocketAddress}
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.{MqttClient, MqttGlobalPublishFilter}
import io.circe.{Codec, Decoder, Json}
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.log4s.getLogger
import ox.*
import ox.channels.Channel

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object Main extends OxApp {
  private val logger = getLogger

  def run(args: Vector[String])(using Ox): ExitCode = {
    logger.info("loading config")

    val config = Config.fromEnv

    logger.info("loaded config")

    val openTelemetry: OpenTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk
    val meter = openTelemetry.getMeter(getClass.getName)

    logger.info("connecting to mqtt server")

    val client =
      MqttClient.builder()
        .useMqttVersion3()
        .identifier(s"mqtt-monitoring/${UUID.randomUUID()}")
        .serverHost(config.server.host.toString)
        .serverPort(config.server.port.value)
        .buildBlocking()

    useInScope {
      client
        .connectWith()
        .simpleAuth()
        .username(config.username)
        .password(config.password.getBytes(StandardCharsets.UTF_8))
        .applySimpleAuth()
        .send()
    } { _ =>
      logger.info("disconnecting from mqtt server")

      client.disconnect()

      logger.info("disconnected from mqtt server")
    }

    logger.info("connected to mqtt server")

    // TODO: unsubscribe

    val publishes = useCloseableInScope(client.publishes(MqttGlobalPublishFilter.ALL))

    val c = Channel.bufferedDefault[Mqtt3Publish]

    fork {
      /*client
        .subscribeWith()
        .topicFilter("test/asdf")
        .send()*/

      client
        .subscribeWith()
        .topicFilter("#")
        .send()

      while (true) {
        c.send(publishes.receive())
      }
    }

    val gauge = meter.gaugeBuilder("mqtt.value").build()

    val topicKey = AttributeKey.stringKey("mqtt.topic")

    case class TopicValues(values: Map[Topic, Double])

    def valuesFromJson(
                        json: Json,
                        topic: Topic,
                        mapping: Topic => String => Option[Double],
                        enableArrays: Boolean
                      ): Map[Topic, Double] = {
      json.fold[Map[Topic, Double]](
        jsonNull = Map.empty,
        jsonBoolean =
          if (_) Map(topic -> 1)
          else Map(topic -> 0),
        jsonNumber = e => Map(topic -> e.toDouble),
        jsonString = mapping(topic)(_) match {
          case Some(double) => Map(topic -> double)
          case None => Map.empty
        },
        jsonArray = elems =>
          if (enableArrays)
            elems.zipWithIndex.toMap.flatMap((e, i) =>
              valuesFromJson(e, topic / i.toString, mapping, enableArrays)
            )
          else
            Map.empty,
        jsonObject = obj =>
          obj.toMap.flatMap((k, v) =>
            valuesFromJson(v, topic / k, mapping, enableArrays)
          )
      )
    }

    def valuesFromString(
                          string: String,
                          topic: Topic,
                          mapping: Topic => String => Option[Double],
                          enableArrays: Boolean
                        ): Map[Topic, Double] =
      string.toDoubleOption match {
        case Some(double) =>
          Map(topic -> double)
        case None =>
          io.circe.parser.parse(string) match {
            case Right(json) =>
              valuesFromJson(json, topic, mapping, enableArrays)
            case Left(_) =>
              mapping(topic)(string) match {
                case Some(double) => Map(topic -> double)
                case None => Map.empty
              }
          }
      }

    while (true) {
      val msg = c.receive()
      val topic = Topic(msg.getTopic)
      if (topic.segments.startsWith(List("zigbee2mqtt"))) {
        val msgString = new String(msg.getPayloadAsBytes)
        val values = valuesFromString(msgString, topic, _ => _ => None, false)
        values.foreach { case (topic, value) =>
          println(s"$topic: $value")
          //gauge.set(value, Attributes.of(topicKey, topic.toString))
        }
      }
    }

    ExitCode.Success
  }
}
