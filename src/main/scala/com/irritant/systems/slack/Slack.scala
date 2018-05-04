package com.irritant.systems.slack

import akka.Done
import akka.actor.ActorSystem
import cats.data.NonEmptyList
import com.atlassian.jira.rest.client.api.domain.Issue
import com.irritant.{SlackCfg, User, Users}
import com.irritant.systems.jira.Jira.Implicits._
import slack.api.SlackApiClient
import cats.implicits._
import com.irritant.systems.jira.Jira.{JiraUser, Ticket}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class Slack(config: SlackCfg, users: Users)(implicit actorSystem: ActorSystem) {

  import Slack._

  private implicit def ec: ExecutionContext = actorSystem.dispatcher

  private val api = SlackApiClient(config.token)

  def ping: Future[Either[Error, SlackTeam]] =
    api.testAuth()
      .map(_.team.asRight[Error].map(SlackTeam.apply))
      .recover { case NonFatal(ex) => show"Failed to connect to slack: ${ex.getMessage}}".asLeft[SlackTeam] }

  def nag(issues: Iterable[Issue]): Future[Done] = {

    val allIssues = issues.toList.groupByNel(i => Option(i.getAssignee).map(_.getName).map(JiraUser.apply))

    val unassingedIssues: Iterable[Issue] = allIssues.collect { case (None, is) => is.toList }.flatten
    val issuesByUser = allIssues.collect { case (Some(u), is) => (users.findByJira(u).toRight(u), is) }
    val unmappedUsers = issuesByUser.collect { case (Left(jUser), is) => (jUser, is) }
    val mappedUsers = issuesByUser.collect { case (Right(user), is) => (user, is) }

    unassingedIssues.foreach { i =>
      println(show"Issue ${i.getKey} is in testing, but not assigned")
    }

    unmappedUsers.foreach { case (user, is) =>
      println(show"User ${user.username} is not mapped to slack and issues: ${is.toList.mkString(",")}")
    }

    Future
      .sequence(mappedUsers.map {
        case (user, is) =>
          api
            .postChatMessage(
              channelId = user.slack.userId,
              text = missingTestInstructions(user, is),
              username = Some("Jira Issue Nagger"))
            .map { s =>
              println(show"Notified ${user.prettyName} about ${is.size} issues w/o testing instructions")
              s
            }
      })
      .map(_ => Done)
  }

  def readyForTesting(forTesting: NonEmptyList[(User, NonEmptyList[Ticket])]): Future[Done] =
    forTesting
      .map { case (user, tickets) =>
        api
          .postChatMessage(
            channelId = user.slack.userId,
            text = readyForTestingMsg(user, tickets),
            username = Some("Deployment Nagger"))
          .map { s =>
            println(show"Notified ${user.prettyName} about ${tickets.size} issues ready for testing")
            s
          }
      }
      .sequence[Future, String]
      .map(_ => Done)

}

object Slack {
  type Error = String

  private def missingTestInstructions(user: User, is: NonEmptyList[Issue]): String = {
    def issueTitle(i: Issue): String = Option(i.getDescription).getOrElse(i.getKey)
    val issueList: String = is
      .map { i => show" - ${issueTitle(i)} : ${i.getSelf.toString}" }
      .toList.mkString("\n")

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

  private def readyForTestingMsg(user: User, tickets: NonEmptyList[Ticket]): String = {
    val issueList = tickets.map(i => show" - ${i.key}").toList.mkString("\n")

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
