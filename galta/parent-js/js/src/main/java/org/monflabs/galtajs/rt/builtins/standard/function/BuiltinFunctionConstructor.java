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
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;

/**
 * Eqv of the JavaScript Function constructor.
 */
public class BuiltinFunctionConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Function";
	
	public BuiltinFunctionConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinFunctionPrototype.get(env),1);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BuiltinFunction.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		StringBuilder b = new StringBuilder();
		b.append("function anonymous(");
		for(int i=0; i<parameters.length-1; i++) {
			if(i>0) {
				b.append(",");
			}
			b.append(RuntimeUtil.toString(getEnvironment(),parameters[i]));
		}
		// A real line terminator, not just ") {\n" directly after the last
		// parameter, is required per spec (CreateDynamicFunction's source
		// template) - without it, a last parameter string ending in a `//`
		// line comment would swallow the following ")" as part of the
		// comment, producing a SyntaxError. See test262 built-ins/Function/
		// prototype/toString/Function.js.
		b.append("\n) {\n");
		if(parameters.length>=1) {
			b.append(RuntimeUtil.toString(getEnvironment(),parameters[parameters.length-1]));
		
		}
		b.append("\n}");
		
		String code = b.toString();
		
		try {
			ASTFunctionDecl fct = getEnvironment().createFunction(code);
			// CreateDynamicFunction: the new function belongs to the realm of
			// THIS Function constructor (the "current realm" while it runs),
			// not the caller's - `new other.Function()` from a different
			// realm must produce a function whose realm is `other` (its
			// GetFunctionRealm drives proto-from-ctor-realm resolution
			// everywhere else). Same realm as the caller in the overwhelmingly
			// common case, where the caller's own main context is reused.
			JSRuntimeContext ambientMain = JSRuntimeContext.get().getMainContext(); // Only exec in main context (program of module)
			JSRuntimeContext parentContext = ambientMain.getEnvironment()==getEnvironment() ? ambientMain : getEnvironment().getRealmContext();
			// Wrapped (not used as-is, even in interpreted mode): the generated function
			// is never lexically nested in the caller, so it must not inherit the
			// caller's strict-mode-ness - see FunctionConstructorParentContext. Note:
			// does NOT eagerly read JSRuntimeContext.get().getThis() here - the caller
			// may be a derived class constructor's super() call (e.g. `class Fn extends
			// Function {}`) where `this` is still TDZ-poisoned; the generated function
			// is never an arrow function, so its parent context's getThis() is never
			// actually consulted at runtime anyway.
			JSInterpretedRuntimeContext ctx = new FunctionConstructorParentContext(parentContext);
			JSResult r = new JSResult();
			ctx.run( () -> {
				fct.evaluate(ctx, r);
			});
			
			// Should have an easier way for this!
			BuiltinFunction ctor = (BuiltinFunction)r.deref();
			JSObject cp = JSObject.create(getEnvironment());
			cp.setOwnProperty(Constructor.CONSTRUCTOR,ctor,PropertyDescriptor.DESC_PROP_CONSTRUCTOR);
			ctor.setOwnProperty(Constructor.PROTOTYPE, cp,PropertyDescriptor.DESC_FUNCTION_PROTOTYPE);
			if(!ctor.isGenuinelyStrictMode()) {
				ctor.setOwnProperty(Arguments.ARGUMENTS, null); // always null, this is deprecated
			}
			// GetPrototypeFromConstructor(newTarget, %Function.prototype%)
			return applyNewTargetPrototype(ctor, topConstructor);
		} catch(JSParseException ex) {
			throw RuntimeUtil.syntaxError(ex.getLocalizedMessage());
		}
	}
}