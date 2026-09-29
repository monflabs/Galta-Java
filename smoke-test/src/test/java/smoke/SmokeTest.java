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
package smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

/**
 * Exercises the published jars the way an application would: JSON parsing and
 * stringifying, and scripts evaluated by the GaltaJS engine.
 */
public class SmokeTest {

	@Test
	public void jsonRoundTrip() {
		JsonObject o = JsonObject.parse("{\"name\":\"Galta\",\"tags\":[\"json\",\"js\"],\"n\":42}");
		assertEquals("Galta", o.getString("name"));
		assertEquals(42, o.getInt("n"));
		assertEquals(2, ((JsonArray) o.get("tags")).size());
		String s = JsonFactory.get().stringify(o, true);
		assertEquals(o, JsonObject.parse(s));
	}

	@Test
	public void javaScriptExpression() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object o = env.evaluateExpression("1 + Math.abs(-2)");
		assertEquals(3, o);
	}

	@Test
	public void javaScriptScript() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object o = env.evaluateScript(
				"const data = JSON.parse('{\"items\":[1,2,3,4]}');\n"
				+ "const total = data.items.reduce((a, b) => a + b, 0);\n"
				+ "`total=${total}`;");
		assertEquals("total=10", o);
	}

	@Test
	public void galtaJSExtensions() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object o = env.evaluateExpression("1m / 4m");
		assertTrue(o instanceof java.math.BigDecimal);
		assertEquals("0.25", o.toString());
	}
}
