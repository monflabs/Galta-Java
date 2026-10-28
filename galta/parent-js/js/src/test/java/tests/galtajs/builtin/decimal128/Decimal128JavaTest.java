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
package tests.galtajs.builtin.decimal128;

import java.math.BigDecimal;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.number.Decimal128;

/**
 * Decimal128 in the engine, checked through the Java API: the {@code useDecimal128} flag, the JsonFactory
 * settings and the Java interop.
 */
public class Decimal128JavaTest extends __BaseTestCase {

	private static Object eval(JSEnvironment env, String script) throws Exception {
		return env.evaluateScript(script);
	}

	public void testFlagDefaultsToDecimal128() throws Exception {
		JSEnvironment env = GaltaJSEnvironment.newBuilder().build();
		assertTrue(env.useDecimal128());
		assertEquals(Decimal128.of("1.5"), eval(env, "1.5m"));
		assertEquals(Decimal128.of("0.3"), eval(env, "0.1m + 0.2m"));
	}

	public void testFlagOffKeepsBigDecimal() throws Exception {
		JSEnvironment env = GaltaJSEnvironment.newBuilder().useDecimal128(false).build();
		assertEquals(new BigDecimal("1.5"), eval(env, "1.5m"));
		// A Decimal128 is still supported in parallel
		assertEquals(Decimal128.of("1.5"), eval(env, "Decimal128('1.5')"));
		assertEquals(Decimal128.of("3"), eval(env, "Decimal128('1.5') + Decimal128('1.5')"));
	}

	public void testJsonFactoryHonorsTheFlag() throws Exception {
		JSEnvironment on = GaltaJSEnvironment.newBuilder().useDecimal128(true).forceBigDecimalOperations(true).build();
		assertEquals(Decimal128.of("1.5"), eval(on, "JSON.parse('{\"a\":1.5}').a"));
		JSEnvironment off = GaltaJSEnvironment.newBuilder().useDecimal128(false).forceBigDecimalOperations(true).build();
		assertEquals(new BigDecimal("1.5"), eval(off, "JSON.parse('{\"a\":1.5}').a"));
		// Not forced: doubles
		JSEnvironment dbl = GaltaJSEnvironment.newBuilder().useDecimal128(true).forceBigDecimalOperations(false).build();
		assertEquals(Double.valueOf(1.5), eval(dbl, "JSON.parse('{\"a\":1.5}').a"));
	}

	public void testJsonFactoryOverflow() throws Exception {
		JSEnvironment env = GaltaJSEnvironment.newBuilder().useDecimal128(true).supportBigDecimalPromotion(true).build();
		Object v = eval(env, "JSON.parse('[0.1234567890123456789012345]')[0]");
		assertTrue(String.valueOf(v), v instanceof Decimal128);
		assertEquals(Decimal128.of("0.1234567890123456789012345"), v);
	}

	public void testJavaCasts() throws Exception {
		JSEnvironment env = GaltaJSEnvironment.newBuilder().useDecimal128(true).build();
		// Casts with the Java classes
		assertEquals(new BigDecimal("1.5"), eval(env, "Java.type('java.math.BigDecimal')(1.5m)"));
		assertEquals(Decimal128.of("2.5"), eval(env, "Java.type('org.monflabs.util.number.Decimal128')(2.5)"));
		assertEquals(Double.valueOf(1.5), eval(env, "Java.type('java.lang.Double')(1.5m)"));
		// A Decimal128 as the argument of a Java method taking a number
		assertEquals(Double.valueOf(2.5), eval(env, "Java.type('java.lang.Math').abs(-2.5m)"));
		assertEquals(Double.valueOf(7), eval(env, "Java.type('java.lang.Math').abs(-7m)"));
	}

	public void testDecimal128FromJava() throws Exception {
		JSEnvironment env = GaltaJSEnvironment.newBuilder().useDecimal128(true).build();
		Decimal128 d = Decimal128.of("2.25");
		assertEquals(Decimal128.of("4.5"), env.evaluateScript("this * 2m", d));
		assertEquals("decimal", env.evaluateScript("typeof this", d));
		assertEquals(Decimal128.of("4.75"), env.evaluateScript("this + 2.5", d));
	}
}
