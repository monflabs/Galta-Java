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
package tests.javascript.util;

import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.builtins.primitives.number.BuiltinNumberPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.string.BuiltinStringPrototype;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * 
 */
public class PrimitivePropertyMapTest extends JavaScriptStrictTestCase {

	public void testPrimitiveMap() {
		PrimitivePropertyMap m = new PrimitivePropertyMap(getEnvironment());
		
		Object o1 = 12;
		assertNull(m.get(o1));
		
		m.create(o1,BuiltinNumberPrototype.get(getEnvironment()));
		JSObject s1 = m.get(o1);
		s1.setOwnProperty("a",11);
		assertNotNull( s1 );
		assertEquals( s1, m.get(o1) );
		
		String o2 = "13";
		assertNull(m.get(o2));
		
		m.create(o2,BuiltinStringPrototype.get(getEnvironment()));
		JSObject s2 = m.get(o2);
		assertNotNull( s2 );
		assertEquals( s2, m.get(o2) );
		
		assertEquals( 11, m.get(o1).getProperty("a") );
		
		//m.dumpStats();
	}
}
