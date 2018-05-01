package com.irritant.systems.jira

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.irritant.JiraCfg
import Jql._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class Jira (config: JiraCfg) {

  import Jira._

  private val restClient: JiraRestClient = {
    val factory = new AsynchronousJiraRestClientFactory()
    factory.createWithBasicHttpAuthentication(config.uri, config.username, config.password)
  }

  def close(): Unit =
    restClient.close()

  def inTestingWithoutInstructions(): Iterable[Issue] =
    restClient.getSearchClient
      .searchJql(Expr.compile(currentlyInTesting()), null, null, AllFields)
      .claim()
      .getIssues.asScala.filterNot(containsTestInstructions)

}

object Jira {

  private val AllFields = Set("*all").asJava

  def withJira[A](config: JiraCfg)(act: Jira => Future[A]): Future[A] = {
    val jira = new Jira(config)
    try act(jira) finally jira.close()
  }

  private def currentlyInTesting(): Expr =
    And(Status("In Testing"), OpenSprints)

  private def containsTestInstructions(i: Issue): Boolean =
    i.getComments.asScala.exists(_.getBody.contains("Test Instructions"))

}
