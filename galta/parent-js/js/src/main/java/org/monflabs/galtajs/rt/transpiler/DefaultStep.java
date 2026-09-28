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
package org.monflabs.galtajs.rt.transpiler;

import java.util.function.Supplier;

// Runtime counterpart of node.literal.DefaultRef: a chain of destructuring
// defaults, each tied to the specific path DEPTH it applies at. More than
// one can apply to the same leaf at DIFFERENT depths - e.g. a defaulted
// function parameter containing its own further-nested-defaulted
// sub-pattern, `function f([[x]=[9]] = []) {}` - see VarAccessor.destruct().
public record DefaultStep(int depth, Supplier<Object> value, DefaultStep next) {
	public DefaultStep(int depth, Supplier<Object> value) {
		this(depth, value, null);
	}

	public static DefaultStep find(DefaultStep step, int depth) {
		for(; step!=null; step=step.next()) {
			if(step.depth()==depth) {
				return step;
			}
		}
		return null;
	}
}
