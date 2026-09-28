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
		} else if(n instanceof ExprPathOp) {
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
			
		JsonValues rLeft = null;
		if(left instanceof ExprPathOp p) {
			rLeft = p.executePath(root, current);
		}
		JsonValues rRight = null;
		if(right instanceof ExprPathOp p) {
			rRight = p.executePath(root, current);
		}
		switch(op) {
			case EQ:	 return eq(rLeft,rRight);
			case NE:	 return ne(rLeft,rRight);
			case LT:	 return lt(rLeft,rRight);
			case LE:	 return le(rLeft,rRight);
			case GT:	 return gt(rLeft,rRight);
			case GE:	 return ge(rLeft,rRight);
		}
		throw new IllegalStateException();
	}
	private boolean eq(JsonValues rLeft , JsonValues rRight) {
		return eq(left,rLeft,right,rRight);
	}
	private boolean ne(JsonValues rLeft , JsonValues rRight) {
		return !eq(left,rLeft,right,rRight);
	}
	private boolean lt(JsonValues rLeft , JsonValues rRight) {
		return lt(left,rLeft,right,rRight);
	}
	private boolean le(JsonValues rLeft , JsonValues rRight) {
		return lt(left,rLeft,right,rRight) || eq(left,rLeft,right,rRight);
	}
	private boolean gt(JsonValues rLeft , JsonValues rRight) {
		return lt(right,rRight,left,rLeft);
	}
	private boolean ge(JsonValues rLeft , JsonValues rRight) {
		return lt(right,rRight,left,rLeft) || eq(left,rLeft,right,rRight);
	}

	
	
	// Comparisons are different from the Javascript spec!
	private static boolean eq(ExprNode left, JsonValues rLeft, ExprNode right, JsonValues rRight) {
		if(rLeft!=null) {
			if(!((ExprPathOp)left).isDefinite()) {
				throw new JsonException(null,"Can't use an indefinite path in filters");
			}
		}
		if(rRight!=null) {
			if(!((ExprPathOp)right).isDefinite()) {
				throw new JsonException(null,"Can't use an indefinite path in filters");
			}
		}
		if(rLeft!=null) {
			if(rRight!=null) {
				int leftSize = rLeft._size();
				int rightSize = rRight._size();
				if(leftSize==rightSize) {
					for(int i=0; i<leftSize; i++) {
						if(!eqValue(rLeft._get(i), rRight._get(i))) {
							return false;
						}
					}
					return true;
				}
			} else if(right instanceof ExprLiteral lit) {
				if(rLeft._size()==1) {
					return eqValue(rLeft._get(0), lit.getValue());
				}
			}
		} else if(rRight!=null) {
			if(left instanceof ExprLiteral lit) {
				if(rRight._size()==1) {
					return eqValue(rRight._get(0), lit.getValue());
				}
			}
		} else {
			if(left instanceof ExprLiteral leftLit && right instanceof ExprLiteral rightLit) {
				return eqValue(leftLit.getValue(), rightLit.getValue());
			}
		}
			
		return false;
	}
	private static boolean eqValue(Object v1, Object v2) {
		if(v1==v2) {
			return true;
		}
		if(v1==null || v2==null) {
			return false;
		}
		if(v1 instanceof Number n1 && v2 instanceof Number n2) {
			double d1 = n1.doubleValue();
			double d2 = n2.doubleValue();
			if(Double.isNaN(d1) || Double.isNaN(d2)) {
				return false;
			}
			return d1==d2;
		}
		// Works for Boolean, Strings, Array & Objects
		if(v1.getClass()==v2.getClass()) {
			return v1.equals(v2);
		}
			
		return false;
	}
	
	private static boolean lt(ExprNode left, JsonValues rLeft, ExprNode right, JsonValues rRight) {
		if(rLeft!=null) {
			if(!((ExprPathOp)left).isDefinite()) {
				throw new JsonException(null,"Can't use an indefinite path in filters");
			}
		}
		if(rRight!=null) {
			if(!((ExprPathOp)right).isDefinite()) {
				throw new JsonException(null,"Can't use an indefinite path in filters");
			}
		}

		Object v1,v2;
		if(rLeft!=null) {
			if(rLeft._size()!=1) {
				return false;
			}
			v1 = rLeft._get(0);
		} else if(left instanceof ExprLiteral litLeft) {
			v1 = litLeft.getValue();
		} else {
			throw new IllegalStateException();
		}
		
		if(rRight!=null) {
			if(rRight._size()!=1) {
				return false;
			}
			v2 = rRight._get(0);
		} else if(right instanceof ExprLiteral litRight) {
			v2 = litRight.getValue();
		} else {
			throw new IllegalStateException();
		}

		return ltValue(v1, v2);
	}
	private static boolean ltValue(Object v1, Object v2) {
		if(v1 instanceof String s1 && v2 instanceof String s2) {
			return compareCodePoints(s1,s2)<0;
		}
		if(v1 instanceof Number n1 && v2 instanceof Number n2) {
			double d1 = n1.doubleValue();
			double d2 = n2.doubleValue();
			if(Double.isNaN(d1) || Double.isNaN(d2)) {
				return false;
			}
			return d1<d2;
		}
		return false;
	}
	
	// RFC 9535: strings are compared by Unicode scalar values, not UTF-16 code units
	private static int compareCodePoints(String s1, String s2) {
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
		return n instanceof ExprLiteral || n instanceof ExprPathOp;
	}
}
