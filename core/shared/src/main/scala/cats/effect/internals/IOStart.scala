/*
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect.internals

import cats.effect.{Fiber, IO, Timer}
import cats.effect.internals.Callback.Extensions
import scala.concurrent.Promise

private[effect] object IOStart {
  /**
   * Implementation for `IO.start`.
   */
  def apply[A](timer: Timer[IO], fa: IO[A]): IO[Fiber[IO, A]] = {
    val start: Start[Fiber[IO, A]] = (_, cb) => {
      // Memoization
      val p = Promise[Either[Throwable, A]]()

      // Starting the source `IO`, with a new connection, because its
      // cancellation is now decoupled from our current one
      val conn2 = IOConnection()
      IORunLoop.startCancelable(IOForkedStart(fa, timer), conn2, p.success)

      // Building a memoized IO - note we cannot use `IO.fromFuture`
      // because we need to link this `IO`'s cancellation with that
      // of the executing task; then signal the fiber
      cb(Right(fiber(p, conn2)))
    }
    IO.Async(start, trampolineAfter = true)
  }

  private[internals] def fiber[A](p: Promise[Either[Throwable, A]], conn: IOConnection): Fiber[IO, A] = {
    val join = IO.Async[A] { (ctx, cb) =>
      implicit val ec = TrampolineEC.immediate

      // Short-circuit for already completed `Future`
      p.future.value match {
        case Some(value) =>
          cb.async(value.get)
        case None =>
          // Cancellation needs to be linked to the active task
          ctx.push(conn.cancel)
          p.future.onComplete { r =>
            ctx.pop()
            cb(r.get)
          }
      }
    }
    Fiber(join, conn.cancel)
  }
}
