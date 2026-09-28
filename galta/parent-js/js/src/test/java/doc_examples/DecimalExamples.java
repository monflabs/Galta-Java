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
package doc_examples;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extensions/Decimal.md
 */
public class DecimalExamples extends __BaseTestCase {

	public void testDecimalLiterals() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object v = env.evaluateExpression("123456.789m");
		assertEquals(BigDecimal.class, v.getClass());
		assertEquals(new BigDecimal("123456.789"), v);
		assertEquals("decimal", (Object)env.evaluateExpression("typeof 1m"));
		// Exact arithmetic where doubles are not
		assertEquals(0.30000000000000004, (Object)env.evaluateExpression("0.1 + 0.2"));
		assertEquals(new BigDecimal("0.3"), (Object)env.evaluateExpression("0.1m + 0.2m"));
	}

	public void testDecimalFunction() {
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(new BigDecimal("123456789123456789.25689"), (Object)env.evaluateExpression("Decimal('123456789123456789.25689')"));
		assertEquals(true, (Object)env.evaluateExpression("Decimal('1.5') === 1.5m"));
		// Decimal is callable but not constructible
		assertEquals("TypeError", (Object)env.evaluateScript("let r; try { new Decimal('1') } catch(e) { r = e.name } r"));
	}

	public void testPrecisionIsAMathContext() {
		// DECIMAL128 (34 digits) by default
		assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), GaltaJSEnvironment.create().evaluateExpression("1m / 3m"));
		JSEnvironment env64 = GaltaJSEnvironment.newBuilder().mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
		assertEquals(new BigDecimal("0.3333333333333333"), (Object)env64.evaluateExpression("1m / 3m"));
	}

	public void testForceBigDecimalOperations() {
		// Every floating point literal and operation becomes a Decimal
		JSEnvironment env = GaltaJSEnvironment.newBuilder().forceBigDecimalOperations(true).build();
		assertEquals(new BigDecimal("0.3"), (Object)env.evaluateExpression("0.1 + 0.2"));
		assertEquals(BigDecimal.class, (Object)env.evaluateExpression("1.0").getClass());
		assertEquals(Integer.class, (Object)env.evaluateExpression("1 + 2").getClass());          // integers are untouched
		assertEquals(BigDecimal.class, (Object)env.evaluateExpression("1 / 2").getClass());        // non-exact division
	}

	public void testMathWithBigNumbers() {
		// supportBigNumberMath: Math accepts BigInt/Decimal and exposes Decimal constants
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(new BigDecimal("1.414213562373095048801688724209698"), (Object)env.evaluateExpression("Math.sqrt(2m)"));
		assertEquals(new BigDecimal("2"), (Object)env.evaluateExpression("Math.abs(-2m)"));
		assertEquals("decimal", (Object)env.evaluateExpression("typeof Math.PIm"));
		assertEquals(true, (Object)env.evaluateExpression("['LN10m','LN2m','LOG10Em','LOG2Em','PIm','SQRT1_2m','SQRT2m'].every(n => typeof Math[n] === 'decimal')"));
	}

	public void testDecimalLiteralsRequireTheExtension() {
		try {
			JavaScriptEnvironment.create().evaluateExpression("1m");
			fail();
		} catch(JSParseException e) {
			// a syntax error in plain ECMAScript
		}
		// The flags can be enabled individually on a plain environment
		JSEnvironment env = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).build();
		assertEquals(new BigDecimal("2.5"), (Object)env.evaluateExpression("1m + 1.5m"));
	}
}
