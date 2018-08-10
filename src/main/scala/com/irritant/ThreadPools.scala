package com.irritant

import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.{Effect, IO, Resource}
import cats.implicits._

import scala.concurrent.ExecutionContext

/**
 * Available types of thread pools according to Daniel Spiewak's
 * suggestion in 'The Making of an IO' (scala.io, 2017)
 *
 * @param computation for computation. work-stealing, non-daemon
 * @param blocking for blocking IO. unbounded and caching
 * @param dispatcher for event dispatching. very small (1-4 threads), high priority, daemon
 */
case class ThreadPools(
  computation: ExecutionContext,
  blocking: ExecutionContext,
  dispatcher: ExecutionContext
) {

  def runBlocking[F[_], A](thunk: => A)(implicit F: Effect[F]): F[A] =
    runBlockingF(F.delay(thunk))

  def runBlockingF[F[_], A](act: F[A])(implicit F: Effect[F]): F[A] =
    F.bracket(
      acquire = F.liftIO(IO.shift(blocking)))(
      use =     _ => act)(
      release = _ => F.liftIO(IO.shift(computation)))

  def runDispatching[F[_], A](k: (Either[Throwable, A] => Unit) => Unit)(implicit F: Effect[F]): F[A] =
    F.bracket(
      acquire = F.liftIO(IO.shift(dispatcher)))(
      use =     _ => F.async(k))(
      release = _ => F.liftIO(IO.shift(computation)))

}

object ThreadPools {

  def default[F[_]](implicit F: Effect[F]): Resource[F, ThreadPools] = {

    def poolShutdown(pool: ExecutorService): F[Unit] =
      F.delay(pool.shutdown())

    def mkDispatcher: F[ExecutorService] =
      F.delay(Executors.newFixedThreadPool(1, (r: Runnable) => {
        val t = new Thread(r)
        t.setDaemon(true)
        t.setPriority(Thread.MAX_PRIORITY)
        t
      }))

    for {
      computation <- Resource.make(F.delay(Executors.newWorkStealingPool()))(poolShutdown)
      blocking <- Resource.make(F.delay(Executors.newCachedThreadPool()))(poolShutdown)
      dispatcher <- Resource.make(mkDispatcher)(poolShutdown)
    } yield ThreadPools(
      computation = ExecutionContext.fromExecutorService(computation),
      blocking = ExecutionContext.fromExecutorService(blocking),
      dispatcher = ExecutionContext.fromExecutorService(dispatcher)
    )

  }
}
