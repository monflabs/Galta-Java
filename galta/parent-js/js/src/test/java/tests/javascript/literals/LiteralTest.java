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
package tests.javascript.literals;

import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.rt.RuntimeUtil;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Basic Literal tests.
 * @author Philippe Riand
 */
public class LiteralTest extends JavaScriptStrictTestCase {
	
	@SuppressWarnings("unchecked")
	public void testScript() throws Exception {
		ExecutionResult r = execute();

		assertEquals(null,r.context().getVariableValue("v1",RuntimeUtil.UNDEFINED));
		assertEquals(79,r.context().getVariableValue("v2",RuntimeUtil.UNDEFINED));
		assertEquals(34.79,r.context().getVariableValue("v3",RuntimeUtil.UNDEFINED));
		assertEquals(true,r.context().getVariableValue("v4",RuntimeUtil.UNDEFINED));
		assertEquals(false,r.context().getVariableValue("v5",RuntimeUtil.UNDEFINED));
		assertEquals("String1",r.context().getVariableValue("v6",RuntimeUtil.UNDEFINED));
		assertEquals("String2",r.context().getVariableValue("v7",RuntimeUtil.UNDEFINED));
		
		List<Object> v8 = (List<Object>)r.context().getVariableValue("v8",RuntimeUtil.UNDEFINED);
		assertEquals(0,v8.size());
		List<Object> v8a = (List<Object>)r.context().getVariableValue("v8a",RuntimeUtil.UNDEFINED);
		assertEquals(1,v8a.size());
		assertEquals(1,v8a.get(0));
		List<Object> v8b = (List<Object>)r.context().getVariableValue("v8b",RuntimeUtil.UNDEFINED);
		assertEquals(3,v8b.size());
		assertEquals(1,v8b.get(0));
		assertEquals(2,v8b.get(1));
		assertEquals(3,v8b.get(2));
		
		Map<String,Object> v9 = (Map<String,Object>)r.context().getVariableValue("v9",RuntimeUtil.UNDEFINED);
		assertEquals(0,v9.size());
		Map<String,Object> v9a = (Map<String,Object>)r.context().getVariableValue("v9a",RuntimeUtil.UNDEFINED);
		assertEquals(1,v9a.size());
		assertEquals("A",v9a.get("a"));
		Map<String,Object> v9b = (Map<String,Object>)r.context().getVariableValue("v9b",RuntimeUtil.UNDEFINED);
		assertEquals(3,v9b.size());
		assertEquals("A",v9b.get("a"));
		assertEquals("B",v9b.get("b"));
		assertEquals("C",v9b.get("c"));
	}
}
