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
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.HomeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;


/**
 * Runtime script function.
 */
public abstract class BuiltinFunction extends BaseCallableObject implements HomeObject, Constructor {
	
	public static final int ARROW 		= 0x0001;
	public static final int GENERATOR 	= 0x0002;
	public static final int ASYNC 		= 0x0004;
	public static final int STRICT_MODE	= 0x0008;
	// Like STRICT_MODE, but only set from an actual "use strict" directive (or being a class member),
	// never from JSEnvironment.isStrictMode(). See ASTNode.InitContext.isGenuinelyStrict().
	public static final int GENUINE_STRICT_MODE = 0x0010;
	// A MethodDefinition (object-literal shorthand method/getter/setter, or a class
	// method/getter/setter - NOT the class's own "constructor" method, which is built
	// into a distinct BuiltinClassConstructor rather than a plain BuiltinFunction) has
	// no [[Construct]] per spec - see ASTFunctionMethod.isMethod().
	public static final int METHOD = 0x0020;
	
	private JSRuntimeContext parentCtx;
	private Object homeObject;
	private int modifiers;
	private BuiltinClassConstructor classConstructor;
	
	public BuiltinFunction(JSRuntimeContext parentCtx, String name, int modifiers, int length) {
		this(parentCtx.getEnvironment(),name,modifiers,length);
		this.parentCtx = parentCtx;
	}
	public BuiltinFunction(JSEnvironment env, String name, int modifiers, int length) {
		super(env);
		this.modifiers = modifiers;
		// Own-property insertion order matters for Object.getOwnPropertyNames();
		// spec-created function objects get "length" before "name".
		setOwnProperty("length",length,PropertyDescriptor.DESC_PROP_FCTPROP);
		// Anonymous functions/arrows (no name yet inferred) have a "name" of "", not null (spec: SetFunctionName).
		setOwnProperty("name",name!=null?name:"",PropertyDescriptor.DESC_PROP_FCTPROP);
	}
	
	
	public JSRuntimeContext getParentContext() {
		return parentCtx;
	}
	
	public int getModifiers() {
		return modifiers;
	}
	public void setModifiers(int modifiers) {
		this.modifiers = modifiers;
	}
	
	// Function.prototype.toString() fallback for a script function whose exact
	// original source text is unavailable (source not tracked at parse/transpile
	// time, or the recorded offsets are out of range). Shape-preserving so
	// toString() still returns well-formed `function name() { [unavailable] }`
	// rather than the [native code] form reserved for genuine built-ins. Shared
	// by both the interpreted and transpiled function implementations.
	protected String unavailableSource() {
		String name = getFunctionName();
		return "function " + (name!=null ? name : "") + "() { [unavailable] }";
	}

	public boolean isGenerator() {
		return (modifiers & GENERATOR)!=0;
	}
	public boolean isAsync() {
		return (modifiers & ASYNC)!=0;
	}
	public boolean isArrow() {
		return (modifiers & ARROW)!=0;
	}
	public boolean isMethod() {
		return (modifiers & METHOD)!=0;
	}
	public boolean isForceStrictMode() {
		return (modifiers & STRICT_MODE)!=0;
	}
	public boolean isGenuinelyStrictMode() {
		return (modifiers & GENUINE_STRICT_MODE)!=0;
	}

	// Spec IsConstructor(): a generator/async/arrow function or a
	// MethodDefinition has no [[Construct]] at all (same condition
	// constructObject() below already throws on) - the Constructor
	// interface's own default (isConstructor()==true) is only correct for
	// an ordinary function/class, so every OTHER kind must override it here
	// rather than leaving every caller of isConstructor() (Reflect.construct
	// target/newTarget validation, a Proxy's targetConstructible flag,
	// class-extends validation) to independently rediscover this.
	@Override
	public boolean isConstructor() {
		return !(isGenerator() || isAsync() || isArrow() || isMethod());
	}

	@Override
	public Object getHomeObject() {
		return homeObject;
	}
	@Override
	public void setHomeObject(Object homeObject) {
		this.homeObject = homeObject;
	}
	
	public BuiltinClassConstructor getClassConstructor() {
		return classConstructor;
	}
	public void setClassConstructor(BuiltinClassConstructor classConstructor) {
		this.classConstructor = classConstructor;
	}
	
	public Constructor getSuperConstructor() {
		// classConstructor.getPrototype() is ordinarily the actual superclass
		// constructor (whatever `extends X` evaluated to) - but a class's
		// [[Prototype]] can be reassigned after the fact (e.g.
		// `Object.setPrototypeOf(C, someNonConstructorFunction)`), so an
		// unconditional cast here could hit a Java value that doesn't
		// implement Constructor at all (a raw ClassCastException, surfacing
		// as a generic JS Error rather than the spec-mandated TypeError).
		// Returning null instead lets the existing "superConstructor==null"
		// check in RuntimeUtil.superCtor() raise the correct TypeError - see
		// test262 language/expressions/super/call-proto-not-ctor.js.
		Object proto = classConstructor.getPrototype();
		return proto instanceof Constructor c ? c : null;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinFunctionPrototype.get(getEnvironment());
	}
	
	// Whether THIS function's own "caller"/"arguments" must be a
	// %ThrowTypeError% poison pill. Ordinarily that's a function of its own
	// strictness/arrow-ness (not the ambient calling context's strictness) -
	// but a bound function (Function.prototype.bind()) must poison both
	// unconditionally, regardless of the target function's strictness
	// (Annex B, BoundFunctionExoticObject), so BuiltinFunctionBind overrides
	// this to always return true.
	//
	// Generator/async/method functions are ALSO always poisoned, regardless
	// of strictness - the real "arguments"/"caller" OWN data properties
	// (value null/current-caller, no throw) are a legacy, non-strict-
	// SIMPLE-function-only quirk (confirmed via V8: a plain sloppy
	// `function f(){}` has its own "caller"/"arguments"; a sloppy
	// `function* g(){}`/`async function a(){}`/an object/class method do
	// not, and reading either throws TypeError like any other function
	// inheriting %Function.prototype%'s poison-pill accessor) - test262
	// language/statements/generators/restricted-properties.js.
	protected boolean poisonsCallerArguments() {
		return isGenuinelyStrictMode() || isArrow() || isGenerator() || isAsync() || isMethod();
	}

	@Override
	public final PropertyDescriptor getOwnPropertyDescriptor(String member) {
		if(Arguments.ARGUMENTS.equals(member) || "caller".equals(member)) {
			if(poisonsCallerArguments()) {
				return null;
			}
		}
		return super.getOwnPropertyDescriptor(member);
	}

	@Override
	public Object getOwnProperty(String member, Object defaultValue, Object receiver) {
		// Must check THIS function (the current level of whatever prototype-
		// chain walk got us here) for self-poisoning, not `receiver` (the
		// ORIGINAL object the property access started on - only relevant for a
		// SuperProperty/Proxy-receiver mismatch): a self-poisoned instance
		// (strict/arrow/bound function) genuinely has no own "caller"/
		// "arguments" property at all (see restricted-properties.js's
		// hasOwnProperty(...)===false assertions), so throw directly here
		// instead of ever creating/consulting a real property.
		if(Arguments.ARGUMENTS.equals(member) || "caller".equals(member)) {
			if(poisonsCallerArguments()) {
				throw RuntimeUtil.typeError("function.{0} is not available", member);
			}
			// %Function.prototype% ITSELF (not just any non-self-poisoned
			// function) now carries a real, shared %ThrowTypeError% accessor
			// (see BuiltinFunctionPrototype's constructor) - fall through to
			// super so Object.getOwnPropertyDescriptor(Function.prototype,...)/
			// Function.prototype.arguments/.caller actually consult it. Every
			// OTHER non-self-poisoned function kind (ordinary, and - a
			// deliberate, narrower-than-spec choice, see
			// StrictModeExhaustiveTest.js's testSloppy_ArgumentsCallee_Caller -
			// also non-strict generators/async/etc.) keeps GaltaJS's existing,
			// intentionally-quiet legacy behavior: report as absent WITHOUT
			// inheriting %Function.prototype%'s throwing accessor, rather than
			// spec's stricter "every function kind inherits-and-throws" model
			// (see KnownGaps.md's %ThrowTypeError% entry - full inheritance
			// was attempted and reverted for test-order-dependent flakiness,
			// so it's deliberately not attempted again here).
			if(!(this instanceof BuiltinFunctionPrototype)) {
				return RuntimeUtil.UNDEFINED;
			}
		}
		return super.getOwnProperty(member,defaultValue,receiver);
	}

	@Override
	public boolean setOwnProperty(String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(Arguments.ARGUMENTS.equals(member) || "caller".equals(member)) {
			if(receiver instanceof BuiltinFunction f) {
				if(f.poisonsCallerArguments()) {
					throw RuntimeUtil.typeError("function.{0} is not available", member);
				}
			}
		}
		return super.setOwnProperty(member, value, desc, check, receiver);
	}


	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		if(isGenerator() || isAsync() || isArrow() || isMethod()) {
			// Generator/async/arrow functions and MethodDefinitions (object-literal
			// shorthand methods/getters/setters, class methods/getters/setters) have no
			// [[Construct]] per spec.
			throw RuntimeUtil.typeError("{0} is not a constructor", getProperty("name",""));
		}
		// OrdinaryCreateFromConstructor(newTarget, "%Object.prototype%"): the
		// new object's [[Prototype]] must come from newTarget's OWN
		// "prototype" property - which, for an ordinary `new Fn()`/`Fn()`-as-
		// constructor call, IS this function itself, but for
		// `Reflect.construct(Fn, args, NewTarget)` (or a derived-class
		// super() call) is a DIFFERENT constructor entirely. Previously
		// always read THIS function's own "prototype", ignoring
		// topConstructor - confirmed via
		// built-ins/Reflect/construct/return-with-newtarget-argument.js.
		Object prototype = RuntimeUtil.getPrototypeFromConstructor(getEnvironment(), topConstructor!=null ? topConstructor : this, org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype.get(getEnvironment()));
		JSObject _this = JSObject.createWithPrototype(getEnvironment(),prototype);

		Object v = call(_this,parameters,topConstructor);
		if(v==null || v==RuntimeUtil.UNDEFINED) {
			return _this;
		}
		return RuntimeUtil.isPrimitiveValue(getEnvironment(),v) ? _this : v;
	}

	@Override
	public final Object call(Object _this, Object[] parameters) {
		return call(_this, parameters, null);
	}

	protected abstract Object call(Object _this, Object[] parameters, Constructor newTarget);


	@Override
	public Object constructArray(int dimensions, long size) {
		throw RuntimeUtil.typeError("Builtin Function is not an array constructor");
	}
}
