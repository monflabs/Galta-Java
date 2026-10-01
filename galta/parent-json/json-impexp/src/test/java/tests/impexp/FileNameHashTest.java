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

import java.util.Random;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.file.FilenameHash;

import tests.ProjectTestCase;

public class FileNameHashTest extends ProjectTestCase {

	public void testHashLength() throws Exception {
		String s1 = FilenameHash.hash("ABC", 1);
		assertEquals(2, s1.length());
		String s4 = FilenameHash.hash("ABC", 4);
		assertEquals(11, s4.length());
		try {
			FilenameHash.hash("ABC", 0);
			fail();
		} catch(JsonException e) {}
		// The hash is an int (4 bytes): more levels would only add constant 00/FF folders
		try {
			FilenameHash.hash("ABC", 5);
			fail();
		} catch(JsonException e) {}
	}

	public void testHash() throws Exception {
		assertEquals( "1A", FilenameHash.hash("ABC", 1) );
		assertEquals( "1A/07", FilenameHash.hash("ABC", 2) );
		assertEquals( "1A/07/10/00", FilenameHash.hash("ABC", 4) );

		assertEquals( "A7/93", FilenameHash.hash("doc1", 2) );
		assertEquals( "B7/93", FilenameHash.hash("doc2", 2) );
		assertEquals( "C7/93", FilenameHash.hash("doc3", 2) );
		assertEquals( "7F/5F", FilenameHash.hash("doc11", 2) );
		assertEquals( "8F/5F", FilenameHash.hash("doc12", 2) );

		assertEquals( "BC/EE", FilenameHash.hash("col1!!doc1", 2) );
		assertEquals( "CC/EE", FilenameHash.hash("col1!!doc2", 2) );
		assertEquals( "C0/A2", FilenameHash.hash("col2!!doc1", 2) );
		assertEquals( "D0/A2", FilenameHash.hash("col2!!doc2", 2) );
	}
	
	public void testDistribution() throws Exception {
		// The 256 first-level folders get a similar share of the keys
		Random random = new Random(42);
		int[] counts = new int[256];
		int n = 256*200;
		for(int i=0; i<n; i++) {
			String h = FilenameHash.hash(randomString(random), 1);
			counts[Integer.parseInt(h, 16)]++;
		}
		int min=Integer.MAX_VALUE, max=-1;
		for(int v: counts) {
			min = Math.min(min,v);
			max = Math.max(max,v);
		}
		// 200 keys expected per folder: a uniform hash stays well within these bounds
		assertTrue("min="+min, min>=100);
		assertTrue("max="+max, max<=300);
	}
	private static String randomString(Random random) {
	    int min = 'A';
	    int max = 'Z';
	    int targetStringLength = random.nextInt(10-3) + 3;
	    StringBuilder buffer = new StringBuilder(targetStringLength);
	    for (int i = 0; i < targetStringLength; i++) {
	        int ch = min + random.nextInt(max - min);
	        buffer.append((char)ch);
	    }
	    return buffer.toString();
	}
}
