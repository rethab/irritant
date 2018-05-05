package com.irritant.systems.slack

import akka.Done
import akka.actor.ActorSystem
import cats.NonEmptyTraverse
import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.IO
import com.irritant.{SlackCfg, User, Users}
import com.irritant.systems.jira.Jira.Implicits._
import slack.api.SlackApiClient
import cats.implicits._
import com.irritant.systems.jira.Jira.{Issue, JiraUser}

import scala.util.control.NonFatal

class Slack(config: SlackCfg, users: Users)(implicit actorSystem: ActorSystem) {

  import Slack._

  private val api = SlackApiClient(config.token)

  def ping: IO[Either[Error, SlackTeam]] =
    IO.fromFuture(IO(api.testAuth()))
      .map(_.team.asRight[Error].map(SlackTeam.apply))
      .recover { case NonFatal(ex) => show"Failed to connect to slack: ${ex.getMessage}}".asLeft[SlackTeam] }

  def nag(issues: Iterable[Issue]): IO[Done] = {

    val allIssues = issues.toList.groupByNel(_.assignee)

    val unassingedIssues: Iterable[Issue] = allIssues.collect { case (None, is) => is.toList }.flatten
    val issuesByUser = allIssues.collect { case (Some(u), is) => (users.findByJira(u).toRight(u), is) }
    val unmappedUsers = issuesByUser.collect { case (Left(jUser), is) => (jUser, is) }
    val mappedUsers: Map[User, NonEmptyList[Issue]] = issuesByUser.collect { case (Right(user), is) => (user, is) }

    unassingedIssues.foreach { i =>
      IO(println(show"Issue ${i.key} is in testing, but not assigned"))
    }

    unmappedUsers.foreach { case (user, is) =>
      IO(println(show"User ${user.username} is not mapped to slack and issues: ${is.toList.mkString(",")}"))
    }

    mappedUsers
      .toList
      .map {
        case (user, is) =>
          IO.fromFuture(IO(api
            .postChatMessage(
              channelId = user.slack.userId,
              text = missingTestInstructions(user, is),
              username = Some("Jira Issue Nagger"))))
            .flatMap { s =>
              IO(println(show"Notified ${user.prettyName} about ${is.size} issues w/o testing instructions"))
                .map(_ => s)
            } }
      .sequence[IO, String]
      .map(_ => Done)
  }

  def readyForTesting(forTesting: NonEmptyList[Issue]): IO[Done] = {

    /* notify tester if tester is set
     * notify assignee if assignee is set
     * notify reporter */

    val (woTester, wTester) = forTesting.toList.partitionEither(i => i.tester.map(t => (i, t)).toRight(i))
    val (unassigned, assigned) = woTester.partitionEither(i => i.assignee.map(a => (i, a)).toRight(i))

    val aIO = wTester.groupByNel(_._2).map(i => (i._1, i._2.map(_._1))).toList.traverse[IO, Either[MissingSlackUser, String]] { case (user, issues) => notifyJiraUser(user, u => readyForTestingMsg(u, issues)) }
    val bIO = assigned.groupByNel(_._2).map(i => (i._1, i._2.map(_._1))).toList.traverse[IO, Either[MissingSlackUser, String]] { case (user, issues) => notifyJiraUser(user, u => missingTesterMsg(u, issues)) }
    val cIO = unassigned.groupByNel(_.reporter.get).toList.traverse[IO, Either[MissingSlackUser, String]] { case (user, issues) => notifyJiraUser(user, u => missingAssingeeAndTesterMsg(u, issues)) }

    for {
      a <- aIO
      b <- bIO
      c <- cIO
      msus: Seq[MissingSlackUser] = a.collect { case Left(msu) => msu } ++ b.collect { case Left(msu) => msu } ++ c.collect { case Left(msu) => msu }
      _ <- NonEmptySet.fromSet[MissingSlackUser](scala.collection.immutable.SortedSet(msus: _*)) match {
        case Some(nesMus) => notifyMissingSlackUser(nesMus)
        case None => Done.pure[IO]
      }
    } yield {
      Done
    }
  }

  private def notifyMissingSlackUser(msu: NonEmptySet[MissingSlackUser]): IO[Done] = {
    IO(println(show"Missing mappings: ${msu.map(_.user.username).intercalate(", ")}")) *> IO.pure(Done)
  }

  private def notifyJiraUser(jiraUser: JiraUser, text: User => String): IO[Either[MissingSlackUser, String]] = {
    users.findByJira(jiraUser) match {
      case None => MissingSlackUser(jiraUser).asLeft[String].pure[IO]
      case Some(user) =>
        IO.fromFuture(IO(
          api.postChatMessage(
            channelId = user.slack.userId,
            text = text(user),
            username = Some("Irritant"))
        )).map(_.asRight[MissingSlackUser])
    }

  }

}

object Slack {
  type Error = String

  case class SlackUser(userId: String) extends AnyVal

  case class SlackTeam(name: String) extends AnyVal

  case class MissingSlackUser(user: JiraUser)

  private implicit val missingSlackUserOrdering: Ordering[MissingSlackUser] = Ordering.by(_.user)

  private def missingTestInstructions[R[_]: NonEmptyTraverse](user: User, is: R[Issue]): String = {
    def issueTitle(i: Issue): String = i.description.getOrElse(i.key.show)
    val issueList: String = is
      .map(i => show" - ${issueTitle(i)} : ${i.userLink.toString}")
      .intercalate("\n")

    show"""
          |Hi ${user.prettyName},
          |
          |the following issues are missing test instructions:
          |$issueList
          |
          |Thanks :)
          |
        """.stripMargin
  }

  private def readyForTestingMsg[R[_]: NonEmptyTraverse](tester: User, tickets: R[Issue]): String = {
    val issueList = tickets.map(i => show" - ${i.key}").intercalate("\n")

    show"""
          |Hi ${tester.prettyName},
          |
          |the following issues are ready for testing:
          |$issueList
          |
          |Thanks :)
          |
        """.stripMargin
  }

  private def missingTesterMsg[R[_]: NonEmptyTraverse](assignee: User, tickets: R[Issue]): String = {
    val issueList = tickets.map(i => show" - ${i.key}").intercalate("\n")

    show"""
          |Hi ${assignee.prettyName},
          |
          |the following issues are ready for testing but don't have a tester:
          |$issueList
          |
          |Thanks :)
          |
        """.stripMargin
  }

  private def missingAssingeeAndTesterMsg[R[_]: NonEmptyTraverse](reporter: User, tickets: R[Issue]): String = {
    val issueList = tickets.map(i => show" - ${i.key}").intercalate("\n")

    show"""
          |Hi ${reporter.prettyName},
          |
          |the following issues are ready for testing but don't have an assignee or tester:
          |$issueList
          |
          |Thanks :)
          |
        """.stripMargin
  }

}
