package com.irritant

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.irritant.systems.git.Git
import com.irritant.systems.jira.Jira
import com.irritant.systems.jira.Jira.Ticket
import com.irritant.systems.slack.Slack

object Commands {

  case class Ctx(
    users: Users,
    git: Git,
    slack: Slack,
    jira: Jira
  )

  sealed trait Command {
    def runCommand(ctx: Ctx): IO[Unit]
  }

  object Command {

    case object NotifyDeployedTickets extends Command {
      override def runCommand(ctx: Ctx): IO[Unit] = {

        val prgm: EitherT[IO, String, Unit] =
          for  {
            range <- EitherT.right[String](ctx.git.guessRange().map(_.get))

            tickets <- EitherT.fromOptionF(ctx.git.showDiff(range._1, range._2)
              .map(_.flatMap(c => Ticket.fromCommitMessage(c.msg)).toList.toNel), "No tickets from commits")

            ticketsWithTesters <- EitherT.right[String](ctx.jira.findTesters(ctx.users, tickets))

            issuesForTesting <- EitherT.fromOption[IO](ticketsWithTesters
              .collect { case (ticket, Some(tester)) => (ticket, tester)}
              .groupByNel(_._2).mapValues(_.map(_._1))
              .toList.toNel, "No issues for testing")

            _ <- EitherT.right[String](ctx.slack.readyForTesting(issuesForTesting))
          } yield ()

        prgm.value.flatMap {
          case Left(msg) => IO(println(msg))
          case Right(_) => IO.unit
        }

      }
    }

  }

}
