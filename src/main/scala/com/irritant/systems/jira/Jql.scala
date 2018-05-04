package com.irritant.systems.jira

import cats.data.NonEmptyList
import cats.implicits._

object Jql {

  sealed trait Expr {
    def compile: String = Expr.compile(this)
  }
  case class And(lhs: Expr, rhs: Expr) extends Expr
  case class Or(lhs: Expr, rhs: Expr) extends Expr
  case class In(lhs: Expr, rhs: Expr) extends Expr
  case class Eq(lhs: Expr, rhs: Expr) extends Expr
  case class Field(name: String) extends Expr
  case class Expression(name: String) extends Expr
  case class Value(name: String) extends Expr
  case class Values(vs: NonEmptyList[String]) extends Expr

  object Expr {
    def compile(expr: Expr): String = expr match {
      case And(lhs, rhs) => show"(${lhs.compile} AND ${rhs.compile})"
      case Or(lhs, rhs) => show"(${lhs.compile} OR ${rhs.compile})"
      case In(lhs, rhs) => show"(${lhs.compile} IN ${rhs.compile})"
      case Eq(lhs, rhs) => show"(${lhs.compile} = ${rhs.compile})"
      case Field(name) => name
      case Expression(name) => name
      case Value(name) => show"'$name'"
      case Values(names) => show"(${names.toList.mkString("'", ",", "'")})"
    }
  }

  object Predef {
    val OpenSprints: Expr = In(Field("sprint"), Expression("openSprints()"))
    def EqStatus(status: String): Expr = Eq(Field("status"), Value(status))
    def InKeys(keys: NonEmptyList[String]): Expr = In(Field("key"), Values(keys))
  }
}
