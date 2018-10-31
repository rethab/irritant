package com.irritant.systems.slack

import cats.NonEmptyTraverse
import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.Effect
import com.flyberrycapital.slack.SlackClient
import com.irritant._
import com.irritant.Utils.{getLine, putStrLn}
import com.irritant.systems.jira.Jira.Implicits._
import cats.implicits._
import com.irritant.RunMode.{Dry, Safe, Yolo}
import com.irritant.systems.jira.Jira.{Issue, JiraUser}

import scala.concurrent.duration._

class Slack[F[_]](config: SlackCfg, users: Users, runMode: RunMode, threadPools: ThreadPools)(implicit F: Effect[F]) {

  import Slack._

  private val api = new SlackClient(config.token)
  api.connTimeout(10.seconds.toMillis.toInt)

  def testIssuesWithoutInstructions(issues: Iterable[Issue]): F[Unit] = {

    val allIssues = issues.toList.groupByNel(_.assignee)

    val unassingedIssues: Iterable[Issue] = allIssues.collect { case (None, is) => is.toList }.flatten
    val issuesByUser = allIssues.collect { case (Some(u), is) => (users.findByJira(u).toRight(u), is) }
    val unmappedUsers = issuesByUser.collect { case (Left(jUser), is) => (jUser, is) }
    val mappedUsers: Map[User, NonEmptyList[Issue]] = issuesByUser.collect { case (Right(user), is) => (user, is) }

    unassingedIssues.foreach { i =>
      putStrLn(show"Issue ${i.key} is in testing, but not assigned")
    }

    unmappedUsers.foreach { case (user, is) =>
      putStrLn(show"User ${user.username} is not mapped to slack and issues: ${is.toList.mkString(",")}")
    }

    mappedUsers
      .toList
      .map { case (user, is) =>
        sendSlackMsg(
          user = user,
          slackMessage = missingTestInstructions(user, is),
          messageInfo = show"${is.size} issues w/o testing instructions")
      }
      .sequence[F, Unit]
      .map(_ => ())
  }

  def unresolvedIssues(issues: Iterable[Issue]): F[Unit] = {
    println(issues.size)

    val allIssues = issues.toList.groupByNel(_.assignee)

    val unassingedIssues: Iterable[Issue] = allIssues.collect { case (None, is) => is.toList }.flatten
    val issuesByUser = allIssues.collect { case (Some(u), is) => (users.findByJira(u).toRight(u), is) }
    val unmappedUsers = issuesByUser.collect { case (Left(jUser), is) => (jUser, is) }
    val mappedUsers: Map[User, NonEmptyList[Issue]] = issuesByUser.collect { case (Right(user), is) => (user, is) }

    unassingedIssues.foreach { i =>
      putStrLn(show"Issue ${i.key} is in testing, but not assigned")
    }

    unmappedUsers.foreach { case (user, is) =>
      putStrLn(show"User ${user.username} is not mapped to slack and issues: ${is.toList.mkString(",")}")
    }

    mappedUsers
      .toList
      .map { case (user, is) =>
        sendSlackMsg(
          user = user,
          slackMessage = unresolvedIssuesMsg(user, is),
          messageInfo = show"${is.size} unresolved issues")
      }
      .sequence[F, Unit]
      .map(_ => ())
  }

  def readyForTesting(forTesting: NonEmptyList[Issue]): F[Unit] = {

    /* notify tester if tester is set
     * notify assignee if assignee is set
     * notify reporter */

    val (woTester, wTester) = forTesting.toList.partitionEither(i => i.tester.map(t => (i, t)).toRight(i))
    val (unassigned, assigned) = woTester.partitionEither(i => i.assignee.map(a => (i, a)).toRight(i))

    val aEff = wTester.groupByNel(_._2).map(i => (i._1, i._2.map(_._1))).toList.traverse[F, Either[MissingSlackUser, Unit]] {
      case (user, issues) => notifyJiraUser(user, u => readyForTestingMsg(u, issues), messageInfo = show"${issues.size} tickets ready for testing") }

    val bEff = assigned.groupByNel(_._2).map(i => (i._1, i._2.map(_._1))).toList.traverse[F, Either[MissingSlackUser, Unit]] {
      case (user, issues) => notifyJiraUser(user, u => missingTesterMsg(u, issues), messageInfo = show"${issues.size} ticket missing tester") }

    val cEff = unassigned.groupByNel(_.reporter.get).toList.traverse[F, Either[MissingSlackUser, Unit]] {
      case (user, issues) => notifyJiraUser(user, u => missingAssingeeAndTesterMsg(u, issues), messageInfo = show"${issues.size} tickets missing assingnee and tester") }

    for {
      a <- aEff
      b <- bEff
      c <- cEff
      msus: Seq[MissingSlackUser] = a.collect { case Left(msu) => msu } ++ b.collect { case Left(msu) => msu } ++ c.collect { case Left(msu) => msu }
      _ <- NonEmptySet.fromSet[MissingSlackUser](scala.collection.immutable.SortedSet(msus: _*)) match {
        case Some(nesMus) => notifyMissingSlackUser(nesMus)
        case None => F.unit
      }
    } yield ()
  }

  private def notifyMissingSlackUser(msu: NonEmptySet[MissingSlackUser]): F[Unit] =
    putStrLn(show"Missing mappings: ${msu.map(_.user.username).intercalate(", ")}")

  private def notifyJiraUser(jiraUser: JiraUser, text: User => String, messageInfo: String): F[Either[MissingSlackUser, Unit]] =
    users.findByJira(jiraUser) match {
      case None => MissingSlackUser(jiraUser).asLeft[Unit].pure[F]
      case Some(user) =>
        sendSlackMsg(
          user = user,
          slackMessage = text(user),
          messageInfo = messageInfo)
        .map(_.asRight[MissingSlackUser])
    }

  private def sendSlackMsg(user: User, slackMessage: String, messageInfo: String): F[Unit] = {

    def doSend(): F[Unit] =
      for {
        _ <- threadPools.runBlocking(api.chat.postMessage(user.slack.userId, slackMessage, Map("as_user" -> "false", "username" -> config.postAsUser)))
        _ <- putStrLn(show"Sent message to ${user.prettyName} about $messageInfo")
      } yield ()

    runMode match {
      case Dry =>
        putStrLn(show"Dry: Slack message to user ${user.slack.userId} (${user.prettyName}: $slackMessage")
      case Safe =>
        implicit val pools: ThreadPools = threadPools
        (putStrLn(show"Send Slack Notification to ${user.prettyName} about $messageInfo? [y/n]") *> getLine).flatMap { answer =>
          if (answer.trim == "y" || answer.trim == "Y") doSend()
          else putStrLn(show"Not sending message to ${user.prettyName} about $messageInfo")
        }
      case Yolo =>
        doSend()
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
    def issueTitle(i: Issue): String = i.summary.getOrElse(i.key.show)
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

  private def unresolvedIssuesMsg[R[_]: NonEmptyTraverse](user: User, is: R[Issue]): String = {
    def issueTitle(i: Issue): String = i.summary.getOrElse(i.key.show)
    val issueList: String = is
      .map(i => show" - ${issueTitle(i)} : ${i.userLink.toString}")
      .intercalate("\n")

    show"""
          |Hi ${user.prettyName},
          |
          |it's getting late in the sprint and the following issues are still unresolved. Please consider moving them:
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
