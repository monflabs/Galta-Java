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

import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionInterpreter;
import org.monflabs.util.generators.Yielder;

/**
 * Function runtime context.
 */
public class InterpretedFunctionRuntimeContext extends InterpretedRuntimeContext implements JSInterpreterFunctionRuntimeContext {
	
	private BuiltinFunctionInterpreter function;
	private JSRuntimeContext callContext;
	private Yielder<Object> yielder;
	private Object _this;
	private boolean strictMode;
	
	// The global context carries some state, like the running one
	// So it must be related to the process and not the module if the function is declared in a module
	// It is extracted from the call context.
	public InterpretedFunctionRuntimeContext(JSRuntimeContext parent, JSRuntimeContext callContext, BuiltinFunctionInterpreter function, Object _this, Object[] values, Constructor newTarget) {
		super(parent,callContext.getGlobalContext());
		this.function = function;
		this.callContext = callContext;
		this._this = _this;
		this.newTarget = newTarget;
		this.strictMode = function.isForceStrictMode() || parent.isStrictMode();
		
		// Add the arguments
		// It can be overridden, so it is not a constant but it cannot be deleted
		ASTFunction fn = function.getFunctionNode();
		// Elide the (relatively expensive) Arguments.create when the parse-time
		// analysis proved the body never reads `arguments`. The binding is still
		// materialized as UNDEFINED so any dynamic path that consults it by name
		// gets a well-defined value rather than a missing slot. Arrow functions
		// keep the inheritance behavior since a nested arrow may still read it.
		boolean needsArgs = fn==null || fn.isArrow() || fn.isUseArguments();
		Object args;
		if(!needsArgs) {
			args = RuntimeUtil.UNDEFINED;
		} else if(fn.isArrow()) {
			// Arrow functions don't have their own arguments object; they inherit the
			// lexically enclosing one. Keep the same variable-slot shape (still SYSTEM,
			// still created) but reuse the parent's value instead of a fresh instance.
			args = parent.getVariableValue(Arguments.ARGUMENTS, RuntimeUtil.UNDEFINED);
		} else if(!strictMode && fn.isSimpleParameterList()) {
			args = Arguments.create(getEnvironment(), values, function, this, fn.getSimpleParameterNames());
		} else {
			// This branch is reached for one of THREE distinct reasons: (1) a
			// genuine "use strict" directive, (2) a non-simple parameter list
			// (rest/default/destructured - unmapped regardless of
			// strictness), or (3) purely GaltaJS's own env-wide dialect
			// toggle (`strictMode`/RuntimeUtil.isStrictMode(), NEVER a real
			// spec concept) forcing the unmapped path for what's otherwise a
			// perfectly ordinary sloppy, simple-parameter function. Per spec
			// 9.4.4.7 CreateUnmappedArgumentsObject, "callee" is the
			// %ThrowTypeError% poison pill for reasons (1) and (2) - but NOT
			// for (3), which must keep its prior plain-`undefined`-value
			// behavior (own test suite's ArgumentsTest.js asserts
			// `arguments.callee === undefined`, readable without throwing,
			// for a plain `function f(){}` under this toggle). Confirmed via
			// test262's ThrowTypeError/unique-per-realm-non-simple.js: a
			// sloppy-mode function with a default parameter DOES still
			// poison callee (reason 2), independent of the toggle.
			boolean poison = function.isGenuinelyStrictMode() || !fn.isSimpleParameterList();
			args = Arguments.create(getEnvironment(), values, strictMode?RuntimeUtil.UNDEFINED:function, poison);
		}
		// Phase 2c: allocate the frame's slot array up front (sized to the
		// function's static VariableDef count) and bind arguments into its
		// declared slot. Later hash-view lookups (getEntry("arguments")) and
		// direct slot reads (getSlot(idx)) share the same storage via the
		// VariableEntryArray alias, so mapped-arguments write-back stays
		// consistent. See BuiltinFunctionInterpreter.bindParametersAndVars,
		// which follows the same pattern for the funcName self-binding,
		// parameters, and var/function hoisting.
		VariableDef argsVar = fn!=null ? fn.getOwnVariable(Arguments.ARGUMENTS) : null;
		if(argsVar!=null) {
			VariableMap vm = getVariableMap(true);
			vm.initSlots(fn.getVariables().size());
			vm.initVariableInSlot(Arguments.ARGUMENTS, argsVar.getJavaVariableIndex(), VAR_TYPE.SYSTEM);
			vm.setSlot(argsVar.getJavaVariableIndex(), args);
		} else {
			createVariable(Arguments.ARGUMENTS, args, VAR_TYPE.SYSTEM);
		}
	}
	
	@Override
	public Object getThis() {
		if(function.isArrow()) {
			// An arrow function has no `this` binding of its own - always defer
			// LIVE to the lexically enclosing scope (exactly like the ordinary,
			// non-function-frame default in AbstractRuntimeContext.getThis()),
			// rather than this class's own constructor-frame-style cached/
			// validated _this field below. This must stay a live delegation on
			// every read, not a value resolved once at call time: an arrow
			// merely being CALLED (e.g. from a `finally` block, before its body
			// - which may itself call `super()` - has run) must not eagerly
			// trip the enclosing derived-class constructor's "not yet
			// initialized" check. See BuiltinFunctionInterpreter.call()'s
			// isArrow() branch for the call-time half of this fix, and
			// RuntimeUtil.superCtor(), which already correctly walks past arrow
			// frames to set `this` on the real enclosing constructor frame.
			return parent.getThis();
		}
		if(function.getClassConstructor()!=null) {
			if(!isThisSet()) {
				throw RuntimeUtil.referenceError("'this' cannot be called before 'super' in a constructor");
			}
		}
		return _this;
	}
	@Override
	public void setThis(Object _this) {
		if(isThisSet()) {
			throw RuntimeUtil.referenceError("Super constructor may only be called once");
		}
		this._this = _this;
	}
	public boolean isThisSet() {
		return _this!=RuntimeUtil.UNDEFINED;
	}
	
	@Override
	public boolean isStrictMode() {
		return strictMode;
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
	public JSRuntimeContext getDebugCallParent() {
		return callContext;
	}
	
	public ASTFunction getFunctionNode() {
		return function.getFunctionNode();
	}

	// True exactly when a spec-conforming implementation would have split
	// this call's parameter environment from its body's own variable
	// environment (see BuiltinFunctionInterpreter.bindParametersAndVars /
	// InterpretedFunctionBodyRuntimeContext) - i.e. this context's own
	// bindings are ONLY the parameters (plus `this`/`arguments`), never the
	// function body's real var-declaration target. Used by
	// StandardLibrary.BaseEvalContext.createVariable() to detect the one
	// window where THIS context (rather than the body's separate frame)
	// legitimately gets resolved as a direct eval's declContext: while a
	// parameter's own default-value expression is still being evaluated,
	// before the body frame is even created. See that call site for why a
	// name collision found here specifically must be a SyntaxError.
	public boolean hasParameterExpressions() {
		ASTFunction fn = function.getFunctionNode();
		return fn!=null && !fn.isSimpleParameterList();
	}
}
