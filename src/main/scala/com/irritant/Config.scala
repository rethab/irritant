package com.irritant

import java.io.File
import java.net.URI

import cats.Order
import cats.implicits._
import com.irritant.systems.jira.Jira.JiraUser
import com.irritant.systems.slack.Slack.SlackUser
import pureconfig.{ConfigReader, ConvertHelpers}


case class Config (
  jira: JiraCfg,
  slack: SlackCfg,
  users: Seq[User]
)

case class JiraCfg(uri: URI, username: String, password: String)

case class SlackCfg(
  token: String,
  postAsUser: String
)

case class User(
  prettyName: String,
  jira: JiraUser,
  slack: SlackUser
)

object User {
  implicit val userOrder: Order[User] = Order.by(_.prettyName)
}

case class GitConfig(
  repo: File
)

object Config {

  implicit val uriReader: ConfigReader[URI] = ConfigReader.fromString(
    ConvertHelpers.catchReadError(s => new URI(s))
  )
}

