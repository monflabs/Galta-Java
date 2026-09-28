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

import java.io.StringReader;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;


public class StringParsingTest extends ProjectTestCase {

	public void testS1() throws Exception {
		String text = "My Test";
		String s = "{t:\"" + text + "\"}";
		JsonObject o = (JsonObject) JsonFactory.get().parse(s);
		assertEquals(o.get("t"), text);
	}

	public void testS2() throws Exception {
		String text = "My Test";
		String s = "{t:'" + text + "'}";
		JsonObject o = (JsonObject) JsonFactory.get().parse(s);
		assertEquals(o.get("t"), text);
	}

	public void testSEscape() throws Exception {
		String text = "My\r\nTest";
		String text2 = "My\\r\\nTest";
		String s = "{t:'" + text2 + "'}";
		JsonObject o = (JsonObject) JsonFactory.get().parse(s);
		assertEquals(o.get("t"), text);
	}

	public void testUnicode2String() throws Exception {
		String s = "{\"t\":\"Before\\x0CAfter\"}";
		JsonObject o = (JsonObject) JsonFactory.get().parse(s);
		assertEquals("Before\u000CAfter", o.get("t"));
	}

	public void testUnicode4String() throws Exception {
		String s = "{\"t\":\"Before\\u000CAfter\"}";
		JsonObject o = (JsonObject) JsonFactory.get().parse(s);
		assertEquals("Before\u000CAfter", o.get("t"));
	}
	
	
	// Sinhalese language
	static String[] nonLatinTexts = new String[] { 
			"සිංහල ජාතිය", "日本語", "Русский", "فارسی", 
			"한국어", "Հայերեն", "हिन्दी", "עברית", "中文", 
			"አማርኛ", "മലയാളം", "ܐܬܘܪܝܐ", "მარგალური" };

	public void testString() throws Exception {
		for (String nonLatinText : nonLatinTexts) {
			String s = "{\"key\":\"" + nonLatinText + "\"}";
			JsonObject obj = (JsonObject) JsonFactory.get().parse(s);
			String v = (String) obj.get("key"); // result is incorrect
			assertEquals(v, nonLatinText);
		}
	}

	public void testReader() throws Exception {
		for (String nonLatinText : nonLatinTexts) {
			String s = "{\"key\":\"" + nonLatinText + "\"}";
			StringReader reader = new StringReader(s);
			JsonObject obj = (JsonObject) JsonFactory.get().parse(reader);

			String v = (String) obj.get("key"); // result is incorrect
			assertEquals(v, nonLatinText);
		}
	}
}
