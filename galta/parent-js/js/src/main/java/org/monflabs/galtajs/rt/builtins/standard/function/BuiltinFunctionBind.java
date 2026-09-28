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

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;


/**
 * Built-in function generated from Function.bind()
 */
public class BuiltinFunctionBind extends BuiltinFunction {

	private Callable boundedCallable;
	private Object boundedThis;
	private Object[] boundedArgs;

	private static String boundName(Object targetName) {
		return targetName instanceof String s ? s : "";
	}

	public BuiltinFunctionBind(JSEnvironment env, BuiltinFunction boundedFunction, Object boundedThis, Object[] boundedArgs) {
		super(
				env,
				// Spec (Function.prototype.bind): targetName is Get(Target,
				// "name") UNCHANGED if it's already a String - any other
				// Type (Symbol, Number, undefined, an ordinary object, ...)
				// becomes the EMPTY string, not a ToString-converted one
				// (verified against instance-name-non-string.js, which
				// expects exactly "bound " - no name suffix at all - for a
				// Symbol-valued target name, among others).
				"bound " + boundName(boundedFunction.getProperty("name")),
				0,
				// Placeholder - the real, spec-correct value (which may be a
				// non-integer double, or +Infinity, neither representable by
				// this constructor's `int length` parameter) is computed and
				// re-set as an own property right below instead.
				0
			);
		// Spec steps 5-8: targetHasLength = HasOwnProperty(Target,"length");
		// if false, L=0 outright (getProperty("length") must NOT be
		// consulted at all - it could resolve a same-named PROTOTYPE
		// property instead, which mustn't count here). Else L is 0 unless
		// targetLen's Type is genuinely Number (a boxed `new Number(...)`
		// wrapper has Type Object, not Number, and must also give L=0,
		// same as any other non-Number value) - confirmed via
		// instance-length-default-value.js. RuntimeUtil.toInt()'s int-based
		// ToInt32-style truncation was also wrong for the actual ToInteger
		// semantics needed here: NaN/-0 -> 0 (already matched by luck),
		// +Infinity must STAY +Infinity (toInt() clamped it to
		// Integer.MAX_VALUE instead), -Infinity -> 0 (toInt() clamped it to
		// Integer.MIN_VALUE, which then underflowed further when subtracting
		// argCount), and a value beyond int32 range (2^31, MAX_SAFE_INTEGER)
		// must be preserved at full precision, not silently truncated -
		// confirmed via instance-length-exceeds-int32.js.
		setOwnProperty("length", computeBoundLength(env, boundedFunction, boundedArgs.length), PropertyDescriptor.DESC_PROP_FCTPROP);

		this.boundedCallable = boundedFunction;
		this.boundedThis = boundedThis;
		this.boundedArgs = boundedArgs;
	}

	// The spec's [[BoundTargetFunction]] internal slot - needed by
	// OrdinaryHasInstance (Function.prototype[Symbol.hasInstance]) to
	// delegate `boundFn instanceof X` checks to the wrapped target rather
	// than boundFn's own (nonexistent) "prototype" property. See test262
	// built-ins/Function/prototype/Symbol.hasInstance/this-val-bound-target.js.
	public Callable getBoundedCallable() {
		return boundedCallable;
	}

	private static Object computeBoundLength(JSEnvironment env, BuiltinFunction boundedFunction, int argCount) {
		if(!boundedFunction.hasOwnProperty("length")) {
			return 0;
		}
		Object targetLen = boundedFunction.getOwnProperty("length");
		if(!(targetLen instanceof Number n) || !RuntimeUtil.isPrimitiveValue(env, targetLen)) {
			return 0;
		}
		double d = n.doubleValue();
		if(Double.isNaN(d)) {
			return 0;
		}
		if(d==Double.POSITIVE_INFINITY) {
			return Double.POSITIVE_INFINITY;
		}
		if(d==Double.NEGATIVE_INFINITY) {
			return 0;
		}
		// ToIntegerOrInfinity: truncate toward zero (magnitude floor, same sign).
		double targetLenAsInt = d<0 ? Math.ceil(d) : Math.floor(d);
		return Math.max(targetLenAsInt-argCount, 0);
	}
	public BuiltinFunctionBind(JSEnvironment env, Callable boundedCallable, Object boundedThis, Object[] boundedArgs) {
		super(env,"bound callable",0,0);

		this.boundedCallable = boundedCallable;
		this.boundedThis = boundedThis;
		this.boundedArgs = boundedArgs;
	}

	// A bound function's own "caller"/"arguments" must always be a
	// %ThrowTypeError% poison pill (Annex B, BoundFunctionExoticObject),
	// regardless of the target function's own strictness.
	@Override
	protected boolean poisonsCallerArguments() {
		return true;
	}

	@Override
	protected Object call(Object _this, @NonNull Object[] parameters, Constructor newTarget) {
		if(boundedArgs.length==0) {
			return boundedCallable.call(boundedThis,parameters);
		}
		if(parameters.length==0) {
			return boundedCallable.call(boundedThis,boundedArgs);
		}
		Object[] p = new Object[boundedArgs.length+parameters.length];
		System.arraycopy(boundedArgs, 0, p, 0, boundedArgs.length);
		System.arraycopy(parameters, 0, p, boundedArgs.length, parameters.length);
		return boundedCallable.call(boundedThis,p);
	}
	
	// BoundFunctionCreate (10.4.1.3) only wires up [[Construct]] at all when
	// the wrapped target itself has one.
	@Override
	public boolean isConstructor() {
		return boundedCallable instanceof Constructor c && c.isConstructor();
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		if(!(boundedCallable instanceof Constructor ctor)) {
			throw RuntimeUtil.typeError("Object '{0}' is not constructable",boundedCallable);
		}
		// BoundFunctionExoticObject [[Construct]] (10.4.1.2): "If
		// SameValue(F, newTarget) is true, set newTarget to target" - a
		// `new`/Reflect.construct call whose newTarget is THIS bound
		// wrapper itself (the common case: `new boundFn()`, or a
		// bound-of-a-bound chain where some link's newTarget is still
		// itself) must see the ORIGINAL wrapped constructor as new.target,
		// not the wrapper - any OTHER newTarget (e.g. an explicit
		// Reflect.construct(..., SomeOtherClass)) passes through unchanged.
		Constructor effectiveNewTarget = topConstructor==this ? ctor : topConstructor;
		// [[Construct]] never uses [[BoundThis]] (only [[Call]] does) - just
		// [[BoundArgs]] prepended to the new call's own arguments.
		if(boundedArgs.length==0) {
			return ctor.constructObject(parameters,effectiveNewTarget);
		}
		if(parameters.length==0) {
			return ctor.constructObject(boundedArgs,effectiveNewTarget);
		}
		Object[] p = new Object[boundedArgs.length+parameters.length];
		System.arraycopy(boundedArgs, 0, p, 0, boundedArgs.length);
		System.arraycopy(parameters, 0, p, boundedArgs.length, parameters.length);
		return ctor.constructObject(p,effectiveNewTarget);
	}

}
