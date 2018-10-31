package com.irritant

import java.io.File
import java.net.URI

import cats.Order
import cats.implicits._
import com.irritant.systems.jira.Jira.JiraUser
import com.irritant.systems.slack.Slack.SlackUser
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto.exportReader


case class Config (
  jira: JiraCfg,
  slack: SlackCfg,
  users: Seq[User]
)

/**
 * @param uri base uri of the project, eg. https://my-project.atlassian.net
 * @param username to be used in api requests
 * @param password to be used in api requests
 */
case class JiraCfg(
  uri: URI,
  username: String,
  password: String
) {

  /**
   * Creates a link the user can click on to
   * open the ticket. This is needed, because
   * the api always returns issue links that
   * point to the api rather than the user-facing
   * instance.
   * @param key key of the issue, eg. MTP-3
   */
  def issueUrl(key: String): URI =
    new URI(show"${uri.toString}/browse/$key")
}

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

  def load(): Either[ConfigReaderFailures, Config] = pureconfig.loadConfig[Config]

}

