package com.irritant

import java.io.File

import akka.actor.ActorSystem
import cats.data.NonEmptyList
import cats.implicits._
import com.irritant.systems.git.Git
import com.irritant.systems.jira.Jira
import com.irritant.systems.jira.Jira.Ticket
import com.irritant.systems.slack.Slack

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


object Main {

  /**
   * missing functionality:
   *  - ticket links in slack should not link to api but real/user jira instance
   *  - find issues that are not in testing
   *  - pagination for jira issues
   *  - dynamically determine sprint --> use 'sprint IN openSprints()' jira has no good api for this
   */

  def main(args: Array[String]): Unit = {
    runWithConfig { case (config, as) =>

      implicit val actorSystem: ActorSystem = as
      implicit val ec: ExecutionContext = actorSystem.dispatcher

      val gitCfg = GitConfig(new File("/home/rethab/dev/test-project"))
      val users = Users(config.users)
      val slack = new Slack(config.slack, users)(actorSystem)
      Git.withGit(gitCfg) { git =>
        val Some((start, end)) = git.guessRange()

        val maybeTickets = git.showDiff(start, end).flatMap(c => Ticket.fromCommitMessage(c.msg)).toList.toNel
        maybeTickets match {
          case None => Future.successful(println("No tickets from commits"))
          case Some(tickets) =>
            Jira.withJira(config.jira) { jira =>
              val maybeIssuesForTesting: Option[NonEmptyList[(User, NonEmptyList[Ticket])]] = jira
                .findTesters(users, tickets)
                .collect { case (ticket, Some(tester)) => (ticket, tester)}
                .groupByNel(_._2)
                .mapValues(_.map(_._1))
                .toList.toNel

              maybeIssuesForTesting match {
                case None => Future.successful(())
                case Some(issuesForTesting) => slack.readyForTesting(issuesForTesting)
              }
            }
        }

      }

    }
  }

  def runWithConfig[A](act: (Config, ActorSystem) => Future[A]): A = {
    pureconfig.loadConfig[Config] match {
      case Left(errors) =>
        sys.error("Failed to read config: " + errors.toList.mkString(", "))
        sys.exit(1)
      case Right(config) =>

        implicit val actorSystem: ActorSystem = ActorSystem("irritant")
        try {
          Await.result(act(config, actorSystem), 30.seconds)
        } finally {
          Await.result(actorSystem.terminate(), 10.seconds)
        }
    }

  }

}

