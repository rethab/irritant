package com.irritant

import akka.actor.ActorSystem
import com.irritant.systems.jira.Jira
import com.irritant.systems.slack.Slack

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


object Main {

  /**
   * missing functionality:
   *  - dynamically determine sprint --> use 'sprint IN openSprints()' jira has no good api for this
   *  - find issues that are not in testing
   *  - pagination for jira issues
   *  - post tickets to slack that were deployed
   */

  def main(args: Array[String]): Unit = {
    runWithConfig { case (config, as) =>

      implicit val actorSystem: ActorSystem = as
      implicit val ec: ExecutionContext = actorSystem.dispatcher

      val users = Users(config.users)
      val slack = new Slack(config.slack, users)(actorSystem)
      Jira.withJira(config.jira) { jira =>
        val issues = jira.inTestingWithoutInstructions()
        slack.nag(issues)
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

