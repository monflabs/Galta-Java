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
 * %AsyncGeneratorFunction% - mirrors BuiltinGeneratorFunctionConstructor
 * exactly (real dynamic compilation of "async function* anonymous(...)
 * {...}" source text, via the same shared FunctionConstructorParentContext).
 * The resulting function object is only verified for its own SHAPE (own
 * "prototype", non-constructibility) by the currently-targeted test262
 * files - none of them actually call/iterate the constructed result (real
 * async generator execution needs the coroutine executor to interleave
 * yield and await, a separate, substantial gap - see
 * BuiltinAsyncGeneratorPrototype's own javadoc).
 */
public class BuiltinAsyncGeneratorFunctionConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "AsyncGeneratorFunction";

	public BuiltinAsyncGeneratorFunctionConstructor(JSEnvironment env) {
		super(env, CLASSNAME, BuiltinAsyncGeneratorFunctionPrototype.get(env), 1);
		// 27.4.3.1 AsyncGeneratorFunction.prototype.constructor: non-writable,
		// unlike BaseConstructor's generic writable:true default - see
		// BuiltinGeneratorFunctionConstructor's matching comment.
		BuiltinAsyncGeneratorFunctionPrototype.get(env).setOwnProperty("constructor",this,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	public Class<?> getNativeClass() {
		return JSObject.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		JSEnvironment env = getEnvironment();
		StringBuilder b = new StringBuilder();
		b.append("async function* anonymous(");
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
		// prototype/toString/AsyncGenerator.js.
		b.append("\n) {\n");
		if(parameters.length>=1) {
			b.append(RuntimeUtil.toString(env,parameters[parameters.length-1]));
		}
		b.append("\n}");

		String code = b.toString();
		try {
			ASTFunctionDecl fct = env.createFunction(code);
			// The function belongs to this constructor's realm (like Function)
			JSRuntimeContext ambientMain = JSRuntimeContext.get().getMainContext();
			JSRuntimeContext parentContext = ambientMain.getEnvironment()==getEnvironment() ? ambientMain : getEnvironment().getRealmContext();
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
