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

import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.util.generators.Yielder;

/**
 * Transpiled-mode counterpart of
 * {@code org.monflabs.galtajs.rt.interpreter.InterpretedFieldInitializerRuntimeContext}
 * - see that class's own doc for the full spec rationale. A class static
 * initialization block or field initializer (static or instance) is not a
 * method, but the spec still runs it as if it were one: it needs a genuine
 * [[HomeObject]] so `super.prop` (and any closure created within the body,
 * e.g. an arrow function or eval'd code) resolves correctly, while
 * `super(...)` (a constructor call) must still throw, since neither has a
 * [[ClassConstructor]]/[[SuperConstructor]] binding of its own.
 *
 * Made a JSFunctionContext itself (via a synthetic, never-invoked
 * BuiltinFunction whose only role is to carry [[HomeObject]]), exactly like
 * the interpreted counterpart, so RuntimeUtil.getSuper()'s parent-chain walk
 * (AbstractRuntimeContext.getFunctionContext(), which stops at the first
 * JSFunctionContext) finds this context's HomeObject instead of leaking
 * through to whatever function/context happened to lexically enclose the
 * class declaration - which, for a static block/field initializer's inlined
 * transpiled codegen (unlike an ordinary method, which is always its own
 * separately-generated Java method with its own fresh
 * TranspiledFunctionRuntimeContext), is otherwise the only context in reach.
 * Callers install an instance of this class by shadowing the local Java
 * variable named `_ctx` (JSTranspiler.MAIN_CONTEXT) for the body's own
 * block scope - see ASTClassStaticBlock/ASTClassField's transpiled codegen.
 *
 * The synthetic function's getClassConstructor() is never set (stays null),
 * so RuntimeUtil.superCtor() still correctly throws "super() can only be
 * used in a constructor method" for a bare `super(...)`. This context's own
 * newTarget field is also never set, so new.target correctly evaluates to
 * undefined inside the body (field initializers/static blocks run via
 * [[Call]], not [[Construct]], even though they execute as part of object
 * construction).
 */
public class TranspiledFieldInitializerRuntimeContext extends TranspiledRuntimeContext implements JSTranspiledFunctionRuntimeContext {

	// Synthetic - never actually called. Exists solely to carry [[HomeObject]].
	private static final class FieldInitializerFunction extends BuiltinFunction {
		FieldInitializerFunction(JSRuntimeContext parentCtx, Object homeObject) {
			super(parentCtx, null, 0, 0);
			setHomeObject(homeObject);
		}
		@Override
		protected Object call(Object _this, Object[] parameters, Constructor newTarget) {
			throw new IllegalStateException("Field-initializer synthetic function is not callable");
		}
	}

	private final Object thisValue;
	private final BuiltinFunction function;
	private Yielder<Object> yielder;
	private Object[] locals;
	// The class's own PrivateEnvironment (TranspiledClassPrivateScopeRuntimeContext,
	// "classScope_N"), used ONLY by resolvePrivateName() below - deliberately
	// NOT folded into `parent` (which drives getThis()/getSurroundingContext()/
	// ordinary identifier resolution via AbstractRuntimeContext's parent-chain
	// walk): reparenting this context's own `parent` onto classScope_N wholesale
	// would fix a direct eval's private-name visibility but regresses eval/
	// super/new.target resolution, since those paths depend on `parent`/
	// getThis()/getFunctionContext() being untouched. Threading classScope_N
	// through this SEPARATE, narrowly-scoped field instead leaves those paths
	// completely unaffected. Null for a class with no private members (nothing
	// to resolve), or when a caller predates this constructor overload.
	private final JSRuntimeContext privateNameScope;

	public TranspiledFieldInitializerRuntimeContext(JSRuntimeContext parent, Object homeObject, Object thisValue) {
		this(parent, homeObject, thisValue, null);
	}

	public TranspiledFieldInitializerRuntimeContext(JSRuntimeContext parent, Object homeObject, Object thisValue, JSRuntimeContext privateNameScope) {
		super(parent);
		this.thisValue = thisValue;
		this.function = new FieldInitializerFunction(parent, homeObject);
		this.privateNameScope = privateNameScope;
	}

	// Direct (non-eval) private-name access is always compiled straight to a
	// baked-in PrivateName token (see clazz.getOrCreatePrivateName() call
	// sites in ASTClassField's generated code) - this override is exercised
	// ONLY when a direct eval's dynamically-interpreted text (which has no
	// compile-time-known token) reads a private name from INSIDE a field
	// initializer's own body, via ASTMember.readProperty ->
	// context.resolvePrivateName(). See ASTClassField.transpileInitInstanceStatement
	// for where privateNameScope is threaded in as classScope_N.
	@Override
	public PrivateName resolvePrivateName(String name) {
		if(privateNameScope!=null) {
			return privateNameScope.resolvePrivateName(name);
		}
		return super.resolvePrivateName(name);
	}

	@Override
	public Object getThis() {
		return thisValue;
	}

	@Override
	public BuiltinFunction getFunction() {
		return function;
	}

	@Override
	public Yielder<Object> getYielder() {
		return yielder;
	}
	@Override
	public void setYielder(Yielder<Object> yielder) {
		this.yielder = yielder;
	}

	@Override
	public Object[] getLocals() {
		return locals;
	}
	@Override
	public void setLocals(Object[] locals) {
		this.locals = locals;
	}
}
