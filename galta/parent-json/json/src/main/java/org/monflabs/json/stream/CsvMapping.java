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
package org.monflabs.json.stream;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.Function;

/**
 * CSV &lt;-&gt; Json data converters for streams.
 * 
 * Inspired from:
 *   https://github.com/osiegmar/FastCSV/blob/master/src/main/java/de/siegmar/fastcsv/writer/CsvWriter.java
 * MIT license
 */
public class CsvMapping {

	public enum QuoteStrategy {
	    /**
	     * Delimits only text fields that requires it. Simple strings (not containing delimiters,
	     * field separators, new line or carriage return characters), empty strings and
	     * {@code null} fields will not be delimited.
	     */
	    REQUIRED,
	    /**
	     * In addition to fields that require delimiting also delimit empty text fields to
	     * differentiate between empty and {@code null} fields.
	     * This is required for PostgreSQL CSV imports for example.
	     */
	    EMPTY,
	    /**
	     * Delimits any text field regardless of its content (even empty and {@code null} fields).
	     */
	    ALWAYS
	}
	
    //<R> Stream<R> map(Function<? super T, ? extends R> mapper);

	
	public static Function<Object, String> toCsvStrings() {
		return toCsvStrings(',',QuoteStrategy.REQUIRED);
	}
	/**
	 * A function converting a row (a List or an array) to a CSV line.
	 * The function is stateless and can be used by a parallel stream.
	 */
	public static Function<Object, String> toCsvStrings(char fieldSeparator, QuoteStrategy quoteStrategy) {
		// A new encoder per row: its buffers must not be shared between threads
		return row -> new CsvStringHandler(fieldSeparator,quoteStrategy).toCsvString(row);
	}
	
	// https://en.wikipedia.org/wiki/Comma-separated_values
	private static class CsvStringHandler {

		private QuoteStrategy quoteStrategy;
		private char fieldSeparator;
		
		private int ptr;
		private char[] buffer = new char[512];
		private char[] valueBuffer = new char[128];
		
		CsvStringHandler(char fieldSeparator, QuoteStrategy quoteStrategy) {
			this.fieldSeparator = fieldSeparator;
			this.quoteStrategy = quoteStrategy;
		}
		
		private void ensureCapacity(int cap) {
			if(cap>(buffer.length-ptr)) {
				char[] newArray = new char[Math.max(buffer.length*2, ptr+cap+256)];
				System.arraycopy(buffer, 0, newArray, 0, ptr);
				buffer = newArray;
			}
		}
		
		private void ensureValueBuffer(int size) {
			if(size>valueBuffer.length) {
				// Nothing to preserve: the caller overwrites the value buffer right after
				// ("ptr" is a position in the OUTPUT buffer, copying that many chars overflowed)
				valueBuffer = new char[Math.max(valueBuffer.length*2, size+64)];
			}
		}
		
		private String toCsvString(Object row) {
			if(row!=null) {
				this.ptr = 0;
				if(row instanceof List<?> list) {
					int al = list.size();
					for(int i=0; i<al; i++) {
						if(i>0) {
			            	ensureCapacity(1);
			            	buffer[ptr++] = fieldSeparator;
						}
						writeCell(list.get(i));
					}
					return String.valueOf(buffer, 0, ptr);
				}
				if(row.getClass().isArray()) {
					int al = Array.getLength(row);
					for(int i=0; i<al; i++) {
						if(i>0) {
			            	ensureCapacity(1);
			            	buffer[ptr++] = fieldSeparator;
						}
						writeCell(Array.get(row, i));
					}
					return String.valueOf(buffer, 0, ptr);
				}
			}
			return "";
		}

		// Returns -1 when the value needs no quoting, else the number of double quotes
		// it contains (each is doubled, so this is the extra room needed)
		private int countCharsBeEncoded(int length) {
			int quotes = 0;
			boolean quoting = false;
			char[] ca = valueBuffer;
			for(int i=0; i<length; i++) {
				char c = ca[i];
				if(c=='\"') {
					quotes++;
					quoting = true;
				} else if(c==fieldSeparator || c=='\n' || c=='\r') {
					quoting = true;
				}
			}
			if(!quoting) {
				if(ca[0]==' ' || ca[0]=='\t' || ca[length-1]==' ' || ca[length-1]=='\t') {
					quoting = true;
				}
			}
			return quoting ? quotes : -1;
		}
		private void writeCell(final Object value) {
	        if (value == null) {
	            if (quoteStrategy == QuoteStrategy.ALWAYS) {
	            	ensureCapacity(2);
	            	buffer[ptr++] = '\"';
	            	buffer[ptr++] = '\"';
	            }
	            return;
	        }
	        
	        // We convert the non string values to a string
	        // Note that even Numbers have to be checked for encoding as they can contain a ',' as the decimal separator
        	String str = value.toString();
        	int length = str.length();

        	if(length==0) {
	            if (quoteStrategy == QuoteStrategy.ALWAYS || quoteStrategy == QuoteStrategy.EMPTY) {
	            	ensureCapacity(2);
	            	buffer[ptr++] = '\"';
	            	buffer[ptr++] = '\"';
	            }
	            return;
	        }
        	
        	ensureValueBuffer(length);
        	str.getChars(0, length, valueBuffer, 0);

		    int quotes = countCharsBeEncoded(length);
		    if(quotes>=0 || quoteStrategy == QuoteStrategy.ALWAYS) {
		    	// Quote the field and double every embedded double quote (RFC 4180)
            	ensureCapacity(length+Math.max(quotes,0)+2);
            	buffer[ptr++] = '\"';
            	for(int i=0; i<length; i++) {
                	if( (buffer[ptr++] = valueBuffer[i])=='\"' ) {
                    	buffer[ptr++] = '\"';
                	}
            	}
            	buffer[ptr++] = '\"';            	
		    } else {
            	ensureCapacity(length);
	            System.arraycopy(valueBuffer, 0, buffer, ptr, length);
	            ptr += length;
		    }
	    }
	};
    
	private CsvMapping() {}

}
