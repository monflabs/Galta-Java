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
package tests.json.arrayproto;

import org.monflabs.json.JsonArray;

import tests.ProjectTestCase;

public class JoinTest extends ProjectTestCase {
		
	public void testJoin() throws Exception {
		assertEquals("", JsonArray.of().join('/'));
		assertEquals("John", JsonArray.of("John").join('/'));
		assertEquals("John/Doe", JsonArray.of("John","Doe").join('/'));
		assertEquals("John/Doe/Boston", JsonArray.of("John","Doe","Boston").join('/'));
	}	
}
