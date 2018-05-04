package com.irritant.systems.slack

import akka.Done
import akka.actor.ActorSystem
import cats.NonEmptyTraverse
import cats.data.NonEmptyList
import cats.effect.IO
import com.atlassian.jira.rest.client.api.domain.Issue
import com.irritant.{SlackCfg, User, Users}
import com.irritant.systems.jira.Jira.Implicits._
import slack.api.SlackApiClient
import cats.implicits._
import com.irritant.systems.jira.Jira.{JiraUser, Ticket}

import scala.util.control.NonFatal

class Slack(config: SlackCfg, users: Users)(implicit actorSystem: ActorSystem) {

  import Slack._

  private val api = SlackApiClient(config.token)

  def ping: IO[Either[Error, SlackTeam]] =
    IO.fromFuture(IO(api.testAuth()))
      .map(_.team.asRight[Error].map(SlackTeam.apply))
      .recover { case NonFatal(ex) => show"Failed to connect to slack: ${ex.getMessage}}".asLeft[SlackTeam] }

  def nag(issues: Iterable[Issue]): IO[Done] = {

    val allIssues = issues.toList.groupByNel(i => Option(i.getAssignee).map(_.getName).map(JiraUser.apply))

    val unassingedIssues: Iterable[Issue] = allIssues.collect { case (None, is) => is.toList }.flatten
    val issuesByUser = allIssues.collect { case (Some(u), is) => (users.findByJira(u).toRight(u), is) }
    val unmappedUsers = issuesByUser.collect { case (Left(jUser), is) => (jUser, is) }
    val mappedUsers: Map[User, NonEmptyList[Issue]] = issuesByUser.collect { case (Right(user), is) => (user, is) }

    unassingedIssues.foreach { i =>
      IO(println(show"Issue ${i.getKey} is in testing, but not assigned"))
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

  def readyForTesting[T[_] : NonEmptyTraverse](forTesting: T[(User, T[Ticket])]): IO[Done] =
    forTesting
      .traverse[IO, String] { case (user, tickets) =>
        IO.fromFuture(IO(api
          .postChatMessage(
            channelId = user.slack.userId,
            text = readyForTestingMsg(user, tickets),
            username = Some("Deployment Nagger"))))
          .flatMap { s =>
            IO(println(show"Notified ${user.prettyName} about ${tickets.size} issues ready for testing"))
              .map(_ => s)
          }
      }
      .map(_ => Done)

}

object Slack {
  type Error = String

  case class SlackUser(userId: String) extends AnyVal

  case class SlackTeam(name: String) extends AnyVal

  private def missingTestInstructions[R[_]: NonEmptyTraverse](user: User, is: R[Issue]): String = {
    def issueTitle(i: Issue): String = Option(i.getDescription).getOrElse(i.getKey)
    val issueList: String = is
      .map(i => show" - ${issueTitle(i)} : ${i.getSelf.toString}")
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

  private def readyForTestingMsg[R[_]: NonEmptyTraverse](user: User, tickets: R[Ticket]): String = {
    val issueList = tickets.map(i => show" - ${i.key}").intercalate("\n")

    show"""
          |Hi ${user.prettyName},
          |
          |the following issues are ready for testing:
          |$issueList
          |
          |Thanks :)
          |
        """.stripMargin
  }
}
