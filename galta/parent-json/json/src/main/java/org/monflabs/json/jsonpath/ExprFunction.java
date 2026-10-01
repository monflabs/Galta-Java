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
package org.monflabs.json.jsonpath;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;

/**
 * RFC 9535 function extensions: length(), count(), match(), search() and value().
 *
 * @author priand
 */
public final class ExprFunction extends ExprNode {

	public enum ArgType {
		/** A value: a literal, a singular query or a function returning a value */
		VALUE,
		/** A query, whatever the number of nodes it selects */
		NODES,
	}

	public enum Function {
		LENGTH("length", false, ArgType.VALUE),
		COUNT("count", false, ArgType.NODES),
		MATCH("match", true, ArgType.VALUE, ArgType.VALUE),
		SEARCH("search", true, ArgType.VALUE, ArgType.VALUE),
		VALUE("value", false, ArgType.NODES);

		private final String name;
		private final boolean logical;
		private final ArgType[] argTypes;
		Function(String name, boolean logical, ArgType... argTypes) {
			this.name = name;
			this.logical = logical;
			this.argTypes = argTypes;
		}
		public String getName() {
			return name;
		}
		/** True for a test function (LogicalType), false for a function returning a value (ValueType) */
		public boolean returnsLogical() {
			return logical;
		}
		public ArgType[] getArgTypes() {
			return argTypes.clone();
		}
		public static Function get(String name) {
			for(Function f: values()) {
				if(f.name.equals(name)) {
					return f;
				}
			}
			return null;
		}
	}

	private final Function function;
	private final ExprNode[] args;
	// match/search with a literal regular expression: compiled once (null if invalid)
	private final Pattern literalPattern;
	private final boolean literalRegex;

	public ExprFunction(Function function, ExprNode[] args) {
		this.function = function;
		this.args = args;
		if((function==Function.MATCH || function==Function.SEARCH) && args[1] instanceof ExprLiteral l) {
			this.literalRegex = true;
			this.literalPattern = l.getValue() instanceof String s ? compile(s) : null;
		} else {
			this.literalRegex = false;
			this.literalPattern = null;
		}
	}

	public Function getFunction() {
		return function;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(function.getName());
		b.append('(');
		for(int i=0; i<args.length; i++) {
			if(i>0) {
				b.append(',');
			}
			b.append(args[i].toString());
		}
		b.append(')');
		return b.toString();
	}

	@Override
	public boolean execute(Object root, Object current) {
		if(function.returnsLogical()) {
			return test(root, current);
		}
		// The parser doesn't accept a value function as a test expression
		throw new IllegalStateException("The result of "+function.getName()+"() is not a logical value");
	}

	@Override
	public Object evaluate(Object root, Object current) {
		switch(function) {
			case LENGTH: {
				Object v = args[0].evaluate(root, current);
				if(v instanceof String s) {
					return s.codePointCount(0, s.length());
				}
				if(v instanceof JsonArray a) {
					return a.size();
				}
				if(v instanceof JsonObject o) {
					return o.size();
				}
				return NOTHING;
			}
			case COUNT: {
				return ((ExprPathOp)args[0]).executePath(root, current)._size();
			}
			case VALUE: {
				JsonValues r = ((ExprPathOp)args[0]).executePath(root, current);
				return r._size()==1 ? r._get(0) : NOTHING;
			}
			default:
				return test(root, current);
		}
	}

	private boolean test(Object root, Object current) {
		Object v = args[0].evaluate(root, current);
		if(!(v instanceof String s)) {
			return false;
		}
		Pattern p;
		if(literalRegex) {
			p = literalPattern;
		} else {
			Object re = args[1].evaluate(root, current);
			p = re instanceof String sr ? cachedPattern(sr) : null;
		}
		if(p==null) {
			return false;
		}
		return function==Function.MATCH ? p.matcher(s).matches() : p.matcher(s).find();
	}

	//
	// I-Regexp (RFC 9485)
	//
	private static final Pattern INVALID = Pattern.compile("");
	private static final int MAX_CACHED_PATTERNS = 256;
	private static final Map<String,Pattern> PATTERNS = new ConcurrentHashMap<>();

	private static Pattern cachedPattern(String re) {
		Pattern p = PATTERNS.get(re);
		if(p==null) {
			if(PATTERNS.size()>=MAX_CACHED_PATTERNS) {
				PATTERNS.clear();
			}
			p = compile(re);
			PATTERNS.put(re, p!=null ? p : INVALID);
			return p;
		}
		return p==INVALID ? null : p;
	}

	/**
	 * Compile an I-Regexp as a Java regular expression, or return null if it is invalid.
	 * '.' matches any character but \n and \r, and '^' and '$' are ordinary characters
	 * (I-Regexp has no anchors: match() matches the whole string).
	 */
	static Pattern compile(String re) {
		StringBuilder b = new StringBuilder(re.length()+16);
		boolean inClass = false;
		int length = re.length();
		for(int i=0; i<length; i++) {
			char c = re.charAt(i);
			if(c=='\\') {
				b.append(c);
				if(i+1<length) {
					b.append(re.charAt(++i));
				}
				continue;
			}
			if(inClass) {
				if(c==']') {
					inClass = false;
				} else if(c=='[' || c=='&') {
					// Not special in an I-Regexp class (no Java union or intersection)
					b.append('\\');
				}
				b.append(c);
				continue;
			}
			switch(c) {
				case '[' -> {
					inClass = true;
					b.append(c);
					if(i+1<length && re.charAt(i+1)=='^') {
						b.append('^');
						i++;
					}
				}
				case '.' -> b.append("[^\\n\\r]");
				case '^', '$' -> b.append('\\').append(c);
				default -> b.append(c);
			}
		}
		try {
			return Pattern.compile(b.toString());
		} catch(PatternSyntaxException ex) {
			return null;
		}
	}
}
