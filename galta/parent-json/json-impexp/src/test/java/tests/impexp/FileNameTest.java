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

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.util.FileNameUtil;

import tests.ProjectTestCase;

public class FileNameTest extends ProjectTestCase {

	public void testEncode() throws Exception {
		check("", "");
		check("a", "a");
		check("abc", "abc");
		check("%2A", "*");
		check("abc%2A", "abc*");
		check("%2Aabc", "*abc");
		check("abc%2Ad", "abc*d");
		check("%2A%25%3C%3E%3A%22%2F%5C%7C%3F", "*%<>:\"/\\|?");
		check("ã%3FŠç–²ã‚Œæ§˜ã%3F§ã%3F™.txt", "ã?Šç–²ã‚Œæ§˜ã?§ã?™.txt");
		// '@' marks a collection folder, so it is encoded in ids and collection names
		check("%40abc", "@abc");
		check("a%40b", "a@b");
	}

	public void testCollectionFolder() throws Exception {
		assertEquals("@c", FileNameUtil.encodeCollectionFolder("c"));
		assertEquals("@a%2Fb", FileNameUtil.encodeCollectionFolder("a/b"));
		assertEquals("@..%2F..%2Fevil", FileNameUtil.encodeCollectionFolder("../../evil"));
		assertEquals("a/b", FileNameUtil.decodeCollectionFolder("@a%2Fb"));
		assertNull(FileNameUtil.decodeCollectionFolder("ab"));

		assertEquals("c", FileNameUtil.collectionFromZipPath("@c/k.json"));
		assertEquals("a/b", FileNameUtil.collectionFromZipPath("@a%2Fb/1A/k.json"));
		assertNull(FileNameUtil.collectionFromZipPath("1A/k.json"));
		// A root level entry is never in a collection, even if its name starts with '@'
		assertNull(FileNameUtil.collectionFromZipPath("@abc.json"));
	}

	// C7: a truncated escape sequence is a JsonException, not a StringIndexOutOfBoundsException
	public void testDecodeTruncatedEscape() throws Exception {
		checkInvalid("%");
		checkInvalid("abc%");
		checkInvalid("%A");
		checkInvalid("abc%A");
		checkInvalid("abc%2Ad%");
		checkInvalid("%ZZ");
	}
	private void checkInvalid(String encoded) {
		try {
			FileNameUtil.decodeFilename(encoded);
			fail("Decoding "+encoded+" should fail");
		} catch(JsonException ex) {
			// Expected
		}
	}

	public void check(String encoded, String decoded) throws Exception {
		assertEquals(encoded, FileNameUtil.encodeFilename(decoded));
		assertEquals(decoded, FileNameUtil.decodeFilename(encoded));		
	}
}
