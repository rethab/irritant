package com.irritant.systems.jira

import com.irritant.systems.jira.Jira.Key
import org.scalatest.{Inside, MustMatchers, OptionValues, WordSpec}

class JiraTest extends WordSpec with OptionValues with Inside with MustMatchers {

  "Jira" should {
    "Key" should {
      "parse ticket key from commit message" in {
        Key.fromCommitMessage("test: fixed tests for KT-456").value mustBe Key("KT-456")
        Key.fromCommitMessage("did some improvements YHR-1").value mustBe Key("YHR-1")
        Key.fromCommitMessage("test commit:VK-78 fixed").value mustBe Key("VK-78")
        Key.fromCommitMessage("MH-12432: all fixes done").value mustBe Key("MH-12432")
      }
    }
  }



}
