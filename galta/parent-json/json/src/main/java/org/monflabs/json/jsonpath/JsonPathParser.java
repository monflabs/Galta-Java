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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.ExprBinaryOp.OP;
import org.monflabs.json.jsonpath.ExprPathOp.VALUE;
import org.monflabs.util.BaseException;
import org.monflabs.util.StringMatcher;

/**
 * JsonPath Parser (RFC 9535).
 * <p>
 * By default, the parser is lenient: it accepts a few syntaxes that RFC 9535 rejects
 * (member names with '-' or '$', "$.1", "$[-0]", "\x41" or a quote of the other kind
 * escaped in strings, unpaired surrogates, ".5" numbers, raw control characters but
 * line breaks and tabs in strings, a space after '.', a trailing space, the empty query).
 * In strict mode, these are syntax errors.
 */
public class JsonPathParser extends StringMatcher {

	//
	// The initial JsonPath implementations permitted syntaxes that are mostly
	// side effects of the implementation, like $.['key']
	// We better try to comply with that proposal:
	//   https://github.com/ietf-wg-jsonpath/draft-ietf-jsonpath-base
	//
	public static final boolean RELAXED_SYNTAX = false;

	/** The largest index magnitude (RFC 9535: the I-JSON exact integer range) */
	public static final long MAX_INDEX = 9007199254740991L; // 2^53-1

	private static final Pattern STRICT_NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
	private static final Pattern LENIENT_NUMBER = Pattern.compile("-?(?:(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)?");

	private final boolean strict;

	public JsonPathParser(String seq) {
		this(seq,0);
	}
	public JsonPathParser(String seq, int start) {
		this(seq, start, false);
	}
	public JsonPathParser(String seq, int start, boolean strict) {
		super(seq, start);
		this.strict = strict;
	}

	public boolean isStrict() {
		return strict;
	}

	@Override
	protected BaseException _createException(Throwable cause, String message) {
		return new JsonException(cause, message);
	}

	public PathNode readPathNode(boolean partial) {
		skipSpaces();
		if(isEmpty()) {
			return null;
		}

		if(match("..")) {
			if(startsWith('.')) {
				throw createException(null, ptr, "Invalid descendant segment '...'");
			}
			return new PathDeepScan(readPathMemberNode(partial,true));
		}
		return readPathMemberNode(partial,false);
	}

	@SuppressWarnings("unused")
	private PathNode readPathMemberNode(boolean partial, boolean isDeep) {
		if(match('[')) {
			skipSpaces();

			PathIndex.Index firstIndex=null, lastIndex=null;
			while(ptr<length) {
				switch(seq.charAt(ptr)) {
					case '\'', '\"' -> {
						String id = readStringLiteral();
						skipSpaces();
						lastIndex = PathIndex.addMember(lastIndex, id);
					}
					case '*' -> {
						skip();
						skipSpaces();
						lastIndex = PathIndex.addFlatten(lastIndex);
					}
					case '?' -> {
						skip();
						skipSpaces();
						// Looks like () is not required
						// https://github.com/ietf-wg-jsonpath/draft-ietf-jsonpath-base/blob/main/draft-ietf-jsonpath-base.md#filter-selector-filter-selector
						int start = ptr;
						ExprNode expr = readExpression();
						if(expr==null) {
							throw createException(null, start, "Filter expression is empty");
						}
						if(expr instanceof ExprLiteral) {
							throw createException(null, start, "Filter expression can't be a literal");
						}
						requireLogical(expr, start);
						lastIndex = PathIndex.addFilter(lastIndex, expr);
					}
					default -> {
						Integer start = null;
						if(!startsWith(':')) {
							long v = readIndex();
							skipSpaces();
							if(!startsWith(':')) {
								lastIndex = PathIndex.addIndex(lastIndex, v);
								break;
							}
							start = clamp(v);
						}
						skipSpaces();
						Integer end = null, inc = null;
						if(match(':')) {
							skipSpaces();
							if(startsWithInteger()) {
								end = clamp(readIndex());
								skipSpaces();
							}
							if(match(':')) {
								skipSpaces();
								if(startsWithInteger()) {
									inc = clamp(readIndex());
									skipSpaces();
								}
							}
						}
						lastIndex = PathIndex.addRange(lastIndex, start, end, inc);
					}
				}
				if(firstIndex==null) {
					firstIndex = lastIndex;
				}
				if(match(']')) {
					skipSpaces();
					return new PathIndex(readPathNode(partial),firstIndex);
				}
				if(!match(',')) {
					throw createException(null, ptr, "Missing closing bracket ']'");
				}
				skipSpaces();
			}
		}

		if(match('.') || isDeep) { // isDeep means it was called from '..' so the '.' in not required
			if(strict && startsWithSpace()) {
				throw createException(null, ptr, "Unexpected space after '.'");
			}
			skipSpaces();
			if(RELAXED_SYNTAX && startsWith('[')) {
				// If this is an array, ignore the '.' and treat the array
				// e.g. '.[' is equivalent to '['
				return readPathNode(partial);
			}
			if(match('*')) {
				return new PathIndex(readPathNode(partial), PathIndex.addFlatten(null));
			}
			if(startsWithInteger()) {
				if(strict) {
					throw createException(null, ptr, "A member name cannot start with a digit: use ['...']");
				}
				int idx = readInteger();
				PathIndex.Member firstMember = PathIndex.addMember(null,Integer.toString(idx));
				PathIndex node = new PathIndex(readPathNode(partial),firstMember);
				return node;
			}
			String id = null;
			if(RELAXED_SYNTAX && (startsWith('\'') || startsWith('"')) ) {
				id = readQuotedString();
			} else if(strict) {
				id = readStrictName();
			} else {
				id = readIdentifier();
			}
			skipSpaces();
			PathIndex.Member firstMember = PathIndex.addMember(null,id);
			PathIndex node = new PathIndex(readPathNode(partial),firstMember);
			return node;
		}

		if(partial) {
			return null;
		}
		throw createException(null, ptr, "Expecting '[' or '.' part");
	}

	// See: member-name-shorthand
	@Override
	protected boolean isIdentifierStart(char ch) {
		return JsonUtil.isIdentifierStart(ch);
	}
	@Override
	protected boolean isIdentifierPart(char ch) {
		return JsonUtil.isIdentifierPart(ch);
	}

	/**
	 * RFC 9535 member-name-shorthand: name-first = ALPHA / "_" / non ASCII,
	 * name-char = name-first / DIGIT.
	 */
	private String readStrictName() {
		int start = ptr;
		while(ptr<length) {
			char c = seq.charAt(ptr);
			boolean first = (c>='a' && c<='z') || (c>='A' && c<='Z') || c=='_' || c>=0x80;
			if(first || (ptr>start && c>='0' && c<='9')) {
				if(Character.isHighSurrogate(c)) {
					if(ptr+1>=length || !Character.isLowSurrogate(seq.charAt(ptr+1))) {
						throw createException(null, ptr, "Invalid unpaired surrogate");
					}
					ptr++;
				} else if(Character.isLowSurrogate(c)) {
					throw createException(null, ptr, "Invalid unpaired surrogate");
				}
				ptr++;
			} else {
				break;
			}
		}
		if(ptr==start) {
			throw createException(null, start, "Invalid member name");
		}
		return seq.substring(start, ptr);
	}

	/**
	 * Read an index: an integer in the range +/-(2^53-1), without leading zeros
	 * ("-0" is only accepted in lenient mode).
	 */
	private long readIndex() {
		int start = ptr;
		boolean neg = match('-');
		if(ptr>=length || seq.charAt(ptr)<'0' || seq.charAt(ptr)>'9') {
			throw createException(null, start, "Invalid integer");
		}
		if(seq.charAt(ptr)=='0') {
			ptr++;
			if(ptr<length && seq.charAt(ptr)>='0' && seq.charAt(ptr)<='9') {
				throw createException(null, start, "Invalid integer at position {0}", start);
			}
			if(neg && strict) {
				throw createException(null, start, "Invalid index -0");
			}
			return 0;
		}
		long v = 0;
		while(ptr<length) {
			char c = seq.charAt(ptr);
			if(c<'0' || c>'9') {
				break;
			}
			v = v*10 + (c-'0');
			if(v>MAX_INDEX) {
				throw createException(null, start, "Index out of range at position {0}", start);
			}
			ptr++;
		}
		return neg ? -v : v;
	}
	// Slice bounds and steps beyond the int range select the same elements as the int bounds
	private static int clamp(long v) {
		return (int)Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, v));
	}

	/**
	 * Read a quoted string. In strict mode, only the RFC 9535 escapes are accepted.
	 */
	private String readStringLiteral() {
		if(!strict) {
			return readQuotedString();
		}
		int start = ptr;
		char q = seq.charAt(ptr++);
		StringBuilder b = new StringBuilder();
		for(;;) {
			if(ptr>=length) {
				throw createException(null, start, "Unterminated string");
			}
			char c = seq.charAt(ptr++);
			if(c==q) {
				return b.toString();
			}
			if(c<0x20) {
				throw createException(null, ptr-1, "Invalid control character {0} in string", Integer.toString(c));
			}
			if(c=='\\') {
				if(ptr>=length) {
					throw createException(null, start, "Unterminated string");
				}
				char e = seq.charAt(ptr++);
				switch(e) {
					case 'b' -> b.append('\b');
					case 'f' -> b.append('\f');
					case 'n' -> b.append('\n');
					case 'r' -> b.append('\r');
					case 't' -> b.append('\t');
					case '/' -> b.append('/');
					case '\\' -> b.append('\\');
					case '\'', '"' -> {
						if(e!=q) {
							throw createException(null, ptr-2, "Invalid escape sequence \\{0} in a string quoted with {1}", e, q);
						}
						b.append(e);
					}
					case 'u' -> {
						char u = readHex4();
						if(Character.isHighSurrogate(u)) {
							if(ptr+1<length && seq.charAt(ptr)=='\\' && seq.charAt(ptr+1)=='u') {
								ptr += 2;
								char low = readHex4();
								if(!Character.isLowSurrogate(low)) {
									throw createException(null, ptr-6, "Invalid unpaired surrogate");
								}
								b.append(u).append(low);
							} else {
								throw createException(null, ptr-6, "Invalid unpaired surrogate");
							}
						} else if(Character.isLowSurrogate(u)) {
							throw createException(null, ptr-6, "Invalid unpaired surrogate");
						} else {
							b.append(u);
						}
					}
					default -> throw createException(null, ptr-2, "Invalid escape sequence \\{0}", e);
				}
			} else {
				if(Character.isHighSurrogate(c)) {
					if(ptr>=length || !Character.isLowSurrogate(seq.charAt(ptr))) {
						throw createException(null, ptr-1, "Invalid unpaired surrogate");
					}
					b.append(c).append(seq.charAt(ptr++));
					continue;
				} else if(Character.isLowSurrogate(c)) {
					throw createException(null, ptr-1, "Invalid unpaired surrogate");
				}
				b.append(c);
			}
		}
	}
	private char readHex4() {
		if(ptr+4>length) {
			throw createException(null, ptr, "Unterminated unicode escape sequence");
		}
		int v = 0;
		for(int i=0; i<4; i++) {
			int d = Character.digit(seq.charAt(ptr++), 16);
			if(d<0) {
				throw createException(null, ptr-1, "Invalid unicode escape sequence");
			}
			v = v*16 + d;
		}
		return (char)v;
	}

	//
	// Filter expression
	//
	public ExprNode readExpression() {
		return readExprOr();
	}
	public ExprNode readExprOr() {
		ExprNode n = readExprAnd();
		while(true) {
			if(matchAndSkipSpaces("||")) {
				requireLogical(requireOperand(n, "||"), ptr);
				ExprNode n2 = requireLogical(requireOperand(readExprOr(), "||"), ptr);
				n = new ExprBinaryOp(OP.OR, n, n2);
			} else {
				break;
			}
		}
		return n;
	}
	public ExprNode readExprAnd() {
		ExprNode n = readExprEqNe();
		while(true) {
			if(matchAndSkipSpaces("&&")) {
				requireLogical(requireOperand(n, "&&"), ptr);
				ExprNode n2 = requireLogical(requireOperand(readExprAnd(), "&&"), ptr);
				n = new ExprBinaryOp(OP.AND, n, n2);
			} else {
				break;
			}
		}
		return n;
	}
	public ExprNode readExprEqNe() {
		ExprNode n = readExprGtLtGeLe();
		while(true) {
			if(matchAndSkipSpaces("==")) {
				requireComparable(n, "==");
				ExprNode n2 = requireComparable(readExprEqNe(), "==");
				n = new ExprBinaryOp(OP.EQ, n, n2);
			} else if(matchAndSkipSpaces("!=")) {
				requireComparable(n, "!=");
				ExprNode n2 = requireComparable(readExprEqNe(), "!=");
				n = new ExprBinaryOp(OP.NE, n, n2);
			} else {
				break;
			}
		}
		return n;
	}
	public ExprNode readExprGtLtGeLe() {
		ExprNode n = readExprNot();
		while(true) {
			// >= & <= should be first as they are the longest tokens
			if(matchAndSkipSpaces(">=")) {
				requireComparable(n, ">=");
				ExprNode n2 = requireComparable(readExprGtLtGeLe(), ">=");
				n = new ExprBinaryOp(OP.GE, n, n2);
			} else if(matchAndSkipSpaces("<=")) {
				requireComparable(n, "<=");
				ExprNode n2 = requireComparable(readExprGtLtGeLe(), "<=");
				n = new ExprBinaryOp(OP.LE, n, n2);
			} else if(matchAndSkipSpaces(">")) {
				requireComparable(n, ">");
				ExprNode n2 = requireComparable(readExprGtLtGeLe(), ">");
				n = new ExprBinaryOp(OP.GT, n, n2);
			} else if(matchAndSkipSpaces("<")) {
				requireComparable(n, "<");
				ExprNode n2 = requireComparable(readExprGtLtGeLe(), "<");
				n = new ExprBinaryOp(OP.LT, n, n2);
			} else {
				break;
			}
		}
		return n;
	}
	public ExprNode readExprNot() {
		if(matchAndSkipSpaces("!")) {
			ExprNode n = requireLogical(requireOperand(readExprNot(), "!"), ptr);
			return new ExprUnaryOp(org.monflabs.json.jsonpath.ExprUnaryOp.OP.NOT, n);
		}
		return readExprTerm();
	}
	private ExprNode requireOperand(ExprNode n, String operator) {
		if(n==null) {
			throw createException(null, ptr, "Missing operand for operator {0}", operator);
		}
		return n;
	}
	private ExprNode requireComparable(ExprNode n, String operator) {
		requireOperand(n, operator);
		// RFC 9535: comparison operands are literals, singular queries or functions returning
		// a value, not logical expressions nor queries that can select several nodes
		if(n instanceof ExprPathOp p && !p.isSingular()) {
			throw createException(null, ptr, "Operator {0} can only compare a singular query (a path selecting at most one node): {1}", operator, p.toString());
		}
		if(n instanceof ExprFunction f && f.getFunction().returnsLogical()) {
			throw createException(null, ptr, "Operator {0} cannot compare the logical result of {1}()", operator, f.getFunction().getName());
		}
		if(!ExprBinaryOp.isComparable(n)) {
			throw createException(null, ptr, "Operator {0} can only compare a literal or a path", operator);
		}
		return n;
	}
	// A function returning a value must be compared, it is not a test expression
	private ExprNode requireLogical(ExprNode n, int pos) {
		if(n instanceof ExprFunction f && !f.getFunction().returnsLogical()) {
			throw createException(null, pos, "The result of {0}() must be compared, it is not a logical value", f.getFunction().getName());
		}
		return n;
	}

	public ExprNode readExprTerm() {
		if(matchAndSkipSpaces('(')) {
			ExprNode node = readExprOr();
			if(!matchAndSkipSpaces(')')) {
				throw createException(null, ptr, "Missing closing parenthesis");
			}
			return node;
		}
		if(matchAndSkipSpaces('@')) {
			PathNode node = readPathNode(true);
			skipSpaces();
			return new ExprPathOp(node, VALUE.CURRENT);
		}
		if(matchAndSkipSpaces('$')) {
			PathNode node = readPathNode(true);
			skipSpaces();
			return new ExprPathOp(node, VALUE.ROOT);
		}
		if(startsWith('\'') || startsWith('"')) {
			String str = readStringLiteral();
			skipSpaces();
			return new ExprLiteral(str);
		}
		ExprNode function = readFunction();
		if(function!=null) {
			return function;
		}
		Number n = readNumberLiteral();
		if(n!=null) {
			skipSpaces();
			return new ExprLiteral(n);
		}
		if(matchAndSkipSpaces("true")) {
			skipSpaces();
			return new ExprLiteral(Boolean.TRUE);
		}
		if(matchAndSkipSpaces("false")) {
			skipSpaces();
			return new ExprLiteral(Boolean.FALSE);
		}
		if(matchAndSkipSpaces("null")) {
			skipSpaces();
			return new ExprLiteral(null);
		}
		return null;
	}

	/**
	 * A function call: a lower case name immediately followed by '('.
	 */
	private ExprNode readFunction() {
		int start = ptr;
		int p = ptr;
		if(p>=length || seq.charAt(p)<'a' || seq.charAt(p)>'z') {
			return null;
		}
		while(p<length) {
			char c = seq.charAt(p);
			if((c>='a' && c<='z') || (c>='0' && c<='9') || c=='_') {
				p++;
			} else {
				break;
			}
		}
		if(p>=length || seq.charAt(p)!='(') {
			return null;
		}
		String name = seq.substring(start, p);
		ExprFunction.Function f = ExprFunction.Function.get(name);
		if(f==null) {
			throw createException(null, start, "Unknown function {0}()", name);
		}
		ptr = p+1;
		skipSpaces();
		List<ExprNode> args = new ArrayList<>();
		if(!match(')')) {
			for(;;) {
				int argStart = ptr;
				ExprNode a = readExprOr();
				if(a==null) {
					throw createException(null, argStart, "Missing argument for function {0}()", name);
				}
				args.add(a);
				skipSpaces();
				if(match(',')) {
					skipSpaces();
					continue;
				}
				if(match(')')) {
					break;
				}
				throw createException(null, ptr, "Expecting ',' or ')' in the arguments of {0}()", name);
			}
		}
		skipSpaces();
		ExprFunction.ArgType[] types = f.getArgTypes();
		if(args.size()!=types.length) {
			throw createException(null, start, "Function {0}() expects {1} argument(s)", name, types.length);
		}
		for(int i=0; i<types.length; i++) {
			ExprNode a = args.get(i);
			switch(types[i]) {
				case VALUE -> {
					boolean ok = a instanceof ExprLiteral
							|| (a instanceof ExprPathOp po && po.isSingular())
							|| (a instanceof ExprFunction fa && !fa.getFunction().returnsLogical());
					if(!ok) {
						throw createException(null, start, "Argument {0} of {1}() must be a value: a literal, a singular query or a function returning a value", i+1, name);
					}
				}
				case NODES -> {
					if(!(a instanceof ExprPathOp)) {
						throw createException(null, start, "Argument {0} of {1}() must be a query", i+1, name);
					}
				}
			}
		}
		return new ExprFunction(f, args.toArray(new ExprNode[args.size()]));
	}

	/**
	 * Read a number literal as an exact value: an Integer, Long or BigInteger for an
	 * integer, a BigDecimal otherwise ("-0" stays the double -0.0).
	 */
	private Number readNumberLiteral() {
		Matcher m = (strict ? STRICT_NUMBER : LENIENT_NUMBER).matcher(seq);
		m.region(ptr, length);
		if(!m.lookingAt()) {
			return null;
		}
		int start = ptr;
		String s = m.group();
		ptr = m.end();
		if(ptr<length) {
			char nc = seq.charAt(ptr);
			// "010", and in strict mode "1." or "1e"
			if((nc>='0' && nc<='9') || (strict && (nc=='.' || nc=='e' || nc=='E'))) {
				throw createException(null, start, "Invalid number {0}", s+nc);
			}
		}
		try {
			if(s.indexOf('.')<0 && s.indexOf('e')<0 && s.indexOf('E')<0) {
				if(s.equals("-0")) {
					return -0.0;
				}
				BigInteger bi = new BigInteger(s);
				if(bi.bitLength()<32) {
					return bi.intValue();
				}
				if(bi.bitLength()<64) {
					return bi.longValue();
				}
				return bi;
			}
			BigDecimal bd = new BigDecimal(s);
			if(bd.signum()==0 && s.startsWith("-")) {
				return -0.0;
			}
			return bd;
		} catch(NumberFormatException ex) {
			throw createException(ex, start, "Invalid number {0}", s);
		}
	}
}
