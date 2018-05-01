package com.irritant

import com.irritant.systems.jira.JiraUser

import cats.implicits._

case class Users(users: Seq[User]) {

  def findByJira(jira: JiraUser): Option[User] =
    users.find(_.jira === jira)

}
