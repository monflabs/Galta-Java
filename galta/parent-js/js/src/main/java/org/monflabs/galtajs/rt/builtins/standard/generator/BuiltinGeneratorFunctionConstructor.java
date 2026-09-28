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
package org.monflabs.galtajs.rt.builtins.standard.generator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.function.FunctionConstructorParentContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;

/**
 * %GeneratorFunction% - unlike %Generator% (never directly constructible),
 * this genuinely is a constructible intrinsic per spec: `new
 * GeneratorFunction(...)` dynamically compiles a generator function from
 * source text, mirroring BuiltinFunctionConstructor's real dynamic
 * source-text compilation exactly (just with a "function*" prologue instead
 * of "function") - the same CreateDynamicFunction algorithm underlies both
 * (spec 27.3.1.1 delegates straight to 20.2.1.1.1). The resulting function
 * IS a real, working generator (own [[Prototype]] %GeneratorFunction.
 * prototype%, own "prototype" chained to %GeneratorPrototype% via
 * ASTFunctionDecl/BuiltinFunctionInterpreter's already-correct generator
 * wiring - nothing generator-specific needed here beyond the source text
 * itself).
 */
public class BuiltinGeneratorFunctionConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "GeneratorFunction";

	public BuiltinGeneratorFunctionConstructor(JSEnvironment env) {
		super(env, CLASSNAME, BuiltinGeneratorFunctionPrototype.get(env), 1);
		// 27.3.3.1 GeneratorFunction.prototype.constructor: unlike most
		// constructor/prototype back-links (BaseConstructor's generic
		// DESC_PROP_CONSTRUCTOR default is writable:true), %GeneratorFunction.
		// prototype% is a special intrinsic with no directly-reachable global
		// binding of its own - spec explicitly marks this one non-writable.
		// Override the generic descriptor super() just set.
		BuiltinGeneratorFunctionPrototype.get(env).setOwnProperty("constructor",this,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	public Class<?> getNativeClass() {
		return JSObject.class;
	}

	// Spec 19.2.1.1.1 CreateDynamicFunction: all arguments but the last are
	// parameter-list source text (each individually ToString-coerced, then
	// joined with "," - so a single arg like "x, y" contributes 2 params,
	// same as passing "x","y" as two separate args), the last is the body.
	// Zero args means both are empty. This counts comma-separated parameter
	// NAMES without validating or ever evaluating them as real bindings.
	public static int paramCount(JSEnvironment env, Object[] parameters) {
		if(parameters.length<=1) {
			return 0;
		}
		StringBuilder combined = new StringBuilder();
		for(int i=0; i<parameters.length-1; i++) {
			if(i>0) {
				combined.append(',');
			}
			combined.append(RuntimeUtil.toString(env, parameters[i]));
		}
		int count = 0;
		for(String p : combined.toString().split(",", -1)) {
			if(!p.trim().isEmpty()) {
				count++;
			}
		}
		return count;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		JSEnvironment env = getEnvironment();
		StringBuilder b = new StringBuilder();
		b.append("function* anonymous(");
		for(int i=0; i<parameters.length-1; i++) {
			if(i>0) {
				b.append(",");
			}
			b.append(RuntimeUtil.toString(env,parameters[i]));
		}
		// A real line terminator, not just ") {\n" directly after the last
		// parameter, is required per spec (CreateDynamicFunction's source
		// template) - without it, a last parameter string ending in a `//`
		// line comment would swallow the following ")" as part of the
		// comment, producing a SyntaxError. See test262 built-ins/Function/
		// prototype/toString/GeneratorFunction.js.
		b.append("\n) {\n");
		if(parameters.length>=1) {
			b.append(RuntimeUtil.toString(env,parameters[parameters.length-1]));
		}
		b.append("\n}");

		String code = b.toString();
		try {
			ASTFunctionDecl fct = env.createFunction(code);
			JSRuntimeContext parentContext = JSRuntimeContext.get().getMainContext();
			JSInterpretedRuntimeContext ctx = new FunctionConstructorParentContext(parentContext);
			JSResult r = new JSResult();
			ctx.run( () -> {
				fct.evaluate(ctx, r);
			});

			BuiltinFunction ctor = (BuiltinFunction)r.deref();
			return applyNewTargetPrototype(ctor, topConstructor);
		} catch(JSParseException ex) {
			throw RuntimeUtil.syntaxError(ex.getLocalizedMessage());
		}
	}
}
