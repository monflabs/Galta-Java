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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;

/**
 * Base class for an expression node.
 * 
 * @author priand
 */
public final class ExprBinaryOp extends ExprNode {
	
	public enum OP {
		EQ,
		NE,
		LT,
		LE,
		GT,
		GE,
		
		AND,
		OR
	};
	
	private OP op;
	private ExprNode left;
	private ExprNode right;
	
	public ExprBinaryOp(OP op, ExprNode left, ExprNode right) {
		this.op = op;
		this.left = left;
		this.right = right;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		addNode(b,left);
		switch(op) {
			case EQ ->	b.append("==");
			case NE ->	b.append("!=");
			case LT ->	b.append("<");
			case LE ->	b.append("<=");
			case GT ->	b.append(">");
			case GE ->	b.append(">=");
			case AND ->	b.append(" && ");
			case OR ->	b.append(" || ");
		}
		addNode(b,right);
		return b.toString();
	}
	private static void addNode(StringBuilder b, ExprNode n) {
		if(n instanceof ExprLiteral) {
			b.append(n.toString());
		} else if(n instanceof ExprPathOp || n instanceof ExprFunction) {
			b.append(n.toString());
		} else {
			b.append("(");
			b.append(n.toString());
			b.append(")");
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	@Override
	public boolean execute(Object root, Object current) {
		switch(op) {
			case AND: {
				if(!left.execute(root, current)) {
					return false;
				}
				return right.execute(root, current);
			}
			case OR: {
				if(left.execute(root, current)) {
					return true;
				}
				return right.execute(root, current);
			}
		}
			
		// RFC 9535: the operands are values, or Nothing (an empty singular query)
		Object l = left.evaluate(root, current);
		Object r = right.evaluate(root, current);
		switch(op) {
			case EQ:	 return eq(l,r);
			case NE:	 return !eq(l,r);
			case LT:	 return lt(l,r);
			case LE:	 return lt(l,r) || eq(l,r);
			case GT:	 return lt(r,l);
			case GE:	 return lt(r,l) || eq(l,r);
		}
		throw new IllegalStateException();
	}
	
	// Comparisons are different from the Javascript spec!
	private static boolean eq(Object v1, Object v2) {
		if(v1==NOTHING || v2==NOTHING) {
			// Nothing only equals Nothing
			return v1==v2;
		}
		return eqValue(v1, v2);
	}
	private static boolean eqValue(Object v1, Object v2) {
		if(v1==v2) {
			return true;
		}
		if(v1==null || v2==null) {
			return false;
		}
		if(v1 instanceof Number n1 && v2 instanceof Number n2) {
			return compareNumbers(n1, n2)==0;
		}
		// Works for Boolean, Strings, Array & Objects
		if(v1.getClass()==v2.getClass()) {
			return v1.equals(v2);
		}
		if(v1 instanceof JsonObject && v2 instanceof JsonObject || v1 instanceof JsonArray && v2 instanceof JsonArray) {
			return v1.equals(v2);
		}
		return false;
	}
	
	private static boolean lt(Object v1, Object v2) {
		if(v1==NOTHING || v2==NOTHING) {
			return false;
		}
		return ltValue(v1, v2);
	}
	
	private static final int UNORDERED = Integer.MIN_VALUE;
	/**
	 * Compare two numbers: as doubles if one of them is a Double or a Float, else exactly
	 * (an integer literal larger than 2^53 is not rounded). UNORDERED for NaN.
	 */
	static int compareNumbers(Number n1, Number n2) {
		if(n1 instanceof Double || n1 instanceof Float || n2 instanceof Double || n2 instanceof Float) {
			double d1 = n1.doubleValue();
			double d2 = n2.doubleValue();
			if(Double.isNaN(d1) || Double.isNaN(d2)) {
				return UNORDERED;
			}
			// -0.0 == 0.0
			return d1<d2 ? -1 : d1>d2 ? 1 : 0;
		}
		return toBigDecimal(n1).compareTo(toBigDecimal(n2));
	}
	private static BigDecimal toBigDecimal(Number n) {
		if(n instanceof BigDecimal bd) {
			return bd;
		}
		if(n instanceof BigInteger bi) {
			return new BigDecimal(bi);
		}
		if(n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte
				|| n instanceof java.util.concurrent.atomic.AtomicInteger || n instanceof java.util.concurrent.atomic.AtomicLong) {
			return BigDecimal.valueOf(n.longValue());
		}
		try {
			return new BigDecimal(n.toString());
		} catch(NumberFormatException ex) {
			return BigDecimal.valueOf(n.doubleValue());
		}
	}
	
	private static boolean ltValue(Object v1, Object v2) {
		if(v1 instanceof String s1 && v2 instanceof String s2) {
			return compareCodePoints(s1,s2)<0;
		}
		if(v1 instanceof Number n1 && v2 instanceof Number n2) {
			int c = compareNumbers(n1, n2);
			return c!=UNORDERED && c<0;
		}
		return false;
	}
	private static boolean isNaN(Number n) {
		return (n instanceof Double d && d.isNaN()) || (n instanceof Float f && f.isNaN());
	}
	
	// RFC 9535: strings are compared by Unicode scalar values, not UTF-16 code units
	static int compareCodePoints(String s1, String s2) {
		int i1=0, i2=0;
		int l1=s1.length(), l2=s2.length();
		while(i1<l1 && i2<l2) {
			int c1 = s1.codePointAt(i1);
			int c2 = s2.codePointAt(i2);
			if(c1!=c2) {
				return Integer.compare(c1, c2);
			}
			i1 += Character.charCount(c1);
			i2 += Character.charCount(c2);
		}
		return (i1<l1) ? 1 : (i2<l2) ? -1 : 0;
	}
	
	/**
	 * Check if a node can be an operand of a comparison (a literal or a path).
	 */
	static boolean isComparable(ExprNode n) {
		return n instanceof ExprLiteral || n instanceof ExprPathOp || n instanceof ExprFunction;
	}
}
