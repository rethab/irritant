package com.irritant.systems.jira

import cats.NonEmptyTraverse
import cats.implicits._

object Jql {

  sealed trait Expr {
    def compile: String
  }
  case class And(lhs: Expr, rhs: Expr) extends Expr {
    def compile: String = show"(${lhs.compile} AND ${rhs.compile})"
  }
  case class Or(lhs: Expr, rhs: Expr) extends Expr {
    override def compile: String = show"(${lhs.compile} OR ${rhs.compile})"
  }
  case class In(lhs: Expr, rhs: Expr) extends Expr {
    override def compile: String = show"(${lhs.compile} IN ${rhs.compile})"

  }
  case class Eq(lhs: Expr, rhs: Expr) extends Expr {
    override def compile: String = show"(${lhs.compile} = ${rhs.compile})"
  }
  case class Field(name: String) extends Expr {
    override def compile: String = name

  }
  case class Expression(name: String) extends Expr {
    override def compile: String = name

  }
  case class Value(name: String) extends Expr {
    override def compile: String = show"'$name'"

  }
  case class Values[R[_] : NonEmptyTraverse](vs: R[Value]) extends Expr {
    override def compile: String = show"(${vs.map(_.name).intercalate(",")})"
  }


  object Predef {
    val OpenSprints: Expr = In(Field("sprint"), Expression("openSprints()"))
    def EqStatus(status: String): Expr = Eq(Field("status"), Value(status))
    def InKeys[R[_] : NonEmptyTraverse](keys: R[String]): Expr = In(Field("key"), Values(keys.map(Value.apply)))
  }
}
