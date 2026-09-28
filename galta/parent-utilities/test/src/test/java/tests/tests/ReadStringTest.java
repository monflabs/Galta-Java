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
package tests.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.monflabs.tests.UnitTestSupport;
import org.monflabs.tests.__BaseTestCase;

public class ReadStringTest extends __BaseTestCase {

	public void testMultiByteAcrossChunkBoundary() throws Exception {
		// 8191 ASCII bytes followed by a 3-byte character: the character straddles the
		// old 8K decode chunk and used to come back as U+FFFD
		StringBuilder b = new StringBuilder();
		for(int i=0; i<8191; i++) b.append('a');
		b.append('世').append("tail");
		String expected = b.toString();
		byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
		UnitTestSupport support = new UnitTestSupport(getClass());
		String actual = support.readString(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
		assertEquals(expected, actual);
		assertFalse(actual.contains("�"));
	}
}
