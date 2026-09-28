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
package tests.json.factory;

import org.monflabs.json.JsonObject;
import org.monflabs.json.stringifier.JsonStringifier;

import tests.ProjectTestCase;


public class JsonStringifierTest extends ProjectTestCase {

    public void testStringSerializer() throws Exception {
    	JsonObject o = JsonObject.of("a","Value for a", "b","Value for b", "c","Value for c");
    	
		JsonStringifier.StringSerializer sg = new JsonStringifier.StringSerializer();
		sg.setCompact(true);
		assertEquals("{\"a\":\"Value for a\",\"b\":\"Value for b\",\"c\":\"Value for c\"}",sg.stringify(o));
    }

    public void testLimitedStringSerializer() throws Exception {
    	JsonObject o = JsonObject.of("a","Value for a", "b","Value for b", "c","Value for c");
    	
		JsonStringifier.LimitedStringSerializer sg = new JsonStringifier.LimitedStringSerializer(1000);
		sg.setCompact(true);
		assertEquals("{\"a\":\"Value for a\",\"b\":\"Value for b\",\"c\":\"Value for c\"}",sg.stringify(o));
		assertFalse(sg.isTruncated());

		JsonStringifier.LimitedStringSerializer sg2 = new JsonStringifier.LimitedStringSerializer(10);
		sg2.setCompact(true);
		assertEquals("{\"a\":\"Valu",sg2.stringify(o));
		assertTrue(sg2.isTruncated());

		JsonStringifier.LimitedStringSerializer sg3 = new JsonStringifier.LimitedStringSerializer(0);
		sg3.setCompact(true);
		assertEquals("",sg3.stringify(o));
		assertTrue(sg3.isTruncated());
    }
}
