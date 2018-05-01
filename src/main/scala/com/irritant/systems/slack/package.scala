package com.irritant.systems

package object slack {

  case class SlackUser(userId: String) extends AnyVal

  case class SlackTeam(name: String) extends AnyVal

}
