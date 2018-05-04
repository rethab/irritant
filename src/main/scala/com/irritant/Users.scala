package com.irritant

import com.irritant.systems.jira.Jira.JiraUser
import com.irritant.systems.jira.Jira.Implicits._
import cats.implicits._

case class Users(users: Seq[User]) {

  def findByJira(jira: JiraUser): Option[User] =
    users.find(_.jira === jira)

}
