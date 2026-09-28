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
package org.monflabs.galtajs.types;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Type returned by an expression.
 *
 * Beyond the plain value atoms below, a JSType can also carry:
 * - nullish info (canBeNull()/canBeUndefined()) layered on top of a concrete
 *   base type via orNull()/orUndefined()/orNullOrUndefined() - e.g.
 *   STRING.orUndefined() models a TypeScript-like "string | undefined".
 *   Only null/undefined are modeled as unions; two genuinely different
 *   concrete bases (e.g. "int or string") still collapse to UNKNOWN via
 *   join(), same as before this was added.
 * - an optional constructor reference (getConstructorRef(), set via
 *   ofConstructor()) identifying which declaration produced an object type,
 *   for future member-access optimization. The reference is kept as an
 *   opaque Object (not a typed AST node) so this package stays free of any
 *   dependency on org.monflabs.galtajs.node - callers that create it are
 *   responsible for casting it back to whatever concrete type they stored.
 */
public class JSType {

	public static JSType UNKNOWN = new JSType(null);

	// Primitive types
	public static JSType BOOLEAN = new JSType(Boolean.TYPE);
	public static JSType BYTE = new JSType(Byte.TYPE);
	public static JSType SHORT = new JSType(Short.TYPE);
	public static JSType INT = new JSType(Integer.TYPE);
	public static JSType LONG = new JSType(Long.TYPE);
	public static JSType FLOAT = new JSType(Float.TYPE);
	public static JSType DOUBLE = new JSType(Double.TYPE);
	public static JSType BIGINTEGER = new JSType(BigInteger.class);
	public static JSType BIGDECIMAL = new JSType(BigDecimal.class);
	public static JSType STRING = new JSType(String.class);

	// The value is definitely exactly `null` / exactly `undefined`.
	public static JSType NULL = new JSType(null);
	public static JSType UNDEFINED = new JSType(null);

	// Definitely some object, constructor not statically known. Distinct
	// from UNKNOWN: it still rules out the value being a primitive/string/etc.
	public static JSType OBJECT = new JSType(null);


	private final Class<?> javaClass;
	// Non-null only for a "base type, possibly nullish" composite (e.g. a
	// result of orNull()/orUndefined()) - null for a plain atom.
	private final JSType base;
	private final boolean canBeNull;
	private final boolean canBeUndefined;
	// Opaque handle to the declaration that produced this object type (see
	// ofConstructor()); null when not statically known.
	private final Object constructorRef;

	// Lazily-memoized derived variants, so repeated orNull()/orUndefined()/
	// orNullOrUndefined() calls on the same instance don't reallocate. Left
	// unsynchronized on purpose: these are immutable value holders and a
	// benign race just produces a redundant-but-equivalent duplicate - no
	// consumer needs `==` identity across independently-derived composites.
	private JSType nullVariant;
	private JSType undefinedVariant;
	private JSType bothVariant;

	protected JSType(Class<?> javaClass) {
		this(javaClass, null, false, false, null);
	}

	private JSType(Class<?> javaClass, JSType base, boolean canBeNull, boolean canBeUndefined, Object constructorRef) {
		this.javaClass = javaClass;
		this.base = base;
		this.canBeNull = canBeNull;
		this.canBeUndefined = canBeUndefined;
		this.constructorRef = constructorRef;
	}

	/**
	 * A JSType for "an object produced by this declaration" - e.g. instances
	 * of a specific class. Not interned/cached here: callers that need
	 * `==`-stable identity for repeated queries against the same declaration
	 * should memoize the result on the declaring node itself (its lifetime
	 * then matches the node's), not in a global map here - this codebase has
	 * already hit a real OOM from a static IdentityHashMap keyed on AST nodes
	 * that never got evicted (see ScopeResolutionOptimizer's class doc).
	 */
	public static JSType ofConstructor(Object constructorRef) {
		return new JSType(null, null, false, false, constructorRef);
	}

	/**
	 * 2-way merge for branch-like nodes (ternary, {@code &&}/{@code ||}). Returns UNKNOWN
	 * unless both sides agree on the same concrete base (in which case their
	 * nullish flags are combined) or one side is purely null/undefined and
	 * the other has a concrete base (in which case that base gains the
	 * corresponding nullish flag). Never invents a multi-base union.
	 */
	public static JSType join(JSType a, JSType b) {
		if(a==b) {
			return a;
		}
		if(a==UNKNOWN || b==UNKNOWN) {
			return UNKNOWN;
		}
		JSType baseA = a.baseType();
		JSType baseB = b.baseType();
		boolean aPureNullish = (baseA==NULL || baseA==UNDEFINED);
		boolean bPureNullish = (baseB==NULL || baseB==UNDEFINED);
		if(baseA==baseB && !aPureNullish) {
			JSType result = baseA;
			if(a.canBeNull() || b.canBeNull()) {
				result = result.orNull();
			}
			if(a.canBeUndefined() || b.canBeUndefined()) {
				result = result.orUndefined();
			}
			return result;
		}
		if(aPureNullish && !bPureNullish) {
			return withNullish(baseB, a==NULL || b.canBeNull(), a==UNDEFINED || b.canBeUndefined());
		}
		if(bPureNullish && !aPureNullish) {
			return withNullish(baseA, b==NULL || a.canBeNull(), b==UNDEFINED || a.canBeUndefined());
		}
		return UNKNOWN;
	}

	private static JSType withNullish(JSType base, boolean canBeNull, boolean canBeUndefined) {
		JSType result = base;
		if(canBeNull) {
			result = result.orNull();
		}
		if(canBeUndefined) {
			result = result.orUndefined();
		}
		return result;
	}

	public Class<?> getJavaClass()  {
		return javaClass;
	}

	public boolean isNullable() {
		return javaClass != null && !javaClass.isPrimitive();
	}

	/** Strips any nullish info, returning the underlying concrete type (or this instance, if it has none). */
	public JSType baseType() {
		return base != null ? base : this;
	}

	public boolean canBeNull() {
		return this==UNKNOWN || canBeNull || baseType()==NULL;
	}

	public boolean canBeUndefined() {
		return this==UNKNOWN || canBeUndefined || baseType()==UNDEFINED;
	}

	/** The declaration that produced this object type, if statically known - see ofConstructor(). */
	public Object getConstructorRef() {
		return constructorRef;
	}

	public boolean isObject() {
		return this==OBJECT || constructorRef!=null;
	}

	public JSType orNull() {
		if(this==UNKNOWN) {
			return UNKNOWN;
		}
		if(canBeNull()) {
			return this;
		}
		if(nullVariant==null) {
			nullVariant = new JSType(javaClass, baseType(), true, canBeUndefined, constructorRef);
		}
		return nullVariant;
	}

	public JSType orUndefined() {
		if(this==UNKNOWN) {
			return UNKNOWN;
		}
		if(canBeUndefined()) {
			return this;
		}
		if(undefinedVariant==null) {
			undefinedVariant = new JSType(javaClass, baseType(), canBeNull, true, constructorRef);
		}
		return undefinedVariant;
	}

	public JSType orNullOrUndefined() {
		if(this==UNKNOWN) {
			return UNKNOWN;
		}
		if(canBeNull() && canBeUndefined()) {
			return this;
		}
		if(bothVariant==null) {
			bothVariant = new JSType(javaClass, baseType(), true, true, constructorRef);
		}
		return bothVariant;
	}
}
