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
package org.monflabs.galtajs.template.engines;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.template.Template;
import org.monflabs.galtajs.template.TemplateEngine;
import org.monflabs.galtajs.template.templates.IdentityTemplate;
import org.monflabs.galtajs.template.templates.ScriptTemplate;
import org.monflabs.util.StringUtil;


/**
 * Script Template engine.
 * 
 * Uses a syntax similar to JSPs:
 * <ul>
 * <li><code>&lt;% .... %&gt;</code> executes a piece of script</li>
 * <li><code>&lt;%= ... %&gt;</code> evaluates an expression and emits its result in place</li>
 * </ul>
 * A single line break following a statement tag is not emitted.
 * 
 * @author Philippe Riand
 */
public class JspTemplateEngine implements TemplateEngine { 
	
	public static final String FUNCTION = "__emit__";
	
	public static final String START_TAG = "<%";
	public static final String END_TAG 	 = "%>";

	private JSEnvironment env;
	private String tmplName;
	
	public JspTemplateEngine(JSEnvironment env) {
		this(env,"JSP Template");
	}
	public JspTemplateEngine(JSEnvironment env, String tmplName) {
		this.env = env;
		this.tmplName = tmplName;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	
	public String getTemplateName() {
		return tmplName;
	}
	
	@Override
	public String execute(InterpretedGlobalRuntimeContext context, String source) {
		Template template = compile(source);
		return template.execute(context);
	}
	
	@Override
	public Template compile(String source) {
		int pos = source.indexOf(START_TAG,0);
		if(pos>=0) {
			// Generate the script from the template 
			StringBuilder generatedScript = new StringBuilder(1024);
			int start = 0;
			do {
				// Check if the next tag, if exists, is an expression
				//  <%= ... %>
				boolean expression = pos+START_TAG.length()<source.length() && source.charAt(pos+START_TAG.length())=='=';
				
				// Static text
				if(pos>start) {
					// If the token is a statement, then ignore the spaces before up to the next line
					// Expression just keep the same new lines...
					int end = pos;
					if(!expression) {
						end = rewindSpaces(source,pos);
					}
					String s = escapeString(source.substring(start,end));
					if(s.length()>0) {
						generatedScript.append(FUNCTION).append("('").append(s).append("');\n");
					}
				}
				if(pos==source.length()) {
					break;
				}
				
				// Script expression
				int endExp = source.indexOf(END_TAG,pos);
				if(endExp>=0) {
					start = endExp+END_TAG.length();
					
					// Check if this is an expression or a script statement
					if(expression) { 
						String expr = source.substring(pos+START_TAG.length()+1,endExp);
						if(StringUtil.isNotEmpty(expr)) {
							generatedScript.append(FUNCTION).append("(").append(expr).append(");\n");
						}
					} else {
						generatedScript.append(source,pos+START_TAG.length(),endExp).append("\n");
						// We skip to the LOOP_COUNT line, eating the spaces and the EOL the spaces after the closing tags
						start = skipSpaces(source, start);
						start = skipEol(source, start);
					}

					// Find the next start tag
					pos = source.indexOf(START_TAG,start);
					if(pos<0) pos = source.length();
				} else {
					throw new JSException(null,"Missing closing tag {0}",END_TAG); 
				}

				
			} while(true);
			
			JSInterpretedUnit expression = env.createScript(generatedScript.toString(),tmplName);
			return new ScriptTemplate(this,expression);
		}
		
		// Optimize when the template is static
		return new IdentityTemplate(source);
	}
	private int rewindSpaces(String source, int pos) {
		int oldPos = pos;
		while(pos>0) {
			char c = source.charAt(pos-1);
			if(c=='\n' || c=='\r') {
				return pos;
			}
			if(!isSpace(c)) {
				// we have not reach an EOL but a char is not a space
				// we should not rewind anything
				return oldPos;
			}
			pos--;
		}
		return 0;
	}
	private int skipSpaces(String source, int pos) {
		while(pos<source.length()) {
			if(!isSpace(source.charAt(pos))) {
				break;
			}
			pos++;
		}
		return pos;
	}
	private int skipEol(String source, int pos) {
		if(pos<source.length()) {
			char c = source.charAt(pos);
			// A single line break: "\n", "\r\n" or "\r" ("\n\r" is two of them)
			if(c=='\n') {
				pos++;
			} else if(c=='\r') {
				pos++;
				if(pos<source.length() && source.charAt(pos)=='\n') {
					pos++;
				}
			}
		}
		return pos;
	}
	
	private static boolean isSpace(char c) {
		return c==' ' || c=='\t';
	}
	
    private static String escapeString(CharSequence s) {
        StringBuilder b = new StringBuilder();
        int length = s.length();
        for( int i=0; i<length; i++ ) {
        	// Be of the safe side for non ascii characters
            char c = s.charAt(i);
        	switch(c) {
        		case '\n' ->		b.append( "\\n" );
        		case '\r' ->		b.append( "\\r" );
        		case '\\' ->		b.append( "\\\\" );
        		case '\'' ->		b.append( "\\'" );
        		case '"' ->			b.append( "\\\"" );
        		default -> {
        	        if((c<32) || (c > 126)) {
			        	String hex = Integer.toHexString(c);
			        	b.append( "\\u" );
			        	b.append("0000".substring(0,4-hex.length()));
			        	b.append(hex);
        	        } else {
        	        	b.append(c);
        	        }
        		}
	        }
        }
        return b.toString();
    }
}
