package com.irritant.systems.jira

import java.net.URI
import java.util.concurrent.{CompletableFuture, ExecutionException}

import cats.effect.IO
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.{SearchResult, Issue => JiraIssue, User => JUser}
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.irritant.JiraCfg
import cats.{Eq, NonEmptyTraverse, Order, Show}
import cats.implicits._
import com.irritant.systems.jira.Jql.{And, Expr}
import com.irritant.systems.jira.Jql.Predef._
import org.codehaus.jettison.json.JSONObject

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.util.Try
import scala.util.matching.Regex

class Jira (cfg: JiraCfg, restClient: JiraRestClient) {

  import Jira._

  def inTestingAndMissingInstructions(): IO[Iterable[Issue]] =
    searchJql(currentlyInTesting())
      .map(_.getIssues.asScala.filter(missingInstructions)
        .map(Issue.fromJiraIssue(cfg))
      )

  def findTesters[R[_] : NonEmptyTraverse](tickets: R[Key]): IO[R[Issue]] = {
    val ioIssues = searchJql(byKeys(tickets)).map(_.getIssues.asScala)

    // todo: what to do with tickets that were not found? eg. could be misspelled commit message
    def attachTester(issues: Iterable[JiraIssue])(key: Key): Issue =
      issues
        .find(_.getKey === key.key)
        .fold(Issue.mkEmpty(cfg, key))(Issue.fromJiraIssue(cfg))

    ioIssues.map(issues => tickets.map(attachTester(issues)))
  }

  private def searchJql(expr: Expr): IO[SearchResult] =
    IO.fromFuture(IO(
      makeCompletableFuture(restClient.getSearchClient.searchJql(expr.compile, null, null, AllFields)).toScala
    ))
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
    summary: Option[String], // the 'title' of the issue
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
        summary = Option(i.getSummary),
        userLink = cfg.issueUrl(i.getKey),
        reporter = fromNullableUser(i.getReporter),
        assignee = fromNullableUser(i.getAssignee),
        tester = Option(i.getFieldByName("Tester"))
          .flatMap(f => Option(f.getValue)).collect { case obj: JSONObject => obj }
          .flatMap(obj => Try(obj.getString("name")).toOption.map(JiraUser.apply))
      )
    }

    private[jira] def mkEmpty(cfg: JiraCfg, key: Key): Issue =
      Issue(key = key, summary = None, userLink = cfg.issueUrl(key.key), reporter = None, assignee = None, tester = None)

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

  /** Bugs with reproduction steps don't need test
   * instructions, everything else does. */
  def missingInstructions(i: JiraIssue): Boolean =
    !containsTestInstructions(i) && !isBugWithSteps(i)

  private def containsTestInstructions(i: JiraIssue): Boolean =
    Set(
        "Testing instructions"
      , "testing instructions"
      , "Testing Instructions"
      , "Test Instructions"
      , "Test instructions"
    ).exists(title => i.getComments.asScala.exists(_.getBody.contains(title)))

  private def isBugWithSteps(issue: JiraIssue): Boolean =
    ( for {
      issueType <- Option(issue.getIssueType).flatMap(it => Option(it.getName))
      issueDescription <- Option(issue.getDescription)
      } yield issueType == "Bug" && issueDescription.contains("Steps")
    ).getOrElse(false)


  private def makeCompletableFuture[A](future: java.util.concurrent.Future[A]): CompletableFuture[A] = {
    CompletableFuture.supplyAsync(() => {
      try {
        future.get()
      } catch {
        case e: InterruptedException => throw new RuntimeException(e);
        case e: ExecutionException => throw new RuntimeException(e);
      }
    })
  }
}
