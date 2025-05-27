package de.lhns.mqtt.monitoring

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.{MqttClient, MqttGlobalPublishFilter}
import io.circe.Json
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

    config.exportInterval.foreach(interval => System.setProperty("otel.metric.export.interval", interval.toSeconds.toString))

    val openTelemetry: OpenTelemetry = useInScope(
      AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk
    )(_.shutdown())
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


    val messages = Channel.bufferedDefault[Mqtt3Publish]

    fork {
      logger.info("registering mqtt listener")

      val publishes = useCloseableInScope(client.publishes(MqttGlobalPublishFilter.SUBSCRIBED))

      config.filters
        .flatMap(_.topics)
        .map(_.stripJsonPattern)
        .distinct
        .foreach { topic =>
          useInScope {
            logger.debug(s"subscribing to mqtt topic: $topic")
            client
              .subscribeWith()
              .topicFilter(topic.toString)
              .send()
            logger.debug(s"subscribed to mqtt topic: $topic")
          } { _ =>
            logger.debug(s"unsubscribing from mqtt topic: $topic")
            client.unsubscribeWith()
              .topicFilter(topic.toString)
              .send()
            logger.debug(s"unsubscribed from mqtt topic: $topic")
          }
        }

      logger.info("listening for mqtt messages")

      while (true) {
        messages.send(publishes.receive())
      }
    }

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
              valuesFromJson(json, topic / "#>", mapping, enableArrays)
            case Left(_) =>
              mapping(topic)(string) match {
                case Some(double) => Map(topic -> double)
                case None => Map.empty
              }
          }
      }

    val topicKey = AttributeKey.stringKey("mqtt.topic")

    val gaugeByMetricName: Map[String, DoubleGauge] =
      config.filters.map(_.metricNameOrDefault).distinct.map { metricName =>
        val gauge = meter.gaugeBuilder(metricName).build()
        metricName -> gauge
      }.toMap

    while (true) {
      val msg = messages.receive()
      val topic = Topic(msg.getTopic)

      MDC("topic") = topic.toString

      config.filters
        .find(_.topics.exists(_.matches(topic, anchored = true)))
        .filterNot(_.discardOrDefault)
        .foreach { filter =>
          val gauge = gaugeByMetricName(filter.metricNameOrDefault)

          val msgString = try {
            new String(msg.getPayloadAsBytes, StandardCharsets.UTF_8)
          } catch {
            case NonFatal(e) =>
              if (!filter.ignoreWarningsOrDefault)
                logger.warn(e)(s"mqtt message is not a string")
              throw e
          }
          val values = valuesFromString(
            string = msgString,
            topic = topic,
            mapping = topic => string => {
              MDC("topic") = topic.toString
              filter.valueMappingsOrDefault.collectFirst(Function.unlift { (pattern, replacement) =>
                val matcher = pattern.pattern.matcher(string)
                if (matcher.matches())
                  Some(matcher.replaceFirst(replacement))
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
            },
            enableArrays = filter.enableArraysOrDefault
          )

          values.foreach { (topic, value) =>
            MDC.clear()
            MDC("topic") = topic.toString
            MDC("metric") = filter.metricNameOrDefault

            val labels: Map[String, String] = filter.labelMatchersOrDefault.collectFirst(Function.unlift { pattern =>
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
    }

    MDC.clear()

    ExitCode.Success
  }
}
