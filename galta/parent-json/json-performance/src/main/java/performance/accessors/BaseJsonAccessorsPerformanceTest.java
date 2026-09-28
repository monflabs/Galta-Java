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
package performance.accessors;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.java.JavaJsonFactory;

import performance.BaseJsonPerformanceTest;
import performance.parsing.BaseJsonParsingPerformanceTest.JavaInternedJsonFactory;

public abstract class BaseJsonAccessorsPerformanceTest extends BaseJsonPerformanceTest {
	
	public BaseJsonAccessorsPerformanceTest(JsonLibrary jsonLib, String title) {
		super(jsonLib,title);
	}
	
	public void runBenchmark(int iterations) {
		for(int i=0; i<iterations; i++) {
			switch(getJsonLibrary()) {
				case JSON_JAVA:			accessJava(JavaJsonFactory.instance); break;
				case JSON_JAVA_INTERNED:accessJava(JavaInternedJsonFactory.instance); break;
				case GSON_NATIVE:		accessGson(); break;
				case JSONORG_NATIVE:	accessJsonOrg(); break;
			}
		}
	}

	protected abstract void accessJava(JsonFactory factory);
	protected abstract void accessGson();
	protected abstract void accessJsonOrg();
}


