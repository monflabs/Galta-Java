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
package org.monflabs.json.parser;

import java.io.IOException;
import java.io.Reader;
import java.text.MessageFormat;

/**
 * Source code handler.
 */
public class SourceCode {

	public static class LineCol {
		private int line;
		private int col;
		public LineCol(int line, int col) {
			this.line = line;
			this.col = col;
		}
		public int getLine() {
			return line;
		}
		public int getCol() {
			return col;
		}
		@Override
		public String toString() {
			return MessageFormat.format("{0}:{1}", getLine(), getCol());
		}
	}
	
	private String source;
	private int length;
	
	public SourceCode(String source) {
		this.source = source;
		this.length = source.length();
	}
	
	public String getText() {
		return source;
	}
	
    public LineCol findCodePosition(int pos) {
        if (source!=null && !source.isEmpty()) {
            // Line terminators: LF, CR, and CRLF (a single one). Line and column are 1-based
            int line=1; int col=1;
            int end = Math.min(pos, length);
            for(int i=0; i<end; i++) {
            	char c = source.charAt(i);
    	        if(c=='\r' && i+1<end && source.charAt(i+1)=='\n') {
    	        	i++; // CRLF: one terminator
    	        	line++; col=1;
    	        } else if(c=='\n' || c=='\r') {
    	        	line++; col=1;
    	        } else {
    	        	col++;
    	        }
            }
            return new LineCol(line, col);
        }
    	return new LineCol(0, 0);
    }
    
    public static final int OPT_LINENUMBER	= 0x0001;
    public static final int OPT_LINEERRPTR	= 0x0002;
    
    public String extractSourceCode(int nLines, LineCol errpos) {
    	return extractSourceCode(new StringBuilder(128), nLines, errpos).toString();
    }
    public StringBuilder extractSourceCode(StringBuilder builder, int nLines, LineCol errpos) {
    	return extractSourceCode(builder, nLines, errpos, OPT_LINENUMBER|OPT_LINEERRPTR);
    }
    public StringBuilder extractSourceCode(StringBuilder builder, int nLines, LineCol errpos, int options) {
        if (source!=null && !source.isEmpty()) {
            int pos = 0;
            int errline = errpos.getLine();
            int errcol = errpos.getCol();
            boolean newline = false;
            for (int line = 1; pos<length; line++) {
                int start = pos;
                pos = nextLine(pos);
                boolean displayLine = nLines<0 || Math.abs(line-errline)<=(nLines/2); 
                if(displayLine) {
                	if(newline) {
    	                builder.append("\n");
                	} else {
                		newline = true; // next one
                	}
                	if((options&OPT_LINENUMBER)!=0) {
                		builder.append(padLeft(Integer.toString(line), 4, ' '));
                		builder.append(": ");
                	}
	                builder.append(source.substring(start, pos));
                	if((options&OPT_LINEERRPTR)!=0) {
		                if(errline == line) {
			                builder.append("\n      "); // line #
			                // errcol is 1-based: errcol-1 characters before the caret
			                for(int i=0; i<errcol-1; i++) {
			                	char c = start+i<source.length() ?  source.charAt(start+i) : 0;
			                	builder.append(c=='\t'?c:' ');
			                }
	    	                builder.append("^^^");
		                }
                	}
                }
                pos = skipEndline(pos);
            }
        }
        return builder;
    }
    private final int nextLine(int pos) {
        while(pos<length&& source.charAt(pos)!='\r' && source.charAt(pos)!='\n') {
            pos++;
        }
        return pos;
    }
    private final int skipEndline(int pos) {
        if(charAt(pos)=='\n') {
            pos++;
        } else if(charAt(pos)=='\r') {
            pos++;
            if(charAt(pos)=='\n') {
                pos++;
            }
        }
        return pos;
    }
    private final char charAt(int pos) {
    	return pos<source.length() ? source.charAt(pos) : '\0'; 
    }   
    
    private static String padLeft(String s, int len, char c) {
    	if(s.length()<len) {
    		StringBuilder b = new StringBuilder(len);
    		int count = len-s.length();
    		for(int i=0; i<count; i++) {
    			b.append(c);
    		}
    		b.append(s);
    		return b.toString();
    	}
    	return s;
    }
    
	public static String readString(Reader reader) throws IOException {
    	StringBuilder sb = new StringBuilder(256);
		char[] c = new char[8192];
		int count;
		while((count=reader.read(c))>=0) {
			sb.append(c, 0, count);
		}
		return sb.toString();
    }
}