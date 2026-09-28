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
package org.monflabs.galtajs.rt.util.strings;

import java.util.ArrayDeque;
import java.util.Deque;

// Enabled via: JSConfiguration.ENABLE_CONSSTRING
public class ConsString implements ConsSequence {
	
    // Below this depth, charAt()'s per-call recursive tree walk (see
    // getCharAtIndex()) is cheap enough to leave alone; above it, a single
    // O(n) flatten (memoized, same as toString()/equals()/hashCode() already
    // do) is worth paying once so every FURTHER charAt() call on this same
    // instance is O(1) instead of repeating an O(depth) walk - without this,
    // a string built via `s = s + x` in a loop (a left-leaning tree of depth
    // N) makes iterating it via repeated charAt() O(N^2).
    private static final int FLATTEN_DEPTH_THRESHOLD = 32;

    private final CharSequence left;
    private final CharSequence right;
    private final int length;
    // Computed once at construction from the immediate children's OWN
    // already-known depth (0 for a non-ConsString leaf) rather than a
    // recursive walk - see depthOf() - so building a new node via of()
    // stays O(1), exactly like `length` above, instead of re-walking the
    // whole tree on every concatenation just to know how deep it now is.
    private final int depth;
    private String flatString;

    // Private constructor for internal use
    private ConsString(CharSequence left, CharSequence right) {
        this.left = left;
        this.right = right;
        this.length = this.left.length() + this.right.length();
        this.depth = 1 + Math.max(depthOf(left), depthOf(right));
        this.flatString = null;
    }
    private static int depthOf(CharSequence seq) {
        return seq instanceof ConsString cs ? cs.depth : 0;
    }

    
    /**
     * Create a CharSequence from two CharSequences
     */
    public static CharSequence of(CharSequence left, CharSequence right) {
        // Handle empty cases
        if (isEmpty(left)) {
            if (isEmpty(right)) {
            	return EmptyCharSequence.INSTANCE;
            }
            return right;
        } else if (isEmpty(right)) {
            return left;
        }

        return new ConsString(left, right);
    }
    private static final boolean isEmpty(CharSequence seq) {
        return seq == null || seq.length() == 0;
    }
    
    /**
     * Create a CharSequence from a CharSequence and a character
     */
    public static CharSequence of(CharSequence seq, char c) {
        return of(seq, new CharWrapper(c));
    }
    
    /**
     * Create a CharSequence from a character and a CharSequence
     */
    public static CharSequence of(char c, CharSequence seq) {
        return of(new CharWrapper(c), seq);
    }
    
    /**
     * Create a CharSequence from two characters
     */
    public static CharSequence of(char left, char right) {
        return of(new CharWrapper(left), new CharWrapper(right));
    }
        
    /**
     * Create a CharSequence from a single character
     */
    public static CharSequence of(char c) {
        return new CharWrapper(c);
    }
    
    /**
     * Create an empty CharSequence
     */
    public static CharSequence empty() {
        return EmptyCharSequence.INSTANCE;
    }

    
    /**
     * Append a CharSequence to this ConsString
     */
    public CharSequence append(CharSequence seq) {
        return ConsString.of(this, seq);
    }
    
    /**
     * Append a character to this ConsString
     */
    public CharSequence append(char c) {
        return ConsString.of(this, new CharWrapper(c));
    }
    
    /**
     * Prepend a CharSequence to this ConsString
     */
    public CharSequence prepend(CharSequence seq) {
        return ConsString.of(seq, this);
    }
    
    /**
     * Prepend a character to this ConsString
     */
    public CharSequence prepend(char c) {
        return ConsString.of(new CharWrapper(c), this);
    }
    
    /**
     * Get the length of the ConsString (CharSequence interface)
     */
    @Override
    public int length() {
        return length;
    }
    
    /**
     * Check if the ConsString is empty
     */
    @Override
	public boolean isEmpty() {
        return length == 0;
    }
    
    
    /**
     * Get character at specific index (CharSequence interface)
     */
    @Override
    public char charAt(int index) {
        if (index < 0 || index >= length) {
            throw new StringIndexOutOfBoundsException(index);
        }
        if (flatString != null) {
            // Already flattened (via a prior toString()/equals()/hashCode()/
            // deep charAt() below) - reuse it instead of re-walking the tree.
            return flatString.charAt(index);
        }
        if (depth > FLATTEN_DEPTH_THRESHOLD) {
            return flatten().charAt(index);
        }
        // Shallow enough that the recursive walk below is cheap - flattening
        // here would cost O(n) for a single character read that doesn't need it.
        return getCharAtIndex(index);
    }
    
    /**
     * Get subsequence (CharSequence interface)
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || end > length || start > end) {
            throw new StringIndexOutOfBoundsException("start=" + start + ", end=" + end + ", length=" + length);
        }
        if (start == end) {
            return EmptyCharSequence.INSTANCE;
        }
        if (start == 0 && end == length) {
            return this;
        }
        
        // For subsequences, we flatten and return a new ConsString
        String flattened = flatten();
        String sub = flattened.substring(start, end);
        return sub.isEmpty() ? EmptyCharSequence.INSTANCE : sub;
    }
    
    /**
     * Efficiently get character at index without full flattening when possible
     */
    private char getCharAtIndex(int index) {
        int leftLength = left.length();
        
        if (index < leftLength) {
            // Character is in left side
            return left.charAt(index);
        } else {
            // Character is in right side
            return right.charAt(index - leftLength);
        }
    }
    
    /**
     * Flatten the ConsString tree into a regular String
     * This is where the actual concatenation happens
     */
    public String flatten() {
        if (flatString == null) {
            flatString = buildString();
        }
        return flatString;
    }
    
    private String buildString() {
        if (length == 0) {
            return "";
        }

        char[] chars = new char[length];
        int offset = 0;

        // Optimization for simple a+b expressions
        if(!(left instanceof ConsString) && !(right instanceof ConsString)) {
        	offset = appendCharSequenceToArray(chars,0,left);
        	appendCharSequenceToArray(chars,offset,right);
		} else {
	        CharSequence current = this;
	        Deque<CharSequence> stack = new ArrayDeque<>(64);
	        
	        while (current != null) {
 	            while (current != null) {
	                if (current instanceof ConsString) {
	                    ConsString cons = (ConsString) current;
	                    if (cons.right != null) {
	                        stack.push(cons.right);
	                    }
	                    current = cons.left;
	                } else {
	                	offset = appendCharSequenceToArray(chars,offset,current);
	                    current = null;
	                }
	            }
	            
	            // Process next item from stack
	            if (!stack.isEmpty()) {
	                current = stack.pop();
	            }
	        }
	    }
        
        return new String(chars);
    }
    private int appendCharSequenceToArray(char[] chars, int offset, CharSequence seq) {
        if (seq instanceof EmptyCharSequence) {
            return offset; 
        } else if (seq instanceof CharWrapper) {
            chars[offset] = ((CharWrapper) seq).getChar();
            return offset + 1;
        } else if (seq instanceof String str) {
            str.getChars(0, str.length(), chars, offset);
            return offset + str.length();
        } else {
            for (int i = 0; i < seq.length(); i++) {
                chars[offset + i] = seq.charAt(i);
            }
            return offset + seq.length();
        }
    }
    
    /**
     * Convert to String - this flattens the ConsString
     */
    @Override
    public String toString() {
        return flatten();
    }
    
    @Override
    public boolean equals(Object obj) {
    	if(obj instanceof CharSequence cs) {
    		if(cs.length()!=length) {
    			// Cheap (length is a cached field on both a ConsString and a
    			// plain String) - avoids flattening either side just to
    			// discover they can't possibly be equal.
    			return false;
    		}
    		obj = cs.toString();
    	}
        return flatten().equals(obj);
    }
    
    @Override
    public int hashCode() {
        return flatten().hashCode();
    }
    
    /**
     * Get the depth of the ConsString tree (for debugging/optimization)
     */
    public int depth() {
        return depth;
    }
}
