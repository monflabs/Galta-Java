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
package tests.javascript.builtin.map;

import java.util.Map;

import org.monflabs.galtajs.rt.builtins.standard.map.BuiltinMap;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test JsonObjectMap and ensure it works like LinkedHashMap
 */
public class JSMapTest extends JavaScriptStrictTestCase {
	
	public void testMap() {
		Map<Object,Object> m = new BuiltinMap(getEnvironment());
		
		m.put(1, "a");
		assertEquals( "a", m.get(1.0) );
	}
}
