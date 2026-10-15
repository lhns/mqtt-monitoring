package de.lhns.mqtt.monitoring

import io.circe.Json

object Values {
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
          elems.zipWithIndex.flatMap((e, i) =>
            valuesFromJson(e, topic / i.toString, mapping, enableArrays)
          ).toMap
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
            // "#>" marks the JSON-extracted sub-topics
            valuesFromJson(json, topic / "#>", mapping, enableArrays)
          case Left(_) =>
            mapping(topic)(string) match {
              case Some(double) => Map(topic -> double)
              case None => Map.empty
            }
        }
    }
}
