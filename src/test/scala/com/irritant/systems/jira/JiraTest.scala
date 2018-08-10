package com.irritant.systems.jira

import com.atlassian.jira.rest.client.api.domain.{Comment, Issue, IssueType}
import com.irritant.systems.jira.Jira.Key
import org.scalatest.{Inside, MustMatchers, OptionValues, WordSpec}

import scala.collection.JavaConverters._

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

    "missingTestInstructions" should {

      "not be missing if bug w/ steps" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Bug",
          description = "This is a bug!\nSteps:\n1. do this\n2. do that",
          comment = "some random comment"
        )) must be(false)
      }

      "be missing if bug w/o steps" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Bug",
          description = "This is a bug",
          comment = "some random comment"
        )) must be(true)
      }

      "be missing if feature w/ steps in description" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Feature",
          description = "This is a feature.\nSteps:\n1. do this\n2. do that",
          comment = "some random comment"
        )) must be(true)
      }

      "not be missing if feature w/ steps" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Feature",
          description = "This is a feature.\nSteps:\n1. do this\n2. do that",
          comment = "Testing:\n1. Do this"
        )) must be(false)
      }

      "not be missing if feature w/ steps in uppercase title" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Feature",
          description = "This is a feature.\nSteps:\n1. do this\n2. do that",
          comment = "TESTING:\n1. Do this"
        )) must be(false)
      }

      "not be missing if feature w/ instructions" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Feature",
          description = "This is a feature",
          comment = "Test Instructions:"
        )) must be(false)
      }

      "be missing if feature w/o steps" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Feature",
          description = "This is a feature",
          comment = "some random comment"
        )) must be(true)
      }

      "not be missing if feature w/ steps and title 'for testers'" in {
        Jira.missingInstructions(mkIssue(
          issueType = "Feature",
          description = "This is a feature",
          comment = "For Testers:\ntesters need to do x\nand y"
        )) must be(false)
      }
    }
  }

  private def mkIssue(issueType: String, description: String, comment: String): Issue =
    new Issue(
      null, null, null, null, null,
      new IssueType(null, null, issueType, false, null, null),
      null, description, null,
      null, null, null, null,
      null, null, null, null,
      null, null, null, null,
      Seq(new Comment(null, comment, null, null, null, null, null, null)).asJava,
      null, null, null, null,
      null, null, null, null,
      null, null
    )

}
