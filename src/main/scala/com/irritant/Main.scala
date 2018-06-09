package com.irritant

import java.io.File

import cats.effect._
import cats.implicits._
import com.irritant.Commands.{Command, Ctx}
import com.irritant.RunMode.Safe
import com.irritant.systems.git.Git
import com.irritant.systems.jira.Jira
import com.irritant.systems.slack.Slack
import com.irritant.Utils.putStrLn
import scopt.Read

object Main extends IOApp {

  /**
   * missing functionality:
   *  - santity check when searching version in git commit log: how old is comit --> how to create commits in test?
   *  - notify missing slack user in slack
   *  - find issues that are not in testing
   *  - pagination for jira issues
   *  - dynamically determine sprint --> use 'sprint IN openSprints()' jira has no good api for this
   */
  def run(args: List[String]): IO[ExitCode] = {
    argParser.parse(args, Arguments()) match {
      case None =>
        ExitCode.Error.pure[IO]
      case Some(arguments) =>
        pureconfig.loadConfig[Config] match {
          case Left(errors) =>
            System.err.println(show"Failed to read config: ${errors.toList.mkString(", ")}")
            sys.exit(1)
          case Right(config) =>
            runEffect[IO](config, arguments)
        }
    }
  }

  private def runEffect[F[_]: Effect](config: Config, arguments: Arguments): F[ExitCode] = {
    val systems: Resource[F, (Jira[F], Git[F])] = for {
      jira <- Jira.mkJira(config.jira)
      git <- Git.mkGit(GitConfig(arguments.gitPath))
    } yield (jira, git)

    systems.use { case (jira, git) =>
      val users = Users(config.users)
      val slack = new Slack[F](config.slack, users, arguments.runMode)
      val ctx = Ctx[F](users, git, slack, jira)
      runModeInfo[F](arguments.runMode) *> arguments.command.get.runCommand(ctx)
    }
  }

  private def runModeInfo[F[_]: Effect](runMode: RunMode): F[Unit] =
    runMode match {
      case RunMode.Dry => putStrLn("Running in Dry Mode, won't send notifications")
      case RunMode.Safe => putStrLn("Running in Safe Mode, will ask before sending notifications")
      case RunMode.Yolo => putStrLn("Running in Yolo Mode, will send notifications without asking")
    }

  private implicit val runModeRead: Read[RunMode] =
    Read.reads {
      case "dry" => RunMode.Dry
      case "safe" => RunMode.Safe
      case "yolo" => RunMode.Yolo
      case unknown => throw new IllegalArgumentException(show"$unknown is not a RunMode")
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

    opt[RunMode]('m', "run-mode").valueName("<run-mode>")
      .action( (x, c) => c.copy(runMode = x) )
      .text("run-mode can be one of: 'safe' (ask before notifying, default), 'dry' (don't trigger notifications), 'yolo' (don't ask before triggering)")

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
    runMode: RunMode = Safe
  )


}

