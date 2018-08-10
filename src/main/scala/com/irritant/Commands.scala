package com.irritant

import cats.data.EitherT
import cats.effect.{Effect, ExitCode}
import cats.implicits._
import com.irritant.Commands.Command.{NotifyDeployedTickets, NotifyMissingTestInstructions, NotifyUnresolvedTickets}
import com.irritant.Utils._
import com.irritant.systems.git.Git
import com.irritant.systems.jira.Jira
import com.irritant.systems.jira.Jira.Key
import com.irritant.systems.slack.Slack

object Commands {

  case class Ctx[F[_]: Effect](
    users: Users,
    git: Git[F],
    slack: Slack[F],
    jira: Jira[F]
  )

  val allCommands: Seq[Command] = Seq(
      NotifyDeployedTickets
    , NotifyMissingTestInstructions
    , NotifyUnresolvedTickets
  )

  sealed trait Command {

    /** command to be used on the command line (should be dash-separated) */
    def cmd: String

    /** user-friendly description of what this command does */
    def infoText: String

    /** command implementation */
    def runCommand[F[_]: Effect](ctx: Ctx[F]): F[ExitCode]
  }

  object Command {

    case object NotifyDeployedTickets extends Command {

      val cmd = "notify-deployed-tickets"
      val infoText = "Notify people in slack after deployment that their tickets are ready for testing"

      override def runCommand[F[_]: Effect](ctx: Ctx[F]): F[ExitCode] = {

        val prgm: EitherT[F, String, Unit] =
          for  {
            range <- EitherT.fromOptionF(ctx.git.guessRange(), "Could not find commit range")

            issueKeys <- EitherT.fromOptionF(ctx.git.showDiff(range._1, range._2)
              .map(_.flatMap(c => Key.fromCommitMessage(c.msg)).toList.toNel), "No tickets from commits")

            _ <- EitherT.right[String](putStrLn(show"${issueKeys.size} ready for testing"))

            readyForTesting <- EitherT.right[String](ctx.jira.findTesters(issueKeys))

            _ <- EitherT.right[String](ctx.slack.readyForTesting(readyForTesting))
          } yield ()

        prgm.value.flatMap {
          case Left(msg) => putStrLn(msg) *> ExitCode.Error.pure[F]
          case Right(_) => ExitCode.Success.pure[F]
        }

      }
    }

    case object NotifyMissingTestInstructions extends Command {

      val cmd = "notify-missing-test-instructions"
      val infoText = "Notify people in slack if their tickets are missing test instructions"

      override def runCommand[F[_]: Effect](ctx: Ctx[F]): F[ExitCode] = {
        ctx.jira.inTestingAndMissingInstructions()
          .flatMap(ctx.slack.testIssuesWithoutInstructions)
          .map(_ => ExitCode.Success)
      }
    }

    case object NotifyUnresolvedTickets extends Command {

      val cmd = "notify-unresolved-tickets"
      val infoText = "Notify people in slack if their tickets unresolved in the current sprint"

      override def runCommand[F[_]: Effect](ctx: Ctx[F]): F[ExitCode] = {
        ctx.jira.unresolvedInCurrentSprint()
          .flatMap(ctx.slack.unresolvedIssues)
          .map(_ => ExitCode.Success)
      }
    }

  }

}
