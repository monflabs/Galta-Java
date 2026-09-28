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

import java.util.HashMap;
import java.util.Map;
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
		Map<String,Integer> hashes = new HashMap<>();
		
		for(int i=0; i<1_000_000; i++) {
			String s = randomString();
			String h = FilenameHash.hash(s, 4);
			Integer count = hashes.get(h);
			if(count==null) {
				count = 0;
			}
			count++;
			hashes.put(h, count);
		}
		int min=Integer.MAX_VALUE, max=-1;
		for(Integer v: hashes.values()) {
			min = Math.min(min,v);
			max = Math.max(max,v);
		}
		support.print("File hash distribution, min/max entries: {0}-{1}", min, max);
	}
	private static Random random = new Random();
	private static String randomString() {
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
