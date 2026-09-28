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

import java.util.Map;

import org.monflabs.galtajs.rt.RuntimeUtil;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test object literal.
 * @author Philippe Riand
 */
public class ObjectLiteralTest extends JavaScriptStrictTestCase {
	
	@SuppressWarnings("unchecked")
	public void testScript() throws Exception {
		ExecutionResult r = execute();

		Map<String,Object> v1 = (Map<String,Object> )r.context().getVariableValue("v1",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("{a:'A'}",v1);

		Map<String,Object>  v2 = (Map<String,Object> )r.context().getVariableValue("v2",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("{a:'a',b:'b'}",v2);

		Map<String,Object>  v3 = (Map<String,Object> )r.context().getVariableValue("v3",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("{a:'a',b:'b'}",v3);

		Map<String,Object>  v4 = (Map<String,Object> )r.context().getVariableValue("v4",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("{a:'a',b:'b'}",v4);

		Map<String,Object>  v5 = (Map<String,Object> )r.context().getVariableValue("v5",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("{a:'a',b:'B'}",v5);

		Map<String,Object>  v6 = (Map<String,Object> )r.context().getVariableValue("v6",RuntimeUtil.UNDEFINED);
		support.assertJsonEquals("{a:'A',b:'b'}",v6);
	}
}
