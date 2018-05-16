package com.irritant

import java.io.File

import cats.implicits._
import com.irritant.Commands.{Command, Ctx}
import com.irritant.systems.git.Git
import com.irritant.systems.jira.Jira
import com.irritant.systems.slack.Slack

object Main {

  /**
   * missing functionality:
   *  - santity check when searching version in git commit log: how old is comit
   *  - notify missing slack user in slack
   *  - find issues that are not in testing
   *  - pagination for jira issues
   *  - dynamically determine sprint --> use 'sprint IN openSprints()' jira has no good api for this
   */
  def main(args: Array[String]): Unit = {
    argParser.parse(args, Arguments()) match {
      case None =>
        sys.exit(1)
      case Some(arguments) =>
        pureconfig.loadConfig[Config] match {
          case Left(errors) =>
            System.err.println(show"Failed to read config: ${errors.toList.mkString(", ")}")
            sys.exit(1)
          case Right(config) =>
            Git.withGit(GitConfig(arguments.gitPath)) { git =>
              Jira.withJira(config.jira) { jira =>
                val users = Users(config.users)
                val slack = new Slack(config.slack, users, arguments.dryRun)
                val ctx = Ctx(users, git, slack, jira)
                arguments.command.get.runCommand(ctx)
              }
            }.unsafeRunSync()
        }
    }
  }

  private val argParser = new scopt.OptionParser[Arguments]("irritant") {
    head("irritant", "0.1")

    help("help")

    opt[File]('g', "git-path").required().valueName("<git-path>")
      .action( (x, c) => c.copy(gitPath = x) )
      .text("git-path should point to a git repository")
      .validate(d =>
        if (!new File(d, ".git").exists()) Left(show"${d.getAbsolutePath} must be a git directory")
        else Right(()))

    opt[Unit]("dry-run")
      .action( (x, c) => c.copy(dryRun =  true))
      .text("only write to stdout, don't send slack notifications")

    Commands.allCommands.foreach { command =>
      cmd(command.cmd)
        .action((_, c) => c.copy(command = Some(command)))
        .text("\t" + command.infoText)
    }

    checkConfig(args =>
      Either.cond(args.command.nonEmpty, (), "Command missing."))
  }

  case class Arguments(
    gitPath: File = new File("."),
    command: Option[Command] = None,
    dryRun: Boolean = false
  )


}

