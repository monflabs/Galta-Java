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
package performance.parsing;

import org.json.JSONObject;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.util.StringUtil;

import com.google.gson.JsonParser;

import performance.BaseJsonPerformanceTest;

public abstract class BaseJsonParsingPerformanceTest extends BaseJsonPerformanceTest {
	
	public static class JavaInternedJsonFactory extends JavaJsonFactory {
		
		public static final JavaInternedJsonFactory instance = new JavaInternedJsonFactory();
		
		@Override
		public Object parse(String json) {
			if(StringUtil.isEmpty(json)) {
				throw new JsonException(null);
			}
			try {
				org.monflabs.json.parser.JsonParser.StringParser parser = new org.monflabs.json.parser.JsonParser.StringParser(this);
				parser.setInternStrings(true);
				return parser.parse(json);
	        } catch(Throwable ex) {
	        	// Can be anything, like a stack overflow, like an array with "[[[[[[[[[...."
	        	if(ex instanceof JsonException jex) {
	        		throw jex;
	        	}
			    throw new JsonException(ex,"Error when parsing JSON string"); 
			}		
		}
	}

	public BaseJsonParsingPerformanceTest(JsonLibrary jsonLib, String title) {
		super(jsonLib,title);
	}

	
	public void benchmarkParseJson(String json, int iterations) {
		switch(getJsonLibrary()) {
			case JSON_JAVA:			parseJson(JavaJsonFactory.instance,json,iterations); break;
			case JSON_JAVA_INTERNED:parseJson(JavaInternedJsonFactory.instance,json,iterations); break;
			case GSON_NATIVE:		parseGsonNative(json,iterations); break;
			case JSONORG_NATIVE:	parseJsonOrgNative(json,iterations); break;
		}
	}
	
	private void parseJson(JsonFactory factory, String json, int iterations) {
		for(int i=0; i<iterations; i++) {
			doParseJson(factory,json);
		}
	}
	private void doParseJson(JsonFactory factory, String json) {
		factory.parse(json);
	}
	
	private void parseGsonNative(String json, int iterations) {
		for(int i=0; i<iterations; i++) {
			doParseGson(json);
		}
	}
	private void doParseGson(String json) {
		JsonParser.parseString(json);
	}
	
	private void parseJsonOrgNative(String json, int iterations) {
		for(int i=0; i<iterations; i++) {
			doParseJsonOrg(json);
		}
	}
	private void doParseJsonOrg(String json) {
		new JSONObject(json);
	}
}


