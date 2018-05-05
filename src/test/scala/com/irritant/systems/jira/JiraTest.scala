package com.irritant.systems.jira

import com.irritant.systems.jira.Jira.Issue
import org.scalatest.{Inside, MustMatchers, OptionValues, WordSpec}

class JiraTest extends WordSpec with OptionValues with Inside with MustMatchers {

  "Jira" should {
    "Ticket" should {
      "parse ticket key from commit message" in {
        Issue.fromCommitMessage("test: fixed tests for KT-456").value mustBe Issue("KT-456")
        Issue.fromCommitMessage("did some improvements YHR-1").value mustBe Issue("YHR-1")
        Issue.fromCommitMessage("test commit:VK-78 fixed").value mustBe Issue("VK-78")
        Issue.fromCommitMessage("MH-12432: all fixes done").value mustBe Issue("MH-12432")
      }
    }
  }



}
