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
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionTranspiler;
import org.monflabs.util.generators.Yielder;

public class TranspiledFunctionRuntimeContext extends TranspiledRuntimeContext implements JSTranspiledFunctionRuntimeContext {
	
	private BuiltinFunctionTranspiler function;
	private Yielder<Object> yielder;
	private Object _this;
	private boolean strictMode;
	private Object[] locals;

	public TranspiledFunctionRuntimeContext(JSRuntimeContext parent, JSRuntimeContext callContext, BuiltinFunctionTranspiler function, Object _this, Constructor newTarget) {
		super(parent,callContext.getGlobalContext());
		this.function = function;
		this._this = _this;
		this.newTarget = newTarget;
		this.strictMode = function.isForceStrictMode() || parent.isStrictMode();
	}
	
	@Override
	public boolean isStrictMode() {
		return strictMode;
	}
	
	@Override
	public Object getThis() {
		return _this;
	}

	@Override
	public void setThis(Object _this) {
		// Mirrors InterpretedFunctionRuntimeContext.setThis()'s identical
		// guard - BindThisValue's "already initialized" check (spec 10.2.2
		// [[Construct]] step: a derived constructor's `this` may only be
		// bound once, by its own super() call). RuntimeUtil.superCtor()
		// (called for both execution modes, unconditionally, from every
		// `super(...)` call site) calls setThis() on this exact context -
		// the ONLY caller of setThis() in either mode - so a second/
		// redundant super() call (e.g. `super(super())`, or one reached via
		// a bound copy of the constructor) now throws here before either
		// the JS-visible `this` value or the transpiled codegen's own local
		// `_this` Java variable (assigned from superCtor()'s return value)
		// ever observes the second call as having succeeded.
		if(this._this!=RuntimeUtil.UNDEFINED) {
			throw RuntimeUtil.referenceError("Super constructor may only be called once");
		}
		this._this = _this;
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
