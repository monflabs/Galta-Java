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
package org.monflabs.galtajs.rt.builtins.standard.arguments;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.ThrowTypeErrorFunction;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.iterators.Iterators;

/**
 * List of arguments.
 *
 * When the owning function is non-strict and has a simple parameter list (no rest,
 * default or destructured parameters), indices are "mapped": reading/writing
 * {@code arguments[i]} reads/writes the corresponding named parameter binding
 * (spec 9.4.4 Arguments Exotic Objects). The mapping for an index is severed as soon
 * as that index is redefined with an accessor or a {@code writable:false} descriptor.
 *
 * Internally, mapping is represented as a {@code VarAccessor[]} (one accessor per
 * argument position, {@code null} for an unmapped position) rather than a
 * {@code (JSRuntimeContext scope, String[] mappedNames)} pair - a single, mode-
 * agnostic "get/set this specific binding" shape both execution modes can supply:
 * TRANSPILED mode builds a {@code JSVarRef} directly against the owning function's
 * own {@code Object[]} local-array slot (no deferred/by-name lookup needed - a
 * transpiled local's array+index is already fixed at codegen time, unlike an
 * interpreted binding, which doesn't exist in its {@code VariableMap} until AFTER
 * this object is constructed - see {@code InterpretedFunctionRuntimeContext}'s own
 * call site). INTERPRETED mode keeps its existing (scope,name) call shape via the
 * {@link #create(JSEnvironment,Object[],Object,JSRuntimeContext,String[])} overload,
 * which wraps each name into a small deferred-lookup {@code VarAccessor} internally.
 */
public final class Arguments extends NativeObject implements Iterable<Object> {

	public static final String ARGUMENTS = "arguments";

	public static Arguments create(JSEnvironment env, Object[] values) {
		return new Arguments(env,values,RuntimeUtil.UNDEFINED,null,false);
	}
	public static Arguments create(JSEnvironment env, Object[] values, Object callee) {
		return new Arguments(env,values,callee,null,false);
	}
	public static Arguments create(JSEnvironment env, Object[] values, Object callee, boolean poisonPillCallee) {
		return new Arguments(env,values,callee,null,poisonPillCallee);
	}
	// Interpreted mode - a binding named mappedNames[i] doesn't exist in
	// `scope`'s own VariableMap yet at this exact call site (parameter
	// binding happens afterward), so lookup must stay deferred/by-name;
	// each accessor is a thin, lazily-resolving wrapper around scope's own
	// (already correctly deferred) getVariableValue/setVariable calls.
	public static Arguments create(JSEnvironment env, Object[] values, Object callee, JSRuntimeContext scope, String[] mappedNames) {
		VarAccessor[] mappedAccessors = null;
		if(mappedNames!=null) {
			mappedAccessors = new VarAccessor[mappedNames.length];
			for(int i=0; i<mappedNames.length; i++) {
				String name = mappedNames[i];
				if(name!=null) {
					mappedAccessors[i] = new VarAccessor() {
						@Override
						public String getKey() {
							return name;
						}
						@Override
						public Object getValue() {
							return scope.getVariableValue(name, RuntimeUtil.UNDEFINED);
						}
						@Override
						public Object setValue(Object value) {
							scope.setVariable(name, value);
							return value;
						}
					};
				}
			}
		}
		return new Arguments(env,values,callee,mappedAccessors,false);
	}
	// Transpiled mode - each accessor is a JSVarRef directly against the
	// owning function's own local-array slot; no scope/deferred lookup
	// needed (see class doc).
	public static Arguments create(JSEnvironment env, Object[] values, Object callee, VarAccessor[] mappedAccessors) {
		return new Arguments(env,values,callee,mappedAccessors,false);
	}

	private int length;
	private Object callee; // for non strict mode

	// Mapped-arguments support (null when this is an unmapped arguments object)
	private VarAccessor[] mappedAccessors;
	private boolean[] disconnected;

	private Arguments(JSEnvironment env, Object[] values, Object callee, VarAccessor[] mappedAccessors, boolean poisonPillCallee) {
		super(env);
		this.length = values.length;
		this.callee = callee;
		this.mappedAccessors = mappedAccessors;
		this.disconnected = mappedAccessors!=null ? new boolean[mappedAccessors.length] : null;

		if(poisonPillCallee) {
			// Genuinely strict-mode functions expose "callee" as a poison-pill accessor (spec 9.4.4.6),
			// not a plain value -- reading or writing it always throws. Both getter AND setter must be
			// the EXACT SAME %ThrowTypeError% singleton object (test262's unique-per-realm-*.js) - unlike
			// the setOwnProperty(id,configurable,enumerable,getterLambda,setterLambda) convenience helper
			// (which always wraps each lambda in a FRESH BaseGetter/BaseSetter object), this passes the
			// SAME ThrowTypeErrorFunction instance for both slots directly.
			ThrowTypeErrorFunction thrower = ThrowTypeErrorFunction.get(env);
			setOwnProperty("callee", RuntimeUtil.NOT_AVAILABLE, PropertyDescriptor.of(true,false,false,thrower,thrower));
		} else {
			super.setOwnProperty("callee", callee, PropertyDescriptor.of(true,true,false), DESC_CHECK.NONE, this);
		}
		super.setOwnProperty("length", length, PropertyDescriptor.of(true,true,false), DESC_CHECK.NONE, this);

		// The %Array.prototype.values% INTRINSIC (spec 9.4.4 Create{Unm,M}apped
		// ArgumentsObject step "...@@iterator, PropertyDescriptor {[[Value]]:
		// %ArrayProto_values%...}") - a fixed reference captured once per
		// realm, NOT Array.prototype's own (mutable, user-reassignable)
		// current [Symbol.iterator]/.values property value. Reading the LIVE
		// property here instead used to let a PRIOR `Array.prototype[Symbol.
		// iterator] = ...` reassignment leak into every arguments object
		// created afterward - real engines never do this, since the
		// intrinsic identity is fixed at realm-creation, immune to later
		// prototype mutation (test262 language/statements/class/subclass/
		// default-constructor-spread-override.js: the auto-generated default
		// derived constructor's `super(...arguments)` must NOT observe a
		// user-poisoned Array.prototype[Symbol.iterator]).
		Object arrayIterator = BuiltinArrayPrototype.get(env).getValuesMethod();
		super.setOwnProperty(Symbol.ITERATOR, arrayIterator, PropertyDescriptor.of(true,true,false), DESC_CHECK.NONE, this);

		for(int i=0; i<values.length; i++) {
			super.setOwnProperty(Integer.toString(i), values[i], PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NONE, this);
		}
	}

	@Override
	public String getClassName() {
		return "Arguments";
	}

	@Override
	public String toString() {
		return Integer.toString(length);
	}

	@Override
	public int size() {
		return length;
	}

	public Object getCallee() {
		return callee;
	}

	public Object get(int index) {
		return getOwnProperty(Integer.toString(index), RuntimeUtil.UNDEFINED, this);
	}

	public Object[] toArray() {
		Object[] result = new Object[length];
		for(int i=0; i<length; i++) {
			result[i] = get(i);
		}
		return result;
	}

	@Override
	public Iterator<Object> iterator() {
		return Iterators.map(Iterators.intSequence(0,length), this::get);
	}


	//
	// Mapped-arguments linkage (spec 9.4.4)
	//

	private int mappedIndexOf(String key) {
		if(mappedAccessors==null) {
			return -1;
		}
		long index = RuntimeUtil.memberIndex(key);
		// Bound by the mapped slots created at call time, not by the "length"
		// property: arguments.length=0 does not disconnect the parameters
		if(index<0 || index>=mappedAccessors.length) {
			return -1;
		}
		int idx = (int)index;
		if(mappedAccessors[idx]==null || disconnected[idx]) {
			return -1;
		}
		return idx;
	}


	//
	// Accessor
	//

	@Override
	public Object getOwnProperty(String key, Object defaultValue, Object receiver) {
		Object v = super.getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v==RuntimeUtil.NOT_AVAILABLE) {
			return defaultValue;
		}
		int idx = mappedIndexOf(key);
		return idx>=0 ? mappedAccessors[idx].getValue() : v;
	}

	@Override
	public Object getOwnProperty(long index, Object defaultValue, Object receiver) {
		if(RuntimeUtil.isMemberIndex(index)) {
			return getOwnProperty(Long.toString(index), defaultValue, receiver);
		}
		return super.getOwnProperty(RuntimeUtil.memberIndex(index),defaultValue,receiver);
	}

	@Override
	public boolean setOwnProperty(String key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		int idx = mappedIndexOf(key);
		if(idx>=0 && value==RuntimeUtil.NOT_AVAILABLE && descriptor!=null && descriptor.isData() && !descriptor.isWritable()) {
			// About to go non-writable (and so disconnect): freeze the live mapped value
			// into the stored property now, since nothing will sync it afterwards (spec 9.4.4.2 step 4).
			value = mappedAccessors[idx].getValue();
		}
		boolean ok = super.setOwnProperty(key, value, descriptor, check, receiver);
		if(ok && idx>=0) {
			if(descriptor!=null && descriptor.isAccessor()) {
				disconnected[idx] = true;
			} else {
				if(value!=RuntimeUtil.NOT_AVAILABLE) {
					mappedAccessors[idx].setValue(value);
				}
				if(descriptor!=null && !descriptor.isWritable()) {
					disconnected[idx] = true;
				}
			}
		} else if(ok && "length".equals(key) && value!=RuntimeUtil.NOT_AVAILABLE) {
			// "length" is an ordinary (non-mapped) own property - keep the
			// internal `length` field (used by size()/get()/toArray()/
			// iterator()/mappedIndexOf() for every array-like operation,
			// including the shared %ArrayIteratorPrototype%.next's live
			// LengthOfArrayLike re-check) in sync with whatever value JS
			// code just wrote, e.g. "arguments.length = 4;" - previously
			// this only updated the property map entry, leaving `length`
			// permanently stale at its original value.
			length = RuntimeUtil.toInt(value);
		}
		return ok;
	}

	@Override
	public boolean deleteProperty(String key, DESC_CHECK check) {
		int idx = mappedIndexOf(key);
		boolean ok = super.deleteProperty(key, check);
		if(ok && idx>=0) {
			disconnected[idx] = true;
		}
		return ok;
	}

	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		Iterator<Map.Entry<Object,Object>> base = super.ownPropertyEntries(strings, symbols, enumerableOnly);
		if(mappedAccessors==null) {
			return base;
		}
		return Iterators.map(base, (Map.Entry<Object,Object> e) -> {
			if(e.getKey() instanceof String s) {
				int idx = mappedIndexOf(s);
				if(idx>=0) {
					return JSAccessor.newEntry(s, mappedAccessors[idx].getValue());
				}
			}
			return e;
		});
	}
}
