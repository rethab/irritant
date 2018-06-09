package com.irritant

import cats.effect.Effect

import scala.io.StdIn

object Utils {

  def putStrLn[F[_]: Effect](str: String): F[Unit] =
    implicitly[Effect[F]].delay(println(str))

  def getLine[F[_]: Effect]: F[String] =
    implicitly[Effect[F]].delay(StdIn.readLine())

}
