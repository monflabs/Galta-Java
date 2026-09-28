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
package org.monflabs.galtajs.rt.builtins.standard.proxy;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.CustomLinkedMap;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.jsonfactory.PrivateElementsMap;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.AccessorFactory;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.ProxyAccessor;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateElementsHolder;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;


/**
 * Proxy Object
 */
public class BuiltinProxy implements AccessorFactory, Callable, Constructor, PrivateElementsHolder {

	private JSAccessor targetAccessor;
	private Object target;
	// Spec: the handler just needs to be an Object - not necessarily a
	// JSObject-backed one (e.g. another Proxy, or any other exotic object).
	private Object handler;
	// Whether the proxy has a [[Call]] internal slot is fixed at creation time (spec
	// 10.5 ProxyCreate) and must not change once the target is cleared by revoke().
	private final boolean targetCallable;
	// Same idea for [[Construct]] - ProxyCreate only wires it up if the target
	// was genuinely a constructor (isConstructor(), not just Java's
	// `instanceof Constructor` - a proxy wrapping e.g. a bare `eval` or
	// another proxy wrapping a non-constructor must itself report false).
	private final boolean targetConstructible;
	// A Proxy can itself become `this` for a class construction (a base
	// class constructor returning `new Proxy(this, {...})`) - private field
	// access on it must work directly against the Proxy's OWN storage,
	// bypassing its trap handlers entirely (spec: [[PrivateElements]] is
	// never trap-dispatched) - see PrivateElementsHolder's doc.
	private PrivateElementsMap privateElements;

	public BuiltinProxy(JSEnvironment env, Object target, Object handler) {
		this.targetAccessor = env.getAccessor(target);
		this.target = target;
		this.targetCallable = target instanceof Callable c && c.isCallable();
		this.targetConstructible = target instanceof Constructor c && c.isConstructor();
		this.handler = handler;
	}

	public boolean isTargetCallable() {
		return targetCallable;
	}

	@Override
	public boolean isCallable() {
		return targetCallable;
	}

	@Override
	public boolean isConstructor() {
		return targetConstructible;
	}

	public JSEnvironment getEnvironment() {
		return JSEnvironment.getEnvironment();
	}

	@Override
	public String getClassName() {
		// Spec: Object.prototype.toString's builtinTag steps check internal
		// slots of O (the Proxy) directly, not the target - a Proxy exotic
		// object never itself has [[ErrorData]]/[[BooleanData]]/[[DateValue]]/
		// etc. even when its target does (confirmed via
		// Array.prototype.toString's non-callable-join-string-tag.js:
		// `new Proxy(new Date, {})` must report "Object", not "Date"). The
		// only slots a Proxy genuinely inherits from its target are
		// [[Call]] (-> "Function") and array-ness, since IsArray is spec'd
		// to recurse through Proxies.
		if(targetCallable) {
			return "Function";
		}
		String targetClassName = targetAccessor.getClassName(target);
		if("Array".equals(targetClassName)) {
			return targetClassName;
		}
		return "Object";
	}
	
	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new ProxyAccessor(env);
	}
	
	public Object getRootTarget() {
		Object t = target;
		while(t instanceof BuiltinProxy) {
			t = ((BuiltinProxy)t).getTarget();
		}
		return t;
	}
	
	public Object getTarget() {
		return target;
	}
	public JSAccessor getTargetAccessor() {
		return targetAccessor;
	}
	
	public Object getHandler() {
		return handler;
	}
	public JSAccessor getHandlerAccessor() {
		return targetAccessor;
	}

	public boolean isRevoked() {
		return target==null;
	}
	public void revoke() {
		target = null;
		targetAccessor = null;
	}

	//
	// PrivateElementsHolder - see the field doc above. Mirrors
	// JSObjectImpl's implementation exactly; deliberately NOT routed through
	// any trap (get/set/has/etc.) or the wrapped target at all.
	//
	@Override
	public boolean hasPrivateElement(PrivateName name) {
		return privateElements!=null && privateElements.getEntry(name)!=null;
	}
	@Override
	public PropertyDescriptor getPrivateElementDescriptor(PrivateName name) {
		if(privateElements!=null) {
			CustomLinkedMap.EntryImpl<PrivateName> e = privateElements.getEntry(name);
			if(e!=null) {
				return e.getPropertyDescriptor();
			}
		}
		return null;
	}
	@Override
	public Object getPrivateElementValue(PrivateName name) {
		if(privateElements!=null) {
			CustomLinkedMap.EntryImpl<PrivateName> e = privateElements.getEntry(name);
			if(e!=null) {
				return e.resolveValue(this);
			}
		}
		return RuntimeUtil.NOT_AVAILABLE;
	}
	@Override
	public void definePrivateElement(PrivateName name, Object value, PropertyDescriptor descriptor) {
		if(privateElements==null) {
			privateElements = new PrivateElementsMap();
		}
		privateElements.put(name, value, descriptor, DESC_CHECK.NONE, this);
	}
	@Override
	public boolean setPrivateElementValue(PrivateName name, Object value) {
		if(privateElements!=null && privateElements.getEntry(name)!=null) {
			privateElements.put(name, value, null, DESC_CHECK.STRICT, this);
			return true;
		}
		return false;
	}

	// GetMethod(handler, name): a trap that is absent, or explicitly
	// undefined/null (whether a plain property or a getter's return value),
	// means "no trap" - fall through to the target. Only some OTHER
	// non-callable value is a TypeError.
	public Callable getFunction(String name) {
		if(RuntimeUtil.isNotNullOrUndefined(handler)) {
			Object fct = RuntimeUtil.getProperty(getEnvironment(), handler, name, RuntimeUtil.NOT_AVAILABLE);
			if(fct!=RuntimeUtil.NOT_AVAILABLE && !RuntimeUtil.isNullOrUndefined(fct)) {
				if (fct instanceof Callable) {
					return (Callable)fct;
				}
				throw RuntimeUtil.typeError("'{0}' is not a function, '{1}'", name, RuntimeUtil.objectTypeName(getEnvironment(), fct));
			}
		}
		return null;
	}

	
	// Callable
	@Override
	public Object call(Object _this, @NonNull Object[] parameters) {
		if(isRevoked()) {
			throw RuntimeUtil.typeError("Cannot access a revoked proxy");
		}
		Callable fct = getFunction(ProxyAccessor.CALL);
		if(fct!=null) {
			// CreateArrayFromList(argumentsList): the trap sees a real JS
			// array, not the raw Java Object[] backing it.
			return fct.call(getHandler(), new Object[] { getTarget(), _this, JSArray.of(getEnvironment(), parameters) });
		}
		Object target = getTarget();
		if(target instanceof Callable callable) {
			return callable.call(_this, parameters);
		}
		throw RuntimeUtil.typeError("Object is not callable: {0}", RuntimeUtil.objectTypeName(getEnvironment(), this));
	}
	// Constructor
	@Override
	public Object constructObject(Object[] parameters, Constructor newTarget) {
		if(isRevoked()) {
			throw RuntimeUtil.typeError("Cannot access a revoked proxy");
		}
		Callable fct = getFunction(ProxyAccessor.CONSTRUCT);
		if(fct!=null) {
			// CreateArrayFromList(argumentsList): the trap sees a real JS
			// array, not the raw Java Object[] backing it.
			Object result = fct.call(getHandler(), new Object[] { getTarget(), JSArray.of(getEnvironment(), parameters), newTarget });
			if(!RuntimeUtil.isObject(getEnvironment(), result)) {
				throw RuntimeUtil.typeError("Proxy 'construct' trap must return an object, '{0}'", RuntimeUtil.objectTypeName(getEnvironment(), result));
			}
			return result;
		}
		Object target = getTarget();
		if(target instanceof Constructor ctor) {
			return ctor.constructObject(parameters,newTarget);
		}
		throw RuntimeUtil.typeError("Object is not constructible: {0}", RuntimeUtil.objectTypeName(getEnvironment(), this));
	}
	@Override
	public Object constructArray(int dimensions, long size) {
		if(isRevoked()) {
			throw RuntimeUtil.typeError("Cannot access a revoked proxy");
		}
		Callable fct = getFunction(ProxyAccessor.CONSTRUCT_ARRAY);
		if(fct!=null) {
			return fct.call(getHandler(), new Object[] { getTarget(), dimensions, size });
		}
		Object target = getTarget();
		if(target instanceof Constructor ctor) {
			return ctor.constructArray(dimensions,size);
		}
		throw RuntimeUtil.typeError("Object is not constructible: {0}", RuntimeUtil.objectTypeName(getEnvironment(), this));
	}
}
