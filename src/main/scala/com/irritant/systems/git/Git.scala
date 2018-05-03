package com.irritant.systems.git

import com.irritant.GitConfig
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.lib.ObjectId

import scala.collection.JavaConverters._

/**
 * JGit Cookbook: https://github.com/centic9/jgit-cookbook
 */
class Git(cfg: GitConfig) {

  import Git._

  private val git = JGit.open(cfg.repo)

  /**
   * Looks at masters commit log and tries to find the two
   * most recently released version, which may be later used
   * to create a diff from.
   */
  def guessRange(): Option[(VersionWithId, VersionWithId)] = {
    val masterRef = git.getRepository.exactRef(MasterRef)
    val versions = git
      .log().add(masterRef.getObjectId).setMaxCount(100).call().asScala
      .flatMap(c => Version.fromCommitMessage(c.getShortMessage).map(v => (v, c.getId.toObjectId)))
      .take(2).toSeq

    versions match {
      case latestTag +: prevTag +: _ => Some(latestTag, prevTag)
      case _ => None
    }
  }

  def showDiff(start: VersionWithId, end: VersionWithId): Seq[Commit] =
    git.log().addRange(end._2, start._2).call().asScala.drop(1).map(c => Commit(c.getShortMessage)).toSeq

  def close(): Unit = git.close()

}

object Git {

  private val MasterRef = "refs/heads/master"

  type VersionWithId = (Version, ObjectId)

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

  case class Commit(msg: String) extends AnyVal

  def withGit[A](cfg: GitConfig)(act: Git => A): A = {
    val git = new Git(cfg)
    try act(git) finally git.close()
  }


}
