/*
 * Copyright (c) 2017-2019 The Typelevel Cats-effect Project Developers
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

package cats.effect

import cats.Eq
import cats.effect.concurrent.Ref
import cats.implicits._
import org.scalatest.compatible.Assertion
import org.scalatest.AsyncFunSuite
import org.scalatest.{Matchers, Succeeded}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ConcurrentTests extends AsyncFunSuite with Matchers {

  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private val smallDelay: IO[Unit] = timer.sleep(20.millis)

  private def awaitEqual[A: Eq](t: IO[A], success: A): IO[Unit] =
    t.flatMap(a => if (Eq[A].eqv(a, success)) IO.unit else smallDelay *> awaitEqual(t, success))

  private def run(t: IO[Unit]): Future[Assertion] = t.as(Succeeded).unsafeToFuture

  test("parSequenceN") {
    val finalValue = 100
    val r = Ref.unsafe[IO, Int](0)
    val modifies = Concurrent.parSequenceN(3)(List.fill(finalValue)(IO.shift *> r.update(_ + 1)))
    run(IO.shift *> modifies.start *> awaitEqual(r.get, finalValue))
  }

}
