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

import org.monflabs.galtajs.rt.RuntimeUtil;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test array literal.
 * @author Philippe Riand
 */
public class ArrayLiteralTest extends JavaScriptStrictTestCase {
	
	@SuppressWarnings("unchecked")
	public void testScript() throws Exception {
		ExecutionResult r = execute();

		List<Object> v1 = (List<Object>)r.context().getVariableValue("v1",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("['A']",v1);

		List<Object> v2 = (List<Object>)r.context().getVariableValue("v2",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("['a','b']",v2);

		List<Object> v3 = (List<Object>)r.context().getVariableValue("v3",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("['A','a','b']",v3);

		List<Object> v4 = (List<Object>)r.context().getVariableValue("v4",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("['a','b','B']",v4);

		List<Object> v5 = (List<Object>)r.context().getVariableValue("v5",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("['A','a','b','B']",v5);
	}
}
