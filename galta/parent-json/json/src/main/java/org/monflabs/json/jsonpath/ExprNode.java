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

/**
 * Base class for an expression node.
 * 
 * @author priand
 */
public abstract class ExprNode {
	
	/**
	 * The absence of a value (RFC 9535 "Nothing"): a singular query that selects no node,
	 * or a function that has no result.
	 */
	public static final Object NOTHING = new Object() {
		@Override
		public String toString() {
			return "Nothing";
		}
	};
	
	/**
	 * Evaluate the expression as a logical (test) expression.
	 */
	public abstract boolean execute(Object root, Object current);
	
	/**
	 * Evaluate the expression as a value (comparison operand, function argument): a JSON
	 * value, or {@link #NOTHING}.
	 */
	public Object evaluate(Object root, Object current) {
		throw new IllegalStateException("The expression "+this+" is not a value");
	}
	
	@Override
	public abstract String toString();
	
	
//	// https://262.ecma-international.org/5.1/#sec-9.3
//	public static double toNumber(Object v) {
//		if(v==null) {
//			return 0.0;
//		}
//		if(v instanceof Number n) {
//			return n.doubleValue();
//		}
//		if(v instanceof Boolean b) {
//			return b.booleanValue() ? 1.0 : 0.0;
//		}
//		if(v instanceof String s) {
//			try {
//				return Double.parseDouble(s);
//			} catch(NumberFormatException ex) {
//				return Double.NaN;
//			}
//		}
//		return Double.NaN;
//	}
//	
//	// https://262.ecma-international.org/5.1/#sec-9.8
//	public static String toString(Object v) {
//		if(v==null) {
//			return "null";
//		}
//		if(v instanceof Boolean b) { 
//			return b ? "true" : "false";
//		}
//		if(v instanceof Number n) {
//			return JsonUtil.toString(n);
//		}
//		return v.toString();	
//	}
//
//	// https://262.ecma-international.org/5.1/#sec-9.2
//	public static boolean toBoolean(Object v) {
//		if(v==null) {
//			return false;
//		}
//		if(v instanceof Boolean b) {
//			return b;
//		}
//		if(v instanceof Number n) {
//			double d = n.doubleValue();
//			return d!=0.0 && !Double.isNaN(d);
//		}
//		if(v instanceof String s) {
//			return s.length()>0;
//		}
//		return true;
//	}
}
