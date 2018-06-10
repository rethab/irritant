package com.irritant.systems.jira

import java.net.URI

import cats.effect.{Effect, IO, Resource}
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.{SearchResult, Issue => JiraIssue, User => JUser}
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.irritant.{JiraCfg, ThreadPools}
import cats.{Eq, NonEmptyTraverse, Order, Show}
import cats.implicits._
import com.google.common.util.concurrent.FutureCallback
import com.irritant.systems.jira.Jql.{And, Expr}
import com.irritant.systems.jira.Jql.Predef._
import org.codehaus.jettison.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.matching.Regex

class Jira[F[_]](cfg: JiraCfg, restClient: JiraRestClient, threadPools: ThreadPools)(implicit F: Effect[F]) {

  import Jira._

  def inTestingAndMissingInstructions(): F[Iterable[Issue]] =
    searchJql(currentlyInTesting())
      .map(_.getIssues.asScala.filter(missingInstructions)
        .map(Issue.fromJiraIssue(cfg))
      )

  def findTesters[R[_] : NonEmptyTraverse](tickets: R[Key]): F[R[Issue]] = {
    val ioIssues: F[Iterable[JiraIssue]] = searchJql(byKeys(tickets)).map(_.getIssues.asScala)

    // todo: what to do with tickets that were not found? eg. could be misspelled commit message
    def attachTester(issues: Iterable[JiraIssue])(key: Key): Issue =
      issues
        .find(_.getKey === key.key)
        .fold(Issue.mkEmpty(cfg, key))(Issue.fromJiraIssue(cfg))

    ioIssues.map(issues => tickets.map(attachTester(issues)))
  }

  private def searchJql(expr: Expr): F[SearchResult] = {

    def completeAsync(cb: Either[Throwable, SearchResult] => Unit): FutureCallback[SearchResult] =
      new FutureCallback[SearchResult] {
        override def onFailure(t: Throwable): Unit = cb(Left(t))
        override def onSuccess(result: SearchResult): Unit = cb(Right(result))
      }

    val query = expr.compile
    for {
      _ <- F.liftIO(IO.shift(threadPools.dispatcher))
      res <- F.async { (cb: Either[Throwable, SearchResult] => Unit) =>
        restClient.getSearchClient
          .searchJql(query, null, null, AllFields)
          .`then`(completeAsync(cb))
        ()
      }
      _ <- F.liftIO(IO.shift(threadPools.computation))
    } yield res
  }


  private def close(): F[Unit] =
    F.delay(restClient.close())
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

  def mkJira[F[_]](config: JiraCfg, threadPools: ThreadPools)(implicit F: Effect[F]): Resource[F, Jira[F]] = {
    def acquire = {
      val factory = new AsynchronousJiraRestClientFactory()
      F.delay(factory.createWithBasicHttpAuthentication(config.uri, config.username, config.password))
        .map(client => new Jira[F](config, client, threadPools))
    }
    Resource.make(acquire)(jira => jira.close())
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
}
