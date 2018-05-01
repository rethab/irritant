package com.irritant.systems.jira

object Jql {

  sealed trait Expr
  case class And(lhs: Expr, rhs: Expr) extends Expr
  case class Or(lhs: Expr, rhs: Expr) extends Expr
  case class Creator(name: String) extends Expr
  case class Sprint(number: Int) extends Expr
  case class Status(name: String) extends Expr
  case object OpenSprints extends Expr

  object Expr {
    def compile(expr: Expr): String = expr match {
      case And(lhs, rhs) => s"(${Expr.compile(lhs)} AND ${Expr.compile(rhs)})"
      case Or(lhs, rhs) => s"(${Expr.compile(lhs)} OR ${Expr.compile(rhs)})"
      case Creator(name) => s"creator = '$name'"
      case Sprint(number) => s"Sprint = '$number'"
      case Status(name) => s"status = '$name'"
      case OpenSprints => s"sprint IN openSprints()"
    }
  }
}
