package com.irritant.systems.git

import com.irritant.systems.git.Git.Version
import org.scalatest.{Inside, MustMatchers, OptionValues, WordSpec}

class GitTest extends WordSpec with OptionValues with Inside with MustMatchers {

  "Git" should {
    "Version" should {
      "parse versions from commit message" in {
        Version.fromCommitMessage("release: version 120.10.0").value mustBe Version(120, 10, 0)
        Version.fromCommitMessage("release: version 999.1.9").value mustBe Version(999 ,1, 9)
        Version.fromCommitMessage("version 1.10") mustBe None
      }
    }
  }



}
