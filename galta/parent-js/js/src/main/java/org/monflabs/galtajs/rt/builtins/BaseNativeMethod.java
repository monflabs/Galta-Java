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

import java.text.MessageFormat;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * Contains helpers for callable objects.
 */
public abstract class BaseNativeMethod extends BaseCallableObject {

	protected BaseNativeMethod(JSEnvironment env) {
		super(env);
	}

	// Id of the method/Constructor
	public abstract Object getId();
	
	protected JSRuntimeException nullThis() {
		return RuntimeUtil.typeError("Function {0} is called on null", getId());
	}
	protected JSRuntimeException invalidArguments( Object[] args) {
		return invalidArguments(args,-1, null);
	}
	protected JSRuntimeException invalidArguments(Object[] args, int pos, String message, Object...parameters) {
		StringBuilder b = missingArguments(new StringBuilder(), args, pos);
		b.append(MessageFormat.format("Error in argument position #{0}", pos));
		if(StringUtil.isNotEmpty(message)) {
			b.append("\n");
			b.append(StringFormat.format(message,parameters));
		}
		return RuntimeUtil.typeError(b.toString());
	}
	protected JSRuntimeException missingArguments(JSRuntimeContext context, Object[] args, int pos) {
		StringBuilder b = missingArguments(new StringBuilder(), args, pos);
		b.append(MessageFormat.format("Missing argument in position #{0}", pos));
		return RuntimeUtil.typeError(b.toString());
	}
	protected StringBuilder missingArguments(StringBuilder b, Object[] args, int pos) {
		Object id = getId();
		if(id!=null) {
			b.append(id.toString());
		}
		b.append("(");
		for(int i=0; i<args.length; i++) {
			if(i>0) {
				b.append(',');
			}
			// We don't pass the actual value for security reasons, when logged
			b.append(typeAsString(args[i]));
		}
		b.append(")");
		return b;
	}
	protected String typeAsString(Object o) {
		if(o==null) {
			return "null";
		}
		if(o instanceof Boolean) {
			return "boolean";
		}
		if(o instanceof Number) {
			return "number";
		}
		if(o instanceof CharSequence) {
			return "string";
		}
		return o.getClass().getName();
	}

	
	protected boolean has(Object[] arguments, int pos) {
		return pos<arguments.length;
	}
	
	protected Object param(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object v = arguments[pos];
			return v;
		}
		throw invalidArguments(arguments,pos, "Object is not available");
	}
	protected Object paramNotNull(Object[] arguments, int pos) {
		Object o = param(arguments, pos);
		if(o!=null) {
			return o;
		}
		throw invalidArguments(arguments,pos, "Object is cannot be null");
	}
	protected Object param(Object[] arguments, int pos, Object defaultValue) {
		if(pos<arguments.length) {
			Object v = arguments[pos];
			return v!=RuntimeUtil.UNDEFINED?v:defaultValue;
			//return arguments[pos];
		}
		return defaultValue;
	}
	
	protected Callable paramCallable(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object o = arguments[pos];
			if(o instanceof Callable callable) {
				return callable;
			}
			throw invalidArguments(arguments,pos, "Object is not a function");
		}
		throw invalidArguments(arguments,pos, "Function is not available");
	}
	protected Callable paramCallableNotNull(Object[] arguments, int pos) {
		Callable o = paramCallable(arguments, pos);
		if(o!=null) {
			return o;
		}
		throw invalidArguments(arguments,pos, "Function is cannot be null");
	}
	protected Callable paramCallable(Object[] arguments, int pos, Callable defaultValue) {
		if(pos<arguments.length) {
			Object o = arguments[pos];
			// Only an explicitly-passed JS `undefined` (RuntimeUtil.UNDEFINED)
			// falls back to defaultValue - many spec algorithms (e.g.
			// %TypedArray%.prototype.sort's comparefn) treat "comparefn is
			// undefined" as "not given", so `arr.sort(undefined)` must not
			// be rejected as non-callable. JS `null` (Java null, per this
			// engine's convention) is a DIFFERENT value - "not undefined
			// and not callable" - and must still throw, same as any other
			// non-callable value (e.g. `arr.sort(null)`).
			if(o!=RuntimeUtil.UNDEFINED) {
				if(o instanceof Callable callable) {
					return callable;
				}
				throw invalidArguments(arguments,pos, "Object is not a function");
			}
		}
		return defaultValue;
	}
	
	protected JSObject paramJsonObject(Object[] arguments, int pos) {
		Object o = param(arguments, pos);
		// A JS `null` is not exempt here - ToPropertyDescriptor's step 1
		// ("If Type(Obj) is not Object, throw a TypeError") rejects it just
		// like any other primitive (confirmed via
		// defineProperty/15.2.3.6-3-16.js: `Object.defineProperty({},
		// "property", null)` must throw TypeError, not silently pass a null
		// desc through to a later NullPointerException).
		// JSObjectInternal deliberately marks "an Object, as opposed to an
		// Array" (see its own doc comment) - too narrow for callers like
		// Object.defineProperty/defineProperties, whose spec algorithm
		// (ToPropertyDescriptor / the "properties" argument) accepts ANY
		// object, Array included (confirmed via
		// defineProperty/15.2.3.6-3-140.js, whose descriptor argument is a
		// plain Array with a "value" property set on it - genuinely a
		// valid Object per spec, just not an "ordinary" one).
		if(o instanceof JSObject jso) {
			return jso;
		}
		JSObject wrapped = paramJsonObjectGenericWrap(o);
		if(wrapped!=null) {
			return wrapped;
		}
		// A raw JsonException here would propagate as an uncaught Java
		// exception (wrapped into a generic, non-catchable-as-TypeError JS
		// Error) instead of the spec-mandated TypeError - this is a real,
		// user-observable JS-level exception (e.g. Object.defineProperties'
		// non-object "properties" argument), not an internal invariant
		// violation.
		throw RuntimeUtil.typeError("Value {0} is not an object",JsonUtil.toDebugString(o));
	}
	protected JSObject paramJsonObjectNotNull(Object[] arguments, int pos) {
		Object o = paramNotNull(arguments, pos);
		// See paramJsonObject's matching comment above.
		if(o instanceof JSObject jso) {
			return jso;
		}
		JSObject wrapped = paramJsonObjectGenericWrap(o);
		if(wrapped!=null) {
			return wrapped;
		}
		throw RuntimeUtil.typeError("Value {0} is not an object",JsonUtil.toDebugString(o));
	}
	// Any genuine (non-primitive) value is a valid Object per spec's
	// ToPropertyDescriptor, not just this engine's actual JSObject-shaped
	// types - a boxed primitive (GaltaJS has no wrapper type: `new
	// String("abc")` is the SAME java.lang.String as the primitive,
	// distinguished only via the environment's PrimitivePropertyMap) or a
	// host object like Date (its own properties tracked via DateAccessor,
	// not a JSObject at all) both qualify (confirmed via
	// defineProperty/15.2.3.6-3-141.js and 15.2.3.6-3-145-1.js). JSObject.
	// from(env,o) already implements exactly this generic "wrap any
	// accessor-backed value as a JSObject" adapter (ObjectAccessorWrapper) -
	// isObject() first excludes genuine primitives (including null/
	// undefined), which that generic wrap would otherwise happily accept.
	private JSObject paramJsonObjectGenericWrap(Object o) {
		JSEnvironment env = getEnvironment();
		if(RuntimeUtil.isObject(env, o)) {
			return JSObject.from(env, o);
		}
		return null;
	}
	protected JSArray paramJsonArray(Object[] arguments, int pos) {
		Object o = param(arguments, pos);
		if(o==null) {
			return null;
		}
		if(o instanceof JSArray jsa) {
			return jsa;
		}
		throw RuntimeUtil.typeError("Value {0} is not an array",JsonUtil.toDebugString(o));
	}
	protected JSArray paramJsonArrayNotNull(Object[] arguments, int pos) {
		Object o = paramNotNull(arguments, pos);
		if(o instanceof JSArray jsa) {
			return jsa;
		}
		throw RuntimeUtil.typeError("Value {0} is not an array",JsonUtil.toDebugString(o));
	}
	
	protected boolean paramBoolean(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toBoolean(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not a boolean");
	}
	protected boolean paramBoolean(Object[] arguments, int pos, boolean defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toBoolean(getEnvironment(),a);
		}
		return defaultValue;
	}

	protected int paramInt(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toInt(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not an integer");
	}
	protected int paramInt(Object[] arguments, int pos, int defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toInt(getEnvironment(),a);
		}
		return defaultValue;
	}
	// Experiement...
	protected int paramIntStrict(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a instanceof Number n) {
				return JsonUtil.toInt(n);
			}
		}
		throw invalidArguments(arguments,pos, "Not an integer");
	}
	protected int paramIntStrict(Object[] arguments, int pos, int defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a!=RuntimeUtil.UNDEFINED) {
				if(a instanceof Number n) {
					return JsonUtil.toInt(n);
				}
				throw invalidArguments(arguments,pos, "Not an integer");
			}
		}
		return defaultValue;
	}

	protected long paramLong(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toLong(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not a long");
	}
	protected long paramLong(Object[] arguments, int pos, long defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toLong(getEnvironment(),a);
		}
		return defaultValue;
	}

	protected double paramDouble(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toDouble(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not a double");
	}
	protected double paramDouble(Object[] arguments, int pos, double defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toDouble(getEnvironment(),a);
		}
		return defaultValue;
	}
	
	protected Number paramNumber(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toNumber(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not a Number");
	}
	protected Number paramNumber(Object[] arguments, int pos, Number defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toNumber(getEnvironment(),a);
		}
		return defaultValue;
	}
	
	protected Number paramNumeric(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toNumeric(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not a Number");
	}
	protected Number paramNumeric(Object[] arguments, int pos, Number defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toNumeric(getEnvironment(),a);
		}
		return defaultValue;
	}
	
	protected String paramString(Object[] arguments, int pos) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			return RuntimeUtil.toString(getEnvironment(),a);
		}
		throw invalidArguments(arguments,pos, "Not a string");
	}
	protected String paramString(Object[] arguments, int pos, String defaultValue) {
		if(pos<arguments.length) {
			Object a = arguments[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			return RuntimeUtil.toString(getEnvironment(),a);
		}
		return defaultValue;
	}
}
