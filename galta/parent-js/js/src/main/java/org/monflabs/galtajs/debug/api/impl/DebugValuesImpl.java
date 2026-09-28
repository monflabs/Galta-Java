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
package org.monflabs.galtajs.debug.api.impl;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.debug.api.DebugException;
import org.monflabs.galtajs.debug.api.DebugProperty;
import org.monflabs.galtajs.debug.api.DebugValues;
import org.monflabs.galtajs.debug.api.ExecutionContext;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.DebugUtil;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 * Interprets GaltaJS values for a debugger. Two kinds of "object" are
 * accepted: a genuine {@link JSObject} (properties read via its own
 * descriptor API), and a {@link JSRuntimeContext} representing one
 * {@code DebugScope} (properties read via its {@link VariableMap} instead -
 * GaltaJS scopes are not backed by real JSObjects, unlike some engines').
 *
 * v1 scope: common cases (primitives, plain objects, arrays, functions,
 * errors) are handled well; exhaustive CDP subtype fidelity (typed arrays,
 * maps/sets, proxies, generators, ...) is not attempted yet.
 */
public class DebugValuesImpl implements DebugValues {

	private final JSEnvironment env;

	public DebugValuesImpl(JSEnvironment env) {
		this.env = env;
	}

	@Override
	public String type(Object value) {
		if (value instanceof JSRuntimeContext) {
			return "object";
		}
		return RuntimeUtil.typeof(env, value);
	}

	@Override
	public String subtype(Object value) {
		if (value == null) {
			return "null";
		}
		if (isPrimitive(value)) {
			return null;
		}
		if (RuntimeUtil.isArray(value)) {
			return "array";
		}
		if (value instanceof JSRuntimeException) {
			return "error";
		}
		if (value instanceof JSObject jso) {
			String className = jso.getClassName();
			if (className != null) {
				return switch (className) {
					case "Error", "TypeError", "RangeError", "ReferenceError", "SyntaxError", "EvalError", "URIError" -> "error";
					case "RegExp" -> "regexp";
					case "Date" -> "date";
					case "Map" -> "map";
					case "Set" -> "set";
					case "Promise" -> "promise";
					case "GeneratorFunction", "Generator" -> "generator";
					default -> null;
				};
			}
		}
		return null;
	}

	@Override
	public String className(Object value) {
		if (isPrimitive(value)) {
			return null;
		}
		if (value instanceof JSObject jso) {
			return jso.getClassName();
		}
		return "Object";
	}

	@Override
	public String description(Object value) {
		if (value == UNDEFINED) {
			return "undefined";
		}
		// A DebugScope's own "object" is often a raw JSRuntimeContext (see
		// DebugFrameImpl.buildScopes()'s own doc) - an internal engine
		// object, not a genuine JS value. DebugUtil.jsLiteral() only knows
		// how to render real JS values and throws a TypeError
		// ("Unknown object type ...") on anything else - confirmed the hard
		// way, uncaught, it silently killed the paused script's own thread.
		if (value instanceof JSRuntimeContext) {
			return "Scope";
		}
		// V8/CDP convention for an Error's description is "Name: message"
		// (matching Error.prototype's own toString(), what DevTools shows in
		// the Sources/Console panel for a paused/thrown exception) - GaltaJS's
		// own generic jsLiteral()/console formatting doesn't produce that
		// shape for an object (it dumps properties like any other object,
		// and `message` is typically non-enumerable), so this is special-
		// cased here specifically for the CDP-facing description.
		if (value instanceof JSObject jso && "error".equals(subtype(value))) {
			Object name = jso.getProperty("name", className(value));
			Object message = jso.getProperty("message", "");
			String msg = message == null ? "" : String.valueOf(message);
			return msg.isEmpty() ? String.valueOf(name) : name + ": " + msg;
		}
		return DebugUtil.jsLiteral(env, value, 256);
	}

	@Override
	public boolean isPrimitive(Object value) {
		return RuntimeUtil.isPrimitiveValue(env, value);
	}

	@Override
	public Object toJava(Object value) {
		return value;
	}

	@Override
	public String unserializable(Object value) {
		if (value instanceof Double d) {
			if (d.isNaN()) {
				return "NaN";
			}
			if (d == Double.POSITIVE_INFINITY) {
				return "Infinity";
			}
			if (d == Double.NEGATIVE_INFINITY) {
				return "-Infinity";
			}
			if (d == 0.0 && 1 / d < 0) {
				return "-0";
			}
		}
		return null;
	}

	@Override
	public List<DebugProperty> ownProperties(Object object, boolean includeNonEnumerable, boolean includeIndexed) {
		List<DebugProperty> result = new ArrayList<>();
		if (object instanceof JSRuntimeContext ctx) {
			VariableMap map = ctx.getVariableMap();
			if (map != null) {
				for (VarAccessor a : map.entries()) {
					result.add(new DebugProperty(a.getKey(), a.getKey(), a.getValue(), null, null,
							true, true, a.isConfigurable(), true, false));
				}
			}
			return result;
		}
		if (object instanceof JSObject jso) {
			var it = jso.ownPropertyEntries(true, false, !includeNonEnumerable);
			while (it.hasNext()) {
				var entry = it.next();
				Object key = entry.getKey();
				String name = String.valueOf(key);
				PropertyDescriptor desc = jso.getOwnPropertyDescriptor(key);
				Object getter = desc != null ? desc.getGetter() : null;
				Object setter = desc != null ? desc.getSetter() : null;
				result.add(new DebugProperty(name, key, entry.getValue(), getter, setter,
						desc == null || desc.isWritable(), desc == null || desc.isEnumerable(),
						desc == null || desc.isConfigurable(), true, false));
			}
		}
		return result;
	}

	@Override
	public List<DebugProperty> internalProperties(Object object) {
		List<DebugProperty> result = new ArrayList<>();
		if (object instanceof JSObject jso) {
			Object proto = jso.getPrototype();
			result.add(new DebugProperty("[[Prototype]]", "[[Prototype]]", proto, null, null,
					false, false, false, false, false));
		}
		return result;
	}

	@Override
	public Object prototype(Object object) {
		if (object instanceof JSObject jso) {
			return jso.getPrototype();
		}
		return null;
	}

	@Override
	public long arrayLength(Object array) {
		if (RuntimeUtil.isArray(array) && array instanceof JSObject jso) {
			Object length = jso.getProperty("length", 0);
			if (length instanceof Number n) {
				return n.longValue();
			}
		}
		return -1;
	}

	@Override
	public void setProperty(Object object, Object key, Object value) {
		if (object instanceof JSRuntimeContext ctx) {
			// A local, closure or block scope: assign the variable itself
			VarAccessor a = ctx.getLocalVariableEntry(String.valueOf(key));
			if (a == null) {
				throw new IllegalArgumentException("No variable '" + key + "' in this scope");
			}
			a.setValue(value);
			return;
		}
		if (object instanceof JSObject jso) {
			jso.setProperty(key, value);
			return;
		}
		throw new IllegalArgumentException("Cannot set a property on " + (object == null ? "null" : object.getClass().getName()));
	}

	@Override
	public Object callFunction(Object function, Object thisValue, Object... arguments) throws DebugException {
		try {
			return RuntimeUtil.call(env, function, thisValue, arguments);
		} catch (JSRuntimeException e) {
			throw new DebugException(e.getMessage(), e.getJavascriptException(), e);
		}
	}

	@Override
	public Object evaluateWith(ExecutionContext context, String expression, Object thisValue) throws DebugException {
		ExecutionContextImpl impl = (ExecutionContextImpl) context;
		// Must reuse the SAME global context the script actually ran in -
		// not a freshly constructed one, which would be an empty scope with
		// none of the script's own top-level var/function declarations
		// visible (confirmed the hard way: Runtime.evaluate("o.b") against a
		// brand new context couldn't see a top-level `var o` at all).
		// v1 limitation: `thisValue` (a custom receiver) is not honored -
		// every current caller passes null anyway; overriding `this` on an
		// already-live context isn't a small change.
		if (!(impl.getGlobalContext() instanceof org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext interpretedContext)) {
			throw new DebugException("Runtime evaluation is not yet supported for a transpiled execution context", null, null);
		}
		try {
			// Self-sufficient rather than relying on a caller to have bound
			// the realm first (JSContext.with()) - a bare call from a thread
			// that has never run GaltaJS code (any WebSocket connection's
			// own thread, the first time a client asks to evaluate something
			// while nothing is paused) throws "JSRuntimeContext is not
			// currently available" otherwise, confirmed via a real CDP
			// session. Nesting with() when a caller already bound one (e.g.
			// DebuggerImpl.call()) is harmless.
			return interpretedContext.with(() -> env.evaluate(interpretedContext, expression));
		} catch (JSRuntimeException e) {
			throw new DebugException(e.getMessage(), e.getJavascriptException(), e);
		}
	}
}
