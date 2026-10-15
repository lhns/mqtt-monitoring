package de.lhns.mqtt.monitoring

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.{MqttClient, MqttGlobalPublishFilter}
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.metrics.DoubleGauge
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.log4s.{MDC, getLogger}
import ox.*
import ox.channels.Channel

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.util.control.NonFatal

object Main extends OxApp {
  private val logger = getLogger

  def run(args: Vector[String])(using Ox): ExitCode = {
    logger.info("loading config")

    val config = Config.fromEnv

    logger.info("loaded config")

    config.maxCardinality.foreach(cardinality => System.setProperty("otel.java.metrics.cardinality.limit", cardinality.toString))

    config.exportInterval.foreach(interval => System.setProperty("otel.metric.export.interval", s"${interval.toMillis}ms"))

    val openTelemetry: OpenTelemetry = useInScope(
      AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk
    )(_.shutdown())

    val meter = openTelemetry.getMeter(getClass.getName)

    logger.info("connecting to mqtt server")

    val client =
      MqttClient.builder()
        .useMqttVersion3()
        .identifier(s"mqtt-monitoring-${UUID.randomUUID()}")
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

    val messages = Channel.bufferedDefault[Mqtt3Publish]

    fork {
      logger.info("registering mqtt listener")

      val publishes = useCloseableInScope(client.publishes(MqttGlobalPublishFilter.SUBSCRIBED))

      config.filters
        .flatMap(_.topics)
        .map(_.stripJsonPattern)
        .distinct
        .foreach { topic =>
          try {
            useInScope {
              logger.info(s"subscribing to mqtt topic: $topic")
              client
                .subscribeWith()
                .topicFilter(topic.toString)
                .send()
              logger.info(s"subscribed to mqtt topic: $topic")
            } { _ =>
              logger.info(s"unsubscribing from mqtt topic: $topic")
              client.unsubscribeWith()
                .topicFilter(topic.toString)
                .send()
              logger.info(s"unsubscribed from mqtt topic: $topic")
            }
          } catch {
            case NonFatal(e) =>
              throw new RuntimeException(s"failed to subscribe to mqtt topic: $topic", e)
          }
        }

      logger.info("listening for mqtt messages")

      forever {
        messages.send(publishes.receive())
      }
    }

    val topicKey = AttributeKey.stringKey("mqtt.topic")

    val gaugeByMetricName: Map[String, DoubleGauge] =
      config.filters.map(_.metricNameOrDefault).distinct.map { metricName =>
        val gauge = meter.gaugeBuilder(metricName).build()
        metricName -> gauge
      }.toMap

    forever {
      val msg = messages.receive()
      val topic = Topic(msg.getTopic)

      // don't leak metric/label entries from the previous message
      MDC.clear()
      MDC("topic") = topic.toString

      val result = Routing.route(
        filters = config.filters,
        mqttTopic = topic,
        payload = new String(msg.getPayloadAsBytes, StandardCharsets.UTF_8),
        mapping = filter => topic => string => {
          MDC("topic") = topic.toString
          filter.valueMappingsOrDefault.collectFirst(Function.unlift { (pattern, replacement) =>
            val matcher = pattern.pattern.matcher(string)
            if (matcher.matches())
              replacement match {
                case ExtendedGroupPattern.Replacement.PatternReplacement(patternReplacement) =>
                  Some(matcher.replaceFirst(patternReplacement))

                case ExtendedGroupPattern.Replacement.LiteralReplacement(literalReplacement) =>
                  Some(literalReplacement)
              }
            else
              None
          }) match {
            case None =>
              if (!filter.ignoreWarningsOrDefault)
                logger.warn(s"no mapping defined for mqtt message: $string")
              None
            case Some(mappedString) =>
              mappedString.toDoubleOption match {
                case None =>
                  logger.error(s"mapped value is not a double: $mappedString")
                  None
                case e => e
              }
          }
        }
      )

      result.exports.foreach { (filter, values) =>
        val gauge = gaugeByMetricName(filter.metricNameOrDefault)

        values.foreach { (topic, value) =>
          MDC.clear()
          MDC("topic") = topic.toString
          MDC("metric") = filter.metricNameOrDefault

          val labels: Map[String, String] =
            filter.labelsOrDefault ++
              filter.labelMatchersOrDefault.collectFirst(Function.unlift { pattern =>
                val matcher = pattern.pattern.matcher(topic.toString)
                if (matcher.matches())
                  Some(pattern.namedGroupMatches(matcher))
                else
                  None
              }).getOrElse(Map.empty)

          MDC.addAll(labels.map((k, v) => s"label.$k" -> v))

          logger.debug(s"parsed value: $value")

          val attributes =
            labels.foldLeft {
                if (filter.topicLabelOrDefault)
                  Attributes.builder()
                    .put(topicKey, topic.toString)
                else
                  Attributes.builder()
              } { case (attributes, (k, v)) =>
                attributes.put(k, v)
              }
              .build()

          gauge.set(value, attributes)
        }
      }

      if (result.warnNothingSelected) {
        MDC.clear()
        MDC("topic") = topic.toString
        logger.warn("no value selected by json pattern for mqtt message")
      }
    }
  }
}
