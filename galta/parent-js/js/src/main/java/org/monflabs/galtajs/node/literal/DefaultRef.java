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
package org.monflabs.galtajs.node.literal;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.util.StringFormat;

// A chain of destructuring defaults, each tied to the PATH DEPTH it applies
// at. Forwarding an ambient default down through nested, non-defaulting
// patterns (e.g. `{x} = {...}` several levels up from a bare `x`) must
// remember the exact path length at which the default was introduced,
// since a leaf further down the path can independently resolve to
// "undefined" for an unrelated reason (e.g. an explicit `x: undefined`
// property) and must NOT trigger a default meant for an earlier position.
//
// More than one default can legitimately apply to the same leaf at
// DIFFERENT depths - e.g. a defaulted function parameter containing its
// own further-nested-defaulted sub-pattern, `function f([[x]=[9]] = []) {}`
// - so introducing a new default chains the ambient one (if any) as `next`
// rather than discarding it.
public record DefaultRef(ASTNode node, int depth, DefaultRef next) {
	public DefaultRef(ASTNode node, int depth) {
		this(node, depth, null);
	}

	// Emits a `new DefaultStep(depth,()->value,<next>)` chain construction
	// (or the literal "null" for an empty chain) - see the runtime
	// counterpart rt.transpiler.DefaultStep.
	public static String transpileChain(JSTranspilerGeneratorContext jsContext, DefaultRef ref) {
		if(ref==null) {
			return "null";
		}
		return StringFormat.format("new DefaultStep({0},()->{1},{2})", ref.depth(), JSTranspiler.asValue(jsContext,ref.node()), transpileChain(jsContext,ref.next()));
	}
}
