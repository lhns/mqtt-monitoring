package de.lhns.mqtt.monitoring

import com.hivemq.client.mqtt.datatypes.MqttTopic
import scala.jdk.CollectionConverters.*

case class Topic(segments: List[String]) {
  infix def /(segment: String) = copy(segments = segments :+ segment)

  override def toString: String = segments.mkString("/")
}

object Topic {
  def apply(mqttTopic: MqttTopic): Topic = Topic(mqttTopic.getLevels.asScala.toList)

  def fromString(topic: String): Topic = Topic(topic.split("/", -1).toList)
}
