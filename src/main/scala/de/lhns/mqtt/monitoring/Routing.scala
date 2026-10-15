package de.lhns.mqtt.monitoring

import de.lhns.mqtt.monitoring.Config.FilterConfig

import scala.annotation.tailrec

object Routing {
  case class Result(
                     exports: List[(FilterConfig, Map[Topic, Double])],
                     warnNothingSelected: Boolean
                   )

  /*
  Filters are tried in order. A filter with a matching plain pattern takes the whole message and
  ends routing. A filter whose matching patterns all have a json part only takes the value topics
  it selects that no earlier filter has claimed; later filters see the rest.
  Only filters that export at least one value are listed in `exports`.
  */
  def route(
             filters: List[FilterConfig],
             mqttTopic: Topic,
             payload: String,
             mapping: FilterConfig => Topic => String => Option[Double]
           ): Result = {
    @tailrec
    def loop(
              rest: List[FilterConfig],
              claimed: Set[Topic],
              exports: List[(FilterConfig, Map[Topic, Double])],
              matchedFilters: List[FilterConfig]
            ): Result =
      rest match {
        case Nil =>
          finish(claimed, exports, matchedFilters)

        case filter :: tail =>
          filter.valueSelector(mqttTopic) match {
            case None =>
              loop(tail, claimed, exports, matchedFilters)

            case Some(selector) =>
              var newlyClaimed = Set.empty[Topic]
              // claim on selection, even if the mapping fails afterwards
              val select: Topic => Boolean = valueTopic =>
                selector(valueTopic) && !claimed(valueTopic) && {
                  newlyClaimed += valueTopic
                  true
                }
              val values = Values.valuesFromString(
                string = payload,
                topic = mqttTopic,
                mapping = if (filter.discardOrDefault) _ => _ => None else mapping(filter),
                select = select,
                enableArrays = filter.enableArraysOrDefault
              )
              val newExports =
                if (filter.discardOrDefault || values.isEmpty) exports
                else exports :+ (filter -> values)
              val newClaimed = claimed ++ newlyClaimed
              val newMatched = matchedFilters :+ filter
              if (filter.matchedPatterns(mqttTopic).exists(!_.hasJsonPattern))
                finish(newClaimed, newExports, newMatched)
              else
                loop(tail, newClaimed, newExports, newMatched)
          }
      }

    def finish(
                claimed: Set[Topic],
                exports: List[(FilterConfig, Map[Topic, Double])],
                matchedFilters: List[FilterConfig]
              ): Result =
      Result(
        exports = exports,
        warnNothingSelected =
          claimed.isEmpty &&
            matchedFilters.flatMap(_.matchedPatterns(mqttTopic)).exists(_.hasJsonPattern) &&
            matchedFilters.forall(!_.ignoreWarningsOrDefault)
      )

    loop(filters, Set.empty, List.empty, List.empty)
  }
}
