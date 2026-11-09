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
import org.monflabs.util.number.Decimal128;
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

	public void testChoosingTheDecimalType() {
		JSEnvironment galta = GaltaJSEnvironment.create();                                  // the GaltaJS extensions: useBigDecimal(true)
		assertEquals(new BigDecimal("1.5"), (Object)galta.evaluateExpression("1.5m"));
		JSEnvironment d128 = GaltaJSEnvironment.newBuilder().useBigDecimal(false).build();
		assertEquals(Decimal128.of("1.5"), (Object)d128.evaluateExpression("1.5m"));
		JSEnvironment js = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).build();
		assertEquals(Decimal128.of("1.5"), (Object)js.evaluateExpression("1.5m"));                    // no extensions: Decimal128
		JSEnvironment big = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).useBigDecimal(true).build();
		assertEquals(new BigDecimal("1.5"), (Object)big.evaluateExpression("1.5m"));
	}

	public void testComparingTheTypes() {
		JSEnvironment big = GaltaJSEnvironment.create();
		JSEnvironment d128 = GaltaJSEnvironment.newBuilder().useBigDecimal(false).build();
		// Scale: a BigDecimal keeps it, a Decimal128 has none; equality ignores it in both cases
		assertEquals("1.50", (Object)big.evaluateExpression("String(1.50m)"));
		assertEquals("1.5", (Object)d128.evaluateExpression("String(1.50m)"));
		assertEquals(true, (Object)big.evaluateExpression("1.50m === 1.5m"));
		// The global Decimal is the function of the selected type
		assertEquals(false, (Object)big.evaluateExpression("Decimal === Decimal128"));
		assertEquals(true, (Object)d128.evaluateExpression("Decimal === Decimal128"));
		// The proposal's prototype methods are on Decimal128 only
		assertEquals("2.00", (Object)d128.evaluateExpression("(2m).toFixed(2)"));
		assertEquals("TypeError", (Object)big.evaluateScript("let r; try { (2m).toFixed(2) } catch(e) { r = e.name } r"));
		// Mixed with a Number, the result has the type of the decimal operand
		assertEquals(new BigDecimal("1.1"), (Object)big.evaluateExpression("1m + 0.1"));
		assertEquals(Decimal128.of("1.1"), (Object)d128.evaluateExpression("1m + 0.1"));
	}

	public void testCreatingASpecificType() {
		JSEnvironment env = GaltaJSEnvironment.create();
		// The Decimal128 function is always registered
		assertEquals(Decimal128.of("0.1"), (Object)env.evaluateExpression("Decimal128('0.1')"));
		// From Java: Decimal128 has factory methods, BigDecimal a constructor
		String types = "const D128 = Java.type('org.monflabs.util.number.Decimal128'), BigDec = Java.type('java.math.BigDecimal');";
		assertEquals(Decimal128.of("0.1"), (Object)env.evaluateScript(types + "D128.of('0.1')"));
		assertEquals("object", (Object)env.evaluateScript(types + "typeof new BigDec('0.1')"));        // new boxes it
		assertEquals("decimal", (Object)env.evaluateScript(types + "typeof new BigDec('0.1').valueOf()"));
		assertEquals(new BigDecimal("0.3"), (Object)env.evaluateScript(types + "new BigDec('0.1') + 0.2m")); // arithmetic unboxes it
	}

	public void testMixingDecimalTypes() {
		JSEnvironment env = GaltaJSEnvironment.create();
		// A BigDecimal and a Decimal128 give a BigDecimal, whatever the order and the useBigDecimal flag
		assertEquals(new BigDecimal("0.3"), (Object)env.evaluateExpression("0.1m + Decimal128('0.2')"));
		assertEquals(new BigDecimal("0.3"), (Object)env.evaluateExpression("Decimal128('0.1') + 0.2m"));
		assertEquals(true, (Object)env.evaluateExpression("1.5m === Decimal128('1.5')"));
		// The Decimal128 special values become Numbers
		assertEquals(Double.POSITIVE_INFINITY, (Object)env.evaluateExpression("Decimal128('Infinity') + 1m"));
		assertEquals(Double.NaN, (Object)env.evaluateExpression("Decimal128('NaN') + 1m"));
		assertEquals(-0.0, (Object)env.evaluateExpression("Decimal128('-0') * 1m"));
		// The precision is the MathContext's
		JSEnvironment env64 = GaltaJSEnvironment.newBuilder().mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
		assertEquals(new BigDecimal("0.3333333333333333"), (Object)env64.evaluateExpression("1m / Decimal128('3')"));
	}

	public void testDecimalLiterals() {
		// With the GaltaJS extensions, decimals are BigDecimal
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
		// BigDecimal decimals: DECIMAL128 (34 digits) by default
		assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), GaltaJSEnvironment.create().evaluateExpression("1m / 3m"));
		JSEnvironment env64 = GaltaJSEnvironment.newBuilder().mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
		assertEquals(new BigDecimal("0.3333333333333333"), (Object)env64.evaluateExpression("1m / 3m"));
		// A Decimal128 always has 34 digits
		JSEnvironment d128 = GaltaJSEnvironment.newBuilder().useBigDecimal(false).mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
		assertEquals(Decimal128.of("0.3333333333333333333333333333333333"), d128.evaluateExpression("1m / 3m"));
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

	public void testSpecialValues() {
		// Decimal128 (the default without the GaltaJS extensions) carries its own NaN, infinities and -0
		JSEnvironment env = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).supportMixedBigNumber(true).supportBigNumberMath(true).build();
		assertEquals(Decimal128.POSITIVE_INFINITY, (Object)env.evaluateExpression("1m / 0m"));
		assertEquals(Decimal128.NaN, (Object)env.evaluateExpression("0m / 0m"));
		assertEquals(Decimal128.NEGATIVE_ZERO, (Object)env.evaluateExpression("-1m * 0m"));
		assertEquals(Decimal128.POSITIVE_INFINITY, (Object)env.evaluateExpression("9e6144m * 10m"));  // overflow
		assertEquals(Decimal128.NEGATIVE_ZERO, (Object)env.evaluateExpression("Math.ceil(-0.5m)"));
		assertEquals(Decimal128.NEGATIVE_INFINITY, (Object)env.evaluateExpression("Math.log(0m)"));

		// A BigDecimal (the default with the GaltaJS extensions) has none: the same results are the matching Numbers
		JSEnvironment big = GaltaJSEnvironment.create();
		assertEquals(Double.POSITIVE_INFINITY, (Object)big.evaluateExpression("1m / 0m"));
		assertEquals(Double.NaN, (Object)big.evaluateExpression("0m / 0m"));
		assertEquals(-0.0, (Object)big.evaluateExpression("-1m * 0m"));
		assertEquals(Double.POSITIVE_INFINITY, (Object)big.evaluateExpression("9e6144m * 10m"));  // overflow
		assertEquals(-0.0, (Object)big.evaluateExpression("Math.ceil(-0.5m)"));
		assertEquals(Double.NEGATIVE_INFINITY, (Object)big.evaluateExpression("Math.log(0m)"));
		assertEquals(new BigDecimal("1"), (Object)big.evaluateExpression("-0 + 1m"));    // a Number special value is a Decimal operand
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
		assertEquals(Decimal128.of("2.5"), (Object)env.evaluateExpression("1m + 1.5m"));
	}
}
