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
package tests.javascript.builtin.number;

import java.util.Random;

import org.monflabs.galtajs.rt.util.NumberFormatting;
import org.monflabs.util.DtoA;

import junit.framework.TestCase;

/**
 * DtoA.toStandard() (used for JSON output) must produce exactly what the engine's
 * Number.prototype.toString() produces.
 */
public class DtoAOracleTest extends TestCase {

	private static void check(double d) {
		assertEquals( "for "+d, NumberFormatting.numberToString(d), DtoA.toStandard(d) );
	}

	public void testEdgeCases() {
		double[] values = {
			0.0, -0.0, 1, -1, 0.1, 0.1+0.2, 1e-6, 1e-7, 0.0001, 1.5e-6, 1e20, 1e21, -1e21, 1e22,
			9.999999999999999e20, 12345678.5, Math.pow(2,53), Math.pow(2,53)+2, Math.pow(2,62), Math.pow(2,63),
			Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, -Double.MAX_VALUE,
			Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
			Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Math.PI, 123e-20,
		};
		for(double d: values) {
			check(d);
		}
	}

	public void testRandomDoubles() {
		Random r = new Random(20260926);
		for(int i=0; i<300000; i++) {
			double d;
			switch(i%3) {
				case 0:  d = Double.longBitsToDouble(r.nextLong()); break;
				case 1:  d = r.nextDouble()*Math.pow(10, r.nextInt(50)-25); break;
				default: d = Double.longBitsToDouble(r.nextLong() & 0x000fffffffffffffL); break;	// subnormals
			}
			check(d);
		}
	}
}
