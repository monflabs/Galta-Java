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
import java.math.BigInteger;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extensions/Numbers.md
 */
public class NumbersExamples extends __BaseTestCase {

	public void testLiteralSuffixes() {
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(Integer.class,    env.evaluateExpression("1").getClass());
		assertEquals(Integer.class,    env.evaluateExpression("1i").getClass());
		assertEquals(Long.class,       env.evaluateExpression("1L").getClass());
		assertEquals(Double.class,     env.evaluateExpression("1.5").getClass());
		assertEquals(Integer.class,    env.evaluateExpression("1.0").getClass());   // whole values are narrowed
		assertEquals(Double.class,     env.evaluateExpression("1d").getClass());    // unless the suffix says otherwise
		assertEquals(Float.class,      env.evaluateExpression("1.5f").getClass());
		assertEquals(BigInteger.class, (Object)env.evaluateExpression("1n").getClass());
		assertEquals(BigDecimal.class, (Object)env.evaluateExpression("1m").getClass());
		// Suffixes are case-insensitive and work with all radixes
		assertEquals(255L, (Object)env.evaluateExpression("0xFFl"));
		assertEquals(5L, (Object)env.evaluateExpression("0b101L"));
		// -0 must keep its sign, so it is a Double
		assertEquals(-0.0, env.evaluateExpression("-0"));
		assertEquals(Double.class, env.evaluateExpression("-0").getClass());
		// typeof: Integer/Long/Float/Double are all 'number'
		assertEquals(List.of("number", "number", "bigint", "decimal"),
				list(env.evaluateExpression("[typeof 1L, typeof 1.5f, typeof 1n, typeof 1m]")));
	}

	public void testStandardJavaScriptOverflow() {
		// Plain ECMAScript: integers that no longer fit an int become doubles and lose precision
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals(2147483648.0, (Object)env.evaluateExpression("2147483647 + 1"));
		assertEquals(1708494009298794800.0, (Object)env.evaluateExpression("1492553851 * 1144678303"));   // mathematically wrong
	}

	public void testLongPromotion() {
		// supportLongPromotion (on in GaltaJSEnvironment): int overflow promotes to Long
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(2147483648L, (Object)env.evaluateExpression("2147483647 + 1"));
		assertEquals(1708494009298794853L, (Object)env.evaluateExpression("1492553851 * 1144678303"));   // exact
		// Results that fit an int stay ints; non-exact divisions are doubles
		assertEquals(Integer.class, (Object)env.evaluateExpression("(2147483647 - 3) / 4").getClass());
		assertEquals(Double.class, (Object)env.evaluateExpression("2147483647 / 3").getClass());
		// A Long operand always gives a Long, even without promotion
		assertEquals(1708494009298794853L, (Object)JavaScriptEnvironment.create().evaluateExpression("1492553851L * 1144678303"));
	}

	public void testBigIntPromotion() {
		// Long overflow is a double by default...
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(9.223372036854776E18, (Object)env.evaluateExpression("9223372036854775807L + 1"));
		// ...and an exact BigInt with supportBigIntPromotion
		JSEnvironment promoting = GaltaJSEnvironment.newBuilder().supportBigIntPromotion(true).build();
		assertEquals(new BigInteger("9223372036854775808"), (Object)promoting.evaluateExpression("9223372036854775807L + 1"));
		assertEquals("bigint", (Object)promoting.evaluateExpression("typeof (9223372036854775807L + 1)"));
	}

	public void testMixingBigNumbers() {
		// supportMixedBigNumber: BigInt/Decimal and regular numbers can be mixed
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(new BigInteger("3"), (Object)env.evaluateExpression("6 / 2n"));
		assertEquals(new BigInteger("36"), (Object)env.evaluateExpression("6 ** 2n"));
		assertEquals(new BigDecimal("3.0"), (Object)env.evaluateExpression("6.0m / 2"));
		assertEquals(new BigDecimal("3.3"), (Object)env.evaluateExpression("1.1m + 2.2m"));

		// Standard JavaScript refuses to mix them
		JSEnvironment js = JavaScriptEnvironment.create();
		assertEquals("TypeError", (Object)js.evaluateScript("let r; try { 6 / 2n } catch(e) { r = e.name } r"));
	}

	public void testBigDecimalPromotionWhenParsingJson() {
		// supportBigDecimalPromotion: JSON numbers too precise for a double are parsed as Decimal
		JSEnvironment env = GaltaJSEnvironment.newBuilder().supportBigDecimalPromotion(true).build();
		Object v = env.evaluateExpression("JSON.parse('{\"v\": 1234567890.12345678901234567890}').v");
		assertEquals(new BigDecimal("1234567890.12345678901234567890"), v);

		Object plain = GaltaJSEnvironment.create().evaluateExpression("JSON.parse('{\"v\": 1234567890.12345678901234567890}').v");
		assertEquals(1.2345678901234567E9, plain);
	}
}
