/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.util;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.monflabs.util.Console;
import org.monflabs.util.ExceptionUtil;
import org.monflabs.util.ExceptionUtil.ThrowingConsumer;
import org.monflabs.util.ExceptionUtil.ThrowingFunction;
import org.monflabs.util.ExceptionUtil.ThrowingRunnable;
import org.monflabs.util.ExceptionUtil.ThrowingSupplier;
import org.monflabs.util.ForwardRuntimeException;

import tests.ProjectTestCase;

public class ConsoleAndExceptionTest extends ProjectTestCase {

	public void testGetMessageCauseChain() throws Exception {
		Exception e = new Exception("outer", new IOException("inner"));
		assertEquals("outer\ninner", Console.getMessage(e));
		assertEquals("single", Console.getMessage(new Exception("single")));
	}

	public void testGetMessageCyclicCauses() throws Exception {
		// A -> B -> A used to loop forever
		Exception a = new Exception("a");
		Exception b = new Exception("b", a);
		a.initCause(b);
		assertEquals("a\nb", Console.getMessage(a));
	}

	public void testUncheckedWrapsCheckedExceptions() throws Exception {
		ThrowingRunnable<IOException> io = () -> { throw new IOException("io"); };
		ForwardRuntimeException e = assertThrows(ForwardRuntimeException.class,
				() -> ExceptionUtil.unchecked(io).run());
		assertTrue(e.getCause() instanceof IOException);
		ThrowingSupplier<String,IOException> sup = () -> "x";
		assertEquals("x", ExceptionUtil.unchecked(sup).get());
		ThrowingFunction<String,Integer,IOException> f = s -> s.length();
		assertEquals(Integer.valueOf(2), ExceptionUtil.unchecked(f).apply("ab"));
	}

	public void testUncheckedDoesNotWrapRuntimeExceptions() throws Exception {
		// A RuntimeException is already unchecked: it used to be wrapped too
		UncheckedIOException ex = new UncheckedIOException(new IOException());
		ThrowingRunnable<IOException> r = () -> { throw ex; };
		assertSame(ex, assertThrows(UncheckedIOException.class, () -> ExceptionUtil.unchecked(r).run()));
		IllegalStateException ise = new IllegalStateException();
		ThrowingSupplier<String,IOException> sup = () -> { throw ise; };
		assertSame(ise, assertThrows(IllegalStateException.class, () -> ExceptionUtil.unchecked(sup).get()));
		ThrowingConsumer<String,IOException> c = s -> { throw ise; };
		assertSame(ise, assertThrows(IllegalStateException.class, () -> ExceptionUtil.unchecked(c).accept("a")));
		ThrowingFunction<String,String,IOException> f = s -> { throw ise; };
		assertSame(ise, assertThrows(IllegalStateException.class, () -> ExceptionUtil.unchecked(f).apply("a")));
	}

	public void testUncheckedRestoresInterruptStatus() throws Exception {
		// Catching the InterruptedException cleared the interrupt status, which was lost
		ThrowingRunnable<InterruptedException> sleep = () -> Thread.sleep(10_000);
		Thread.currentThread().interrupt();
		try {
			ForwardRuntimeException e = assertThrows(ForwardRuntimeException.class, () -> ExceptionUtil.unchecked(sleep).run());
			assertTrue(e.getCause() instanceof InterruptedException);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		ThrowingConsumer<String,InterruptedException> c = s -> { throw new InterruptedException(); };
		try {
			assertThrows(ForwardRuntimeException.class, () -> ExceptionUtil.unchecked(c).accept("a"));
			assertTrue(Thread.interrupted());
		} finally {
			Thread.interrupted();
		}
		// Other exceptions don't touch the interrupt status
		ThrowingRunnable<IOException> io = () -> { throw new IOException("io"); };
		assertThrows(ForwardRuntimeException.class, () -> ExceptionUtil.unchecked(io).run());
		assertFalse(Thread.currentThread().isInterrupted());
	}

	public void testConsoleOutput() throws Exception {
		java.io.PrintStream out = System.out;
		java.io.PrintStream err = System.err;
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		java.io.ByteArrayOutputStream e = new java.io.ByteArrayOutputStream();
		try {
			System.setOut(new java.io.PrintStream(o, true, "UTF-8"));
			System.setErr(new java.io.PrintStream(e, true, "UTF-8"));
			Console.log("plain {0}");
			Console.log("a {0} b {1}", 1, "x");
			Console.err("err {0}", 2);
			Console.nolog("nothing {0}", 3);
			Console.noerr("nothing {0}", 3);
			Console.log((Throwable)null);
			Console.exception(new IllegalStateException("boom"), "context {0}", 4);
			assertSame(System.out, Console.outStream());
			assertSame(System.err, Console.errStream());
		} finally {
			System.setOut(out);
			System.setErr(err);
		}
		String nl = System.lineSeparator();
		assertEquals("plain {0}" + nl + "a 1 b x" + nl, o.toString("UTF-8"));
		String errText = e.toString("UTF-8");
		assertTrue(errText, errText.startsWith("err 2" + nl + "context 4" + nl + "java.lang.IllegalStateException: boom"));
	}
}
