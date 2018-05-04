package com.irritant.systems.jira

import com.irritant.systems.jira.Jira.Ticket
import org.scalatest.{Inside, MustMatchers, OptionValues, WordSpec}

class JiraTest extends WordSpec with OptionValues with Inside with MustMatchers {

  "Jira" should {
    "Ticket" should {
      "parse ticket key from commit message" in {
        Ticket.fromCommitMessage("test: fixed tests for KT-456").value mustBe Ticket("KT-456")
        Ticket.fromCommitMessage("did some improvements YHR-1").value mustBe Ticket("YHR-1")
        Ticket.fromCommitMessage("test commit:VK-78 fixed").value mustBe Ticket("VK-78")
        Ticket.fromCommitMessage("MH-12432: all fixes done").value mustBe Ticket("MH-12432")
      }
    }
  }



}
