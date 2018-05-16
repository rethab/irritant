package com.irritant

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.irritant.Commands.Command.{NotifyDeployedTickets, NotifyMissingTestInstructions}
import com.irritant.systems.git.Git
import com.irritant.systems.jira.Jira
import com.irritant.systems.jira.Jira.Key
import com.irritant.systems.slack.Slack

object Commands {

  case class Ctx(
    users: Users,
    git: Git,
    slack: Slack,
    jira: Jira
  )

  val allCommands: Seq[Command] = Seq(
      NotifyDeployedTickets
    , NotifyMissingTestInstructions
  )

  sealed trait Command {

    /** command to be used on the command line (should be dash-separated) */
    def cmd: String

    /** user-friendly description of what this command does */
    def infoText: String

    /** command implementation */
    def runCommand(ctx: Ctx): IO[Unit]
  }

  object Command {

    case object NotifyDeployedTickets extends Command {

      val cmd = "notify-deployed-tickets"
      val infoText = "Notify people in slack after deployment that their tickets are ready for testing"

      override def runCommand(ctx: Ctx): IO[Unit] = {

        val prgm: EitherT[IO, String, Unit] =
          for  {
            range <- EitherT.right[String](ctx.git.guessRange().map(_.get))

            issueKeys <- EitherT.fromOptionF(ctx.git.showDiff(range._1, range._2)
              .map(_.flatMap(c => Key.fromCommitMessage(c.msg)).toList.toNel), "No tickets from commits")

            _ <- EitherT.right[String](IO(println(show"${issueKeys.size} ready for testing")))

            readyForTesting <- EitherT.right[String](ctx.jira.findTesters(issueKeys))

            _ <- EitherT.right[String](ctx.slack.readyForTesting(readyForTesting))
          } yield ()

        prgm.value.flatMap {
          case Left(msg) => IO(println(msg))
          case Right(_) => IO.unit
        }

      }
    }

    case object NotifyMissingTestInstructions extends Command {

      val cmd = "notify-missing-test-instructions"
      val infoText = "Notify people in slack if their tickets are missing test instructions"

      override def runCommand(ctx: Ctx): IO[Unit] = {
        ctx.jira.inTestingWithoutInstructions().flatMap(ctx.slack.testIssuesWithoutInstructions).map(_ => ())
      }
    }

  }

}
