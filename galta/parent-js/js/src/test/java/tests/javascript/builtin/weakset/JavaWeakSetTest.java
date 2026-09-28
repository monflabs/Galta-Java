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
package tests.javascript.builtin.weakset;

import org.monflabs.galtajs.rt.builtins.standard.weakset.BuiltinWeakSet;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test JsonObjectMap and ensure it works like LinkedHashMap
 */
public class JavaWeakSetTest extends JavaScriptStrictTestCase {
	
	public void testWeakSet() {
		BuiltinWeakSet s = new BuiltinWeakSet();
		
		Object k1 = 1; 
		Object k2 = 2; 

		s.add(k1);
		assertEquals( true, s.has(k1) );
		
		s.add(k2);
		assertEquals( true, s.has(k2) );
		
		s.delete(k1);
		assertEquals( false, s.has(k1) );
		assertEquals( true, s.has(k2) );
	}
}
