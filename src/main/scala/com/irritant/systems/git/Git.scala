package com.irritant.systems.git

import cats.effect.{IO, Resource}
import cats.kernel.Eq
import cats.implicits._
import com.irritant.GitConfig
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.revwalk.RevCommit

import scala.collection.JavaConverters._

/**
 * JGit Cookbook: https://github.com/centic9/jgit-cookbook
 */
class Git(git: JGit) {

  import Git._


  /**
   * Looks at masters commit log and tries to find the two
   * most recently released version, which may be later used
   * to create a diff from.
   */
  def guessRange(): IO[Option[(VersionWithId, VersionWithId)]] = {
    for {
      masterRef <- IO(git.getRepository.exactRef(MasterRef))
      commits <- IO(git.log().add(masterRef.getObjectId).setMaxCount(100).call().asScala)
    } yield extractVersions(commits)
  }

  def showDiff(start: VersionWithId, end: VersionWithId): IO[Seq[Commit]] = {
    val callCommand = git.log().addRange(end._2, start._2)
    IO(callCommand.call())
      .map(_.asScala.drop(1).map(c => Commit(c.getShortMessage)).toSeq)
  }

  private def close(): IO[Unit] =
    IO(git.close())

}

object Git {

  private val MasterRef = "refs/heads/master"

  type VersionWithId = (Version, RevCommit)

  implicit val revCommitEq: Eq[RevCommit] =
    Eq.instance((c1, c2) => c1.equals(c2))

  case class Version private(major: Int, minor: Int, patch: Int)

  object Version {

    def fromCommitMessage(msg: String): Option[Version] = {
      val version = raw".*version (\d{3})\.(\d{1,2})\.(\d{1,2})$$".r
      msg match {
        case version(major, minor, patch) => Some(Version(major.toInt, minor.toInt, patch.toInt))
        case _ => None
      }
    }

  }

  private[git] def extractVersions(commits: Iterable[RevCommit]): Option[(VersionWithId, VersionWithId)] = {

    def toVersionWithId(c: RevCommit): Option[VersionWithId] =
      Version.fromCommitMessage(c.getShortMessage).map(v => (v, c))

    for {
      newVersion <- commits
        .take(3) // since it was just deployed, the commit should be very new
        .flatMap(toVersionWithId).headOption
      previousVersion <- commits
        .dropWhile(_ =!= newVersion._2).drop(1) // drop all up to (inclusive) new version
        .flatMap(toVersionWithId).headOption
    } yield (newVersion, previousVersion)
  }


  case class Commit(msg: String) extends AnyVal

  def mkGit(cfg: GitConfig): Resource[IO, Git] =
    Resource.make(IO(new Git(JGit.open(cfg.repo))))(_.close())

}
