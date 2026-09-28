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
package org.monflabs.galtajs.transpiler.util;

import java.util.Set;

import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;

public final class TranspilerUtil {

	
	public static String encodeSpreadValue(JSTranspilerGeneratorContext jsContext, Object value) {
		if(value instanceof Set<?> set) {
			return encodeSpreadArray(jsContext,set);
		} else {
			return ASTLiteral.encodeLiteral(jsContext,(value));
		}
	}
	private static String encodeSpreadArray(JSTranspilerGeneratorContext jsContext, Set<?> values) {
		StringBuilder b = new StringBuilder();
		b.append("new Object[]{");
		boolean first = true;
		for(Object v: values) {
			if(first) {
				first = false;
			} else {
				b.append(',');
			}
			b.append(ASTLiteral.encodeLiteral(jsContext,v));
		}
		b.append("}");
		return b.toString();
	}

}