package com.irritant.systems.git

import com.irritant.systems.git.Git.Version
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.scalatest._

class GitTest extends WordSpec with OptionValues with Inside with MustMatchers {

  "Git" should {
    "Version" should {
      "parse versions from commit message" in {
        Version.fromCommitMessage("release: version 120.10.0").value mustBe Version(120, 10, 0)
        Version.fromCommitMessage("release: version 999.1.9").value mustBe Version(999 ,1, 9)
        Version.fromCommitMessage("version 1.10") mustBe None
      }
    }

    "extractVersions" should {

      "find range if new version is in recent commit" ignore {
        val range =
          Git.extractVersions(List(
              mkCommit("release: version 120.0.2")
            , mkCommit("some other normal commit")
            , mkCommit("release: version 120.0.1")
          )).value

        inside(range) { case (v1, v2) =>
          v1._1.patch mustBe 2
          v2._1.patch mustBe 1
        }
      }

      "find range if new version is in second commit" ignore {
        val range =
          Git.extractVersions(List(
              mkCommit("random commit after version")
            , mkCommit("release: version 120.0.2")
            , mkCommit("some other normal commit")
            , mkCommit("some other commit")
            , mkCommit("some other half-normal commit")
            , mkCommit("release: version 120.0.1")
          )).value

        inside(range) { case (v1, v2) =>
          v1._1.patch mustBe 2
          v2._1.patch mustBe 1
        }
      }

      "not range if new version is in old commit" ignore {
        Git.extractVersions(List(
            mkCommit("some other normal commit")
          , mkCommit("some other normal commit")
          , mkCommit("some other normal commit")
          , mkCommit("some other normal commit")
          , mkCommit("some other normal commit")
          , mkCommit("release: version 120.0.2")
          , mkCommit("release: version 120.0.1")
        )) mustBe None
      }

    }
  }

  private def mkCommit(msg: String): RevCommit = {
    val builder = new CommitBuilder()
    builder.setMessage(msg)
    RevCommit.parse(builder.build())
  }

}
