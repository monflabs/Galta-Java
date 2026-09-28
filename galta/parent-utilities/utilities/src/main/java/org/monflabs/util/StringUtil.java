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

import java.lang.reflect.Array;

/**
 * 
 */ 
public class StringUtil {
	
    public static final String[] EMPTY_STRING_ARRAY = new String[0];


    public static final boolean equalsIgnoreCase( String s1, String s2 ) {
        if( s1==null || s2==null ) {
            return isEmpty(s1)==isEmpty(s2);
        }
        return s1.equalsIgnoreCase(s2);
    }
    

    public static String concatStrings( String[] strings, char sep, boolean trim ) {
        int count = strings.length;
        if( count==0 ) {
            return "";
        }
        if( count==1 ) {
            return trim ? trim(strings[0]) : strings[0];
        }

        StringBuilder b = new StringBuilder();

        for( int i=0; i<count; i++ ) {
            if( i>0 && sep!=0 ) {
                b.append(sep);
            }
            b.append( trim ? trim(strings[i]) : strings[i] );
        }
        return b.toString();
    }

    public static final String trim(String s) {
        // Call the String method which more efficient
        if (s!=null) {
            return s.trim();
        } else {
            return null;
        }
    }

    public static final String trimLeft(String s) {
        // Call the String method which more efficient
        if (s!=null) {
            int sz = s.length();
            int st = 0;
            while( (st<sz) && Character.isWhitespace(s.charAt(st)) ) {
            	st++;
            }
            if(st>0) {
            	return s.substring(st, sz);
            }
            return s;
        } else {
            return null;
        }
    }
    
    public static boolean containsIgnoreCase(String str, String searchStr) {
        return indexIgnoreCase(str, searchStr)>=0;
    }
    
    public static int indexIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return -1;
        }
        final int len = searchStr.length();
        final int max = str.length() - len;
        for (int i = 0; i <= max; i++) {
            if (str.regionMatches(true, i, searchStr, 0, len)) {
                return i;
            }
        }
        return -1;
    }


    public static int compareTo(String s1, String s2) {
        boolean e1 = isEmpty(s1);
        boolean e2 = isEmpty(s2);
        if( e1 || e2  ) {
            if( e1 && !e2 ) return -1;
            if( !e1 && e2 ) return 1;
            return 0;
        }
        return s1.compareTo(s2);
    }

    public static int compareToIgnoreCase(String s1, String s2) {
        boolean e1 = isEmpty(s1);
        boolean e2 = isEmpty(s2);
        if( e1 || e2  ) {
            if( e1 && !e2 ) return -1;
            if( !e1 && e2 ) return 1;
            return 0;
        }
        return s1.compareToIgnoreCase(s2);
    }

    public static final boolean equals( String s1, String s2 ) {
        if( s1==null || s2==null ) {
            return isEmpty(s1)==isEmpty(s2);
        }
        return s1.equals(s2);
    }

    public static String nonNull(String s) {
        return s==null ? "" : s;
    }

    public static boolean isEmpty(String s) {
        return s==null || s.length()==0;
    }

    public static boolean isNotEmpty(String s) {
        return s!=null && s.length()>0;
    }
    
    public static String[] splitString( String s, char sep ) {
        return splitString( s, sep, false );
    }
    public static String[] splitString( String s, char sep, boolean trim ) {
        if( s==null ) {
            return EMPTY_STRING_ARRAY;
        }
        return splitString( null, 0, s, 0, sep, trim );
    }
    private static String[] splitString( String[] result, int count, String s, int pos, char sep, boolean trim ) {
        // Iterative (was recursive, one frame per separator - a long input overflowed the stack)
        int n = count+1;
        for( int p=s.indexOf(sep,pos); p>=0; p=s.indexOf(sep,p+1) ) {
            n++;
        }
        result = new String[n];
        int i = count;
        int start = pos;
        for(;;) {
            int newPos = s.indexOf(sep,start);
            String part = newPos>=0 ? s.substring(start,newPos) : s.substring(start);
            result[i++] = trim ? part.trim() : part;
            if(newPos<0) {
                break;
            }
            start = newPos+1;
        }
        return result;
    }

    public static String join( Object[] a, char sep ) {
    	return join(a, sep, 0, a!=null ? a.length : 0, false);
    }
    public static String join( Object[] a, char sep, int index, int length ) {
    	return join(a, sep, index, length, false);
    }
    
    public static String join( Object[] a, char sep, boolean ignoreNulls ) {
    	return join(a, sep, 0, a!=null ? a.length : 0, ignoreNulls);
    }
    public static String join( Object[] a, char sep, int index, int length, boolean ignoreNulls ) {
    	if(a==null) {
    		return null;
    	}
    	if(index<0 || index>=a.length || length<0) {
    		return "";
    	}
    	// index+length can overflow: compare against the remaining length instead
    	int end = index + Math.min(length, a.length-index);
    	
    	switch(end-index) {
    		case 0:		return "";
    		case 1:		return toString(a[index]);
    		default: {
    			StringBuilder b = new StringBuilder();
    			boolean first = true;
    			for(int i=index; i<end; i++) {
    				if(!ignoreNulls || a[i]!=null) {
	    				if(!first) {
	    					b.append(sep);
	    				}
	    				first = false;
						b.append(toString(a[i]));
    				}
    			}
				return b.toString();
    		}
    	}
    }
    public static String toString(Object o) {
    	if(o==null) {
    		return "";
    	}
    	if(o.getClass().isArray()) {
    		StringBuilder b = new StringBuilder();
    		int l = Array.getLength(o);
			b.append("[");
    		for(int i=0; i<l; i++) {
    			if(i>0) {
    				b.append(",");
    			}
				b.append(toString(Array.get(o,i)));
    		}
			b.append("]");
    		return b.toString();
    	}
    	return o.toString();
    }
    
    
    //
    // Replace functions as String native use RegExp
    //
    
	public static final String replaceFirst(String source, String value, String replace) {
		if (isEmpty(source)) {
			return "";
		}
		if (isEmpty(value)) {
			return source;
		}
		if (replace == null) {
			replace = "";
		}
		int idx = source.indexOf(value);
		if (idx >= 0) {
			StringBuilder b = new StringBuilder(source.length()+replace.length()-value.length());
			b.append(source, 0, idx);
			b.append(replace);
			int next = idx + value.length();
			b.append(source, next, source.length());
			return b.toString();
		}
		return source;
	}

	public static final String replaceAll(String source, String value, String replace) {
		if (isEmpty(source)) {
			return "";
		}
		if (isEmpty(value)) {
			return source;
		}
		if (replace == null) {
			replace = "";
		}
		int idx = source.indexOf(value);
		if (idx >= 0) {
			StringBuilder b = new StringBuilder(source.length()+64);
			b.append(source, 0, idx);
			int next;
			do {
				b.append(replace);
				next = idx + value.length();
				idx = source.indexOf(value, next);
				b.append(source, next, idx >= 0 ? idx : source.length());
			} while (idx >= 0);
			return b.toString();
		}
		return source;
	}
	
	public static final String replaceFirst(String source, char value, char replace) {
		if (isEmpty(source)) {
			return "";
		}
		int idx = source.indexOf(value);
		if (idx >= 0) {
			StringBuilder b = new StringBuilder(source.length());
			b.append(source, 0, idx);
			b.append(replace);
			int next = idx + 1;
			b.append(source, next, source.length());
			return b.toString();
		}
		return source;
	}

	public static final String replaceAll(String source, char value, char replace) {
		if (isEmpty(source)) {
			return "";
		}
		int idx = source.indexOf(value);
		if (idx >= 0) {
			StringBuilder b = new StringBuilder(source.length());
			b.append(source, 0, idx);
			int next;
			do {
				b.append(replace);
				next = idx + 1;
				idx = source.indexOf(value, next);
				b.append(source, next, idx >= 0 ? idx : source.length());
			} while (idx >= 0);
			return b.toString();
		}
		return source;
	}
	
	public static String normalizeLineBreaks(String s) {
		if(s!=null && s.indexOf('\r')>=0) {
			// Normalize the lines breaks to make it compatible between the different platforms (Mac, Windows, ....)
			// "\n\r" is NOT a line break sequence: it is an LF followed by a CR (two breaks)
			s = s.replace("\r\n", "\n");
			s = s.replace("\r", "\n");
		}
		return s;
	}
	
	
    public static String padLeft(String s, int len, char c) {
    	if(s==null) {
    		s = "";
    	}
    	int slen = s.length();
    	if(slen<len) {
    		StringBuilder b = new StringBuilder(len);
    		int count = len-slen;
    		for(int i=0; i<count; i++) {
    			b.append(c);
    		}
    		b.append(s);
    		return b.toString();
    	}
    	return s;
    }

    public static String padRight(String s, int len, char c) {
    	if(s==null) {
    		s = "";
    	}
    	int slen = s.length();
    	if(s.length()<len) {
    		StringBuilder b = new StringBuilder(len);
    		b.append(s);
    		int count = len-slen;
    		for(int i=0; i<count; i++) {
    			b.append(c);
    		}
    		return b.toString();
    	}
    	return s;
    }


    /**
     * Truncates a string to at most maxLen characters, ending with "..." when there is room
     * for it (maxLen>3). A shorter maxLen cuts the string without the ellipsis.
     */
    public static String truncate(String s, int maxLen) {
    	if(s!=null && s.length()>maxLen) {
    		if(maxLen>3) {
    			s = s.substring(0,maxLen-3) + "...";
    		} else {
    			s = s.substring(0,Math.max(0,maxLen));
    		}
    	}
    	return s;
    }

	
    public static String toUnsignedHex2(int value) {
   		String v = Integer.toHexString(value);
   		switch(v.length()) {
			case 0:		return "00";	
   			case 1:		return "0"+v;	
   		}
   		return v;
   	}
    
    public static String toUnsignedHex4(int value) {
   		String v = Integer.toHexString(value);
   		switch(v.length()) {
			case 0:		return "0000";	
   			case 1:		return "000"+v;	
   			case 2:		return "00"+v;	
   			case 3:		return "0"+v;
   		}
   		return v;
   	}

	public static String capitalizeFirstCharacter(String s) {
		if(isNotEmpty(s)) {
			// Code point based, so a supplementary character (surrogate pair) is handled too
			int c = s.codePointAt(0);
			if(Character.isLowerCase(c)) {
				return new StringBuilder(s.length()).appendCodePoint(Character.toUpperCase(c)).append(s,Character.charCount(c),s.length()).toString();
			}
		}
		return s;
	}

    
	public static String toCamelCase(String s) {
		if(s!=null) {
			int length = s.length();
			StringBuilder b = new StringBuilder(length);
			boolean nextIsUpperCase = false;
			for(int i=0; i<length; i++) {
				char c = s.charAt(i);
				if(c=='-') {
					nextIsUpperCase = true;
				} else {
					b.append(nextIsUpperCase?Character.toUpperCase(c):Character.toLowerCase(c));
					nextIsUpperCase = false;
				}
			}
			return b.toString();
		}
		return null;
	}
	
	public static String toKebabCase(String s) {
		if(s!=null) {
			int length = s.length();
			StringBuilder b = new StringBuilder(length+4);
			for(int i=0; i<length; i++) {
				char c = s.charAt(i);
				if(Character.isUpperCase(c)) {
					// An acronym stays one word: "HTMLParser" -> "html-parser", not "h-t-m-l-parser"
					boolean prevUpper = i>0 && Character.isUpperCase(s.charAt(i-1));
					boolean nextLower = i+1<length && Character.isLowerCase(s.charAt(i+1));
					if(!b.isEmpty() && (!prevUpper || nextLower)) {
						b.append('-');
					}
					b.append(Character.toLowerCase(c));
				} else {
					b.append(c);
				}
			}
			return b.toString();
		}
		return null;
	}
	
	public static String redacted(String s) {
		return redacted(s,5);
	}
	public static String redacted(String s, int max) {
		if(s==null) {
			return "<null>";
		}
		int len = Math.max(0, Math.min(max, s.length()));
		return s.substring(0,len)+"...REDACTED";
	}
}