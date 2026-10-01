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
package org.monflabs.galtajs.preprocessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import org.monflabs.util.BaseException;
import org.monflabs.util.StringMatcher;

public class ScriptPreProcessor {
	
	
	public static final String preprocess(String source, Map<String,Object> symbols) {
		if(shouldPreprocess(source)) {
			ScriptPreProcessor p = new ScriptPreProcessor(symbols);
			StringWriter sw = new StringWriter(source.length());
			p.preprocess(new BufferedWriter(sw), new BufferedReader(new StringReader(source)));
			return sw.toString();
		}
		return source;
	}
	
	/**
	 * Preprocessed source always uses '\n', as JavaScript source does, and never the platform
	 * line separator that {@code BufferedWriter.newLine()} would emit: the result is parsed,
	 * compared and cached, and must not depend on the OS it was preprocessed on.
	 */
	private static final String LINE_BREAK = "\n";

	// A directive: a whole word (#if, not #ifdebug), right after "//" and
	// spaces
	private static final Pattern DIRECTIVE = Pattern.compile("#(define|undef|if|elif|else|endif)(?![\\w$])");
	// Only a source with a directive at the start of a line is rewritten:
	// "//#" elsewhere (a URL fragment in a string...) is not one
	private static final Pattern CONTAINS_DIRECTIVES = Pattern.compile("(?m)^[ \\t]*//[ \\t]*#(define|undef|if|elif|else|endif)(?![\\w$])");
	private static boolean shouldPreprocess(String s) {
		return s.contains("#") && CONTAINS_DIRECTIVES.matcher(s).find();
	}
	
	private static enum State {
		DEFAULT,
		IF,
		ELIF,
		ELSE
	}
	
	private static final class PreprocessorMatcher extends StringMatcher {
		@Override
		protected BaseException _createException(Throwable cause, String message) {
			return new PreProcessorException(cause, message);
		}
	}

	private Map<String,Object> symbols;
	private boolean removeHidden;
	
	private State state = State.DEFAULT;
	private boolean shouldEmit;
	private boolean hasBeenEmitted;
	private PreprocessorMatcher _matcher = new PreprocessorMatcher();
	
	private ScriptPreProcessor(Map<String,Object> symbols) {
		this(symbols,false);
	}

	private ScriptPreProcessor(Map<String,Object> symbols, boolean removeHidden) {
		this.symbols = symbols;
		this.removeHidden = removeHidden;
	}
	
    private void preprocess(BufferedWriter out, BufferedReader in) {
    	try {
	        String line;
	        while( (line=in.readLine()) != null ) {
	            boolean directive = processLine(line);
	            if(!directive) {
	            	if(state!=State.DEFAULT && !shouldEmit) { 
		            	if(!removeHidden) {
		            		out.write("//");
		            		out.write(line); 
		            		out.write(LINE_BREAK);
		            	}
	            	} else {
	            		out.write(line); 
	            		out.write(LINE_BREAK);
	            	}
	            } else {
	            	if(!removeHidden) {
		            	out.write(line); 
		            	out.write(LINE_BREAK);
	            	}
	            }
	        }
	        // An unterminated #if would silently hide the rest of the file
	        if(state!=State.DEFAULT) {
	        	throw new PreProcessorException(null,"Missing #endif");
	        }
	        out.flush();
    	} catch(IOException e) {
    		throw new PreProcessorException(e);
    	}
    }

    private boolean processLine(String line ) {
    	if(line.contains("//")) {
    		String removeSpace = line.stripLeading();
    		if(removeSpace.startsWith("//")) {
    	        PreprocessorMatcher m = (PreprocessorMatcher)_matcher.set(removeSpace,2);
    	        m.skipSpaces();
    	        String directive = m.readRegExp(DIRECTIVE);
    	        if(directive!=null) {
    	        	switch(directive) {
    	        		case "#define" -> processDefine(m);
    	        		case "#undef" -> processUndef(m);
    	        		case "#if" -> processIf(m);
    	        		case "#else" -> processElse(m);
    	        		case "#elif" -> processElif(m);
    	        		default -> processEndif(m);
    	        	}
    	        	return true;
    	        }
    		}
    	}
        return false;
    }

    private void processIf(PreprocessorMatcher m) {
        if(state!=State.DEFAULT) {
            throw new PreProcessorException(null,"#if cannot be nested");
        }
    	
    	if(!m.startsWithSpace()) {
            throw new PreProcessorException(null,"Invalid #if statement");
    	}
    	m.skipSpaces();
    	
   		shouldEmit = evalExpression(m);
   		hasBeenEmitted = shouldEmit;
   		state = State.IF;
    }
    
    private void processElif(PreprocessorMatcher m) {
        // Any number of #elif can follow an #if
        if(state!=State.IF && state!=State.ELIF) {
            throw new PreProcessorException(null,"Invalid #elif location");
        }
    	
    	if(!m.startsWithSpace()) {
            throw new PreProcessorException(null,"Invalid #elif statement");
    	}
    	m.skipSpaces();
   		shouldEmit = evalExpression(m); // Make sure it is validated
    	if(hasBeenEmitted) {
       		shouldEmit = false;
    	} else {
       		hasBeenEmitted = shouldEmit;
    	}
   		state = State.ELIF;
    }
    
    private void processElse(PreprocessorMatcher m) {
        if(state!=State.IF && state!=State.ELIF) {
            throw new PreProcessorException(null,"Invalid #else location");
        }
        state = State.ELSE;
       	shouldEmit = !hasBeenEmitted;
       	return;
    }
    
    private void processEndif(PreprocessorMatcher m) {
        if(state!=State.IF && state!=State.ELIF && state!=State.ELSE) {
            throw new PreProcessorException(null,"Invalid #endif location");
        }
        state = State.DEFAULT;
    }
    
    // Recognized, so that a script relying on them fails rather than
    // silently running with the wrong code (see the documentation)
    private void processDefine(PreprocessorMatcher m) {
        throw new PreProcessorException(null,"#define is not supported");
    }
    
    private void processUndef(PreprocessorMatcher m) {
        throw new PreProcessorException(null,"#undef is not supported");
    }
    
    private boolean evalExpression(PreprocessorMatcher m) {
    	// For now, we only evaluate symbols
    	// We can evaluate a true expression if needed
    	String id = m.readIdentifier();
    	m.skipSpaces();
    	if(!m.isEmpty()) {
            throw new PreProcessorException(null,"Invalid #if expression");
    	}
    	Object value = symbols!=null ? symbols.get(id) : null;
    	return toBoolean(value);
    }
    
    private static boolean toBoolean(Object v) {
    	if(v==null) {
    		return false;
    	}
    	if(v instanceof Boolean b) {
    		return b;
    	}
    	if(v instanceof Number n) {
    		return n.intValue()!=0;
    	}
    	if(v instanceof String s) {
    		return s.length()>0;
    	}
    	return false;
    }
}
