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
package tests.javascript.builtin.weakmap;

import org.monflabs.galtajs.rt.builtins.standard.weakmap.BuiltinWeakMap;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test JsonObjectMap and ensure it works like LinkedHashMap
 */
public class JavaWeakMapTest extends JavaScriptStrictTestCase {
	
	public void testMap() {
		BuiltinWeakMap m = new BuiltinWeakMap();
		
		Object k1 = 1; 
		Object k2 = 2; 
		Object k3 = 3; 
		Object k4 = 4; 
		
		m.set(k1, "a");
		assertEquals( "a", m.get(k1) );
		m.set(k2, "b");
		assertEquals( "b", m.get(k2) );

		assertFalse( m.has(k3) );
		assertEquals( "c", m.getOrInsert(k3,"c") );
		assertTrue ( m.has(k3) );
		assertEquals( "c", m.get(k3) );

		assertEquals( "d", m.getOrInsertComputed(k4, () -> "d") );
		assertEquals( "d", m.get(k4) );

		assertTrue ( m.has(k3) );
		m.delete(k3);
		assertFalse( m.has(k3) );		
	}
}
