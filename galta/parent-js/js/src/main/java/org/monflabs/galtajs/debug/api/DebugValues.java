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
package org.monflabs.galtajs.debug.api;

import java.util.List;

import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * Interprets the values a paused GaltaJS script hands out. The type words
 * follow the Chrome DevTools Protocol, which is what most frontends speak.
 *
 * <p>The reading methods touch script objects and must run on the thread
 * that owns them - through {@link PausedEvent#call} while paused - except
 * that primitives and function names may be read anywhere.
 */
public interface DebugValues {

	/** The value of {@code undefined}. */
	Object UNDEFINED = RuntimeUtil.UNDEFINED;

	/**
	 * The type: {@code object}, {@code function}, {@code undefined}, {@code string},
	 * {@code number}, {@code boolean}, {@code symbol}, {@code bigint}.
	 */
	String type(Object value);

	/**
	 * The subtype of an object: {@code array}, {@code null}, {@code regexp},
	 * {@code date}, {@code map}, {@code set}, {@code error}, {@code promise},
	 * {@code typedarray}, {@code arraybuffer}, {@code dataview}, {@code generator},
	 * or null.
	 */
	String subtype(Object value);

	/**
	 * The class name of an object: {@code Object}, {@code Array}, {@code Function}, ...
	 * Null for a primitive.
	 */
	String className(Object value);

	/**
	 * A one line description: a primitive's text, a function's head, an object's class.
	 */
	String description(Object value);

	/**
	 * Whether the value is a primitive, undefined or null.
	 */
	boolean isPrimitive(Object value);

	/**
	 * A primitive as a Java value: {@link String}, {@link Double}/{@link Integer},
	 * {@link Boolean}, null for {@code null}, {@link #UNDEFINED} for undefined;
	 * an object unchanged.
	 */
	Object toJava(Object value);

	/**
	 * The name a number needs when JSON cannot carry it: {@code NaN},
	 * {@code Infinity}, {@code -Infinity}, {@code -0}; otherwise null.
	 */
	String unserializable(Object value);

	/**
	 * An object's own properties.
	 */
	List<DebugProperty> ownProperties(Object object, boolean includeNonEnumerable, boolean includeIndexed);

	/**
	 * The internal properties a debugger shows in brackets: {@code [[Prototype]]},
	 * a function's {@code [[FunctionLocation]]} as a {@link Location}.
	 */
	List<DebugProperty> internalProperties(Object object);

	/**
	 * An object's prototype, or null.
	 */
	Object prototype(Object object);

	/**
	 * An array's length, or -1 for a non-array.
	 */
	long arrayLength(Object array);

	/**
	 * Sets a property, as an assignment would.
	 */
	void setProperty(Object object, Object key, Object value);

	/**
	 * Calls a function.
	 */
	Object callFunction(Object function, Object thisValue, Object... arguments) throws DebugException;

	/**
	 * Evaluates an expression in a context with a given receiver, on the calling thread.
	 */
	Object evaluateWith(ExecutionContext context, String expression, Object thisValue) throws DebugException;
}
