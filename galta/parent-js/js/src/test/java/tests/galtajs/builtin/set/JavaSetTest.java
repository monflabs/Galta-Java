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
package tests.galtajs.builtin.set;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

import org.monflabs.galtajs.rt.builtins.standard.set.BuiltinSet;

import tests.galtajs.GaltaJSTestCase;

/**
 * Test that it seamlessly handles bug number keys
 */
public class JavaSetTest extends GaltaJSTestCase {
	
	public void testBigInteger() {
		Set<Object> s = new BuiltinSet(getEnvironment());
		s.add(1);
		assertEquals( true, s.contains(BigInteger.ONE) );
		s.remove(BigInteger.ONE);
		assertEquals( false, s.contains(BigInteger.ONE) );
	}
	public void testBigDecimal() {
		Set<Object> s = new BuiltinSet(getEnvironment());
		s.add(1);
		assertEquals( true, s.contains(BigDecimal.ONE) );
		s.remove(BigDecimal.ONE);
		assertEquals( false, s.contains(BigDecimal.ONE) );
	}
	public void testEquals() {
		Set<Object> s1 = new BuiltinSet(getEnvironment());
		s1.add(1);
		
		Set<Object> s2 = new BuiltinSet(getEnvironment());
		s2.add(1.0);

		assertTrue(s1.equals(s1));
		assertTrue(s1.equals(s2));
		assertTrue(s2.equals(s1));

		s1.remove(BigDecimal.ONE);
		assertFalse(s1.equals(s2));
		assertFalse(s2.equals(s1));
	}
}
