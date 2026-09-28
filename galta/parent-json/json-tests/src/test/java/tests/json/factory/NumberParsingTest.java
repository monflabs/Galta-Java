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
package tests.json.factory;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonFactory.INTEGER;
import org.monflabs.json.JsonFactory.OVERFLOW_DECIMAL;
import org.monflabs.json.JsonFactory.OVERFLOW_INTEGER;
import org.monflabs.json.java.JavaJsonFactory;

import tests.ProjectTestCase;

/**
 * Number literal parsing and the number type options.
 */
public class NumberParsingTest extends ProjectTestCase {

	private static class OptionsFactory extends JavaJsonFactory {
		INTEGER defaultInteger = INTEGER.INT;
		OVERFLOW_INTEGER overflowInteger = OVERFLOW_INTEGER.BIGINT;
		OVERFLOW_DECIMAL overflowDecimal = OVERFLOW_DECIMAL.BIGDEC;
		boolean useLongIntegers = true;
		@Override
		public INTEGER defaultInteger() { return defaultInteger; }
		@Override
		public OVERFLOW_INTEGER overflowInteger() { return overflowInteger; }
		@Override
		public OVERFLOW_DECIMAL overflowDecimal() { return overflowDecimal; }
		@Override
		public boolean useLongIntegers() { return useLongIntegers; }
	}

	public void testDecimalOverflowByValue() {
		JsonFactory f = JsonFactory.get();
		// A double holds the value: a Double, whatever the literal length
		assertEquals(3.141592653589793, f.parse("3.141592653589793"));
		assertEquals(-1.2345678901234, f.parse("-1.2345678901234"));
		assertEquals(0.1, f.parse("0.10000000000000000"));
		assertEquals(1.0E-5, f.parse("0.0000100000000000"));
		assertEquals(0.0, f.parse("0.000000000000000000000"));
		assertEquals(5e-324, f.parse("4.9e-324"));
		// It doesn't: an exact BigDecimal
		assertEquals(new BigDecimal("1e400"), f.parse("1e400"));
		assertEquals(new BigDecimal("-1e400"), f.parse("-1e400"));
		assertEquals(new BigDecimal("1e-400"), f.parse("1e-400"));
		assertEquals(new BigDecimal("3.14159265358979323846"), f.parse("3.14159265358979323846"));
		assertEquals(new BigDecimal("0.1000000000000000000001"), f.parse("0.1000000000000000000001"));
		// A subnormal double has fewer digits: 1e-320 round trips, 1.2345678901e-320 doesn't
		assertEquals(1e-320, f.parse("1e-320"));
		assertEquals(new BigDecimal("1.2345678901e-320"), f.parse("1.2345678901e-320"));

		// With the DOUBLE overflow option, always a Double
		OptionsFactory d = new OptionsFactory();
		d.overflowDecimal = OVERFLOW_DECIMAL.DOUBLE;
		assertEquals(Double.POSITIVE_INFINITY, d.parse("1e400"));
		assertEquals(0.0, d.parse("1e-400"));
		assertEquals(3.141592653589793, d.parse("3.14159265358979323846"));
	}

	public void testZero() {
		assertEquals(Integer.valueOf(0), JsonFactory.get().parse("0"));
		assertEquals(Double.valueOf(-0.0), JsonFactory.get().parse("-0"));
		OptionsFactory f = new OptionsFactory();
		f.defaultInteger = INTEGER.BIGINT;
		// "0" has the configured type like any other integer
		assertEquals(BigInteger.ZERO, f.parse("0"));
		assertEquals(BigInteger.ONE, f.parse("1"));
		// -0 is a double, as in JavaScript
		assertEquals(Double.valueOf(-0.0), f.parse("-0"));
		f.defaultInteger = INTEGER.LONG;
		assertEquals(Long.valueOf(0), f.parse("0"));
	}

	public void testLongDefault() {
		OptionsFactory f = new OptionsFactory();
		f.defaultInteger = INTEGER.LONG;
		f.useLongIntegers = false;
		f.overflowInteger = OVERFLOW_INTEGER.DOUBLE;
		// An explicit LONG default always gives a Long
		assertEquals(Long.valueOf(1), f.parse("1"));
		assertEquals(Long.valueOf(Long.MAX_VALUE), f.parse("9223372036854775807"));
		// Past the long range: the overflow option
		assertEquals(9.223372036854775808E18, f.parse("9223372036854775808"));
		// INT default, no longs: a double past the int range
		f.defaultInteger = INTEGER.INT;
		assertEquals(Integer.valueOf(12), f.parse("12"));
		assertEquals(3.0E9, f.parse("3000000000"));
	}

	public void testParseFloatInvalid() {
		JsonFactory f = JsonFactory.get();
		for(String s: new String[] {"e5", "-", ".", "+", "-.e1", "E", "", "abc"}) {
			assertThrows(s, JsonException.class, () -> f.parseFloat(s, 0));
			Number n = f.parseFloat(s, JsonFactory.PARSEINT_RETURNNAN);
			assertTrue(s, Double.isNaN(n.doubleValue()));
		}
		assertEquals(1.5, f.parseFloat("1.5abc", JsonFactory.PARSEINT_IGNOREEXTRACHAR));
		assertEquals(0.5, f.parseFloat(".5", 0));
		assertEquals(5.0, f.parseFloat("5.", 0));
		assertEquals(10.0, f.parseFloat("1e1", 0));
		assertEquals(1.0, f.parseFloat("1e", JsonFactory.PARSEINT_IGNOREEXTRACHAR));
	}

	public void testParseIntInvalid() {
		JsonFactory f = JsonFactory.get();
		assertThrows(JsonException.class, () -> f.parseInt("0x", 0, 0));
		assertTrue(Double.isNaN(f.parseInt("0x", 0, JsonFactory.PARSEINT_RETURNNAN).doubleValue()));
		assertTrue(Double.isNaN(f.parseInt("+", 0, JsonFactory.PARSEINT_RETURNNAN).doubleValue()));
		assertEquals(255, f.parseInt("0xff", 0, 0));
		assertEquals(Double.valueOf(-0.0), f.parseInt("-0", 10, 0));
		assertEquals(Double.valueOf(-0.0), f.parseInt("-00", 10, 0));
		assertEquals(0, f.parseInt("00", 10, 0));
	}
}
