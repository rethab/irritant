package com.irritant.systems.jira

import java.net.URI

import cats.effect.IO
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.{User => JUser}
import com.atlassian.jira.rest.client.api.domain.{Issue => JiraIssue}
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.irritant.JiraCfg
import cats.{Eq, NonEmptyTraverse, Order, Show}
import cats.implicits._
import com.irritant.systems.jira.Jql.{And, Expr}
import com.irritant.systems.jira.Jql.Predef._
import org.codehaus.jettison.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.matching.Regex

class Jira (cfg: JiraCfg, restClient: JiraRestClient) {

  import Jira._

  def inTestingWithoutInstructions(): IO[Iterable[Issue]] =
    restClient.getSearchClient
      .searchJql(currentlyInTesting().compile, null, null, AllFields)
      .claim()
      .getIssues.asScala.filterNot(containsTestInstructions)
      .map(Issue.fromJiraIssue(cfg))
      .pure[IO]

  def findTesters[R[_] : NonEmptyTraverse](tickets: R[Key]): IO[R[Issue]] = {
    val issues = restClient.getSearchClient
      .searchJql(byKeys(tickets).compile)
      .claim()
      .getIssues
      .asScala

    // todo: what to do with tickets that were not found? eg. could be misspelled commit message
    def attachTester(key: Key): Issue =
      issues
        .find(_.getKey === key.key)
        .fold(Issue.mkEmpty(cfg, key))(Issue.fromJiraIssue(cfg))

    tickets.map(attachTester).pure[IO]
  }

}

object Jira {

  private val AllFields = Set("*all").asJava

  case class JiraUser(username: String) extends AnyVal

  object Implicits {
    implicit val eqJiraUser: Eq[JiraUser] = Eq.by(_.username)
    implicit val showJiraUser: Show[JiraUser] = Show.show(_.username)
    implicit val orderJiraUser: Order[JiraUser] = Order.by(_.username)

    implicit val showKey: Show[Key] = Show.show(_.key)
  }

  case class Issue (
    key: Key,
    description: Option[String],
    userLink: URI,
    reporter: Option[JiraUser],
    assignee: Option[JiraUser],
    tester: Option[JiraUser]
  )

  object Issue {

    private[jira] def fromJiraIssue(cfg: JiraCfg)(i: JiraIssue): Issue = {

      def fromNullableUser(nullableUser: JUser): Option[JiraUser] =
        Option(nullableUser).flatMap(u => Option(u.getName)).map(JiraUser.apply)

      Issue(
        key = Key(i.getKey),
        description = Option(i.getDescription),
        userLink = cfg.issueUrl(i.getKey),
        reporter = fromNullableUser(i.getReporter),
        assignee = fromNullableUser(i.getAssignee),
        tester = Option(i.getFieldByName("Tester"))
          .flatMap(f => Option(f.getValue)).collect { case obj: JSONObject => obj }
          .flatMap(obj => Try(obj.getString("name")).toOption.map(JiraUser.apply))
      )
    }

    private[jira] def mkEmpty(cfg: JiraCfg, key: Key): Issue =
      Issue(key = key, description = None, userLink = cfg.issueUrl(key.key), reporter = None, assignee = None, tester = None)

  }

  /**
   * @param key jira ticket identifier, eg. KTX-1337
   */
  case class Key private(key: String) extends AnyVal

  object Key {
    def fromCommitMessage(msg: String): Option[Key] = {
      val ticket: Regex = raw"[^A-Z]*([A-Z]+-\d{1,6}).*".r
      msg match {
        case ticket(key) => Some(Key(key))
        case _ => None
      }
    }
  }

  def withJira[A](config: JiraCfg)(act: Jira => IO[A]): IO[A] = {
    IO {
      val factory = new AsynchronousJiraRestClientFactory()
      factory.createWithBasicHttpAuthentication(config.uri, config.username, config.password)
    }.bracket { restClient =>
      act(new Jira(config, restClient))
    } { restClient =>
      IO(restClient.close())
    }
  }

  private def currentlyInTesting(): Expr =
    And(EqStatus("In Testing"), OpenSprints)

  private def byKeys[R[_] : NonEmptyTraverse](tickets: R[Key]): Expr =
    InKeys(tickets.map(_.key))

  private def containsTestInstructions(i: JiraIssue): Boolean =
    i.getComments.asScala.exists(_.getBody.contains("Test Instructions"))

}
