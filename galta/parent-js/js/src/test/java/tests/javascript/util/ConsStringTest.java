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
package tests.javascript.util;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.monflabs.galtajs.rt.util.strings.CharWrapper;
import org.monflabs.galtajs.rt.util.strings.ConsString;
import org.monflabs.galtajs.rt.util.strings.EmptyCharSequence;

import tests.javascript.JavaScriptStrictTestCase;

public class ConsStringTest extends JavaScriptStrictTestCase {
    
    private CharSequence hello;
    private CharSequence world;
    private CharSequence empty;
    private StringBuilder stringBuilder;
    
    @Override
	@Before
    public void setUp() throws Exception {
    	super.setUp();
        hello = "Hello";
        world = "World";
        empty = "";
        stringBuilder = new StringBuilder("Test");
    }
    
    
    // Factory Methods Tests
    
    public void testOfTwoCharSequences() {
        CharSequence result = ConsString.of(hello, world);
        
        assertEquals(10, result.length());
        assertEquals("HelloWorld", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testOfWithEmptyLeft() {
        CharSequence result = ConsString.of(empty, hello);
        
        assertEquals(5, result.length());
        assertEquals("Hello", result.toString());
        // Should return the non-empty sequence directly
        assertSame(hello, result);
    }
    
    public void testOfWithEmptyRight() {
        CharSequence result = ConsString.of(hello, empty);
        
        assertEquals(5, result.length());
        assertEquals("Hello", result.toString());
        // Should return the non-empty sequence directly
        assertSame(hello, result);
    }
    
    public void testOfBothEmpty() {
        CharSequence result = ConsString.of(empty, empty);
        
        assertEquals(0, result.length());
        assertEquals("", result.toString());
        assertTrue(result instanceof EmptyCharSequence);
    }
    
    public void testOfWithNulls() {
        CharSequence result1 = ConsString.of(null, hello);
        CharSequence result2 = ConsString.of(hello, null);
        CharSequence result3 = ConsString.of(null, null);
        
        assertSame(hello, result1);
        assertSame(hello, result2);
        assertTrue(result3 instanceof EmptyCharSequence);
    }
    
    public void testOfCharSequenceAndChar() {
        CharSequence result = ConsString.of(hello, '!');
        
        assertEquals(6, result.length());
        assertEquals("Hello!", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testOfCharAndCharSequence() {
        CharSequence result = ConsString.of('!', hello);
        
        assertEquals(6, result.length());
        assertEquals("!Hello", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testOfTwoChars() {
        CharSequence result = ConsString.of('A', 'B');
        
        assertEquals(2, result.length());
        assertEquals("AB", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testOfSingleCharSequence() {
        CharSequence result = hello;
        
        assertEquals(5, result.length());
        assertEquals("Hello", result.toString());
        // Should return the sequence directly
        assertSame(hello, result);
    }
    
    public void testOfSingleEmptySequence() {
        CharSequence result = empty;
        
        assertEquals(0, result.length());
        assertEquals("", result.toString());
        assertTrue(ConsString.of("",empty) instanceof EmptyCharSequence);
    }
    
    public void testOfSingleChar() {
        CharSequence result = ConsString.of('X');
        
        assertEquals(1, result.length());
        assertEquals("X", result.toString());
        assertTrue(result instanceof CharWrapper);
    }
    
    public void testEmptyFactory() {
        CharSequence result = ConsString.empty();
        
        assertEquals(0, result.length());
        assertEquals("", result.toString());
        assertTrue(result instanceof EmptyCharSequence);
    }
    
    public void testOfWithStringBuilder() {
        CharSequence result = ConsString.of(stringBuilder, hello);
        
        assertEquals(9, result.length());
        assertEquals("TestHello", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    // Instance Methods Tests
    
    public void testAppendCharSequence() {
        ConsString consString = (ConsString) ConsString.of(hello, world);
        CharSequence result = consString.append("!");
        
        assertEquals(11, result.length());
        assertEquals("HelloWorld!", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testAppendChar() {
        ConsString consString = (ConsString) ConsString.of(hello, world);
        CharSequence result = consString.append('!');
        
        assertEquals(11, result.length());
        assertEquals("HelloWorld!", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testPrependCharSequence() {
        ConsString consString = (ConsString) ConsString.of(hello, world);
        CharSequence result = consString.prepend("Say ");
        
        assertEquals(14, result.length());
        assertEquals("Say HelloWorld", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testPrependChar() {
        ConsString consString = (ConsString) ConsString.of(hello, world);
        CharSequence result = consString.prepend('!');
        
        assertEquals(11, result.length());
        assertEquals("!HelloWorld", result.toString());
        assertTrue(result instanceof ConsString);
    }
    
    public void testIsEmptyFalse() {
    	CharSequence consString = ConsString.of(hello, world);
        assertFalse(consString.isEmpty());
    }
    
    public void testIsEmptyTrue() {
    	CharSequence emptyConsString = ConsString.of("", "");
        assertTrue(emptyConsString.isEmpty());
    }
    
    // CharSequence Interface Tests
    
    public void testLength() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertEquals(11, consString.length());
    }
    
    public void testCharAtValidIndices() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        
        assertEquals('H', consString.charAt(0));
        assertEquals('e', consString.charAt(1));
        assertEquals('l', consString.charAt(2));
        assertEquals('o', consString.charAt(4));
        assertEquals(' ', consString.charAt(5));
        assertEquals('W', consString.charAt(6));
        assertEquals('d', consString.charAt(10));
    }
    
    public void testCharAtNegativeIndex() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertThrows(StringIndexOutOfBoundsException.class, () -> consString.charAt(-1));
    }
    
    public void testCharAtTooLargeIndex() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertThrows(StringIndexOutOfBoundsException.class, () -> consString.charAt(11));
    }
    
    public void testCharAtWayTooLargeIndex() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertThrows(StringIndexOutOfBoundsException.class, () -> consString.charAt(100));
    }
    
    public void testSubSequenceValid() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        
        CharSequence sub1 = consString.subSequence(0, 5);
        assertEquals("Hello", sub1.toString());
        assertEquals(5, sub1.length());
        
        CharSequence sub2 = consString.subSequence(6, 11);
        assertEquals("World", sub2.toString());
        assertEquals(5, sub2.length());
        
        CharSequence sub3 = consString.subSequence(2, 8);
        assertEquals("llo Wo", sub3.toString());
        assertEquals(6, sub3.length());
    }
    
    public void testSubSequenceEmpty() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        CharSequence sub = consString.subSequence(5, 5);
        assertEquals(0, sub.length());
        assertEquals("", sub.toString());
        assertTrue(sub instanceof EmptyCharSequence);
    }
    
    public void testSubSequenceFull() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        CharSequence sub = consString.subSequence(0, 11);
        assertSame(consString, sub);
    }
    
    public void testSubSequenceNegativeStart() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertThrows(StringIndexOutOfBoundsException.class, () -> consString.subSequence(-1, 5));
    }
    
    public void testSubSequenceEndTooLarge() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertThrows(StringIndexOutOfBoundsException.class, () -> consString.subSequence(0, 12));
    }
    
    public void testSubSequenceStartAfterEnd() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertThrows(StringIndexOutOfBoundsException.class, () -> consString.subSequence(5, 3));
    }
    
    public void testToString() {
        ConsString consString = (ConsString) ConsString.of("Hello", " World");
        assertEquals("Hello World", consString.toString());
    }
    
    // Equals and HashCode Tests
    
    public void testEqualsSameInstance() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        assertEquals(consString1, consString1);
    }
    
    public void testEqualsEquivalentConsStrings() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        ConsString consString2 = (ConsString) ConsString.of("Hello ", "World");
        
        assertEquals(consString1, consString2);
        assertEquals(consString2, consString1);
    }
    
    public void testEqualsDifferentConsStrings() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        ConsString consString3 = (ConsString) ConsString.of("Different", " String");
        
        assertNotEquals(consString1, consString3);
        assertNotEquals(consString3, consString1);
    }
    
    public void testEqualsConsStringVsString() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        String str = "Hello World";
        assertEquals(consString1, str);
    }
    
    public void testEqualsConsStringVsStringBuilder() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        StringBuilder sb = new StringBuilder("Hello World");
        assertEquals(consString1, sb);
    }
    
    public void testEqualsDifferentLengths() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        String shorter = "Hello";
        assertNotEquals(consString1, shorter);
    }
    
    public void testEqualsNull() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        assertNotEquals(consString1, null);
    }
    
    public void testHashCodeConsistent() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        int hash1 = consString1.hashCode();
        int hash2 = consString1.hashCode();
        assertEquals(hash1, hash2);
    }
    
    public void testHashCodeEquality() {
        ConsString consString1 = (ConsString) ConsString.of("Hello", " World");
        ConsString consString2 = (ConsString) ConsString.of("Hello ", "World");
        assertEquals(consString1.hashCode(), consString2.hashCode());
    }
    
    // Edge Cases and Performance Tests
    
    public void testLongChain() {
        CharSequence result = "Start";
        
        for (int i = 0; i < 100; i++) {
            result = ConsString.of(result, " " + i);
        }
        
        int len = "Start".length() + 10 * 2 + 90 *3;
        assertEquals(len, result.length());
        assertTrue(result.toString().startsWith("Start 0 1 2"));
        assertTrue(result.toString().endsWith("97 98 99"));
        assertTrue(result.toString()==result.toString()); // Check caching
    }
    
    public void testDeepNesting() {
        CharSequence result = "0";
        
        // Create a very deep tree
        for (int i = 1; i < 2000; i++) {
            result = ConsString.of(result, String.valueOf(i));
        }
        
        // This should not throw StackOverflowError
        String flattened = result.toString();
        assertTrue(flattened.startsWith("01234"));
        assertTrue(flattened.contains("100"));
    }
    
    public void testEmptyConsStringOperations() {
    	CharSequence empty = ConsString.empty();
        
        assertEquals(0, empty.length());
        assertTrue(empty.isEmpty());
        assertEquals("", empty.toString());
    }
    
    public void testEmptyConsStringCharAt() {
    	CharSequence empty = ConsString.empty();
        assertThrows(StringIndexOutOfBoundsException.class, () -> empty.charAt(0));
    }
    
    public void testEmptyConsStringSubSequence() {
    	CharSequence empty =ConsString.empty();
        CharSequence subEmpty = empty.subSequence(0, 0);
        assertTrue(subEmpty instanceof EmptyCharSequence);
    }
    
    public void testSingleCharOperations() {
        CharWrapper single = (CharWrapper) ConsString.of('X');
        
        assertEquals(1, single.length());
        assertEquals('X', single.charAt(0));
        assertEquals("X", single.toString());
        assertTrue(single instanceof CharWrapper);
    }
    
    public void testMixedCharSequenceTypes() {
        StringBuilder sb = new StringBuilder("StringBuilder");
        StringBuffer buf = new StringBuffer("StringBuffer");
        String str = "String";
        
        ConsString mixed = (ConsString) ConsString.of(sb, ConsString.of(buf, str));
        assertEquals("StringBuilderStringBufferString", mixed.toString());
    }
    
    public void testDepth() {
        ConsString simple = (ConsString) ConsString.of("A", "B");
        assertEquals(1, simple.depth());
        
        ConsString nested = (ConsString) ConsString.of(simple, simple);
        assertEquals(2, nested.depth());
        
        ConsString deepNested = (ConsString) ConsString.of(nested, nested);
        assertEquals(3, deepNested.depth());
    }
    
    public void testFlattenIdempotence() {
        ConsString cs = (ConsString) ConsString.of("Hello", " World");
        
        String first = cs.flatten();
        String second = cs.flatten();
        
        assertSame(first, second); // Should return cached result
        assertEquals("Hello World", first);
    }
    
    // EmptyCharSequence Tests
    
    public void testEmptyCharSequenceSingleton() {
        CharSequence empty1 = ConsString.empty();
        CharSequence empty2 = ConsString.of("", "");
        
        assertSame(empty1, empty2);
        assertTrue(empty1 instanceof EmptyCharSequence);
    }
    
    public void testEmptyCharSequenceOperations() {
        EmptyCharSequence empty = EmptyCharSequence.INSTANCE;
        
        assertEquals(0, empty.length());
        assertEquals("", empty.toString());
        assertEquals(0, empty.hashCode());
        
        CharSequence sub = empty.subSequence(0, 0);
        assertSame(empty, sub);
    }
    
    public void testEmptyCharSequenceCharAt() {
        EmptyCharSequence empty = EmptyCharSequence.INSTANCE;
        assertThrows(StringIndexOutOfBoundsException.class, () -> empty.charAt(0));
    }
    
    public void testEmptyCharSequenceSubSequenceInvalid() {
        EmptyCharSequence empty = EmptyCharSequence.INSTANCE;
        assertThrows(StringIndexOutOfBoundsException.class, () -> empty.subSequence(0, 1));
    }
    
    public void testEmptyCharSequenceEquals() {
        EmptyCharSequence empty = EmptyCharSequence.INSTANCE;
        
        assertTrue(empty.equals(""));
        assertTrue(empty.equals(new StringBuilder()));
        assertFalse(empty.equals("non-empty"));
        assertFalse(empty.equals(null));
    }
    
    // CharWrapper Tests
    
    public void testCharWrapperBasicOperations() {
        CharWrapper wrapper = new CharWrapper('X');
        
        assertEquals(1, wrapper.length());
        assertEquals('X', wrapper.charAt(0));
        assertEquals('X', wrapper.getChar());
        assertEquals("X", wrapper.toString());
    }
    
    public void testCharWrapperCharAtInvalidIndex() {
        CharWrapper wrapper = new CharWrapper('X');
        assertThrows(StringIndexOutOfBoundsException.class, () -> wrapper.charAt(1));
    }
    
    public void testCharWrapperCharAtNegativeIndex() {
        CharWrapper wrapper = new CharWrapper('X');
        assertThrows(StringIndexOutOfBoundsException.class, () -> wrapper.charAt(-1));
    }
    
    public void testCharWrapperSubSequence() {
        CharWrapper wrapper = new CharWrapper('X');
        
        CharSequence full = wrapper.subSequence(0, 1);
        assertSame(wrapper, full);
        
        CharSequence empty1 = wrapper.subSequence(0, 0);
        assertTrue(empty1 instanceof EmptyCharSequence);
        
        CharSequence empty2 = wrapper.subSequence(1, 1);
        assertTrue(empty2 instanceof EmptyCharSequence);
    }
    
    public void testCharWrapperSubSequenceInvalid() {
        CharWrapper wrapper = new CharWrapper('X');
        assertThrows(StringIndexOutOfBoundsException.class, () -> wrapper.subSequence(0, 2));
    }
    
    public void testCharWrapperEqualsAndHashCode() {
        CharWrapper wrapper1 = new CharWrapper('X');
        CharWrapper wrapper2 = new CharWrapper('X');
        CharWrapper wrapper3 = new CharWrapper('Y');
        
        assertEquals(wrapper1, wrapper2);
        assertNotEquals(wrapper1, wrapper3);
        
        assertEquals(wrapper1.hashCode(), wrapper2.hashCode());
        
        // Test equals with other CharSequence types
        assertTrue(wrapper1.equals("X"));
        assertFalse(wrapper1.equals("Y"));
        assertFalse(wrapper1.equals("XX"));
    }
}
