package de.lhns.mqtt.monitoring

import io.circe.Json

object Values {
  def valuesFromJson(
                      json: Json,
                      topic: Topic,
                      mapping: Topic => String => Option[Double],
                      select: Topic => Boolean,
                      enableArrays: Boolean
                    ): Map[Topic, Double] = {
    // select is checked before mapping so unselected strings never log mapping warnings
    json.fold[Map[Topic, Double]](
      jsonNull = Map.empty,
      jsonBoolean = e =>
        if (!select(topic)) Map.empty
        else if (e) Map(topic -> 1)
        else Map(topic -> 0),
      jsonNumber = e =>
        if (select(topic)) Map(topic -> e.toDouble)
        else Map.empty,
      jsonString = e =>
        if (!select(topic)) Map.empty
        else mapping(topic)(e) match {
          case Some(double) => Map(topic -> double)
          case None => Map.empty
        },
      jsonArray = elems =>
        if (enableArrays)
          elems.zipWithIndex.flatMap((e, i) =>
            valuesFromJson(e, topic / i.toString, mapping, select, enableArrays)
          ).toMap
        else
          Map.empty,
      jsonObject = obj =>
        obj.toMap.flatMap((k, v) =>
          valuesFromJson(v, topic / k, mapping, select, enableArrays)
        )
    )
  }

  def valuesFromString(
                        string: String,
                        topic: Topic,
                        mapping: Topic => String => Option[Double],
                        select: Topic => Boolean,
                        enableArrays: Boolean
                      ): Map[Topic, Double] =
    string.toDoubleOption match {
      case Some(double) =>
        if (select(topic)) Map(topic -> double)
        else Map.empty
      case None =>
        io.circe.parser.parse(string) match {
          case Right(json) =>
            // "#>" marks the JSON-extracted sub-topics
            valuesFromJson(json, topic / "#>", mapping, select, enableArrays)
          case Left(_) =>
            if (!select(topic)) Map.empty
            else mapping(topic)(string) match {
              case Some(double) => Map(topic -> double)
              case None => Map.empty
            }
        }
    }
}
