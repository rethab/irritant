package com.irritant.systems.jira

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.irritant.{JiraCfg, User, Users}
import cats.{Eq, NonEmptyTraverse, Order, Show}
import cats.implicits._
import com.irritant.systems.jira.Jql.{And, Expr}
import com.irritant.systems.jira.Jql.Predef._
import org.codehaus.jettison.json.JSONObject

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try
import scala.util.matching.Regex

class Jira (config: JiraCfg) {

  import Jira._

  private val restClient: JiraRestClient = {
    val factory = new AsynchronousJiraRestClientFactory()
    factory.createWithBasicHttpAuthentication(config.uri, config.username, config.password)
  }

  def close(): Unit =
    restClient.close()

  def inTestingWithoutInstructions(): Iterable[Issue] =
    restClient.getSearchClient
      .searchJql(currentlyInTesting().compile, null, null, AllFields)
      .claim()
      .getIssues.asScala.filterNot(containsTestInstructions)

  def findTesters[R[_] : NonEmptyTraverse](users: Users, tickets: R[Ticket]): R[(Ticket, Option[User])] = {
    val issues = restClient.getSearchClient
      .searchJql(byKeys(tickets).compile)
      .claim()
      .getIssues
      .asScala

    tickets.map(t => (t, issues.find(_.getKey === t.key).flatMap(extractTester(users))))
  }

}

object Jira {

  private val AllFields = Set("*all").asJava

  case class JiraUser(username: String) extends AnyVal

  object Implicits {
    implicit val eqJiraUser: Eq[JiraUser] = Eq.by(_.username)
    implicit val showJiraUser: Show[JiraUser] = Show.show(_.username)
    implicit val orderJiraUser: Order[JiraUser] = Order.by(_.username)
  }

  /**
   * @param key jira ticket identifier, eg. KTX-1337
   */
  case class Ticket private(key: String) extends AnyVal

  object Ticket {
    def fromCommitMessage(msg: String): Option[Ticket] = {
      val ticket: Regex = raw"[^A-Z]*([A-Z]+-\d{1,6}).*".r
      msg match {
        case ticket(key) => Some(Ticket(key))
        case _ => None
      }
    }
  }

  def withJira[A](config: JiraCfg)(act: Jira => Future[A]): Future[A] = {
    val jira = new Jira(config)
    try act(jira) finally jira.close()
  }

  private def currentlyInTesting(): Expr =
    And(EqStatus("In Testing"), OpenSprints)

  private def byKeys[R[_] : NonEmptyTraverse](tickets: R[Ticket]): Expr =
    InKeys(tickets.map(_.key))

  private def containsTestInstructions(i: Issue): Boolean =
    i.getComments.asScala.exists(_.getBody.contains("Test Instructions"))

  private def extractTester(users: Users)(i: Issue): Option[User] = {
    Option(i.getFieldByName("Tester"))
      .flatMap(f => Option(f.getValue))
      .collect { case obj: JSONObject => obj }
      .flatMap(obj => Try(obj.getString("name")).toOption.map(JiraUser.apply))
      .flatMap(users.findByJira)
  }

}
