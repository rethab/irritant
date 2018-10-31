package com.irritant

import cats.effect.Effect

import scala.io.StdIn

object Utils {

  def putStrLn[F[_]: Effect](str: String): F[Unit] =
    implicitly[Effect[F]].delay(println(str)) // scalastyle:ignore

  def getLine[F[_]](implicit F: Effect[F], threadPools: ThreadPools): F[String] =
    threadPools.runBlocking(StdIn.readLine())

}
