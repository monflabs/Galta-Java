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
package org.monflabs.galtajs.rt.interpreter;

import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.util.generators.Yielder;

/**
 * Runtime scope for evaluating a class field initializer (static or
 * instance), used by ASTClassDecl's initClass()/initInstance() callbacks.
 *
 * A field initializer is not a method, but the spec still runs it as if it
 * were one: it needs a genuine [[HomeObject]] so `super.prop` (and any
 * closure created within the initializer body, e.g. an arrow function or
 * eval'd code) resolves correctly, while `super(...)` (a constructor call)
 * must still throw, since a field initializer has no [[ClassConstructor]]/
 * [[SuperConstructor]] binding of its own.
 *
 * This is done by making this context itself a JSFunctionContext, backed by
 * a synthetic, never-invoked BuiltinFunction whose only role is to carry
 * [[HomeObject]] for RuntimeUtil.getSuper() to find (via
 * AbstractRuntimeContext.getFunctionContext(), which walks up the parent
 * chain for the first JSFunctionContext - starting at itself). The synthetic
 * function's getClassConstructor() is never set (stays null), so
 * RuntimeUtil.superCtor() still correctly throws "super() can only be used
 * in a constructor method" for a bare `super(...)` inside a field
 * initializer.
 *
 * A named class is required here (not an anonymous InterpretedBlockRuntimeContext
 * subclass, as previously used) because Java does not allow an anonymous
 * class to both extend a class and implement an additional interface.
 *
 * As a side effect, this also makes new.target correctly evaluate to
 * undefined inside a field initializer (per spec: field initializers are
 * invoked via [[Call]], not [[Construct]], even though they run as part of
 * object construction) - this context's own newTarget field is never set, so
 * ASTNewMember's lookup (via getFunctionContext().getNewTarget()) now
 * terminates here instead of incorrectly leaking the newTarget of whatever
 * outer function context happened to enclose the class declaration.
 */
public class InterpretedFieldInitializerRuntimeContext extends InterpretedBlockRuntimeContext implements JSFunctionContext {

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

	public InterpretedFieldInitializerRuntimeContext(JSInterpretedRuntimeContext parent, Object homeObject, Object thisValue) {
		super(parent);
		this.thisValue = thisValue;
		this.function = new FieldInitializerFunction(parent, homeObject);
	}
	// homeObjectCarrier is the synthetic function built by createHomeObjectCarrier()
	// below - lets a caller that constructs one instance per field/instance
	// initializer run (e.g. ASTClassDecl.initInstance(), called once per `new`)
	// reuse a single carrier across every run that shares the same (parent,
	// homeObject) pair, instead of allocating a fresh one every time.
	public InterpretedFieldInitializerRuntimeContext(JSInterpretedRuntimeContext parent, Object thisValue, BuiltinFunction homeObjectCarrier) {
		super(parent);
		this.thisValue = thisValue;
		this.function = homeObjectCarrier;
	}
	public static BuiltinFunction createHomeObjectCarrier(JSInterpretedRuntimeContext parent, Object homeObject) {
		return new FieldInitializerFunction(parent, homeObject);
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
}
