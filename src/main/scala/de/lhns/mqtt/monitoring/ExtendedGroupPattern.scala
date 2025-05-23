package de.lhns.mqtt.monitoring

import cats.syntax.all.*
import io.circe.{Codec, KeyDecoder, KeyEncoder}

import java.util.UUID
import java.util.regex.{MatchResult, Matcher, Pattern}

case class ExtendedGroupPattern private(
                                         patternString: String,
                                         groupAliases: Map[String, String]
                                       ) {
  val pattern: Pattern = Pattern.compile(patternString)

  override def toString: String = patternString

  val groupIndices: Map[String, Int] = {
    val groups = pattern.namedGroups
    groupAliases.map((groupName, uuidAlias) => groupName -> groups.get(uuidAlias).toInt)
  }

  def namedGroupMatches(matcher: Matcher): Map[String, String] =
    groupIndices.flatMap { (groupName, groupIndex) =>
      matcher.group(groupIndex) match {
        case null => None
        case groupMatch => Some(groupName -> groupMatch)
      }
    }
}

object ExtendedGroupPattern {
  private val namedGroupMatcher = Pattern.compile("""(?<!\\)\(\?<([^>]*)>""")

  def compile(pattern: String): ExtendedGroupPattern = {
    val matcher = namedGroupMatcher.matcher(pattern)
    var groupAliases = Map.empty[String, String]
    val aliasedPattern = matcher.replaceAll { (m: MatchResult) =>
      val groupName = m.group(1)
      val uuidAlias = "g" + UUID.randomUUID().toString.replaceAll("-", "")
      groupAliases += (groupName -> uuidAlias)
      s"(?<$uuidAlias>"
    }
    ExtendedGroupPattern(aliasedPattern, groupAliases)
  }

  given keyDecoder: KeyDecoder[ExtendedGroupPattern] = KeyDecoder[String].map(compile)

  given keyEncoder: KeyEncoder[ExtendedGroupPattern] = KeyEncoder[String].contramap(_.toString)

  given Codec[ExtendedGroupPattern] = Codec.implied[String].imap(keyDecoder(_).get)(keyEncoder(_))
}
