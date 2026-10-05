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


import org.monflabs.util.DtoA;

import tests.ProjectTestCase;

public class DtoATest extends ProjectTestCase {

	public void testToStandard() {
		// JavaScript Number.prototype.toString() layout
		assertEquals( "0", DtoA.toStandard(0.0) );
		assertEquals( "0", DtoA.toStandard(-0.0) );
		assertEquals( "1", DtoA.toStandard(1.0) );
		assertEquals( "1.1", DtoA.toStandard(1.1) );
		assertEquals( "1.123456", DtoA.toStandard(1.123456) );
		assertEquals( "120000000000000000", DtoA.toStandard(1.2E17) );
		assertEquals( "1200000000000000000", DtoA.toStandard(1.2E18) );
		assertEquals( "12000000000000000000", DtoA.toStandard(1.2E19) );
		assertEquals( "120000000000000000000", DtoA.toStandard(1.2E20) );
		assertEquals( "1.2e+25", DtoA.toStandard(1.2E25) );
		assertEquals( "0.30000000000000004", DtoA.toStandard(0.1+0.2) );
		assertEquals( "12345678.5", DtoA.toStandard(12345678.5) );
		assertEquals( "-12345678.5", DtoA.toStandard(-12345678.5) );
	}

	public void testExponentBoundaries() {
		// Plain decimal down to 1e-6, exponent below
		assertEquals( "0.0001", DtoA.toStandard(0.0001) );
		assertEquals( "0.000001", DtoA.toStandard(0.000001) );
		assertEquals( "1e-7", DtoA.toStandard(1e-7) );
		assertEquals( "-1.5e-10", DtoA.toStandard(-1.5e-10) );
		// Plain digits below 1e21, exponent from 1e21
		assertEquals( "100000000000000000000", DtoA.toStandard(1e20) );
		assertEquals( "999999999999999900000", DtoA.toStandard(9.999999999999999e20) );
		assertEquals( "1e+21", DtoA.toStandard(1e21) );
		assertEquals( "-1e+21", DtoA.toStandard(-1e21) );
		assertEquals( "1.7976931348623157e+308", DtoA.toStandard(Double.MAX_VALUE) );
		// Shortest digits, even for subnormals
		assertEquals( "5e-324", DtoA.toStandard(Double.MIN_VALUE) );
		assertEquals( "2.2250738585072014e-308", DtoA.toStandard(Double.MIN_NORMAL) );
	}

	public void testSpecialValues() {
		assertEquals( "NaN", DtoA.toStandard(Double.NaN) );
		assertEquals( "Infinity", DtoA.toStandard(Double.POSITIVE_INFINITY) );
		assertEquals( "-Infinity", DtoA.toStandard(Double.NEGATIVE_INFINITY) );
		assertEquals( "NaN", DtoA.toStandard(Float.NaN) );
		assertEquals( "-Infinity", DtoA.toStandard(Float.NEGATIVE_INFINITY) );
	}

	public void testLongBoundary() {
		// Integers beyond 2^53 print the shortest digits padded with zeros, as JavaScript does
		assertEquals( "9223372036854776000", DtoA.toStandard(9223372036854775808.0) );
		assertEquals( "-9223372036854776000", DtoA.toStandard(-9223372036854775808.0) );
		assertEquals( "4611686018427388000", DtoA.toStandard(4611686018427387904.0) );
		assertEquals( "9007199254740992", DtoA.toStandard(9007199254740992.0) );
		assertEquals( "9007199254740991", DtoA.toStandard(9007199254740991.0) );
	}

	public void testFloat() {
		assertEquals( "0", DtoA.toStandard(0.0f) );
		assertEquals( "1", DtoA.toStandard(1.0f) );
		assertEquals( "1.5", DtoA.toStandard(1.5f) );
		// Shortest float digits, not the digits of the widened double
		assertEquals( "0.1", DtoA.toStandard(0.1f) );
		assertEquals( "10000000000", DtoA.toStandard(1e10f) );
		assertEquals( "1e+30", DtoA.toStandard(1e30f) );
		assertEquals( "1e-10", DtoA.toStandard(1e-10f) );
		assertEquals( "1e-45", DtoA.toStandard(Float.MIN_VALUE) );
	}

	/**
	 * Known JavaScript outputs (from V8, identical to GaltaJS Number.prototype.toString).
	 */
	public void testKnownJavaScriptOutputs() {
		Object[][] table = {
			{ 123e-20, "1.23e-18" },
			{ 1.0/3, "0.3333333333333333" },
			{ 2.0/3, "0.6666666666666666" },
			{ 4.35, "4.35" },
			{ 1e-6, "0.000001" },
			{ 1.5e-6, "0.0000015" },
			{ 1.2345e-7, "1.2345e-7" },
			{ 5e-7, "5e-7" },
			{ 123456789012345680000.0, "123456789012345680000" },
			{ 1.2345678901234568e21, "1.2345678901234568e+21" },
			{ 100.0, "100" },
			{ 0.5, "0.5" },
			{ 1e100, "1e+100" },
			{ 1.7e-300, "1.7e-300" },
			{ Math.PI, "3.141592653589793" },
			{ Math.E, "2.718281828459045" },
			{ 9007199254740993.0, "9007199254740992" },
			{ 18014398509481984.0, "18014398509481984" },
			{ 36028797018963970.0, "36028797018963970" },
		};
		for(Object[] t: table) {
			assertEquals( "for "+t[0], t[1], DtoA.toStandard((Double)t[0]) );
		}
	}

	public void testRoundTripFuzz() {
		// Every output parses back to the same double, and is never longer than Java's shortest digits
		java.util.Random r = new java.util.Random(12345);
		for(int i=0; i<200000; i++) {
			double d = Double.longBitsToDouble(r.nextLong());
			if(Double.isNaN(d) || Double.isInfinite(d)) {
				continue;
			}
			String s = DtoA.toStandard(d);
			assertEquals( s, d==0 ? 0.0 : d, Double.parseDouble(s) );
			assertTrue( s, s.indexOf('E')<0 );
		}
		for(int i=0; i<100000; i++) {
			float f = Float.intBitsToFloat(r.nextInt());
			if(Float.isNaN(f) || Float.isInfinite(f)) {
				continue;
			}
			String s = DtoA.toStandard(f);
			assertEquals( s, f==0 ? 0.0f : f, Float.parseFloat(s) );
		}
	}

	public void testJavaLiteral() {
		// Java-style form used to generate Java source code: unchanged by the JavaScript layout
		assertEquals( "0", DtoA.toJavaLiteral(0.0) );
		assertEquals( "1.1", DtoA.toJavaLiteral(1.1) );
		assertEquals( "4611686018427387904", DtoA.toJavaLiteral(4611686018427387904.0) );
		assertEquals( "9.223372036854776e18", DtoA.toJavaLiteral(9223372036854775808.0) );
		assertEquals( "1.0e21", DtoA.toJavaLiteral(1e21) );
		assertEquals( "1.0e-7", DtoA.toJavaLiteral(1e-7) );
		assertEquals( "1.0e-4", DtoA.toJavaLiteral(0.0001) );
		assertEquals( "1.0e30", DtoA.toJavaLiteral(1e30f) );
		assertEquals( "1.0e-10", DtoA.toJavaLiteral(1e-10f) );
		assertEquals( "10000000000", DtoA.toJavaLiteral(1e10f) );
	}

	// The buffer variant writes the same text as toStandard(double)
	public void testToStandardInBuffer() {
		java.util.Random r = new java.util.Random(23);
		char[] buffer = new char[DtoA.MAX_STANDARD_LENGTH+8];
		double[] fixed = {0.0, -0.0, 1.0, -1.5, 1e21, 1e-7, 0.1, Double.MIN_VALUE, Double.MAX_VALUE,
				Double.MIN_NORMAL, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -123456789.125};
		for(double d: fixed) {
			int end = DtoA.toStandard(d, buffer, 5);
			assertEquals(String.valueOf(d), DtoA.toStandard(d), new String(buffer, 5, end-5));
		}
		for(int i=0; i<200000; i++) {
			double d = i%2==0 ? Double.longBitsToDouble(r.nextLong()) : r.nextDouble()*Math.pow(10, r.nextInt(50)-25);
			int end = DtoA.toStandard(d, buffer, 3);
			assertEquals(String.valueOf(d), DtoA.toStandard(d), new String(buffer, 3, end-3));
		}
	}
}
