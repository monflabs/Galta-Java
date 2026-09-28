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
package tests.io;

import org.monflabs.util.io.LRUCharBuffer;

import tests.ProjectTestCase;

public class LRUCharBufferTest extends ProjectTestCase {
	
    public void testBuffer() throws Exception {
    	LRUCharBuffer b = new LRUCharBuffer.MemoryCharBuffer(20,8);
    	
    	assertEquals( 20, b.capacity() );
    	assertEquals( 8, b.capacityOffset() );
    	
    	assertEquals( 0, b.bufferSize() );
    	
    	b.write("123");
    	assertEquals( 3, b.bufferSize() );
    	assertEquals( "123", b.toString() );
    	
    	b.write("45");
    	assertEquals( 5, b.bufferSize() );
    	assertEquals( "12345", b.toString() );
    	
    	b.write("6789012345");
    	assertEquals( 15, b.bufferSize() );
    	assertEquals( "123456789012345", b.toString() );
    	
    	b.write("6789");
    	assertEquals( 19, b.bufferSize() );
    	assertEquals( "1234567890123456789", b.toString() );
    	
    	// Exceed available capacity
    	b.write("123");
    	assertEquals( 20-8, b.bufferSize() );
    	assertEquals( "123456789123", b.toString() );
    	
    	b.write("0123456789");
    	assertEquals( 20-8, b.bufferSize() );
    	assertEquals( "230123456789", b.toString() );
    	
    	// Exceeds max buffer size
    	b.write("01234567890123456789012345");
    	assertEquals( 20-8, b.bufferSize() );
    	assertEquals( "456789012345", b.toString() );
    }
}
