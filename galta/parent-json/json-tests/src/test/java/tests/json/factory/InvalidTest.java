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

import org.monflabs.json.JsonFactory;

import tests.ProjectTestCase;

public class InvalidTest extends ProjectTestCase {
	
	public void testS0() throws Exception {
		try {
			JsonFactory.get().parse("{\"1\":\"one\"\n\"2\":\"two\"}");
			fail();
		} catch(Exception e) {
			// Expected
		}
 	}

	public void testS1() throws Exception {
		String s = "{\"key\":{}";
		try {
			JsonFactory.get().parse(s);
			fail();
		} catch(Exception e) {
			// Expected
		}
	}

	public void testS2() throws Exception {
		String s = "{\"key\":";
		try {
			JsonFactory.get().parse(s);
			fail();
		} catch(Exception e) {
			// Expected
		}
	}

	public void testS3() throws Exception {
		String s = "{\"key\":123";
		try {
			JsonFactory.get().parse(s);
			fail();
		} catch(Exception e) {
			// Expected
		}
	}

	public void testjunkTaillingData() throws Exception {
		String s = "{\"t\":124}$ifsisg045";
		try {
			JsonFactory.get().parse(s);
			fail();
		} catch(Exception e) {
			// Expected
		}
	}
}
