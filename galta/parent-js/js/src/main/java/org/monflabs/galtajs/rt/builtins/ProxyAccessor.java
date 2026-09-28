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
package org.monflabs.galtajs.rt.builtins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.proxy.BuiltinProxy;
import org.monflabs.util.iterators.Iterators;

/**
 * 
 */
public class ProxyAccessor extends JSAccessor {
	
	public static final String GET_PROTOTYPE_OF = "getPrototypeOf";
	public static final String SET_PROTOTYPE_OF = "setPrototypeOf";
	public static final String IS_EXTENSIBLE = "isExtensible";
	public static final String PREVENT_EXTENSIONS = "preventExtensions";
	public static final String GET_OWN_PROPERTY = "getOwnPropertyDescriptor";
	public static final String DEFINE_OWN_PROPERTY = "defineProperty";
	public static final String HAS_PROPERTY = "has";
	public static final String GET = "get";
	public static final String SET = "set";
	public static final String DELETE = "deleteProperty";
	public static final String OWN_PROPERTY_KEYS = "ownKeys";
	
	public static final String CALL = "apply";
	public static final String CONSTRUCT = "construct";
	public static final String CONSTRUCT_ARRAY = "constructArray";
	
	public ProxyAccessor(JSEnvironment env) {
		super(env);
	}

	@Override
	public String getClassName(Object _this) {
		BuiltinProxy proxy = getProxy(_this);
		return proxy.getClassName();
	}
	
	private BuiltinProxy getProxy(Object _this) {
		BuiltinProxy proxy = (BuiltinProxy)_this;
		if(proxy.isRevoked()) {
			throw RuntimeUtil.typeError("Cannot access a revoked proxy");
		}
		return proxy;
	}

	
	// [[GetPrototypeOf]]
	@Override
	public Object getPrototype(Object _this) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET_PROTOTYPE_OF);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call: the trap may revoke the proxy as
			// a side effect, and target/handler must reflect the pre-call state.
			Object target = proxy.getTarget();
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object result = fct.call(proxy.getHandler(), new Object[] { target });
			Object handlerProto;
			// Only actual null is a valid "no prototype" result - undefined
			// is neither an object nor null, so it must be rejected too.
			if(result==null) {
				handlerProto = null;
			} else if(RuntimeUtil.isObject(env, result)) {
				handlerProto = result;
			} else {
				throw RuntimeUtil.typeError("Proxy handler's '{0}' trap must return an object or null, got '{1}'", GET_PROTOTYPE_OF, RuntimeUtil.objectTypeName(env, result));
			}
			// Invariant: a non-extensible target's prototype can't be spoofed.
			if(targetAcc.isExtensible(target)) {
				return handlerProto;
			}
			Object targetProto = targetAcc.getPrototype(target);
			if(!RuntimeUtil.eqSameValue(env, handlerProto, targetProto)) {
				throw RuntimeUtil.typeError("'getPrototypeOf' on proxy: proxy target is non-extensible but the trap did not return its actual prototype");
			}
			return handlerProto;
		}
		return proxy.getTargetAccessor().getPrototype(proxy.getTarget());
	}

	// [[SetPrototypeOf]]
	@Override
	public boolean setPrototype(Object _this, Object prototype) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(SET_PROTOTYPE_OF);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			Object target = proxy.getTarget();
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, prototype });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(!booleanTrapResult) {
				return false;
			}
			// Invariant: can't change a non-extensible target's prototype.
			if(targetAcc.isExtensible(target)) {
				return true;
			}
			Object targetProto = targetAcc.getPrototype(target);
			if(!RuntimeUtil.eqSameValue(env, prototype, targetProto)) {
				throw RuntimeUtil.typeError("'setPrototypeOf' on proxy: trap returned truish for setting a new prototype on the non-extensible proxy target");
			}
			return true;
		}
		return proxy.getTargetAccessor().setPrototype(proxy.getTarget(),prototype);
	}

	// [[IsExtensible]]
	@Override
	public boolean isExtensible(Object _this) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(IS_EXTENSIBLE);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			Object target = proxy.getTarget();
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object result = fct.call(proxy.getHandler(), new Object[] { target });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			// Invariant: the trap's answer must match the target's actual extensibility.
			boolean targetResult = targetAcc.isExtensible(target);
			if(booleanTrapResult!=targetResult) {
				throw RuntimeUtil.typeError("'isExtensible' on proxy: trap result does not reflect extensibility of proxy target");
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().isExtensible(proxy.getTarget());
	}

	// [[PreventExtensions]]
	@Override
	public boolean preventExtensions(Object _this) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(PREVENT_EXTENSIONS);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			Object target = proxy.getTarget();
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object result = fct.call(proxy.getHandler(), new Object[] { target });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			// Invariant: a truish result must actually have made the target non-extensible.
			if(booleanTrapResult && targetAcc.isExtensible(target)) {
				throw RuntimeUtil.typeError("'preventExtensions' on proxy: trap returned truish but the proxy target is extensible");
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().preventExtensions(proxy.getTarget());
	}

	// SetIntegrityLevel(O, level) - the base JSAccessor.freeze()/seal() are
	// void no-ops (fine for an ordinary object, which JSObjectImpl overrides
	// with its own fast flag-based path) - a Proxy has no such shortcut and
	// must genuinely walk [[OwnPropertyKeys]] then call DefinePropertyOrThrow
	// per key so the defineProperty trap actually observes the lock-down
	// (confirmed via freeze/seal's proxy-no-ownkeys-returned-keys-order.js/
	// -with-defineProperty-handler.js: [[PreventExtensions]] alone, without
	// this, left every key exactly as it was).
	private void setIntegrityLevel(Object _this, boolean frozen) {
		for(Object key : ownKeysList(_this)) {
			if(frozen) {
				// Step 7.b.i: SetIntegrityLevel's OWN explicit
				// [[GetOwnProperty]] call - the ONLY one spec-accurate here,
				// and it must go through THIS proxy's own trap (not the
				// target directly), unlike sealed below.
				PropertyDescriptor current = getOwnPropertyDescriptor(_this, key);
				if(current==null) {
					continue;
				}
				boolean includeWritable = !current.isAccessor();
				if(!definePropertyPartial(_this, key, includeWritable)) {
					throw RuntimeUtil.typeError("Cannot redefine property: {0}", key);
				}
			} else {
				// Step 6.a.i: sealed has no separate currentDesc fetch of
				// its own at all - DefinePropertyOrThrow(O,k,{configurable:
				// false}) is the only spec-mandated step.
				if(!definePropertyPartial(_this, key, false)) {
					throw RuntimeUtil.typeError("Cannot redefine property: {0}", key);
				}
			}
		}
	}

	// SetIntegrityLevel's DefinePropertyOrThrow calls are spec'd to pass a
	// GENUINELY PARTIAL descriptor - {[[Configurable]]: false} for sealed,
	// {[[Configurable]]: false, [[Writable]]: false} for a frozen DATA
	// property (accessor properties only get [[Configurable]]) - with NO
	// enumerable/value/get/set field present at all, not even as undefined.
	// The general setOwnProperty(...) path (used by Object.defineProperty et
	// al) can't express this: PropertyDescriptor has no field-presence
	// tracking, and PropertyDescriptor.asJSObject() unconditionally emits
	// enumerable+configurable+(writable-or-get/set). This bypasses that
	// entirely - builds the WIRE-FORMAT object sent to the trap by hand
	// (partial), while deriving a FULLY-RESOLVED descriptor from targetDesc
	// (already fetched here for the compatibility check regardless) purely
	// for validateDefineOwnPropertyResult's own semantic checks, which need
	// the real target-compatible shape, not the trap's wire format -
	// confirmed via freeze/seal's proxy-with-defineProperty-handler.js,
	// which asserts the trap-received descriptor has get/set/enumerable/
	// value all `undefined` (i.e. absent), only writable(frozen data
	// only)/configurable present.
	private boolean definePropertyPartial(Object _this, Object key, boolean includeWritable) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DEFINE_OWN_PROPERTY);
		JSEnvironment env = getEnvironment();
		JSAccessor targetAcc = proxy.getTargetAccessor();
		Object target = proxy.getTarget();
		// Pre-call snapshot, purely to build `next`/`partial`'s shape (kind,
		// enumerable, writable-to-preserve) - unobserved, since targetAcc
		// bypasses any proxy trap layer and reads the real target directly.
		PropertyDescriptor preDesc = targetAcc.getOwnPropertyDescriptor(target, key);
		PropertyDescriptor next = preDesc==null ? PropertyDescriptor.of(false, false, false)
				: (preDesc.isAccessor()
					? PropertyDescriptor.of(preDesc.isWritable(), false, preDesc.isEnumerable(), preDesc.getGetter(), preDesc.getSetter())
					: PropertyDescriptor.of(includeWritable ? false : preDesc.isWritable(), false, preDesc.isEnumerable()));
		if(fct==null) {
			return targetAcc.setOwnProperty(target, key, RuntimeUtil.NOT_AVAILABLE, next, DESC_CHECK.CHECK, _this);
		}
		JSObject partial = JSObject.create(env);
		partial.setOwnProperty("configurable", false);
		if(includeWritable) {
			partial.setOwnProperty("writable", false);
		}
		Object result = fct.call(proxy.getHandler(), new Object[] { target, key, partial });
		// Post-call snapshot for validateDefineOwnPropertyResult - matching
		// every other trap-invoking method in this class, which fetches
		// targetDesc AFTER the trap runs (reflecting what the trap actually
		// did to target, e.g. via Reflect.defineProperty), not the
		// pre-call state.
		PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, key);
		String keyName = key instanceof Symbol sym ? sym.toString() : key.toString();
		return validateDefineOwnPropertyResult(targetAcc, target, RuntimeUtil.toBoolean(env,result), next, RuntimeUtil.NOT_AVAILABLE, targetDesc, () -> targetAcc.getOwnProperty(target, key, RuntimeUtil.UNDEFINED, target), keyName);
	}
	@Override
	public void seal(Object _this) {
		setIntegrityLevel(_this, false);
	}
	@Override
	public void freeze(Object _this) {
		setIntegrityLevel(_this, true);
	}

	// [[Get]]
	@Override
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET);
		if(fct!=null) {
			// Spec 10.5.8 [[Get]]: target/handler are captured (steps 1-4)
			// BEFORE the trap is invoked (step 7) - the trap may revoke the
			// proxy as a side effect, but step 8's [[GetOwnProperty]] still
			// operates on the target captured before the call, not on
			// proxy.getTarget() re-read afterwards (which would be null).
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, member, receiver });
			validateGetResult(result, targetAcc.getOwnPropertyDescriptor(target, member), () -> targetAcc.getOwnProperty(target, member, RuntimeUtil.UNDEFINED, target), member);
			return result;
		}
		return proxy.getTargetAccessor().getOwnProperty(proxy.getTarget(), member, defaultValue, receiver);
	}
	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET);
		if(fct!=null) {
			// See getOwnProperty(String,...) above: target/handler must be
			// captured before the trap call, not after.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, Long.toString(index), receiver });
			validateGetResult(result, targetAcc.getOwnPropertyDescriptor(target, index), () -> targetAcc.getOwnProperty(target, index, RuntimeUtil.UNDEFINED, target), Long.toString(index));
			return result;
		}
		return proxy.getTargetAccessor().getOwnProperty(proxy.getTarget(), index, defaultValue, receiver);
	}
	@Override
	public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET);
		if(fct!=null) {
			// See getOwnProperty(String,...) above: target/handler must be
			// captured before the trap call, not after.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, symbol, receiver });
			validateGetResult(result, targetAcc.getOwnPropertyDescriptor(target, symbol), () -> targetAcc.getOwnProperty(target, symbol, RuntimeUtil.UNDEFINED, target), symbol.toString());
			return result;
		}
		return proxy.getTargetAccessor().getOwnProperty(proxy.getTarget(), symbol, defaultValue, receiver);
	}

	// Spec 10.5.8 [[Get]] step 7: a non-configurable target property pins
	// what the trap is allowed to report. targetValue is fetched lazily -
	// spec reads it off the descriptor record already produced by
	// [[GetOwnProperty]] rather than issuing a second [[Get]], and only a
	// known-data property's raw value can be read this way without risking
	// an extra, unwanted getter invocation.
	private void validateGetResult(Object trapResult, PropertyDescriptor targetDesc, Supplier<Object> targetValue, String keyName) {
		if(targetDesc!=null && !targetDesc.isConfigurable()) {
			if(targetDesc.isData() && !targetDesc.isWritable()) {
				if(!RuntimeUtil.eqSameValue(getEnvironment(), trapResult, targetValue.get())) {
					throw RuntimeUtil.typeError("'get' on proxy: property '{0}' is a non-configurable, non-writable data property with a different value on the proxy target", keyName);
				}
			} else if(targetDesc.isAccessor() && targetDesc.getGetter()==null) {
				if(trapResult!=RuntimeUtil.UNDEFINED) {
					throw RuntimeUtil.typeError("'get' on proxy: property '{0}' is a non-configurable accessor property without a getter on the proxy target", keyName);
				}
			}
		}
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET_OWN_PROPERTY);
		if(fct!=null) {
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, member });
			PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, member);
			return validateGetOwnPropertyDescriptorResult(targetAcc, target, result, targetDesc, () -> targetAcc.getOwnProperty(target, member, RuntimeUtil.UNDEFINED, target), member);
		}
		return proxy.getTargetAccessor().getOwnPropertyDescriptor(proxy.getTarget(), member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET_OWN_PROPERTY);
		if(fct!=null) {
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, Long.toString(index) });
			PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, index);
			return validateGetOwnPropertyDescriptorResult(targetAcc, target, result, targetDesc, () -> targetAcc.getOwnProperty(target, index, RuntimeUtil.UNDEFINED, target), Long.toString(index));
		}
		return proxy.getTargetAccessor().getOwnPropertyDescriptor(proxy.getTarget(), index);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, Symbol symbol) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET_OWN_PROPERTY);
		if(fct!=null) {
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, symbol });
			PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, symbol);
			return validateGetOwnPropertyDescriptorResult(targetAcc, target, result, targetDesc, () -> targetAcc.getOwnProperty(target, symbol, RuntimeUtil.UNDEFINED, target), symbol.toString());
		}
		return proxy.getTargetAccessor().getOwnPropertyDescriptor(proxy.getTarget(), symbol);
	}

	// Overrides JSAccessor's default (live re-fetch) implementation: the
	// "getOwnPropertyDescriptor" trap's return value already carries a
	// resolved "value" field for a data property (built by the handler,
	// e.g. via `Object.getOwnPropertyDescriptor(target, key)`) - reuse that
	// directly instead of a second live [[Get]] on the proxy, which would be
	// an extra, spec-incorrect observable trap call (see
	// getOwnPropertyDescriptors/observable-operations.js: the expected trap
	// log is exactly one "getOwnPropertyDescriptor" call per key, no "get"
	// calls at all). No trap defined: fall straight through to the TARGET's
	// own accessor (bypassing this proxy layer entirely, matching spec's
	// [[GetOwnProperty]] fallthrough - must not risk invoking some OTHER,
	// separately-defined trap like "get").
	@Override
	public JSObject getOwnPropertyDescriptorAsJSObject(Object _this, Object member) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(GET_OWN_PROPERTY);
		JSAccessor targetAcc = proxy.getTargetAccessor();
		Object target = proxy.getTarget();
		if(fct==null) {
			return targetAcc.getOwnPropertyDescriptorAsJSObject(target, member);
		}
		Object trapKey;
		String keyName;
		if(member instanceof Symbol sym) {
			trapKey = sym;
			keyName = sym.toString();
		} else {
			String s = member instanceof String str ? str : RuntimeUtil.toString(getEnvironment(), member);
			trapKey = s;
			keyName = s;
		}
		Object result = fct.call(proxy.getHandler(), new Object[] { target, trapKey });
		PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, member);
		PropertyDescriptor resultDesc = validateGetOwnPropertyDescriptorResult(targetAcc, target, result, targetDesc, () -> targetAcc.getOwnProperty(target, member, RuntimeUtil.UNDEFINED, target), keyName);
		if(resultDesc==null) {
			return null;
		}
		if(resultDesc.isAccessor()) {
			return resultDesc.asJSObject(getEnvironment(), null);
		}
		Object value = RuntimeUtil.getProperty(getEnvironment(), result, "value", RuntimeUtil.UNDEFINED);
		return resultDesc.asJSObject(getEnvironment(), () -> value);
	}

	// Spec 10.5.5 [[GetOwnProperty]] steps 6-15: validates the trap's raw
	// result against the target's own real descriptor, independent of
	// whatever the handler claims. targetValue is fetched lazily - only
	// consulted (and only ever a safe, side-effect-free raw data read) inside
	// isCompatiblePropertyDescriptor's narrow non-configurable/non-writable case.
	// target/targetAcc are passed in (captured by the caller before the trap
	// call) rather than re-derived from the proxy, since the trap may have
	// revoked it as a side effect.
	private PropertyDescriptor validateGetOwnPropertyDescriptorResult(JSAccessor targetAcc, Object target, Object rawResult, PropertyDescriptor targetDesc, Supplier<Object> targetValue, String keyName) {
		JSEnvironment env = getEnvironment();
		if(rawResult==RuntimeUtil.UNDEFINED) {
			if(targetDesc==null) {
				return null;
			}
			if(!targetDesc.isConfigurable()) {
				throw RuntimeUtil.typeError("'getOwnPropertyDescriptor' on proxy: trap returned undefined for property '{0}' which is non-configurable in the proxy target", keyName);
			}
			if(!targetAcc.isExtensible(target)) {
				throw RuntimeUtil.typeError("'getOwnPropertyDescriptor' on proxy: trap returned undefined for property '{0}' which exists in the non-extensible proxy target", keyName);
			}
			return null;
		}
		if(!RuntimeUtil.isObject(env, rawResult)) {
			throw RuntimeUtil.typeError("Proxy handler's '{0}' trap must return an object or undefined for '{1}', got '{2}'", GET_OWN_PROPERTY, keyName, RuntimeUtil.objectTypeName(env, rawResult));
		}
		boolean extensibleTarget = targetAcc.isExtensible(target);
		Object resultValue = RuntimeUtil.getProperty(env, rawResult, "value", RuntimeUtil.UNDEFINED);
		PropertyDescriptor resultDesc = getPropertyDescriptor(rawResult);
		if(!RuntimeUtil.isCompatiblePropertyDescriptor(env, extensibleTarget, targetDesc, targetValue, resultDesc, resultValue)) {
			throw RuntimeUtil.typeError("'getOwnPropertyDescriptor' on proxy: trap result is incompatible with the property '{0}' on the proxy target", keyName);
		}
		if(!resultDesc.isConfigurable()) {
			if(targetDesc==null || targetDesc.isConfigurable()) {
				throw RuntimeUtil.typeError("'getOwnPropertyDescriptor' on proxy: trap reported non-configurable for property '{0}' which is non-existent or configurable in the proxy target", keyName);
			}
			if(resultDesc.isData() && !resultDesc.isWritable() && targetDesc.isWritable()) {
				throw RuntimeUtil.typeError("'getOwnPropertyDescriptor' on proxy: trap reported non-configurable, non-writable for property '{0}' which is writable in the proxy target", keyName);
			}
		}
		return resultDesc;
	}
	private PropertyDescriptor getPropertyDescriptor(Object jsonObject) {
		JSAccessor accessor = getEnvironment().getAccessor(jsonObject);
		// CompletePropertyDescriptor (6.2.6.6): absent writable/enumerable/
		// configurable default to false, not true. ToPropertyDescriptor reads
		// the fields with [[Get]], so inherited ones count too.
		boolean writable = RuntimeUtil.toBoolean(accessor.getEnvironment(), accessor.getProperty(jsonObject, "writable", false) );
		boolean configurable = RuntimeUtil.toBoolean(accessor.getEnvironment(), accessor.getProperty(jsonObject, "configurable", false) );
		boolean enumerable = RuntimeUtil.toBoolean(accessor.getEnvironment(), accessor.getProperty(jsonObject, "enumerable", false) );

		// ToPropertyDescriptor (6.2.6.5): whether this is a data or accessor
		// descriptor is decided by "get"/"set" PROPERTY PRESENCE
		// (HasProperty), not by whether their VALUES happen to be undefined -
		// {get:undefined,set:undefined} is still a genuine accessor
		// descriptor, distinct from a plain data descriptor that never
		// mentions "get"/"set" at all (confirmed via
		// defineProperty/15.2.3.6-4-{254,430,439,448,457}.js's underlying
		// PropertyDescriptor-kind gap, and this method's own doc history -
		// blindly defaulting an absent "get"/"set" to `null` and then
		// checking nullness alone used to conflate "no get/set field" with
		// "get/set field present but undefined").
		boolean hasGet = accessor.hasProperty(jsonObject, "get");
		boolean hasSet = accessor.hasProperty(jsonObject, "set");
		if(!hasGet && !hasSet) {
			return PropertyDescriptor.of(writable, configurable, enumerable);
		}
		Object _getter = hasGet ? accessor.getProperty(jsonObject, "get", null) : null;
		BaseCallableObject getter = RuntimeUtil.isNullOrUndefined(_getter) ? null : RuntimeUtil.toBaseCallableObject(_getter);

		Object _setter = hasSet ? accessor.getProperty(jsonObject, "set", null) : null;
		BaseCallableObject setter = RuntimeUtil.isNullOrUndefined(_setter) ? null : RuntimeUtil.toBaseCallableObject(_setter);

		return PropertyDescriptor.of(writable, configurable, enumerable, getter, setter);
	}
	

	
	// [[DefineOwnProperty]]
	
	// Protected, so the value is first resolved by the method above
	// These are the ones to be overridden by the subclasses
	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DEFINE_OWN_PROPERTY);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			PropertyDescriptor d = desc!=null ? desc : PropertyDescriptor.DESC_DEFAULT;
			JSObject o = d.asJSObject(env, ()-> value);
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, member, o });
			PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, member);
			return validateDefineOwnPropertyResult(targetAcc, target, RuntimeUtil.toBoolean(env,result), d, value, targetDesc, () -> targetAcc.getOwnProperty(target, member, RuntimeUtil.UNDEFINED, target), member);
		}
		return proxy.getTargetAccessor().setOwnProperty(proxy.getTarget(), member, value, desc, check, receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DEFINE_OWN_PROPERTY);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			PropertyDescriptor d = desc!=null ? desc : PropertyDescriptor.DESC_DEFAULT;
			JSObject o = d.asJSObject(env, ()-> value);
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, Long.toString(index), o });
			PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, index);
			return validateDefineOwnPropertyResult(targetAcc, target, RuntimeUtil.toBoolean(env,result), d, value, targetDesc, () -> targetAcc.getOwnProperty(target, index, RuntimeUtil.UNDEFINED, target), Long.toString(index));
		}
		return proxy.getTargetAccessor().setOwnProperty(proxy.getTarget(), index, value, desc, check, receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DEFINE_OWN_PROPERTY);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			PropertyDescriptor d = desc!=null ? desc : PropertyDescriptor.DESC_DEFAULT;
			JSObject o = d.asJSObject(env, ()-> value);
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, symbol, o });
			PropertyDescriptor targetDesc = targetAcc.getOwnPropertyDescriptor(target, symbol);
			return validateDefineOwnPropertyResult(targetAcc, target, RuntimeUtil.toBoolean(env,result), d, value, targetDesc, () -> targetAcc.getOwnProperty(target, symbol, RuntimeUtil.UNDEFINED, target), symbol.toString());
		}
		return proxy.getTargetAccessor().setOwnProperty(proxy.getTarget(), symbol, value, desc, check, receiver);
	}

	// Spec 10.5.6 [[DefineOwnProperty]] steps 8-13: validates the trap's true
	// result against the target's own real descriptor. targetValue is lazy,
	// same rationale as validateGetOwnPropertyDescriptorResult above.
	// target/targetAcc are passed in (captured by the caller before the trap
	// call), see validateGetOwnPropertyDescriptorResult above.
	private boolean validateDefineOwnPropertyResult(JSAccessor targetAcc, Object target, boolean trapResult, PropertyDescriptor desc, Object value, PropertyDescriptor targetDesc, Supplier<Object> targetValue, String keyName) {
		if(!trapResult) {
			return false;
		}
		JSEnvironment env = getEnvironment();
		boolean extensibleTarget = targetAcc.isExtensible(target);
		// Desc is always a complete, already-merged descriptor by the time it
		// reaches here (see RuntimeUtil.defineProperty) - "settingConfigFalse"
		// (spec 10.5.6 step 8: "Desc explicitly has [[Configurable]]: false")
		// needs desc.hasConfigurable() to distinguish "the original Desc
		// object passed to Object/Reflect.defineProperty never mentioned
		// configurable at all" (hasConfigurable()==false, defaulted/inherited
		// during the merge - NOT "explicitly false") from "explicitly set to
		// false" - `!desc.isConfigurable()` alone can't tell those apart
		// (see PropertyDescriptor's hasConfigurable()/hasEnumerable()/
		// hasWritable() presence tracking, confirmed via
		// `Reflect.defineProperty(proxy,"p",{})` - an EMPTY descriptor -
		// which must NOT be treated as settingConfigFalse).
		boolean settingConfigFalse = desc.hasConfigurable() && !desc.isConfigurable();
		if(targetDesc==null) {
			if(!extensibleTarget) {
				throw RuntimeUtil.typeError("'defineProperty' on proxy: trap returned truish for defining property '{0}' which does not exist in the non-extensible proxy target", keyName);
			}
			if(settingConfigFalse) {
				throw RuntimeUtil.typeError("'defineProperty' on proxy: trap returned truish for adding a non-configurable property '{0}' which does not exist in the proxy target", keyName);
			}
			return true;
		}
		if(!RuntimeUtil.isCompatiblePropertyDescriptor(env, extensibleTarget, targetDesc, targetValue, desc, value)) {
			throw RuntimeUtil.typeError("'defineProperty' on proxy: trap returned truish for defining property '{0}' incompatibly with the existing property in the proxy target", keyName);
		}
		if(settingConfigFalse && targetDesc.isConfigurable()) {
			throw RuntimeUtil.typeError("'defineProperty' on proxy: trap returned truish for defining non-configurable property '{0}' which is configurable in the proxy target", keyName);
		}
		if(targetDesc.isData() && !targetDesc.isConfigurable() && targetDesc.isWritable() && desc.isData() && !desc.isWritable()) {
			throw RuntimeUtil.typeError("'defineProperty' on proxy: trap returned truish for defining property '{0}' as non-writable while it is writable and non-configurable in the proxy target", keyName);
		}
		return true;
	}


	// [[HasProperty]]
	@Override
	public boolean hasProperty(Object _this, String key) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(HAS_PROPERTY);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, key });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(!booleanTrapResult) {
				validateHasPropertyFalseResult(targetAcc.getOwnPropertyDescriptor(target, key), targetAcc.isExtensible(target), key);
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().hasProperty(proxy.getTarget(), key);
	}
	@Override
	public boolean hasProperty(Object _this, long index) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(HAS_PROPERTY);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, Long.toString(index) });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(!booleanTrapResult) {
				validateHasPropertyFalseResult(targetAcc.getOwnPropertyDescriptor(target, index), targetAcc.isExtensible(target), Long.toString(index));
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().hasProperty(proxy.getTarget(), index);
	}
	@Override
	public boolean hasProperty(Object _this, Symbol symbol) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(HAS_PROPERTY);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, symbol });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(!booleanTrapResult) {
				validateHasPropertyFalseResult(targetAcc.getOwnPropertyDescriptor(target, symbol), targetAcc.isExtensible(target), symbol.toString());
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().hasProperty(proxy.getTarget(), symbol);
	}

	// Spec 10.5.7 [[HasProperty]] step 6: a "false" trap result must be
	// consistent with the target actually lacking (or being able to lack) the property.
	private void validateHasPropertyFalseResult(PropertyDescriptor targetDesc, boolean extensibleTarget, String keyName) {
		if(targetDesc!=null) {
			if(!targetDesc.isConfigurable()) {
				throw RuntimeUtil.typeError("'has' on proxy: trap returned falsish for property '{0}' which exists in the proxy target as non-configurable", keyName);
			}
			if(!extensibleTarget) {
				throw RuntimeUtil.typeError("'has' on proxy: trap returned falsish for property '{0}' but the proxy target is not extensible", keyName);
			}
		}
	}

	
// There is no proxy function for [[HasOwnProperty]], just [[GetOwnProperty]]	
//	@Override
//	public boolean hasOwnProperty(Object _this, String key) {
//		BuiltinProxy proxy = getProxy(_this);
//		Callable fct = proxy.getFunction(HAS_PROPERTY);
//		if(fct!=null) {
//			Object result = fct.call(proxy.getHandler(), new Object[] { proxy.getTarget(), key });
//			return RuntimeUtil.toBoolean(env,result);
//		}
//		return proxy.getTargetAccessor().hasOwnProperty(proxy.getTarget(), key);
//	}
//	@Override
//	public boolean hasOwnProperty(Object _this, long index) {
//		BuiltinProxy proxy = getProxy(_this);
//		Callable fct = proxy.getFunction(HAS_PROPERTY);
//		if(fct!=null) {
//			Object result = fct.call(proxy.getHandler(), new Object[] { proxy.getTarget(), index });
//			return RuntimeUtil.toBoolean(env,result);
//		}
//		return proxy.getTargetAccessor().hasOwnProperty(proxy.getTarget(), index);
//	}
//	@Override
//	public boolean hasOwnProperty(Object _this, Symbol symbol) {
//		BuiltinProxy proxy = getProxy(_this);
//		Callable fct = proxy.getFunction(HAS_PROPERTY);
//		if(fct!=null) {
//			Object result = fct.call(proxy.getHandler(), new Object[] { proxy.getTarget(), symbol });
//			return RuntimeUtil.toBoolean(env,result);
//		}
//		return proxy.getTargetAccessor().hasOwnProperty(proxy.getTarget(), symbol);
//	}
	
//	// [[Get]]
//	@Override
//	public Object getProperty(Object _this, String member, Object defaultValue) {
//		BuiltinProxy proxy = getProxy(_this);
//		Callable fct = proxy.getFunction(GET);
//		if(fct!=null) {
//			Object result = fct.call(proxy.getHandler(), new Object[] { proxy.getTarget(), member });
//			return result;
//		}
//		return proxy.getTargetAccessor().getProperty(proxy.getTarget(), member, defaultValue, receiver);
//	}
//	@Override
//	public Object getProperty(Object _this, long index, Object defaultValue) {
//		BuiltinProxy proxy = getProxy(_this);
//		Callable fct = proxy.getFunction(GET);
//		if(fct!=null) {
//			Object result = fct.call(proxy.getHandler(), new Object[] { proxy.getTarget(), index, receiver });
//			return result;
//		}
//		return proxy.getTargetAccessor().getProperty(proxy.getTarget(), index, defaultValue, receiver);
//	}
//	@Override
//	public  Object getProperty(Object _this, Symbol symbol, Object defaultValue) {
//		BuiltinProxy proxy = getProxy(_this);
//		Callable fct = proxy.getFunction(GET);
//		if(fct!=null) {
//			Object result = fct.call(proxy.getHandler(), new Object[] { proxy.getTarget(), symbol, receiver });
//			return result;
//		}
//		return proxy.getTargetAccessor().getProperty(proxy.getTarget(), symbol, defaultValue, receiver);
//	}

	// [[Set]] (P, V, Receiver) - the "set" trap takes 4 arguments: target,
	// property, value, AND receiver. These no-receiver convenience forms
	// just default receiver to `_this` (the proxy itself) - see the
	// receiver-aware overloads below for the real logic. Critically, this
	// must NOT independently re-implement the "no trap" fallback: spec's
	// step 7 ("Return ? target.[[Set]](P, V, Receiver)") preserves the
	// ORIGINAL receiver even when forwarding past a trapless proxy to its
	// target - defaulting to `_this` here (via the 6-arg overload) achieves
	// exactly that, whereas a naive `target.setProperty(...)` 5-arg
	// fallback here would silently reset the receiver to the target.
	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, member, value, desc, check, _this);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, index, value, desc, check, _this);
	}
	@Override
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, symbol, value, desc, check, _this);
	}

	// Receiver-aware [[Set]] - the real algorithm: the `set` trap's 4th
	// argument must be the ORIGINAL receiver (e.g. from OrdinarySet
	// delegating to a Proxy ancestor found in a prototype chain, or a
	// SuperProperty assignment), not always the proxy itself. Without these
	// overrides, JSAccessor's generic default (a plain descriptor-based
	// walk) would be used instead when this proxy is reached via the
	// receiver-aware entry point - silently skipping the `set` trap
	// entirely.
	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(SET);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, member, value, receiver });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(booleanTrapResult) {
				validateSetResult(value, targetAcc.getOwnPropertyDescriptor(target, member), () -> targetAcc.getOwnProperty(target, member, RuntimeUtil.UNDEFINED, target), member);
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().setProperty(proxy.getTarget(), member, value, desc, check, receiver);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(SET);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, Long.toString(index), value, receiver });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(booleanTrapResult) {
				validateSetResult(value, targetAcc.getOwnPropertyDescriptor(target, index), () -> targetAcc.getOwnProperty(target, index, RuntimeUtil.UNDEFINED, target), Long.toString(index));
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().setProperty(proxy.getTarget(), index, value, desc, check, receiver);
	}
	@Override
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(SET);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, symbol, value, receiver });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(booleanTrapResult) {
				validateSetResult(value, targetAcc.getOwnPropertyDescriptor(target, symbol), () -> targetAcc.getOwnProperty(target, symbol, RuntimeUtil.UNDEFINED, target), symbol.toString());
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().setProperty(proxy.getTarget(), symbol, value, desc, check, receiver);
	}

	// Spec 10.5.9 [[Set]] step 8: a truish result must be consistent with a
	// non-configurable target property's own constraints.
	private void validateSetResult(Object value, PropertyDescriptor targetDesc, Supplier<Object> targetValue, String keyName) {
		if(targetDesc!=null && !targetDesc.isConfigurable()) {
			if(targetDesc.isData() && !targetDesc.isWritable()) {
				if(!RuntimeUtil.eqSameValue(getEnvironment(), value, targetValue.get())) {
					throw RuntimeUtil.typeError("'set' on proxy: trap returned truish for property '{0}' which exists in the proxy target as a non-configurable and non-writable data property with a different value", keyName);
				}
			} else if(targetDesc.isAccessor() && targetDesc.getSetter()==null) {
				throw RuntimeUtil.typeError("'set' on proxy: trap returned truish for property '{0}' which exists in the proxy target as a non-configurable accessor property without a setter", keyName);
			}
		}
	}
	
	
	// [[Delete]]
	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DELETE);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, member });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(booleanTrapResult) {
				validateDeletePropertyResult(targetAcc.getOwnPropertyDescriptor(target, member), targetAcc.isExtensible(target), member);
			} else if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of proxy", member);
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().deleteProperty(proxy.getTarget(), member, check);
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DELETE);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, Long.toString(index) });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(booleanTrapResult) {
				validateDeletePropertyResult(targetAcc.getOwnPropertyDescriptor(target, index), targetAcc.isExtensible(target), Long.toString(index));
			} else if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of proxy", index);
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().deleteProperty(proxy.getTarget(), index, check);
	}
	@Override
	public boolean deleteProperty(Object _this, Symbol symbol, DESC_CHECK check) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(DELETE);
		if(fct!=null) {
			JSEnvironment env = getEnvironment();
			// Captured before the trap call - see getPrototype above.
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			Object result = fct.call(proxy.getHandler(), new Object[] { target, symbol });
			boolean booleanTrapResult = RuntimeUtil.toBoolean(env,result);
			if(booleanTrapResult) {
				validateDeletePropertyResult(targetAcc.getOwnPropertyDescriptor(target, symbol), targetAcc.isExtensible(target), symbol.toString());
			} else if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of proxy", symbol);
			}
			return booleanTrapResult;
		}
		return proxy.getTargetAccessor().deleteProperty(proxy.getTarget(), symbol, check);
	}

	// Spec 10.5.10 [[Delete]] steps 8-11: a truish result must be consistent
	// with the target actually being able to lose the property.
	private void validateDeletePropertyResult(PropertyDescriptor targetDesc, boolean extensibleTarget, String keyName) {
		if(targetDesc!=null) {
			if(!targetDesc.isConfigurable()) {
				throw RuntimeUtil.typeError("'deleteProperty' on proxy: trap returned truish for property '{0}' which is non-configurable in the proxy target", keyName);
			}
			if(!extensibleTarget) {
				throw RuntimeUtil.typeError("'deleteProperty' on proxy: trap returned truish for property '{0}' but the proxy target is not extensible", keyName);
			}
		}
	}


	// [[OwnPropertyKeys]]
	// Spec's EnumerableOwnPropertyNames-style per-key [[GetOwnProperty]]
	// checks must go through THIS proxy's own [[GetOwnProperty]] (its
	// "getOwnPropertyDescriptor" trap, if any) even when the "ownKeys"
	// trap itself is absent and the key LIST fell through to the target -
	// [[OwnPropertyKeys]] falling through to the target only affects the
	// key list step, not subsequent per-key operations (confirmed via
	// keys/property-traps-order-with-proxied-array.js: a proxy with no
	// "ownKeys" trap but a "getOwnPropertyDescriptor" trap must still
	// observe that trap being invoked for "length" - delegating straight
	// to the target's own ownPropertyEntries, as this previously did,
	// bypassed the proxy's own per-key trap checking entirely). So always
	// route the KEY LIST through ownKeysList (which already handles the
	// trap-vs-fallback split with no premature filtering) and do the
	// enumerable check / value resolution uniformly through `this` below.
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		Iterator<Object> it = Iterators.filter(
					ownKeysList(_this).iterator(),
					(k) -> (strings && k instanceof String) || (symbols && k instanceof Symbol)
				);
		if(enumerableOnly) {
			it = Iterators.filter(it, (k) -> {
				PropertyDescriptor desc = getOwnPropertyDescriptor(_this, k);
				return desc!=null && desc.isEnumerable();
			});
		}
		// Value resolution (a "get" trap call) must be LAZY, only on an
		// explicit getValue() - matching the ordinary-object accessor-
		// entry pattern in CustomLinkedMap.EntrySet's own iterator. A
		// caller that only needs the KEY (e.g. a snapshot-then-refetch
		// pass, see Object.entries/values/keys) must not trigger a "get"
		// trap call at all - an eager resolution here previously did so
		// unconditionally on next(), regardless of whether getValue()
		// was ever read, corrupting the exact trap-call-order tests
		// (confirmed via entries/values' observable-operations.js).
		return Iterators.map(
					it,
					(k) -> (Map.Entry<Object,Object>)new Map.Entry<Object,Object>() {
						@Override
						public Object getKey() { return k; }
						@Override
						public Object getValue() { return getOwnProperty(_this, k, RuntimeUtil.UNDEFINED, _this); }
						@Override
						public Object setValue(Object v) { throw new UnsupportedOperationException(); }
					}
			   );
	}

	// [[OwnPropertyKeys]] - KEYS only, in spec order, with NO per-key
	// [[GetOwnProperty]]/[[Get]] invocation at all (unlike ownPropertyEntries
	// above, which always eagerly resolves each entry's VALUE too via
	// getOwnProperty - fine for Object.entries/values, but an unwanted extra
	// trap call for callers that only need the key list, e.g.
	// getOwnPropertyDescriptors below, which must invoke ONLY `ownKeys` once
	// then `getOwnPropertyDescriptor` once per key - confirmed via
	// getOwnPropertyDescriptors/observable-operations.js's exact expected
	// trap-call log).
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<Object> ownKeysList(Object _this) {
		BuiltinProxy proxy = getProxy(_this);
		Callable fct = proxy.getFunction(OWN_PROPERTY_KEYS);
		if(fct!=null) {
			JSAccessor targetAcc = proxy.getTargetAccessor();
			Object target = proxy.getTarget();
			JSArray a = RuntimeUtil.getArrayLike(getEnvironment(),fct.call(proxy.getHandler(), new Object[] { target }));
			// See ownPropertyEntries' matching comment - reuse the already-
			// validated key list instead of re-reading `a` a second time.
			return validateOwnKeysResult(targetAcc, target, a);
		}
		List<Object> keys = new ArrayList<>();
		for(Iterator<Map.Entry<Object,Object>> it=proxy.getTargetAccessor().ownPropertyEntries(proxy.getTarget(), true, true, false); it.hasNext(); ) {
			keys.add(it.next().getKey());
		}
		return keys;
	}

	// Spec 6.2.6.5 FromPropertyDescriptor's caller (here, [[OwnPropertyKeys]]
	// followed by one [[GetOwnProperty]] per key, per
	// GetOwnPropertyDescriptors/ObjectDefineProperties/SetIntegrityLevel -
	// every caller of this class's [[OwnPropertyKeys]]+per-key-descriptor
	// pattern) - a key whose [[GetOwnProperty]] returns undefined (deleted
	// mid-enumeration, or a handler that simply omits it) is skipped, not
	// stored as a literal `undefined` (confirmed via
	// getOwnPropertyDescriptors/proxy-undefined-descriptor.js).
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
		for(Object key : ownKeysList(_this)) {
			PropertyDescriptor d = getOwnPropertyDescriptor(_this, key);
			if(d!=null) {
				descriptors.setOwnProperty(key, d);
			}
		}
		return descriptors;
	}

	// Single-pass version of the above: one "ownKeys" trap call, then exactly
	// one "getOwnPropertyDescriptor" trap call per key (via
	// getOwnPropertyDescriptorAsJSObject, which bakes the trap's own "value"
	// straight into the result) - no separate "get" trap calls, unlike the
	// base class's default (which would re-fetch each value live). Confirmed
	// via getOwnPropertyDescriptors/observable-operations.js's exact
	// expected trap-call log.
	@Override
	public JSObject getOwnPropertyDescriptorsAsJSObject(Object _this) {
		JSObject r = JSObject.create(getEnvironment());
		for(Object key : ownKeysList(_this)) {
			JSObject d = getOwnPropertyDescriptorAsJSObject(_this, key);
			if(d!=null) {
				r.setOwnProperty(key, d);
			}
		}
		return r;
	}

	// Spec 10.5.11 [[OwnPropertyKeys]]: (a) the raw trap result must be a
	// list of only distinct Strings/Symbols, and (b) it must include every
	// non-configurable key the target actually has, plus (for a
	// non-extensible target) exactly the target's configurable keys too and
	// nothing else.
	// target/targetAcc are passed in (captured by the caller before the trap
	// call), see validateGetOwnPropertyDescriptorResult above.
	// Returns the validated trap result list - callers MUST reuse this
	// instead of re-reading `a` (length + each index) a second time. `a`
	// can be a plain getter-based array-LIKE object (not a real Array/
	// Proxy), whose element reads are individually observable; re-walking
	// it after this method's own walk doubled every observable read
	// (confirmed via keys/proxy-keys.js/property-traps-order-with-proxied-array.js:
	// the exact trap-call log showed "get ownKeys['length']"/"get
	// ownKeys[0]" etc. each firing twice).
	private List<Object> validateOwnKeysResult(JSAccessor targetAcc, Object target, JSArray a) {
		// Walk the actual array elements by index - a.ownPropertyValues(false)
		// (strings, non-enumerable included) would also yield the array's own
		// "length" value, which isn't part of the trap's result list at all.
		List<Object> trapResult = new ArrayList<>();
		Set<Object> seen = new HashSet<>();
		long length = a.arrayLength();
		for(long i=0; i<length; i++) {
			Object k = a.getProperty(i, RuntimeUtil.UNDEFINED);
			if(!(k instanceof String) && !(k instanceof Symbol)) {
				throw RuntimeUtil.typeError("'ownKeys' on proxy: trap result must be a list of only Strings and Symbols");
			}
			if(!seen.add(k)) {
				throw RuntimeUtil.typeError("'ownKeys' on proxy: trap returned duplicate entries");
			}
			trapResult.add(k);
		}

		boolean extensibleTarget = targetAcc.isExtensible(target);
		List<Object> targetConfigurableKeys = new ArrayList<>();
		List<Object> targetNonconfigurableKeys = new ArrayList<>();
		Iterator<Map.Entry<Object,Object>> targetIt = targetAcc.ownPropertyEntries(target, true, true, false);
		while(targetIt.hasNext()) {
			Object key = targetIt.next().getKey();
			PropertyDescriptor d = targetOwnPropertyDescriptor(targetAcc, target, key);
			if(d!=null && !d.isConfigurable()) {
				targetNonconfigurableKeys.add(key);
			} else {
				targetConfigurableKeys.add(key);
			}
		}
		if(extensibleTarget && targetNonconfigurableKeys.isEmpty()) {
			return trapResult;
		}
		List<Object> uncheckedResultKeys = new ArrayList<>(trapResult);
		for(Object key : targetNonconfigurableKeys) {
			if(!uncheckedResultKeys.remove(key)) {
				throw RuntimeUtil.typeError("'ownKeys' on proxy: trap result did not include non-configurable key '{0}' from the proxy target", key);
			}
		}
		if(extensibleTarget) {
			return trapResult;
		}
		for(Object key : targetConfigurableKeys) {
			if(!uncheckedResultKeys.remove(key)) {
				throw RuntimeUtil.typeError("'ownKeys' on proxy: trap result did not include configurable key '{0}' from the non-extensible proxy target", key);
			}
		}
		if(!uncheckedResultKeys.isEmpty()) {
			throw RuntimeUtil.typeError("'ownKeys' on proxy: trap result contains extra key(s) not present on the non-extensible proxy target");
		}
		return trapResult;
	}
	private PropertyDescriptor targetOwnPropertyDescriptor(JSAccessor targetAcc, Object target, Object key) {
		if(key instanceof Symbol sym) {
			return targetAcc.getOwnPropertyDescriptor(target, sym);
		}
		return targetAcc.getOwnPropertyDescriptor(target, (String)key);
	}
}
