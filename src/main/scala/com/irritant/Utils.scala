package com.irritant

import cats.effect.IO

import scala.io.StdIn

object Utils {

  def putStrLn(str: String): IO[Unit] =
    IO(println(str))

  def getLine: IO[String] =
    IO(StdIn.readLine())

}
