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

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.ExprBinaryOp.OP;
import org.monflabs.json.jsonpath.ExprPathOp.VALUE;
import org.monflabs.util.BaseException;
import org.monflabs.util.StringMatcher;

/**
 * JsonPath Parser 
 */
public class JsonPathParser extends StringMatcher {
	
	//
	// The initial JsonPath implementations permitted syntaxes that are mostly
	// side effects of the implementation, like $.['key']
	// We better try to comply with that proposal:
	//   https://github.com/ietf-wg-jsonpath/draft-ietf-jsonpath-base
	//
	public static final boolean RELAXED_SYNTAX = false;
	
	public JsonPathParser(String seq) {
		this(seq,0);
	}
	public JsonPathParser(String seq, int start) {
		super(seq, start);
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
						String id = readQuotedString();
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
						lastIndex = PathIndex.addFilter(lastIndex, expr);
					}
					default -> {
						Integer start = null;
						if(!startsWith(':')) {
							int v = readInteger();
							skipSpaces();
							if(!startsWith(':')) {
								lastIndex = PathIndex.addIndex(lastIndex, v);
								break;
							}
							start = v;
						}
						skipSpaces();
						Integer end = null, inc = null;
						if(match(':')) {
							skipSpaces();
							if(startsWithInteger()) {
								end = readInteger();
								skipSpaces();
							}
							if(match(':')) {
								skipSpaces();
								if(startsWithInteger()) {
									inc = readInteger();
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
				int idx = readInteger();
				PathIndex.Member firstMember = PathIndex.addMember(null,Integer.toString(idx));
				PathIndex node = new PathIndex(readPathNode(partial),firstMember);
				return node;
			}
			String id = null;
			if(RELAXED_SYNTAX && (startsWith('\'') || startsWith('"')) ) {
				id = readQuotedString();
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
				requireOperand(n, "||");
				ExprNode n2 = requireOperand(readExprOr(), "||");
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
				requireOperand(n, "&&");
				ExprNode n2 = requireOperand(readExprAnd(), "&&");
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
			ExprNode n = requireOperand(readExprNot(), "!");
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
		// RFC 9535: comparison operands are literals or singular queries, not logical expressions
		if(!ExprBinaryOp.isComparable(n)) {
			throw createException(null, ptr, "Operator {0} can only compare a literal or a path", operator);
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
			String str = readQuotedString();
			skipSpaces();
			return new ExprLiteral(str);
		}
		if(startsWithNumber()) {
			Number n = readNumber();
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
}