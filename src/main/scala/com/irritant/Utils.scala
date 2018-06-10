package com.irritant

import cats.effect.{Effect, IO}
import cats.implicits._

import scala.io.StdIn

object Utils {

  def putStrLn[F[_]: Effect](str: String): F[Unit] =
    implicitly[Effect[F]].delay(println(str))

  def getLine[F[_]](implicit F: Effect[F], threadPools: ThreadPools): F[String] =
    for {
      _ <- F.liftIO(IO.shift(threadPools.blocking))
      res <- F.delay(StdIn.readLine())
      _ <- F.liftIO(IO.shift(threadPools.computation))
    } yield res

}
