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
package tests.impexp;

import org.monflabs.json.impexp.JsonKey;

import tests.ProjectTestCase;

public class JsonKeyTest extends ProjectTestCase {

	public void testCreateCollectionId() throws Exception {
		JsonKey k;
		
		k = JsonKey.of(null,null);
		assertEquals("", k.keyString());
		assertEquals("", k.getCollection());
		assertEquals("", k.getId());

		k = JsonKey.of("","");
		assertEquals("", k.keyString());
		assertEquals("", k.getCollection());
		assertEquals("", k.getId());

		k = JsonKey.of("","abc");
		assertEquals("abc", k.keyString());
		assertEquals("", k.getCollection());
		assertEquals("abc", k.getId());

		k = JsonKey.of(null,"abc");
		assertEquals("abc", k.keyString());
		assertEquals("", k.getCollection());
		assertEquals("abc", k.getId());

		k = JsonKey.of("co","");
		assertEquals("co!!", k.keyString());
		assertEquals("co", k.getCollection());
		assertEquals("", k.getId());

		k = JsonKey.of("co",null);
		assertEquals("co!!", k.keyString());
		assertEquals("co", k.getCollection());
		assertEquals("", k.getId());

		k = JsonKey.of("co","abc");
		assertEquals("co!!abc", k.keyString());
		assertEquals("co", k.getCollection());
		assertEquals("abc", k.getId());
	}
}
