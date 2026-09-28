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
package org.monflabs.galtajs.rt.builtins.standard.function;

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
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;

/**
 * %AsyncFunction% - mirrors BuiltinGeneratorFunctionConstructor's real
 * dynamic source-text compilation exactly (just an "async function"
 * prologue instead of "function*" - the same CreateDynamicFunction
 * algorithm underlies both, spec 27.7.1.1 delegating to 20.2.1.1.1), rather
 * than the previous minimal non-compiling stub (a plain JSObject with a
 * hardcoded "anonymous" name/length, never actually callable) - see
 * test262 built-ins/Function/prototype/toString/AsyncFunction.js, which
 * needs the constructed function's real, toString-able source.
 */
public class BuiltinAsyncFunctionConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "AsyncFunction";

	public BuiltinAsyncFunctionConstructor(JSEnvironment env) {
		super(env, CLASSNAME, BuiltinAsyncFunctionPrototype.get(env), 1);
		// 27.7.3.1 AsyncFunction.prototype.constructor: non-writable, unlike
		// BaseConstructor's generic writable:true default - see
		// BuiltinGeneratorFunctionConstructor's matching comment.
		BuiltinAsyncFunctionPrototype.get(env).setOwnProperty("constructor",this,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	public Class<?> getNativeClass() {
		return JSObject.class;
	}

	// Per spec ("Properties of the AsyncFunction Constructor"), %AsyncFunction%'s
	// own [[Prototype]] is %Function% itself (the constructor object), not
	// %Function.prototype% - the generic BaseCallableObject default every
	// other callable falls back to. Confirmed via
	// built-ins/AsyncFunction/AsyncFunction-is-subclass.js.
	@Override
	protected Object getDefaultPrototype() {
		return getEnvironment().getStandardObjects().getOwnProperty(BuiltinFunctionConstructor.CLASSNAME);
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		// Mirrors BuiltinGeneratorFunctionConstructor.constructObject()
		// exactly (see its own comments for the full rationale) - only the
		// "async function" prologue differs from "function*".
		JSEnvironment env = getEnvironment();
		StringBuilder b = new StringBuilder();
		b.append("async function anonymous(");
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
		// prototype/toString/{Function,GeneratorFunction,AsyncGenerator}.js
		// (the same fix already applied to those three constructors).
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
