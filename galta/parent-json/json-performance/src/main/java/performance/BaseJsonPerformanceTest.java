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
package performance;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.performance.BasePerformanceTest;

import com.google.gson.JsonParser;

import performance.parsing.BaseJsonParsingPerformanceTest.JavaInternedJsonFactory;


public abstract class BaseJsonPerformanceTest extends BasePerformanceTest {
	
	public static enum JsonLibrary {
		JSON_JAVA,
		JSON_JAVA_INTERNED,
		GSON_NATIVE,
		JSONORG_NATIVE
	}

	private JsonLibrary jsonLib;

	public BaseJsonPerformanceTest(JsonLibrary jsonLib, String title) {
		super(title);
		this.jsonLib = jsonLib;
	}
	
	public JsonLibrary getJsonLibrary() {
		return jsonLib;
	}

	public static String readResourceString(String resourceName) throws IOException {
		try (InputStream is = getResourceAsStream(resourceName)) {
			Reader r = new InputStreamReader(is,StandardCharsets.UTF_8);
			StringBuilder b = new StringBuilder();
			char[] buf = new char[4096];
			int len;
			while( (len=r.read(buf, 0, buf.length)) >= 0) {
				b.append(buf,0,len);
			}
			return b.toString();
		}
	}

	public static InputStream getResourceAsStream(String resourceName) {
		return BaseJsonPerformanceTest.class.getClassLoader().getResourceAsStream(resourceName);
	}

	public Object readResourceAsJson(String resourceName) throws IOException {
		return parseJson(readResourceString(resourceName));
	}
	
	public Object parseJson(String json) {
		switch(getJsonLibrary()) {
			case JSON_JAVA:			return JavaJsonFactory.instance.parse(json);
			case JSON_JAVA_INTERNED:return JavaInternedJsonFactory.instance.parse(json);
			case GSON_NATIVE:		return JsonParser.parseString(json);
			case JSONORG_NATIVE:	return new JSONObject(json);
		}
		throw new IllegalStateException();	
	}
}
