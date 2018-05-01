package com.irritant.systems

import cats.implicits._
import cats.{Eq, Order, Show}

package object jira {

  case class JiraUser(username: String) extends AnyVal

  object JiraUser {
    implicit val eqJiraUser: Eq[JiraUser] = Eq.by(_.username)
    implicit val showJiraUser: Show[JiraUser] = Show.show(_.username)
    implicit val orderJiraUser: Order[JiraUser] = Order.by(_.username)
  }

}
