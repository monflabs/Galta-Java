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

import org.monflabs.json.JsonUtil;

/**
 * Literal value.
 * 
 * @author priand
 */
public final class ExprLiteral extends ExprNode {
	
	private Object value;
	
	public ExprLiteral(Object value) {
		this.value = value;
	}
	
	public Object getValue() {
		return value;
	}
	
	@Override
	public String toString() {
		if(value instanceof String s) {
			return JsonUtil.encodeString(s,'\"');
		}
		if(value instanceof Number n) {
			return JsonUtil.toString(n);
		}
		return String.valueOf(value);
	}
	
	
	/**
	 * A literal used as a logical operand ({@code @.a && false}) is evaluated by
	 * its truthiness: null, false, 0, NaN and "" are false, anything else is true.
	 */
	@Override
	public Object evaluate(Object root, Object current) {
		return value;
	}
	
	@Override
	public boolean execute(Object root, Object current) {
		return isTruthy(value);
	}
	
	static boolean isTruthy(Object value) {
		if(value==null) {
			return false;
		}
		if(value instanceof Boolean b) {
			return b;
		}
		if(value instanceof Number n) {
			double d = n.doubleValue();
			return d!=0.0 && !Double.isNaN(d);
		}
		if(value instanceof String s) {
			return !s.isEmpty();
		}
		return true;
	}
}
