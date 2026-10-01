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
package org.monflabs.util;

public class StringFormat {

    public static final String format( String fmt, Object... parameters ) {
        if( fmt!=null ) {
        	return format(new StringBuilder(fmt.length()+64), fmt, parameters).toString();
        }
        return "";
    }
    public static final StringBuilder format( StringBuilder buffer, String fmt, Object... parameters ) {
        if( fmt!=null ) {
	        int fmtLength = fmt.length();
	        for( int i=0; i<fmtLength; ) {
	            char c = fmt.charAt(i++);
	            switch(c) {
	                case '{': {
	                	boolean ok = false;
	                	boolean closed = false;
	                    int idx = 0; int j=i;
	                    next: while( j<fmtLength ) {
	                        char d = fmt.charAt(j++);
	                        if(d>='0' && d<='9') {
	                        	ok = true;
	                        	// Saturate instead of wrapping around: a huge index is simply out of range
	                        	idx = idx>(Integer.MAX_VALUE-9)/10 ? Integer.MAX_VALUE : idx*10 + (d-'0');
	                        } else if(d=='}') {
	                        	closed = true;
	                        	break next;
	                        } else {
	                        	ok = false;
	                        	break next;
	                        }
	                    }
	                    // An unterminated "{0" (end of string before '}') is literal text
	                    // A placeholder without a matching parameter is kept as is, like
	                    // MessageFormat does (it used to vanish)
	                    if(ok && closed && parameters!=null && idx<parameters.length) {
	                    	buffer.append( parameters[idx] );
	                    	i = j;
	                    	break;
	                    }
	                }
	                default: {
	                    buffer.append(c);
	                }
	            }
	        }
        }
        return buffer;
    }
}
