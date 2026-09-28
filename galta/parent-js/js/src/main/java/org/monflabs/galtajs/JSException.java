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
package org.monflabs.galtajs;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.util.BaseException;
import org.monflabs.util.StringUtil;


/**
 * @author Philippe Riand
 */
public class JSException extends BaseException  {

	private static final long serialVersionUID = 1L;

	public static final int EXTRACT_LINES = 4;

	private ASTNode sourceNode;

    public JSException(Throwable nextException) {
        super(nextException);
    }

    public JSException(Throwable nextException, String msg) {
        super(nextException,msg);
    }

    public JSException(Throwable nextException, String msg, Object... parameters) {
        super(nextException,msg,parameters);
    }

    public ASTNode getSourceNode() {
    	return sourceNode;
    }

    public JSException setSourceNode(ASTNode sourceNode) {
    	this.sourceNode = sourceNode;
    	return this;
    }

    public static StringBuilder extractSourceCode(StringBuilder builder, int nLines, String code, int errline, int errcol) {
        if (StringUtil.isNotEmpty(code)) {
            int codeLength = code.length();
            int pos = 0;
            boolean newline = false;
            for (int line = 1; pos < codeLength; line++) {
                int start = pos;
                pos = nextLine(code, pos);
                boolean displayLine = nLines<0 || Math.abs(line-errline)<=(nLines/2);
                if(displayLine) {
                	if(newline) {
    	                builder.append("\n");
                	} else {
                		newline = true; // next one
                	}
	                builder.append(padLeft(Integer.toString(line), 4, ' '));
	                builder.append(": ");

	                String text = code.substring(start, pos);
	                int textCol = Math.min(errcol,text.length());
	                if(textCol>80) {
	                	text = "... " + text.substring(Math.max(0,textCol-40),text.length());
	                	textCol = 40;
	                }
	                if(text.length()>80) {
	                	text = text.substring(0,80)+" ...";
	                }
	                builder.append(text);
	                if(errline == line) {
		                builder.append("\n      "); // line #
		                if(textCol!=errcol) {
		                	builder.append("    ");
		                }
		                for(int i=0; i<textCol-2; i++) {
		                	char c = text.charAt(i);
		                	builder.append(c=='\t'?c:' ');
		                }
    	                builder.append("^^^");
	                }
                }
                pos = skipEndline(code, pos);
            }
        }
        return builder;
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

    private static int nextLine(String code, int pos) {
    	int length = code.length();
        while(pos<length&& code.charAt(pos)!='\r' && code.charAt(pos)!='\n') {
            pos++;
        }
        return pos;
    }
    private static int skipEndline(String code, int pos) {
        if(charAt(code,pos)=='\n') {
            pos++;
            if(charAt(code,pos)=='\r') {
                pos++;
            }
        } else if(charAt(code,pos)=='\r') {
            pos++;
            if(charAt(code,pos)=='\n') {
                pos++;
            }
        }
        return pos;
    }
    private static char charAt(String code, int pos) {
    	return pos<code.length() ? code.charAt(pos) : '\0';
    }
}
