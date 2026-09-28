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
}
