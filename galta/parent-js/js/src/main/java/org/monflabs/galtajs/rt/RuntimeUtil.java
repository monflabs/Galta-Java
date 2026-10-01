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
package org.monflabs.galtajs.rt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.monflabs.galtajs.JSContext;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.StaticConfiguration;
import org.monflabs.galtajs.external.ch_obermuhlner_math_big.BigDecimalMath;
import org.monflabs.galtajs.external.org_mozilla_javascript.v8dtoa.DoubleConversion;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.library.java.JavaClass;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.BaseInternalObject;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.ClassPrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.errors.Error;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayArguments;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayJSObject;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayJavaArray;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayList;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayString;
import org.monflabs.galtajs.rt.builtins.primitives.bool.BuiltinBooleanPrototype;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.builtins.primitives.number.BuiltinNumberPrototype;
import org.monflabs.galtajs.rt.builtins.standard.bigint.BuiltinBigIntPrototype;
import org.monflabs.galtajs.rt.builtins.standard.bigdecimal.BuiltinBigDecimalPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.string.BuiltinStringPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.BuiltinSymbolPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateElementsHolder;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIterator;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIteratorHelper;
import org.monflabs.galtajs.rt.builtins.standard.proxy.BuiltinProxy;
import org.monflabs.galtajs.rt.builtins.standard.set.SetLike;
import org.monflabs.galtajs.rt.builtins.standard.set.setlike.JSSetJSObject;
import org.monflabs.galtajs.rt.builtins.standard.set.setlike.JSSetJavaSet;
import org.monflabs.galtajs.rt.protocols.iterator.AsyncFromSyncJavaIterator;
import org.monflabs.galtajs.rt.protocols.iterator.AsyncJavaIterator;
import org.monflabs.galtajs.rt.protocols.iterator.JavaIterator;
import org.monflabs.galtajs.rt.util.NumberFormatting;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.galtajs.rt.util.strings.ConsString;
import org.monflabs.galtajs.rt.util.strings.ConsWrapper;
import org.monflabs.galtajs.util.SparseList;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.util.TypeUtil;
import org.monflabs.util.function.TriFunction;
import org.monflabs.util.function.TriPredicate;
import org.monflabs.util.generators.GeneratorReturnSignal;
import org.monflabs.util.generators.Yielder;
import org.monflabs.util.iterators.Iterators;


/**
 * Runtime utilities.
 */
public class RuntimeUtil {
	
	public static Object[] EMPTY_PARAMS = new Object[0];
	//public static final long _2_POWER_53 = 9007199254740992L;
	public static BigInteger BIGINT_MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

	// So the class in named and identifiable when debugging.
	private static class Unavailable {
		@Override
		public String toString() {
			return "<<unavailable>>";
		}
	}
	private static class Undefined {
		@Override
		public String toString() {
			return "undefined";
		}
	}
	// A let/const binding pre-populated at block/function/program-entry but not
	// yet reached by its own declaration statement (Temporal Dead Zone).
	private static class TdzSentinel {
		@Override
		public String toString() {
			return "<<uninitialized>>";
		}
	}

	public static final Object NOT_AVAILABLE = new Unavailable();
	public static final Object UNDEFINED = new Undefined();
	public static final Object TDZ = new TdzSentinel();

	// Throws if a let/const binding is still in its Temporal Dead Zone;
	// otherwise returns the value unchanged. Used at every read path that
	// resolves directly to a let/const VariableEntry/array slot.
	public static Object checkTDZ(Object value, String name) {
		if(value==TDZ) {
			throw referenceError("Cannot access '{0}' before initialization", name);
		}
		return value;
	}

	// GetThisBinding, as consulted by a derived class constructor's implicit
	// (fall-through, or a bare "return;") return (10.2.1.1
	// OrdinaryCallEvaluateBody / EvaluateBody: "Return ?
	// constructorEnv.GetThisBinding()"): a derived constructor's `_this`
	// starts as this exact UNDEFINED sentinel (BuiltinClassConstructor.
	// constructObject()'s "start with an uninitialized this" branch) until a
	// super() call replaces it - if it's still UNDEFINED here, super() was
	// never called, and per spec that's a ReferenceError, not "the
	// constructor returned undefined". A base (non-derived) constructor's
	// `_this` is always a real object by this point, so this is a no-op for
	// it either way - safe to call unconditionally for any class
	// constructor's own implicit/valueless return, without needing to know
	// separately whether ITS class happens to be derived.
	// The implicit return of a class constructor: see ConstructResultError
	public static Object checkThisBindingOnReturn(Object _this) {
		if(_this==UNDEFINED) {
			throw ConstructResultError.thisNotInitialized();
		}
		return _this;
	}

	public static Object checkThisBinding(Object _this) {
		if(_this==UNDEFINED) {
			throw referenceError("Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
		}
		return _this;
	}

	// NamedEvaluation for a class field initializer (ClassFieldDefinitionEvaluation
	// step "If IsAnonymousFunctionDefinition(Initializer), perform
	// NamedEvaluation..."): an anonymous function/arrow/class expression
	// assigned directly as a field's initializer gets the field's own name
	// (including the "#" prefix for a private field, via PrivateName's own
	// toString()). Transpiled-mode counterpart of ASTClassField's own
	// private interpreted-mode helper of the same name/logic - the
	// interpreter calls its own copy directly from Java, but transpiled
	// codegen has no equivalent runtime call to make without this public,
	// unqualified-callable version (see `import static RuntimeUtil.*` in
	// every generated class).
	public static void maybeSetFunctionName(Object name, Object value) {
		// BaseCallableObject, not BuiltinFunction specifically - the common
		// ancestor of BOTH plain functions (BuiltinFunction) AND class
		// constructors (BuiltinClassConstructor extends BaseStandardConstructor
		// extends BaseConstructor extends BaseNativeMethod extends
		// BaseCallableObject) - an anonymous CLASS expression/declaration
		// needs NamedEvaluation just as much as an anonymous function does
		// (test262 eval-export-dflt-expr-cls-anon.js: `export default (class
		// {...})` must get `.name === "default"` too, not just the function
		// case - the narrower BuiltinFunction-only check silently skipped
		// every class value).
		if(value instanceof org.monflabs.galtajs.rt.builtins.BaseCallableObject fn) {
			Object fnName = fn.getProperty("name");
			if(isNullOrUndefined(fnName) || StringUtil.isEmpty(fnName.toString())) {
				fn.setOwnProperty("name", PropertyDescriptor.propertyKeyToFunctionName(name), null, DESC_CHECK.NONE);
			}
		}
	}
	
	public static enum HINT { 
		DEFAULT(HINT_DEFAULT), 
		NUMBER(HINT_NUMBER), 
		STRING(HINT_STRING)
		;
		private Object[] params;
		HINT(String hint) {
			this.params = new Object[] {hint};
		}
		public Object[] hintParams() {
			return params;
		}
	}

	public static final String HINT_DEFAULT = "default";
	public static final String HINT_NUMBER = "number";
	public static final String HINT_STRING = "string";
	
	
	protected RuntimeUtil() {}
	
	
	public static final boolean isStrictMode() {
		JSContext ctx = JSContext.getUnchecked();
		if(ctx!=null) {
			return ctx.isStrictMode();
		}
		// Strict by default
		return true;
	}
	
	public static final boolean isStrictCheck(DESC_CHECK check) {
		return check==DESC_CHECK.STRICT || (check==DESC_CHECK.CHECK && isStrictMode());
	}
	
	
	//
	// Promote numbers for operations
	//
	
	public static final int NUMBER_INTEGER 		= 0;
	public static final int NUMBER_LONG 		= 1;
	public static final int NUMBER_BIGINTEGER 	= 2;
	public static final int NUMBER_DOUBLE 		= 3;
	public static final int NUMBER_BIGDECIMAL 	= 4;
	public static final int NUMBER_NAN 			= 5;
	public static final int NUMBER_MIXED		= 6; // Mixed reg and big numner

	private static final int[][] PROMOTE = new int[][] {
		//         I  L  BI D  BD Na 
		new int[] {0, 1, 2, 3, 4, 5},  // NUMBER_INTEGER
		new int[] {1, 1, 2, 3, 4, 5},  // NUMBER_LONG
		new int[] {2, 2, 2, 5, 5, 5},  // NUMBER_BIGINTEGER
		new int[] {3, 3, 4, 3, 4, 5},  // NUMBER_DOUBLE
		new int[] {4, 4, 4, 4, 4, 5},  // NUMBER_BIGDECIMAL
		new int[] {5, 5, 5, 5, 5, 5}   // NUMBER_NAN
	};
	private static final boolean[] BIGNUMBER = new boolean[] 
		{false,false,true,false,true,false};
	
	public static int numberType(Number o) {
		if(o instanceof Integer) {
			return NUMBER_INTEGER;
		}
		if(o instanceof Double d) {
			if(d.isNaN()) {
				return NUMBER_NAN;
			}
			return NUMBER_DOUBLE;
		}
		if(o instanceof Long) {
			return NUMBER_LONG;
		}
		if(o instanceof BigInteger) {
			return NUMBER_BIGINTEGER;
		}
		if(o instanceof BigDecimal) {
			return NUMBER_BIGDECIMAL;
		}
		if(o instanceof Short) {
			return NUMBER_INTEGER;
		}
		if(o instanceof Byte) {
			return NUMBER_INTEGER;
		}
		if(o instanceof Float f) {
			if(f.isNaN()) {
				return NUMBER_NAN;
			}
			return NUMBER_DOUBLE;
		}
		throw new JsonException(null,"Unsupported JSON number type {0}", o.getClass());
	}

	public static int promoteNumber(JSEnvironment env, int t1, int t2) {
		if(t1==t2) {
			return t1;
		}
		if(!env.supportMixedBigNumber()) {
			if(BIGNUMBER[t1]!=BIGNUMBER[t2]) {
				return NUMBER_MIXED;
			}
		}
		return PROMOTE[t1][t2];
	}
	public static int promoteNumberUnchecked(int t1, int t2) {
		if(t1==t2) {
			return t1;
		}
		return PROMOTE[t1][t2];
	}
	
	
	
	/////////////////////////////////////////////////////////////////////////
	// Binary operations
	/////////////////////////////////////////////////////////////////////////

	// https://262.ecma-international.org/5.1/#sec-11.6.1
	// Specialized overloads for known-CharSequence operands. Called from transpiled
	// code when ASTAdd.getReturnedType() propagates STRING - the emitted operand
	// is either a String literal (Java-static-typed String) or gets a
	// (CharSequence) cast so overload resolution picks the specialized form.
	// CharSequence (not String) because chained ASTAdd of STRING type returns a
	// ConsString at runtime under StaticConfiguration.ENABLE_CONSSTRING, and
	// String and ConsString are unrelated types (both extend CharSequence).
	// The overloads skip the Integer/Integer instanceof check, skip toPrimitive
	// on any operand already typed as CharSequence (already a primitive), and
	// land directly in the ConsString/concat path. Fall back to the generic
	// Object,Object form when a PrimitivePropertyMap override applies.
	// Below this combined length, a ConsString rope's own consumers
	// (toString()/charAt()/etc, see ConsString.charAt()'s flatten-once
	// logic) end up flattening it right back anyway - the wrapper
	// (two reference fields + a cached length) buys nothing for a result
	// this short, just extra allocation and indirection. Scoped to these
	// add() call sites only (not ConsString.of() itself, which other
	// callers - and ConsStringTest - rely on to always return a real
	// ConsString for two non-empty inputs).
	private static final int CONSSTRING_MIN_LENGTH = 16;
	private static Object consStringOrConcat(CharSequence s1, CharSequence s2) {
		if(s1.length()+s2.length() < CONSSTRING_MIN_LENGTH) {
			return s1.toString().concat(s2.toString());
		}
		return ConsString.of(s1, s2);
	}
	public static Object add(JSEnvironment env, CharSequence s1, CharSequence s2) {
		// Java's overload resolution picks this over (CharSequence,Object) or
		// (Object,Object) when the call site passes a literal `null` in either
		// slot (null is assignable to CharSequence and (CharSequence,CharSequence)
		// is the most specific). Fall back to the generic path so the null
		// coerces to "null" via primitiveToString rather than NPE-ing on
		// s.toString() below.
		if(s1==null || s2==null) {
			return add(env, (Object)s1, (Object)s2);
		}
		if(!isBoxedString(env, s1, s2)) {
			if(StaticConfiguration.ENABLE_CONSSTRING) {
				return consStringOrConcat(s1, s2);
			}
			return s1.toString().concat(s2.toString());
		}
		return add(env, (Object)s1, (Object)s2);
	}
	public static Object add(JSEnvironment env, CharSequence s1, Object o2) {
		if(s1==null) {
			return add(env, (Object)s1, o2);
		}
		if(!isBoxedString(env, s1) && !isBoxedPrimitive(env, o2)) {
			// s1 is a raw primitive CharSequence - always string-concat semantics.
			Object p2 = toPrimitive(env, o2, HINT.DEFAULT);
			if(StaticConfiguration.ENABLE_CONSSTRING) {
				return consStringOrConcat(s1, primitiveToString(p2));
			}
			return s1.toString().concat(primitiveToString(p2));
		}
		return add(env, (Object)s1, o2);
	}
	public static Object add(JSEnvironment env, Object o1, CharSequence s2) {
		if(s2==null) {
			return add(env, o1, (Object)s2);
		}
		if(!isBoxedPrimitive(env, o1) && !isBoxedString(env, s2)) {
			Object p1 = toPrimitive(env, o1, HINT.DEFAULT);
			if(StaticConfiguration.ENABLE_CONSSTRING) {
				return consStringOrConcat(primitiveToString(p1), s2);
			}
			return primitiveToString(p1).concat(s2.toString());
		}
		return add(env, o1, (Object)s2);
	}
	public static Object add(JSEnvironment env, Object o1, Object o2) {
		// Optimization. The type test comes FIRST so each fast path consults
		// only the side table for its own operand type - an int+int add is not
		// disabled by a boxed string or by an expando on a Java object.
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2) {
			if(!isBoxedNumber(env, o1, o2)) {
				// Avoid creating exceptions for perf reasons
				return addExact(env, i1.intValue(),i2.intValue());
			}
		} else if(o1 instanceof CharSequence s1 && o2 instanceof CharSequence s2) {
			if(!isBoxedString(env, o1, o2)) {
				if(StaticConfiguration.ENABLE_CONSSTRING) {
					return consStringOrConcat(s1, s2);
				}
				// String::concat is faster
				//return s1 + s2;
				return s1.toString().concat(s2.toString());
			}
		}
		
		o1 = toPrimitive(env,o1,HINT.DEFAULT);
		o2 = toPrimitive(env,o2,HINT.DEFAULT);
		
		if(o1 instanceof CharSequence || o2 instanceof CharSequence) {
			if(StaticConfiguration.ENABLE_CONSSTRING) {
				return consStringOrConcat(primitiveToString(o1),primitiveToString(o2));
			}
			// String::concat is faster
			return primitiveToString(o1).concat(primitiveToString(o2));
		}

		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER: {
    			// Avoid creating exceptions for perf reasons
    			return addExact(env,n1.intValue(),n2.intValue());
        	}
        	case NUMBER_LONG: {
    			// Avoid creating exceptions for perf reasons
    			return addExact(env,n1.longValue(),n2.longValue());
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(n1).add(TypeUtil.toBigDecimal(n2),env.getMathContext());
        		}
        		double d = TypeUtil.toDouble(n1)+TypeUtil.toDouble(n2);
        		// Should we transform the (double) to an int or long, if the result is one of them
        		//if((int)d==d) {
        		//	return (int)d;
        		//}
            	//if(env.supportLongPromotion()) {
            	//	if((long)d==d) {
            	//		return (long)d;
            	//	}
            	//}
        		return d;
        	}
        	case NUMBER_BIGINTEGER: {
        		return TypeUtil.toBigInteger(n1).add(TypeUtil.toBigInteger(n2));
        	}
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigDecimal(n1).add(TypeUtil.toBigDecimal(n2),env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("+", o1, o2);
	}
	public static Object addExact(JSEnvironment env, int x, int y) {
        int r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
        	if(env.supportLongPromotion()) {
        		return (long)x + (long)y;
        	}
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(x).add(TypeUtil.toBigInteger(y));
        	}
    		return (double)x + (double)y;
        }
        return r;
	}
	public static Object addExact(JSEnvironment env, long x, long y) {
        long r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(x).add(TypeUtil.toBigInteger(y));
        	}
            return (double)x + (double)y;
        }
        return r;
	}
	
	public static Object incNumber(JSEnvironment env, Object o1) {
		// Mostly used in for() loops with integers
		// Is this check really worth it?
		// Type test first, so the check consults only the number side table.
		if(o1 instanceof Integer n1) {
			if(!isBoxedNumber(env, o1)) {
				return incrementExact(env,n1.intValue());
			}
		} else if(o1 instanceof Long n1) {
			if(!isBoxedNumber(env, o1)) {
				return incrementExact(env,n1.longValue());
			}
		}
		
		Number n1 = toNumeric(env,o1);
        switch(numberType(n1)) {
        	case NUMBER_INTEGER: {
    			// Avoid creating exceptions for perf reasons
    			return incrementExact(env,n1.intValue());
        	}
        	case NUMBER_LONG: {
    			// Avoid creating exceptions for perf reasons
    			return incrementExact(env,n1.longValue());
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(n1).add(BigDecimal.ONE,env.getMathContext());
        		}
        		return TypeUtil.toDouble(n1)+1;
        	}
        	case NUMBER_BIGINTEGER: {
        		return TypeUtil.toBigInteger(n1).add(BigInteger.ONE);
        	}
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigDecimal(n1).add(BigDecimal.ONE,env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        }
		throw unary("++", o1);
	}
	public final static Object incrementExact(JSEnvironment env, int a) {
        if (a == Integer.MAX_VALUE) {
        	if(env.supportLongPromotion()) {
        		return (long)a + 1L;
        	}
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(a).add(BigInteger.ONE);
        	}
        	return (double)a + 1.0;
        }
        return a + 1;
	}
	public final static Object incrementExact(JSEnvironment env, long a) {
        if (a == Long.MAX_VALUE) {
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(a).add(BigInteger.ONE);
        	}
        	return (double)a + 1.0;
        }
        return a + 1L;
	}

	// https://262.ecma-international.org/5.1/#sec-11.6.2
	public static Object sub(JSEnvironment env, Object o1, Object o2) {
		// Avoid creating exceptions for perf reasons - loop optimization.
		// Type test first, so the check consults only the number side table.
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
			return subtractExact(env,i1.intValue(),i2.intValue());
		}

		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER: {
    			// Avoid creating exceptions for perf reasons
    			return subtractExact(env,n1.intValue(),n2.intValue());
        	}
        	case NUMBER_LONG: {
    			// Avoid creating exceptions for perf reasons
    			return subtractExact(env,n1.longValue(),n2.longValue());
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(n1).subtract(TypeUtil.toBigDecimal(n2),env.getMathContext());
        		}
        		return TypeUtil.toDouble(n1)-TypeUtil.toDouble(n2);
        	}
        	case NUMBER_BIGINTEGER: {
        		return TypeUtil.toBigInteger(n1).subtract(TypeUtil.toBigInteger(n2));
        	}
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigDecimal(n1).subtract(TypeUtil.toBigDecimal(n2),env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("-", o1, o2);
	}
	public static Object subtractExact(JSEnvironment env, int x, int y) {
        int r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different from the sign of x
        if (((x ^ y) & (x ^ r)) < 0) {
        	if(env.supportLongPromotion()) {
                return (long)x - (long)y;
        	}
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(x).subtract(TypeUtil.toBigInteger(y));
        	}
            return (double)x - (double)y;
        }
        return r;
	}
	public static Object subtractExact(JSEnvironment env, long x, long y) {
        long r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different from the sign of x
        if (((x ^ y) & (x ^ r)) < 0) {
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(x).subtract(TypeUtil.toBigInteger(y));
        	}
            return (double)x - (double)y;
        }
        return r;
	}

	public static Object decNumber(JSEnvironment env, Object o1) {
		// Mostly used in for() loops with integers
		// Is this check really worth it?
		// Type test first, so the check consults only the number side table.
		if(o1 instanceof Integer n1) {
			if(!isBoxedNumber(env, o1)) {
				return decrementExact(env,n1.intValue());
			}
		} else if(o1 instanceof Long n1) {
			if(!isBoxedNumber(env, o1)) {
				return decrementExact(env,n1.longValue());
			}
		}
		
		Number n1 = toNumeric(env,o1);
        switch(numberType(n1)) {
        	case NUMBER_INTEGER: {
    			// Avoid creating exceptions for perf reasons
    			return decrementExact(env,n1.intValue());
        	}
        	case NUMBER_LONG: {
    			// Avoid creating exceptions for perf reasons
    			return decrementExact(env,n1.longValue());
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(n1).subtract(BigDecimal.ONE,env.getMathContext());
        		}
        		return TypeUtil.toDouble(n1)-1;
        	}
        	case NUMBER_BIGINTEGER: {
        		return TypeUtil.toBigInteger(n1).subtract(BigInteger.ONE);
        	}
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigDecimal(n1).subtract(BigDecimal.ONE,env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        }
		throw unary("--", o1);
	}
	public static Object decrementExact(JSEnvironment env, int a) {
        if (a == Integer.MIN_VALUE) {
        	if(env.supportLongPromotion()) {
        		return (long)a - 1L;
        	}
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(a).subtract(BigInteger.ONE);
        	}
        	return (double)a - 1.0;
        }
        return a - 1;
	}
	public static Object decrementExact(JSEnvironment env, long a) {
        if (a == Long.MIN_VALUE) {
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(a).subtract(BigInteger.ONE);
        	}
        	return (double)a - 1.0;
        }
        return a - 1L;
	}

	public static Object mul(JSEnvironment env, Object o1, Object o2) {
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
    		return multiplyExact(env,i1.intValue(),i2.intValue());
		}
		
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER: {
    			// Avoid creating exceptions for perf reasons
        		return multiplyExact(env,n1.intValue(),n2.intValue());
        	}
        	case NUMBER_LONG: {
    			// Avoid creating exceptions for perf reasons
        		return multiplyExact(env,n1.longValue(),n2.longValue());
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(n1).multiply(TypeUtil.toBigDecimal(n2),env.getMathContext());
        		}
        		return TypeUtil.toDouble(n1)*TypeUtil.toDouble(n2);
        	}
        	case NUMBER_BIGINTEGER: {
        		return TypeUtil.toBigInteger(n1).multiply(TypeUtil.toBigInteger(n2));
        	}
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigDecimal(n1).multiply(TypeUtil.toBigDecimal(n2),env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("*", o1, o2);
	}
	public static Object multiplyExact(JSEnvironment env, int x, int y) {
        long r = (long)x * (long)y;
        if (r == 0 && (x ^ y) < 0) {
        	// 0 times a negative number is IEEE-754 -0, which an int can't represent
        	return -0.0;
        }
        if ((int)r != r) {
        	if(env.supportLongPromotion()) {
        		return r;
        	}
        	if(env.supportBigIntPromotion()) {
        		return TypeUtil.toBigInteger(r);
        	}
        	return (double)x * (double)y;
        }
        return (int)r;
		
	}
	public static Object multiplyExact(JSEnvironment env, long x, long y) {
        long r = x * y;
        if (r == 0 && (x ^ y) < 0) {
        	// 0 times a negative number is IEEE-754 -0, which a long can't represent
        	return -0.0;
        }
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
           if (((y != 0) && (r / y != x)) || (x == Long.MIN_VALUE && y == -1)) {
	           	if(env.supportBigIntPromotion()) {
	        		return TypeUtil.toBigInteger(x).multiply(TypeUtil.toBigInteger(y));
	        	}
                return (double)x * (double)y;
            }
        }
        return r;
	}


	public static Object mod(JSEnvironment env, Object o1, Object o2) {
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
			{
				int div = i2.intValue();
				if(div==0) {
					return Double.NaN;
				}
				int r = i1.intValue()%div;
				// A zero remainder from a negative dividend is IEEE-754 -0, which plain
				// int arithmetic can't represent - matches spec: 1/(-1 % 1) === -Infinity.
				return (r==0 && i1.intValue()<0) ? (Object)(-0.0) : (Object)r;
			}
		}

		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER: {
    			int div = n2.intValue();
    			if(div==0) {
    				return Double.NaN;
    			}
        		int r = n1.intValue()%div;
        		return (r==0 && n1.intValue()<0) ? (Object)(-0.0) : (Object)r;
        	}
        	case NUMBER_LONG: {
    			long div = n2.longValue();
    			if(div==0) {
    				return Double.NaN;
    			}
        		long r = n1.longValue()%div;
        		return (r==0 && n1.longValue()<0) ? (Object)(-0.0) : (Object)r;
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
        			BigDecimal div = TypeUtil.toBigDecimal(n2);
        			if(div.compareTo(BigDecimal.ZERO)==0) {
        				throw rangeError("Division by zero");
        			}
            		return TypeUtil.toBigDecimal(n1).remainder(div,env.getMathContext());
        		}
    			double div = TypeUtil.toDouble(n2);
    			if(div==0) {
    				return Double.NaN;
    			}
        		return TypeUtil.toDouble(n1)%div;
        	}
        	case NUMBER_BIGINTEGER: {
    			BigInteger div = TypeUtil.toBigInteger(n2);
    			if(div.compareTo(BigInteger.ZERO)==0) {
    				throw rangeError("Division by zero");
    			}
        		return TypeUtil.toBigInteger(n1).remainder(div);
        	}
        	case NUMBER_BIGDECIMAL: {
    			BigDecimal div = TypeUtil.toBigDecimal(n2);
    			if(div.compareTo(BigDecimal.ZERO)==0) {
    				throw rangeError("Division by zero");
    			}
        		return TypeUtil.toBigDecimal(n1).remainder(div,env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("%", o1, o2);
	}

	public static Object div(JSEnvironment env, Object o1, Object o2) {
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
			{
				int div = i2.intValue();
				if(div==0) {
					if(i1.intValue()==0) {
						return Double.NaN;
					}
					return i1.intValue()<0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				}
				double d = i1.doubleValue()/(double)div;
				int di = (int)d;
				// 0 divided by a negative number is -0: keep the double
				if(d==(double)di && !(di==0 && div<0)) {
					return di;
				}
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(i1).divide(TypeUtil.toBigDecimal(i2),env.getMathContext());
        		}
	    		return d;
			}
		}

		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER: {
    			int div = n2.intValue();
    			if(div==0) {
    				if(n1.intValue()==0) {
    					return Double.NaN;
    				}
    				return n1.intValue()<0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    			}
    			double d = n1.doubleValue()/(double)div;
    			int di = (int)d;
    			if(d==(double)di && !(di==0 && div<0)) {
    				return di;
    			}
        		if(env.forceBigDecimalOperations()) {
            		return TypeUtil.toBigDecimal(n1).divide(TypeUtil.toBigDecimal(n2),env.getMathContext());
        		}
        		return d;
        	}
        	case NUMBER_LONG: {
    			long div = n2.longValue();
    			if(div==0) {
    				if(n1.longValue()==0) {
    					return Double.NaN;
    				}
    				return n1.longValue()<0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    			}
    			long n = n1.longValue();
    			if(n==0 && div<0) {
    				return -0.0;
    			}
    			if(n % div==0 && !(n==Long.MIN_VALUE && div==-1)) {
    				return n/div;
    			}
    			return n1.doubleValue()/(double)div;
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
        			BigDecimal div = TypeUtil.toBigDecimal(n2);
        			if(div.compareTo(BigDecimal.ZERO)==0) {
        				throw rangeError("Division by zero");
        			}
            		return TypeUtil.toBigDecimal(n1).divide(div,env.getMathContext());
        		}
        		double d1 = TypeUtil.toDouble(n1);
    			double div = TypeUtil.toDouble(n2);
    			if(div==0 && d1!=0) {
					int s1 = d1<0? -1 : 1;
					int s2 = isNegativeZero(div) ? -1 : 1;
    				return s1*s2 < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    			}
        		return d1/div;
        	}
        	case NUMBER_BIGINTEGER: {
    			BigInteger div = TypeUtil.toBigInteger(n2);
    			if(div.compareTo(BigInteger.ZERO)==0) {
    				throw rangeError("Division by zero");
    			}
        		return TypeUtil.toBigInteger(n1).divide(div);
        	}
        	case NUMBER_BIGDECIMAL: {
    			BigDecimal div = TypeUtil.toBigDecimal(n2);
    			if(div.compareTo(BigDecimal.ZERO)==0) {
    				throw rangeError("Division by zero");
    			}
        		return TypeUtil.toBigDecimal(n1).divide(div,env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("/", o1, o2);
	}
    public static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0);
    }
    public static boolean isNegativeZero(float value) {
        return Float.floatToRawIntBits(value) == Float.floatToRawIntBits(-0.0f);
    }
    
	public static Object power(JSEnvironment env, Object o1, Object o2) {
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER: 
        	case NUMBER_LONG: {
        		double d = Math.pow(n1.doubleValue(), n2.doubleValue());
        	    if (d>=Integer.MIN_VALUE && d<=Integer.MAX_VALUE) {
        	    	if(d == (int)d) {
        	    		return (int)d;
        	    	}
        	    } else if (env.supportLongPromotion() && d>=Long.MIN_VALUE && d<=Long.MAX_VALUE) {
        	    	// A Long only when the environment uses long integers at all
        	    	if(d == (long)d) {
        	    		return (long)d;
        	    	}
        	    }
        	    return d;
        	}
        	case NUMBER_DOUBLE: {
        		if(env.forceBigDecimalOperations()) {
            		BigDecimal d1 = JsonUtil.toBigDecimal(n1);
            		BigDecimal d2 = JsonUtil.toBigDecimal(n2);
            		return BigDecimalMath.pow(d1, d2, env.getMathContext());
        		}
        		return Math.pow(n1.doubleValue(), n2.doubleValue());
        	}
        	case NUMBER_BIGINTEGER: {
        		BigInteger b1 = JsonUtil.toBigInteger(n1);
        		BigInteger b2 = JsonUtil.toBigInteger(n2);
        		// Checked eagerly (before intValueExact()) rather than relying
        		// on BigInteger.pow(int)'s own negative-exponent check - a
        		// negative exponent too large to fit in an int (e.g.
        		// 1n ** -100000000000000000n) would otherwise make
        		// intValueExact() itself throw first, a DIFFERENT raw
        		// ArithmeticException. Per spec, BigInt exponentiation with a
        		// negative exponent is a RangeError either way.
        		if(b2.signum()<0) {
        			throw rangeError("Exponent must be non-negative");
        		}
        		return b1.pow(b2.intValueExact());
        	}
        	case NUMBER_BIGDECIMAL: {
        		BigDecimal d1 = JsonUtil.toBigDecimal(n1);
        		BigDecimal d2 = JsonUtil.toBigDecimal(n2);
        		return BigDecimalMath.pow(d1, d2, env.getMathContext());
        	}
        	case NUMBER_NAN: {
        		// Number::exponentiate: an exponent of +-0 is always 1, even for a NaN
        		// base - Math.pow already implements this IEEE-754 rule, unlike a bare
        		// NaN short-circuit would.
        		return Math.pow(n1.doubleValue(), n2.doubleValue());
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("**", o1, o2);
	}

	
	// Length of a JS string-typed value that may be a bare Character (never
	// has its own .length()) or a CharSequence (String/ConsString - a rope's
	// .length() is a cached field, free to read without flattening). Used
	// by eq()/eqStrict()/eqSameValue() below to reject a length mismatch
	// before flattening either side just to find out they can't possibly be
	// equal - see ConsString's own doc comment on why forcing a flatten for
	// this is otherwise wasteful (a huge rope compared against a short
	// literal would materialize the whole rope only to fail on length).
	private static int charOrSeqLength(Object o) {
		return o instanceof CharSequence cs ? cs.length() : 1;
	}

	// new Number(12) == new Number(12)    <- false
	// 12 == 12                            <- true
	// new Number(12) == 12                <- true
	public static boolean eq(JSEnvironment env, Object o1, Object o2) {
		if(o1==o2) {
			if(o1 instanceof Double d) {
				return !Double.isNaN(d);
			}
			return true;
		}
		if(o1==null || o1==UNDEFINED) {
			// Annex B.3.7 [[IsHTMLDDA]]: loosely equal to both null and
			// undefined (unlike every other object, per Abstract Equality
			// Comparison's own IsHTMLDDA special case) - but NOT identical
			// via === / SameValue, which never reach this eq() function.
			return o2==null || o2==UNDEFINED || env.isHTMLDDAObject(o2);
		}
		if(o2==null || o2==UNDEFINED) {
			return env.isHTMLDDAObject(o1);
		}
				
        JavascriptType t1 = jsType(env,o1);
        JavascriptType t2 = jsType(env,o2);
        if(t1==t2) {
    		switch(t1) {
				case STRING: {
					if(charOrSeqLength(o1)!=charOrSeqLength(o2)) {
						return false;
					}
					String s1 = o1.toString();
					String s2 = o2.toString();
					// If both are objects -> comparison fails...
					PrimitivePropertyMap map = env.getStringProperties();
					if(map!=null && map.containsKey(o1) && map.containsKey(o2) ) {
						return false;
					}
		            return s1.equals(s2);
				}
				case NUMBER: {
					Number n1 = (Number)o1;
					Number n2 = (Number)o2;
					// If both are objects -> comparison fails...
					PrimitivePropertyMap map = env.getNumberProperties();
					if(map!=null && map.containsKey(o1) && map.containsKey(o2) ) {
						return false;
					}
					return eqNumber(n1, n2, false);
				}
				case BOOLEAN: {
					Boolean b1 = (Boolean)o1;
					Boolean b2 = (Boolean)o2;
					// If both are objects -> comparison fails...
					PrimitivePropertyMap map = env.getBooleanProperties();
					if(map!=null && map.containsKey(o1) && map.containsKey(o2) ) {
						return false;
					}
					return b1.booleanValue()==b2.booleanValue();
				}
				default: {
					return o1==o2;
				}
			}
        }
        
        if(t1==JavascriptType.NUMBER && t2==JavascriptType.STRING) {
    		//return eq(o1, toNumber(o2));
    		Number n1 = (Number)o1;
    		if(n1 instanceof BigInteger bi) {
    			BigInteger parsed = stringToBigInt(o2.toString());
    			return parsed!=null && bi.compareTo(parsed)==0;
    		}
    		Number n2 = toNumeric(env,o2);
    		return eqNumber(n1, n2, false);
        }
        if(t1==JavascriptType.STRING && t2==JavascriptType.NUMBER) {
    		//return eq(toNumber(o1), o2);
    		Number n2 = (Number)o2;
    		if(n2 instanceof BigInteger bi) {
    			BigInteger parsed = stringToBigInt(o1.toString());
    			return parsed!=null && bi.compareTo(parsed)==0;
    		}
    		Number n1 = toNumeric(env,o1);
    		return eqNumber(n1, n2, false);
        }
        if(t1==JavascriptType.BOOLEAN) {
    		return eq(env,toNumeric(env,o1), o2);
        }
        if(t2==JavascriptType.BOOLEAN) {
    		return eq(env,o1, toNumeric(env,o2));
        }
        
        if( (t1==JavascriptType.STRING || t1==JavascriptType.NUMBER || t1==JavascriptType.SYMBOL) && (t2==JavascriptType.OBJECT) ) {
       		return eq(env,o1,toPrimitive(env,o2));
        }
        if( (t1==JavascriptType.OBJECT) && (t2==JavascriptType.STRING || t2==JavascriptType.NUMBER || t2==JavascriptType.SYMBOL) ) {
    		return eq(env,toPrimitive(env,o1),o2);
        }

        // Neither side is Object (that case is handled above via ToPrimitive
        // recursion) - a Symbol compared to any other primitive is never equal.
        if(t1==JavascriptType.SYMBOL || t2==JavascriptType.SYMBOL) {
    		return false;
        }

        return false;
	}
	// StringToBigInt (spec 7.1.14): exact decimal parse, unlike StringToNumber which
	// goes through a double and would lose precision for large integers. Returns
	// null (not a thrown exception) on an unparseable string - callers that need
	// StringToBigInt's SyntaxError (the BigInt() constructor, ToBigInt) check for
	// null themselves; callers doing comparisons (==, <, >) just treat it as "no match".
	public static BigInteger stringToBigInt(String s) {
		String t = trimWhiteSpaces(s);
		if(t.isEmpty()) {
			return BigInteger.ZERO;
		}
		try {
			// NonDecimalIntegerLiteral forms (no sign allowed, unlike the decimal form).
			if(t.length()>2 && t.charAt(0)=='0') {
				int radix = switch(Character.toLowerCase(t.charAt(1))) {
					case 'x' -> 16;
					case 'o' -> 8;
					case 'b' -> 2;
					default -> -1;
				};
				if(radix>0) {
					return new BigInteger(t.substring(2), radix);
				}
			}
			return new BigInteger(t);
		} catch(NumberFormatException ex) {
			return null;
		}
	}
	// PROMOTE has no exact type for BigInteger<->Double (arithmetic mixing is gated
	// elsewhere), but == and < / > must still compare BigInt and Number mathematically per spec.
	private static int compareBigIntegerToDouble(BigInteger bi, double d) {
		if(Double.isInfinite(d)) {
			return d>0 ? -1 : 1;
		}
		return new BigDecimal(bi).compareTo(new BigDecimal(d));
	}
	public static boolean eqNumber(Number n1, Number n2, boolean sameValue) {
		int t1 = numberType(n1), t2 = numberType(n2);
		if(t1==NUMBER_BIGINTEGER && t2==NUMBER_DOUBLE) {
			return compareBigIntegerToDouble((BigInteger)n1, n2.doubleValue())==0;
		}
		if(t1==NUMBER_DOUBLE && t2==NUMBER_BIGINTEGER) {
			return compareBigIntegerToDouble((BigInteger)n2, n1.doubleValue())==0;
		}
        switch(promoteNumberUnchecked(t1, t2)) {
	    	case NUMBER_INTEGER: {
	    		return n1.intValue()==n2.intValue();
	    	}
	    	case NUMBER_LONG: {
	    		return n1.longValue()==n2.longValue();
	    	}
	    	case NUMBER_DOUBLE: {
	    		return TypeUtil.toDouble(n1)==TypeUtil.toDouble(n2);
	    	}
	    	case NUMBER_BIGINTEGER: {
	    		// Use compareTo and not equals
	    		return TypeUtil.toBigInteger(n1).compareTo(TypeUtil.toBigInteger(n2))==0;
	    	}
	    	case NUMBER_BIGDECIMAL: {
	    		// Use compareTo and not equals as 8!=8.0
	    		return TypeUtil.toBigDecimal(n1).compareTo(TypeUtil.toBigDecimal(n2))==0;
	    	}
	    	case NUMBER_NAN: {
	    		// promoteNumberUnchecked collapses to NUMBER_NAN whenever
	    		// EITHER operand is NaN, not only when both are - SameValue
	    		// must still distinguish "both NaN" (true) from "one NaN, one
	    		// not" (false, e.g. SameValue(0, NaN)) - confirmed via
	    		// Object.is/not-same-value-x-y-type.js: `Object.is(0, NaN)`
	    		// must be false, not true.
	    		return sameValue && isNaN(n1) && isNaN(n2);
	    	}
        	case NUMBER_MIXED: {
				throw new IllegalStateException();
        	}
	    }
		throw binary("==", n1, n2);
	}
	public static int compareNumber(Number n1, Number n2) {
		int t1 = numberType(n1), t2 = numberType(n2);
		if(t1==NUMBER_BIGINTEGER && t2==NUMBER_DOUBLE) {
			return compareBigIntegerToDouble((BigInteger)n1, n2.doubleValue());
		}
		if(t1==NUMBER_DOUBLE && t2==NUMBER_BIGINTEGER) {
			return -compareBigIntegerToDouble((BigInteger)n2, n1.doubleValue());
		}
        switch(promoteNumberUnchecked(t1, t2)) {
	    	case NUMBER_INTEGER: {
	    		return Integer.compare(n1.intValue(),n2.intValue());
	    	}
	    	case NUMBER_LONG: {
	    		return Long.compare(n1.longValue(),n2.longValue());
	    	}
	    	case NUMBER_DOUBLE: {
	    		return Double.compare(TypeUtil.toDouble(n1),TypeUtil.toDouble(n2));
	    	}
	    	case NUMBER_BIGINTEGER: {
	    		// Use compareTo and not equals
	    		return TypeUtil.toBigInteger(n1).compareTo(TypeUtil.toBigInteger(n2));
	    	}
	    	case NUMBER_BIGDECIMAL: {
	    		// Use compareTo and not equals as 8!=8.0
	    		return TypeUtil.toBigDecimal(n1).compareTo(TypeUtil.toBigDecimal(n2));
	    	}
	    	case NUMBER_NAN: {
	    		return 0;
	    	}
        	case NUMBER_MIXED: {
				throw new IllegalStateException();
        	}
	    }
		throw binary("==", n1, n2);
	}
	



	public static boolean ne(JSEnvironment env, Object o1, Object o2) {
		return !eq(env,o1,o2);
	}
	

    public static boolean lt(JSEnvironment env, Object v1, Object v2) {
		// Comparing integer is very common particularly in loops
		//    ex: for(i=0; i<xxx; i++)
		// We optimize this use case right away
		if(v1 instanceof Integer i1 && v2 instanceof Integer i2) {
			if(!isBoxedNumber(env, v1, v2)) {
				return i1.intValue()<i2.intValue();
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = toPrimitive(env,v2,HINT.NUMBER);
		return _lt(env,o1, o2, false);
	}
    // Transpiler optimizations - override, for loops
    public static boolean lt(JSEnvironment env, Object v1, Integer v2) {
		if(v1 instanceof Integer i1) {
			if(!isBoxedNumber(env, v1)) {
					return i1.intValue()<(v2!=null?v2.intValue():0);
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = toPrimitive(env,v2,HINT.NUMBER);
		return _lt(env,o1, o2, false);
	}

    public static boolean le(JSEnvironment env, Object v1, Object v2) {
		// Comparing integer is very common particularly in loops
		//    ex: for(i=0; i<xxx; i++)
		// We optimize this use case right away
		if(v1 instanceof Integer i1 && v2 instanceof Integer i2) {
			if(!isBoxedNumber(env, v1, v2)) {
				return i1.intValue()<=i2.intValue();
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = toPrimitive(env,v2,HINT.NUMBER);
		return !_lt(env,o2, o1, true);
	}
    // Transpiler optimizations - override, for loops
    public static boolean le(JSEnvironment env, Object v1, Integer v2) {
		if(v1 instanceof Integer i1) {
			if(!isBoxedNumber(env, v1)) {
					return i1.intValue()<=(v2!=null?v2.intValue():0);
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = toPrimitive(env,v2,HINT.NUMBER);
		return !_lt(env,o2, o1, true);
	}

    public static boolean gt(JSEnvironment env, Object v1, Object v2) {
		// Comparing integer is very common particularly in loops
		//    ex: for(i=0; i<xxx; i++)
		// We optimize this use case right away
		if(v1 instanceof Integer i1 && v2 instanceof Integer i2) {
			if(!isBoxedNumber(env, v1, v2)) {
				return i1.intValue()>i2.intValue();
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = toPrimitive(env,v2,HINT.NUMBER);
		return _lt(env,o2, o1, false);
	}
	public static boolean ge(JSEnvironment env, Object v1, Object v2) {
		// Comparing integer is very common particularly in loops
		//    ex: for(i=0; i<xxx; i++)
		// We optimize this use case right away
		if(v1 instanceof Integer i1 && v2 instanceof Integer i2) {
			if(!isBoxedNumber(env, v1, v2)) {
				return i1.intValue()>=i2.intValue();
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = toPrimitive(env,v2,HINT.NUMBER);
		return !_lt(env,o1, o2, true);
	}
	
	public static boolean _lt(JSEnvironment env, Object o1, Object o2, boolean valueForNaN) {
		if(o1 instanceof CharSequence s1 && o2 instanceof CharSequence s2) {
			return s1.toString().compareTo(s2.toString())<0;
		}

		// BigInt <-> String must use StringToBigInt (exact), not StringToNumber
		// (toNumeric), which would lose precision for large integers.
		if(o1 instanceof BigInteger bi1 && o2 instanceof CharSequence s2) {
			BigInteger parsed = stringToBigInt(s2.toString());
			return parsed==null ? valueForNaN : bi1.compareTo(parsed)<0;
		}
		if(o1 instanceof CharSequence s1 && o2 instanceof BigInteger bi2) {
			BigInteger parsed = stringToBigInt(s1.toString());
			return parsed==null ? valueForNaN : parsed.compareTo(bi2)<0;
		}

		Number n1;
		if(o1 instanceof Number n) {
			n1 = n;
		} else {
			n1 = toNumeric(env,o1);
		}
		Number n2;
		if(o2 instanceof Number n) {
			n2 = n;
		} else {
			n2 = toNumeric(env,o2);
		}
		int t1 = numberType(n1), t2 = numberType(n2);
		// PROMOTE has no exact type for BigInteger<->Double (see compareNumber/
		// eqNumber); compare mathematically instead of falling through to NaN.
		if(t1==NUMBER_BIGINTEGER && t2==NUMBER_DOUBLE) {
			return compareBigIntegerToDouble((BigInteger)n1, n2.doubleValue())<0;
		}
		if(t1==NUMBER_DOUBLE && t2==NUMBER_BIGINTEGER) {
			return compareBigIntegerToDouble((BigInteger)n2, n1.doubleValue())>0;
		}
        switch(promoteNumberUnchecked(t1, t2)) {
        	case NUMBER_INTEGER: {
        		return n1.intValue()<n2.intValue();
        	}
        	case NUMBER_LONG: {
        		return n1.longValue()<n2.longValue();
        	}
        	case NUMBER_DOUBLE: {
        		return TypeUtil.toDouble(n1)<TypeUtil.toDouble(n2);
        	}
        	case NUMBER_BIGINTEGER: {
        		return TypeUtil.toBigInteger(n1).compareTo(TypeUtil.toBigInteger(n2))<0;
        	}
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigDecimal(n1).compareTo(TypeUtil.toBigDecimal(n2))<0;
        	}
        	case NUMBER_NAN: {
        		// This is different from JavaScript that returns undefined!
        		return valueForNaN;
        	}
        	case NUMBER_MIXED: {
				throw new IllegalStateException();
        	}
        }
		throw binary("<", o1, o2);
	}
	
	// new Number(12) === new Number(12)    <- false
	// 12 === 12                            <- true
	// new Number(12) === 12                <- false
	public static boolean eqStrict(JSEnvironment env, Object o1, Object o2) {
		// A WithClosure is a synthetic per-lookup wrapper (see
		// InterpretedWithRuntimeContext.resolveOwnIdentifierEntry()) that
		// exists ONLY to preserve `this` for a subsequent direct call
		// (`with(obj) { f() }`) - it's never a real distinct value per
		// spec, so a bare reference to it (not immediately called) must
		// still compare === to the underlying function itself
		// (`with(obj) { f === obj.f }`, confirmed via test262's
		// dynamic-import/syntax/valid/nested-with-expression-*.js: `with
		// (aPromise) { assert.sameValue(then, Promise.prototype.then); }`).
		// Unwrapped here, in the shared === primitive, rather than at every
		// call site that might receive one.
		if(o1 instanceof org.monflabs.galtajs.rt.builtins.WithClosure wc1) {
			o1 = wc1.getCallable();
		}
		if(o2 instanceof org.monflabs.galtajs.rt.builtins.WithClosure wc2) {
			o2 = wc2.getCallable();
		}
		if(o1==o2) {
			// A boxed Number *object* (e.g. new Number(NaN)) is always === to itself:
			// the "NaN !== NaN" rule only applies to the primitive value, not identity.
			if(o1 instanceof Double d && !isBoxedNumber(env,o1)) {
				return !Double.isNaN(d);
			}
			return true;
		}
		if(o1==null || o1==UNDEFINED || o2==null || o2==UNDEFINED) {
			return false; 
		}
		
		// Use the native side tables to check === for primitives. Each branch
		// below consults only the table for its own operand type.
		// Common use cases
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2) {
			// If one is an objects -> comparison fails...
			if(isBoxedNumber(env, o1, o2)) {
				return false;
			}
			return i1.intValue()==i2.intValue();
		}
		if(o1 instanceof Number n1) {
			if(o2 instanceof Number n2) {
				// If one is an objects -> comparison fails...
				if(isBoxedNumber(env, o1, o2)) {
					return false;
				}
				if(!env.supportMixedBigNumber()) {
					// Both should be BigInteger
					if( n1 instanceof BigInteger ) {
						if( !(n2 instanceof BigInteger) ) {
							return false;
						}
					} else {
						if( n2 instanceof BigInteger ) {
							return false;
						}
					}
					// Both should be BigDecimal
					if( n1 instanceof BigDecimal ) {
						if( !(n2 instanceof BigDecimal) ) {
							return false;
						}
					} else {
						if( n2 instanceof BigDecimal ) {
							return false;
						}
					}
				}
				return eqNumber(n1, n2, false);
			}
			return false;
		}
		if(o1 instanceof CharSequence || o1 instanceof Character) {
			if(o2 instanceof CharSequence || o2 instanceof Character) {
				if(charOrSeqLength(o1)!=charOrSeqLength(o2)) {
					return false;
				}
				// If one is an objects -> comparison fails...
				if(isBoxedString(env, o1, o2)) {
					return false;
				}
				String s1 = o1.toString(); // Can be Character or CharSequence!
				String s2 = o2.toString(); // Can be Character or CharSequence!
	            return s1.equals(s2);
			}
			return false;
		}
		if(o1 instanceof Boolean b1) {
			if(o2 instanceof Boolean b2) {
				// If one is an objects -> comparison fails...
				if(isBoxedBoolean(env, o1, o2)) {
					return false;
				}
				return b1.booleanValue()==b2.booleanValue();
			}
			return false;
		}
		// Includes TYPE_UNKNOWN, like java objects
		return o1==o2;
	}
	public static boolean neStrict(JSEnvironment env, Object o1, Object o2) {
		return !eqStrict(env, o1, o2);
	}

	
	public static boolean eqSameValue(JSEnvironment env, Object o1, Object o2) {
		// A WithClosure is a synthetic per-lookup wrapper (see
		// InterpretedWithRuntimeContext.resolveOwnIdentifierEntry()) that
		// exists ONLY to preserve `this` for a subsequent direct call
		// (`with(obj) { f() }`) - it's never a real distinct value per
		// spec, so a bare reference to it (not immediately called) must
		// still compare equal to the underlying function itself
		// (`with(obj) { f === obj.f }`, confirmed via test262's
		// dynamic-import/syntax/valid/nested-with-expression-*.js: `with
		// (aPromise) { assert.sameValue(then, Promise.prototype.then); }`).
		// Unwrapped here, in the single shared SameValue/=== primitive,
		// rather than at every call site that might receive one.
		if(o1 instanceof org.monflabs.galtajs.rt.builtins.WithClosure wc1) {
			o1 = wc1.getCallable();
		}
		if(o2 instanceof org.monflabs.galtajs.rt.builtins.WithClosure wc2) {
			o2 = wc2.getCallable();
		}
		if(o1==o2) {
			return true;
		}
		if(isObject(env, o1) || isObject(env, o2)) {
			return o1==o2;
		}
		// Ok primitives now
		JavascriptType t1 = jsType(env,o1);
		JavascriptType t2 = jsType(env,o2);
		if(t1!=t2) {
			return false;
		}
		switch(t1) {
			case NULL: {
				return false;
			}
			case UNDEFINED: {
				return false;
			}
			case STRING: {
				if(charOrSeqLength(o1)!=charOrSeqLength(o2)) {
					return false;
				}
				String s1 = o1.toString(); // Can be Character or CharSequence!
				String s2 = o2.toString(); // Can be Character or CharSequence!
	            return s1.equals(s2);
			}
			case NUMBER: {
				Number n1 = (Number)o1;
				Number n2 = (Number)o2;
				if(n1.doubleValue()==0 && n2.doubleValue()==0) {
					return isNegativeZero(n1.doubleValue()) == isNegativeZero(n2.doubleValue()); 
				}
				return eqNumber(n1, n2, true);
			}
			case BOOLEAN: {
				Boolean b1 = (Boolean)o1;
				Boolean b2 = (Boolean)o2;
				return b1.booleanValue()==b2.booleanValue();
			}
			default: {
				// Includes Symbols
				// Includes TYPE_UNKNOWN, like java objects
				return o1==o2;
			}
		}
	}

	// SameValueZero - identical to SameValue (eqSameValue above) EXCEPT +0
	// and -0 are equal (used by Array.prototype.includes/Set/Map/TypedArray
	// methods, as opposed to Object.is's strict SameValue where +0 !== -0).
	// Reuses eqSameValue for every type except NUMBER, where eqSameValue
	// special-cases +0/-0 as DIFFERENT (SameValue's own rule) - going
	// straight to eqNumber's own NUMBER_DOUBLE case (plain Java `==`, which
	// already treats 0.0==-0.0 as true) gives the correct SameValueZero
	// answer instead (confirmed via
	// Array/prototype/includes/samevaluezero.js: `[42,0,1,NaN].includes(-0)`
	// must be true).
	public static boolean eqSameValueZero(JSEnvironment env, Object o1, Object o2) {
		if(o1==o2) {
			return true;
		}
		if(isObject(env, o1) || isObject(env, o2)) {
			return o1==o2;
		}
		JavascriptType t1 = jsType(env,o1);
		JavascriptType t2 = jsType(env,o2);
		if(t1!=t2) {
			return false;
		}
		if(t1==JavascriptType.NUMBER) {
			return eqNumber((Number)o1,(Number)o2,true);
		}
		return eqSameValue(env, o1, o2);
	}

	public static Object and(JSEnvironment real, Object o1, Object o2) {
		if(!toBoolean(real,o1)) {
			return o1;
		}
		return o2;
	}
	public static Object and(JSEnvironment env, Object o1, Supplier<Object> o2) {
		if(!toBoolean(env,o1)) {
			return o1;
		}
		return o2.get();
	}

	public static Object or(JSEnvironment env, Object o1, Object o2) {
		if(toBoolean(env,o1)) {
			return o1;
		}
		return o2;
	}
	public static Object or(JSEnvironment env, Object o1, Supplier<Object> o2) {
		if(toBoolean(env,o1)) {
			return o1;
		}
		return o2.get();
	}

	public static boolean xor(JSEnvironment env, Object o1, Object o2) {
		boolean v1 = toBoolean(env,o1);
		boolean v2 = toBoolean(env,o2);
		if((v1 && !v2) || (!v1 && v2)) {
			return Boolean.TRUE;
		} else {
			return Boolean.FALSE;
		}
	}

	public static Object nullCoalescing(JSEnvironment env, Object leftValue, Object rightValue) {
		if(isNotNullOrUndefined(leftValue)) {
			return leftValue;
		}
		return rightValue;
	}	
	public static Object nullCoalescing(JSEnvironment env, Object leftValue, Supplier<Object> rightValue) {
		if(isNotNullOrUndefined(leftValue)) {
			return leftValue;
		}
		return rightValue.get();
	}	
	

	public static Object bitAnd(JSEnvironment env, Object o1, Object o2) {
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
                int i1 = toInt32(n1);
                int i2 = toInt32(n2);
         		return i1 & i2;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
        		BigInteger b1 = JsonUtil.toBigInteger(n1);
        		BigInteger b2 = JsonUtil.toBigInteger(n2);
        		return b1.and(b2);
        	}
        	case NUMBER_NAN: {
        		if(isBigNumber(n1) || isBigNumber(n2)) {
        			return BigInteger.ZERO;
        		}
         		return 0;
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("&", o1, o2);
	}

	public static Object bitOr(JSEnvironment env, Object o1, Object o2) {
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
        		int i1 = toInt32(n1);
        		int i2 = toInt32(n2);
         		return i1 | i2;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
        		BigInteger b1 = JsonUtil.toBigInteger(n1);
        		BigInteger b2 = JsonUtil.toBigInteger(n2);
        		return b1.or(b2);
        	}
        	case NUMBER_NAN: {
        		if(isNaN(n1)) {
        			if(isNaN(n2)) {
        				return 0;
        			}
        			if(isBigNumber(n2)) {
        				return JsonUtil.toBigInteger(n2);
        			}
        			return toInt32(n2);
        		} else {
        			if(isBigNumber(n1)) {
        				return JsonUtil.toBigInteger(n1);
        			}
        			return toInt32(n1); 
        		}
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("|", o1, o2);
	}

	public static Object bitXor(JSEnvironment env, Object o1, Object o2) {
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
        		int i1 = toInt32(n1);
        		int i2 = toInt32(n2);
         		return i1 ^ i2;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
        		BigInteger b1 = JsonUtil.toBigInteger(n1);
        		BigInteger b2 = JsonUtil.toBigInteger(n2);
        		return b1.xor(b2);
        	}
        	case NUMBER_NAN: {
        		if(isNaN(n1)) {
        			if(isNaN(n2)) {
        				return 0;
        			}
        			if(isBigNumber(n2)) {
        				return JsonUtil.toBigInteger(n2);
        			}
        			return toInt32(n2);
        		} else {
        			if(isBigNumber(n1)) {
        				return JsonUtil.toBigInteger(n1);
        			}
        			return toInt32(n1); 
        		}
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("^", o1, o2);
	}

	public static Object lshift(JSEnvironment env, Object o1, Object o2) {
		// Phase 5b: Integer+Integer inline fast path, gated on the primitive
		// property map so boxed-primitive-with-properties objects still hit the
		// generic path (parity with add()/sub()/mul()/mod()/div()).
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
			{
				return i1.intValue() << (i2.intValue() & 0x1F);
			}
		}
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
        		int i1 = toInt32(n1);
                int i2 = (int)(toUInt32(n2) & 0x1F);
           		return i1 << i2;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
        		BigInteger b1 = JsonUtil.toBigInteger(n1);
        		BigInteger b2 = JsonUtil.toBigInteger(n2);
        		return b1.shiftLeft(b2.intValueExact());
        	}
        	case NUMBER_NAN: {
        		if(numberType(n1)==NUMBER_NAN) {
        			return 0;
        		}
    			if(isBigNumber(n1)) {
    				return JsonUtil.toBigInteger(n1);
    			}
    			return toInt32(n1); 
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary("<<", o1, o2);
	}
	public static Object rshift(JSEnvironment env, Object o1, Object o2) {
		// Phase 5b: Integer+Integer inline fast path.
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
			{
				return (i1.intValue() >> (i2.intValue() & 0x1F)) & 0xFFFFFFFF;
			}
		}
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
        		int i1 = toInt32(n1);
                int i2 = (int)(toUInt32(n2) & 0x1F);
           		return (i1 >> i2) & 0xFFFFFFFF;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
        		BigInteger b1 = JsonUtil.toBigInteger(n1);
        		BigInteger b2 = JsonUtil.toBigInteger(n2);
        		return b1.shiftRight(b2.intValueExact());
        	}
        	case NUMBER_NAN: {
        		if(numberType(n1)==NUMBER_NAN) {
        			return 0;
        		}
    			if(isBigNumber(n1)) {
    				return JsonUtil.toBigInteger(n1);
    			}
    			return toInt32(n1); 
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary(">>", o1, o2);
	}
	public static Object runshift(JSEnvironment env, Object o1, Object o2) {
		// Phase 5b: Integer+Integer inline fast path. Result type is Long
		// (matches the promoteNumber path below), because unsigned right shift
		// of a negative int does not fit in int.
		if(o1 instanceof Integer i1 && o2 instanceof Integer i2 && !isBoxedNumber(env, o1, o2)) {
			{
				int shift = i2.intValue() & 0x1F;
				return (long)(i1.intValue() >>> shift) & 0xFFFFFFFFL;
			}
		}
		Number n1 = toNumeric(env,o1);
		Number n2 = toNumeric(env,o2);
        switch(promoteNumber(env, numberType(n1), numberType(n2))) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
                int i1 = (int)toUInt32(n1);
                int i2 = (int)(toUInt32(n2) & 0x1F);
           		return (long)(i1 >>> i2) & 0xFFFFFFFFL;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
				throw RuntimeUtil.typeError("BigNumber have no unsigned right shift, use >> instead");
        	}
        	case NUMBER_NAN: {
        		if(numberType(n1)==NUMBER_NAN) {
        			return 0;
        		}
    			if(isBigNumber(n1)) {
    				return JsonUtil.toBigInteger(n1);
    			}
    			return toInt32(n1); 
        	}
        	case NUMBER_MIXED: {
				throw RuntimeUtil.typeError("Invalid operation between a number and a BigInt/BigDecimal");
        	}
        }
		throw binary(">>>", o1, o2);
	}

	
	/////////////////////////////////////////////////////////////////////////
	// Unary operations
	/////////////////////////////////////////////////////////////////////////

	//https://262.ecma-international.org/5.1/#sec-11.4.6
	public static Number plus(JSEnvironment env, Object o1) {
        Number n1 = toNumber(env,o1);
		return n1;
	}

	//https://262.ecma-international.org/5.1/#sec-11.4.7
	public static Number minus(JSEnvironment env, Object o1) {
        Number n1 = toNumeric(env,o1);
		return minus(n1);
	}
	public static Number minus(Number n1) {
		switch(numberType(n1)) {
        	case NUMBER_INTEGER: {
    			try {
    				int v = n1.intValue();
    				if(v==0) {
            			return -0.0;
    				}
    				return Math.negateExact(v);
    			} catch(ArithmeticException ex) {}
        		// overflow -> use long!
        	}
        	case NUMBER_LONG: {
    			try {
    				long v = n1.longValue();
    				if(v==0L) {
            			return -0.0;
    				}
    				return Math.negateExact(n1.longValue());
    			} catch(ArithmeticException ex) {}
        		// overflow -> use double!
        	}
        	case NUMBER_DOUBLE: {
        		return -TypeUtil.toDouble(n1);
        	}
        	case NUMBER_BIGINTEGER: {
        		// Unlike Number, there is only one BigInt zero: -0n is the same as 0n.
        		BigInteger v = TypeUtil.toBigInteger(n1);
        		return v.negate();
        	}
        	case NUMBER_BIGDECIMAL: {
        		BigDecimal v = TypeUtil.toBigDecimal(n1);
        		if(v.equals(BigDecimal.ZERO)) {
        			return -0.0;
        		}
        		return v.negate();
        	}
        	case NUMBER_NAN: {
        		return Double.NaN;
        	}
		}
		throw new IllegalStateException();
	}

	// https://262.ecma-international.org/5.1/#sec-11.4.9
	public static Object not(JSEnvironment env, Object o1) {
		return !toBoolean(env,o1);
	}

	// https://262.ecma-international.org/5.1/#sec-11.4.8
	public static Number bitNot(JSEnvironment env, Object o1) {
		Number n1 = toNumeric(env,o1);
        switch(numberType(n1)) {
        	case NUMBER_INTEGER:
        	case NUMBER_LONG:
        	case NUMBER_DOUBLE: {
                int v = toInt32(n1);
                return ~v;
        	}
        	case NUMBER_BIGINTEGER:
        	case NUMBER_BIGDECIMAL: {
        		return TypeUtil.toBigInteger(n1).not();
        	}
        	case NUMBER_NAN: {
        		// ToInt32(NaN) is 0, so ~NaN is ~0 == -1, not 0.
        		return ~toInt32(n1);
        	}
        }
		throw unary("~", n1);
	}

	public static String typeof(JSEnvironment env, Object r) {
        if(r==RuntimeUtil.UNDEFINED || r==RuntimeUtil.NOT_AVAILABLE) {
        	return "undefined";
        } else if(env.isHTMLDDAObject(r)) {
        	// Annex B.3.7 [[IsHTMLDDA]]: typeof reports "undefined" (unlike
        	// every other object), even though it's not the real undefined
        	// value (SameValue/=== still distinguish it - see eq()'s and
        	// Object.is's own separate handling).
        	return "undefined";
        } else if(r==null) {
        	return "object";
        } else if(r instanceof Boolean) {
        	if(isBoxedBoolean(env,r)) {
            	return "object";
        	}
        	return "boolean";
        } else if(r instanceof Number) {
        	if(isBoxedNumber(env,r)) {
            	return "object";
        	}
        	if(r instanceof BigInteger) {
            	return "bigint";
        	}
        	if(r instanceof BigDecimal) {
            	return "decimal";
        	}
        	return "number";
        } else if(r instanceof CharSequence || r instanceof Character) {
        	if(isBoxedString(env,r)) {
            	return "object";
        	}
        	return "string";
        } else if(r instanceof Callable) {
        	if(r instanceof BuiltinProxy p) {
        		if(!p.isTargetCallable()) {
                	return "object";
        		}
        	}
        	return "function";
        } else if(r instanceof Symbol) {
        	if(isBoxedSymbol(env,r)) {
            	return "object";
        	}
        	return "symbol";
        } else {
        	return "object";
        }
	}
	public static String typeof(JSEnvironment env, Object base, Object index) {
		return typeof(env,RuntimeUtil.getProperty(env, base, index));
	}
	
	public static boolean instanceOf(JSEnvironment env, Object leftValue, Object rightValue) {
		if(isNotNullOrUndefined(rightValue)) {
			// Check with [hasInstance]: per spec this is invoked regardless of whether
			// leftValue is primitive - that check only applies to the OrdinaryHasInstance
			// fallback below, not to a custom Symbol.hasInstance handler.
			Object hasInstance = env.getAccessor(rightValue).getProperty(rightValue, Symbol.HAS_INSTANCE, RuntimeUtil.UNDEFINED);
			if(isNotNullOrUndefined(hasInstance)) {
				// GetMethod: a defined-but-non-callable @@hasInstance is a TypeError.
				if(!(hasInstance instanceof Callable c)) {
					throw RuntimeUtil.typeError("Symbol.hasInstance is not callable");
				}
				Object result = c.call(rightValue, new Object[] {leftValue});
				return RuntimeUtil.toBoolean(env, result);
			}

			// No custom (or inherited) hasInstance handler: fall back to Java instanceof.
			Class<?> c = null;
			if(rightValue instanceof Class<?> jc) {
				c = jc;
			} else if(rightValue instanceof JavaClass jc) {
				c = jc.getNativeClass();
			}
			if(c!=null) {
				if(c.isPrimitive()) {
					// The actual value cannot be a primitive, so we convert the type here
					if(c==Character.TYPE) c = Character.class;
					else if(c==Byte.TYPE) c = Byte.class;
					else if(c==Short.TYPE) c = Short.class;
					else if(c==Integer.TYPE) c = Integer.class;
					else if(c==Long.TYPE) c = Long.class;
					else if(c==Float.TYPE) c = Float.class;
					else if(c==Double.TYPE) c = Double.class;
					else if(c==Boolean.TYPE) c = Boolean.class;
				}
				return c.isAssignableFrom(leftValue.getClass());
			}
		}

		throw RuntimeUtil.typeError("Right operand is not a callable of a Java Class but {0}",rightValue!=null?rightValue.getClass().getName():"<null>");
	}	
	
	
	// Remove these 2 methods...
	
	public static boolean in(JSEnvironment env, Object leftValue, Object rightValue) {
		// Per spec, "in"'s RHS must be an object - unlike a member access, primitives
		// are never implicitly boxed here, even though accessors otherwise allow
		// property lookups on them (e.g. true.toString()).
		if(!RuntimeUtil.isObject(env,rightValue)) {
			throw RuntimeUtil.typeError("Cannot use 'in' operator to search for '{0}' in {1}", leftValue, RuntimeUtil.objectTypeName(env,rightValue));
		}
		return RuntimeUtil.hasProperty(env,rightValue,leftValue);
	}
	
	public static boolean delete(JSEnvironment env, Object base, Object index) {
		return RuntimeUtil.deleteProperty(env, base, index);
	}


	/////////////////////////////////////////////////////////////////////////
	// Ternary operations
	/////////////////////////////////////////////////////////////////////////

	public static Object elvis(JSEnvironment env, Object leftValue, Supplier<Object> rightValue) {
		if(toBoolean(env,leftValue)) {
			return leftValue;
		}
		return rightValue.get();
	}	


	/////////////////////////////////////////////////////////////////////////
	// Comparators for collections
	/////////////////////////////////////////////////////////////////////////

	// Array sort compares... strings!
	public static Comparator<Object> comparatorStrings(JSEnvironment env, Callable function) {
		return new Comparator<Object>() {
	        Object[] cbArgs = new Object[2];
			@Override
			public int compare(Object o1, Object o2) {
				// Per spec §23.1.3.30: undefined always sorts after defined values
				boolean u1 = o1 == UNDEFINED;
				boolean u2 = o2 == UNDEFINED;
				if (u1 && u2) return 0;
				if (u1) return 1;
				if (u2) return -1;
	            if(function!=null){
	            	cbArgs[0]=o1;
	            	cbArgs[1]=o2;
	                // Spec: comparefn is called with this===undefined, not
	                // null (confirmed via Array's sort/S15.4.4.11_A8.js).
	                Object r = function.call(RuntimeUtil.UNDEFINED, cbArgs);
	                if(!(r instanceof Number)) {
	                	r = RuntimeUtil.toDouble(env,r);
	                }
	            	double d = ((Number)r).doubleValue();
	            	if(d<0.0) return -1;
	            	if(d>0.0) return 1;
	            	return 0;
	            } else {
	            	// Weird enough, the JS spec converts the args to strings
	            	// so "80" is before "9"
	            	String s1 = RuntimeUtil.toString(env,o1);
	            	String s2 = RuntimeUtil.toString(env,o2);
	            	return s1.compareTo(s2);
	            }
			}
		};
	}
	
	// Mostly for TypedArray 
	public static Comparator<Object> comparatorNumbers(JSEnvironment env, Callable function) {
	    return new Comparator<Object>() {
	        Object[] cbArgs = new Object[2];
			@Override
			public int compare(Object _o1, Object _o2) {
				Number o1 = (Number)_o1;
				Number o2 = (Number)_o2;
	            if(function!=null){
	            	cbArgs[0]=o1;
	            	cbArgs[1]=o2;
	                // Spec: comparefn is called with this===undefined, not
	                // null (confirmed via Array's sort/S15.4.4.11_A8.js).
	                Object r = function.call(RuntimeUtil.UNDEFINED, cbArgs);
	                if(!(r instanceof Number)) {
	                	r = RuntimeUtil.toDouble(env,r);
	                }
	            	double d = ((Number)r).doubleValue();
	            	if(d<0.0) return -1;
	            	if(d>0.0) return 1;
	            	return 0;
	            } else {
	            	// Spec's default TypedArray/Array sort comparator has its
	            	// OWN NaN handling, distinct from generic numeric
	            	// comparison (where a NaN operand conventionally compares
	            	// "equal" since `NaN<x`/`NaN>x` are both false): NaN must
	            	// sort strictly AFTER every non-NaN value, with two NaNs
	            	// considered equal to each other.
	            	boolean n1 = Double.isNaN(o1.doubleValue());
	            	boolean n2 = Double.isNaN(o2.doubleValue());
	            	if(n1 && n2) return 0;
	            	if(n1) return 1;
	            	if(n2) return -1;
	            	return RuntimeUtil.compareNumber(o1, o2);
	            }
			}
		};
	}

	
	
	/////////////////////////////////////////////////////////////////////////
	// Type helpers
	/////////////////////////////////////////////////////////////////////////

	public static boolean isNull(Object o) {
		return o==null;
	}
	public static boolean isUndefined(Object o) {
		return o==RuntimeUtil.UNDEFINED;
	}
	public static boolean isNullOrUndefined(Object o) {
		return o==null || o==RuntimeUtil.UNDEFINED;
	}
	public static boolean isNotNullOrUndefined(Object o) {
		return o!=null && o!=RuntimeUtil.UNDEFINED;
	}
	// RequireObjectCoercible: an ObjectBindingPattern/ObjectAssignmentPattern
	// must reject null/undefined even when empty ({}), since a property read
	// on either would throw anyway - see ASTObjectLiteral.assign() (the
	// interpreted-mode equivalent of this check).
	public static void requireObjectCoercible(Object value) {
		if(isNullOrUndefined(value)) {
			throw typeError("Cannot destructure '{0}' as it is {1}.", value, value==null?"null":"undefined");
		}
	}

	// A static class element (method, field, getter or setter) may not be
	// named "prototype" - checked in the interpreter at the single place all
	// static/computed/literal names get resolved (ASTClassMember.evaluateName);
	// this is the transpiler's equivalent runtime re-check for a COMPUTED
	// static name, which can only resolve to "prototype" at runtime (a
	// literal `static prototype() {}` is already rejected as an early
	// SyntaxError shared by both execution modes).
	public static Object checkStaticElementName(Object result) {
		if("prototype".equals(result)) {
			throw typeError("Classes may not have a static property named 'prototype'");
		}
		return result;
	}

	public enum JavascriptType {
		NULL,
		UNDEFINED,
		BOOLEAN,
		NUMBER,
		STRING,
		SYMBOL,
		OBJECT
	}
	public static JavascriptType jsType(JSEnvironment env, Object o) {
		if(o==null) {
			return JavascriptType.NULL;
		}
		if(o==UNDEFINED) {
			return JavascriptType.UNDEFINED;
		}
		if(o instanceof CharSequence || o instanceof Character) {
			if(isBoxedString(env,o)) {
				return JavascriptType.OBJECT;
			}
			return JavascriptType.STRING;
		}
		if(o instanceof Boolean) {
			if(isBoxedBoolean(env,o)) {
				return JavascriptType.OBJECT;
			}
			return JavascriptType.BOOLEAN;
		}
		if(o instanceof Number) {
			if(isBoxedNumber(env,o)) {
				return JavascriptType.OBJECT;
			}
			return JavascriptType.NUMBER;
		}
		if(o instanceof Symbol) {
			if(isBoxedSymbol(env,o)) {
				return JavascriptType.OBJECT;
			}
			return JavascriptType.SYMBOL;
		}
		return JavascriptType.OBJECT;
	}

	// https://262.ecma-international.org/5.1/#sec-9.3
	public static Number toNumber(Object v) {
		return toNumber(JSEnvironment.getEnvironment(),v);
	}
	public static Number toNumber(JSEnvironment env, Object v) {
		if(v instanceof Number n) {
			if(!env.supportMixedBigNumber() && (n instanceof BigInteger || n instanceof BigDecimal)) {
				throw RuntimeUtil.typeError("BigNumber cannot be used as a number");
			}
			return objectAsPrimitive(env,n);
		}
		if(v==null) {
			return 0;
		}
		if(v==UNDEFINED) {
			return Double.NaN;
		}
		if(v instanceof Boolean b) {
			if(isBoxedBoolean(env,v)) {
				return toNumber(env,toPrimitive(env,v,HINT.NUMBER));
			}
			return b.booleanValue() ? 1 : 0;
		}
		if(v instanceof CharSequence) {
			String s = v.toString();
			if(isBoxedString(env,v)) {
				return toNumber(env,toPrimitive(env,v,HINT.NUMBER));
			}
			try {
				s = trimWhiteSpaces(s);
				if(s.length()==0) {
					return 0;
				}
				int options = 0;
				return env.getJsonFactory().parseNumber(s,options);
			} catch(Exception ex) {
				return Double.NaN;
			}
		}
		if(v instanceof Symbol) {
			// A boxed Symbol goes through ToPrimitive like any other wrapper (an
			// overridden valueOf/toString returning a non-Symbol primitive must be
			// respected); only a raw Symbol primitive is rejected outright.
			if(isBoxedSymbol(env,v)) {
				return toNumber(env,toPrimitive(env,v,HINT.NUMBER));
			}
			throw RuntimeUtil.typeError("Cannot convert a Symbol value to a number");
		}
		return toNumber(env,toPrimitive(env,v,HINT.NUMBER));
	}
	public static Number toNumeric(JSEnvironment env, Object v) {
		if(v instanceof Number n) {
			return objectAsPrimitive(env,n);
		}
		if(v==null) {
			return 0;
		}
		if(v==UNDEFINED) {
			return Double.NaN;
		}
		if(v instanceof Boolean b) {
			if(isBoxedBoolean(env,v)) {
				return toNumberPreservingBigInt(env,toPrimitive(env,v,HINT.NUMBER));
			}
			return b.booleanValue() ? 1 : 0;
		}
		if(v instanceof CharSequence) {
			String s = v.toString();
			if(isBoxedString(env,v)) {
				return toNumberPreservingBigInt(env,toPrimitive(env,v,HINT.NUMBER));
			}
			try {
				s = trimWhiteSpaces(s);
				if(s.length()==0) {
					return 0;
				}
				int options = 0;
				return env.getJsonFactory().parseNumber(s,options);
			} catch(Exception ex) {
				return Double.NaN;
			}
		}
		if(v instanceof Symbol) {
			if(isBoxedSymbol(env,v)) {
				return toNumberPreservingBigInt(env,toPrimitive(env,v,HINT.NUMBER));
			}
			throw RuntimeUtil.typeError("Cannot convert a Symbol value to a number");
		}
		return toNumberPreservingBigInt(env,toPrimitive(env,v,HINT.NUMBER));
	}
	// ToNumeric's final step: unlike ToNumber, a BigInt primitive value is returned as-is
	// rather than rejected.
	private static Number toNumberPreservingBigInt(JSEnvironment env, Object prim) {
		if(prim instanceof BigInteger big) {
			return big;
		}
		return toNumber(env,prim);
	}

	// Spec's TypedArray element-assignment coercion: NOT ToNumeric (which
	// only converts a value that's ALREADY a BigInt and otherwise falls back
	// to ToNumber) - a BigInt-content typed array must coerce via ToBigInt
	// even for e.g. a numeric STRING element ("0" -> 0n), while a
	// Number-content typed array must reject an actual BigInt value outright
	// (ToNumber(BigInt) throws) rather than silently keeping it as one.
	public static Number toTypedArrayElement(JSEnvironment env, org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray ta, Object value) {
		if(ta.isBigIntTypedArray()) {
			Object prim = toPrimitive(env,value,HINT.NUMBER);
			if(prim instanceof Number n && env.supportMixedBigNumber()) {
				// GaltaJS mixed-BigNumber extension: a plain Number element
				// (as opposed to a numeric string) in a BigInt-content array
				// is accepted as-is rather than rejected by strict ToBigInt -
				// matches TypedArray.set(index,Number)'s existing leniency.
				return n;
			}
			return org.monflabs.galtajs.rt.builtins.standard.bigint.BuiltinBigIntConstructor.toBigInt(env,prim);
		}
		return toNumber(env,value);
	}

    public static boolean isWhiteSpace(char c) {
    	switch (c) {
    		case ' ': // <SP>
    		case '\n': // <LF>
    		case '\r': // <CR>
    		case '\t': // <TAB>
    		case '\u00A0': // <NBSP>
    		case '\u000C': // <FF>
    		case '\u000B': // <VT>
    		case '\u2028': // <LS>
    		case '\u2029': // <PS>
    		case '\uFEFF': // <BOM>
    			return true;
    		default:
    			return Character.getType(c) == Character.SPACE_SEPARATOR;
    	}
    }	
    public static String trimWhiteSpaces(String s) {
        int len = s.length();
        int start = 0;
        while ((start < len) && isWhiteSpace(s.charAt(start))) {
            start++;
        }
        int end = len;
        while ((start < end) && isWhiteSpace(s.charAt(end-1)) ) {
            end--;
        }
        return (start>0 || (end < len)) ? s.substring( start, end ) : s;
    }	
    public static final String trimLeadingWhiteSpaces(String s) {
        int len = s.length();
        int start = 0;
        while ((start < len) && isWhiteSpace(s.charAt(start))) {
            start++;
        }
        return start>0 ? s.substring( start, len ) : s;
    }
    public static String trimTrailingWhiteSpaces(String s) {
        int len = s.length();
        int end = len;
        while ((0 < end) && isWhiteSpace(s.charAt(end-1)) ) {
            end--;
        }
        return end<len ? s.substring( 0, end ) : s;
    }	    
    
	public static Number toDecimalNumber(JSEnvironment env, Object v) {
		if(v==null) {
			return 0.0;
		}
		if(v==UNDEFINED) {
			return Double.NaN;
		}
		if(v instanceof Number) {
			if(v instanceof Double n) {
				return n;
			} else if(v instanceof Float n) {
				return n.doubleValue();
			} else if(v instanceof BigDecimal n) {
				return n;
			} else if(v instanceof BigInteger n) {
				return toBigDecimal(env,n);
			} else {
				return ((Number)v).doubleValue();
			}
		}
		return toDecimalNumber(env,toNumber(env,v));	
	}
	// ToIntegerOrInfinity narrowed to a Java int: NaN -> 0, +/-Infinity (and
	// anything beyond the int range) CLAMP to Integer.MAX_VALUE/MIN_VALUE.
	// That is the right contract for every caller spec'd via
	// ToIntegerOrInfinity/ToIndex (String.prototype.at/slice/..., toFixed's
	// digits, ArrayBuffer lengths, ...), which all range-check or clamp the
	// result themselves. It is NOT ToInt32 - a spec algorithm that wraps
	// modulo 2^32 (ToInt32(Infinity) is 0, ToInt32(2^32+2) is 2) must use
	// toInt32() below instead, as Math.imul/Math.clz32 already do.
	public static int toInt(Object o) {
		return toInt(JSEnvironment.getEnvironment(),o);
	}
	public static int toInt(JSEnvironment env, Object o) {
		Number n = toNumber(env,o);
		if(n instanceof Double d) {
			if(d.isNaN()) {
				return 0;
			}
			if(d.isInfinite()) {
				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			}
			return d.intValue();
		}
		if(n instanceof Float f) {
			if(f.isNaN()) {
				return 0;
			}
			if(f.isInfinite()) {
				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			}
			return f.intValue();
		}
		return n.intValue();
	}
	public static long toLong(JSEnvironment env, Object o) {
		Number n = toNumber(env,o);
		if(n instanceof Double d) {
			if(d.isNaN()) {
				return 0;
			}
			if(d.isInfinite()) {
				return d.doubleValue()==Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			}
			return d.longValue();
		}
		if(n instanceof Float f) {
			if(f.isNaN()) {
				return 0;
			}
			if(f.isInfinite()) {
				return f.doubleValue()==Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			}
			return f.longValue();
		}
		return n.longValue();
	}
	public static double toDouble(JSEnvironment env, Object n) {
		return TypeUtil.toDouble(toNumber(env,n));
	}
	// ToLength(argument): ToIntegerOrInfinity then clamped to [0, 2^53-1] -
	// unlike a raw `(int)toDouble(...)` cast, this doesn't silently corrupt
	// values beyond Integer's range (needed e.g. for RegExp lastIndex, which
	// a poisoned valueOf() can set arbitrarily high).
	public static long toLength(JSEnvironment env, Object n) {
		double d = toDouble(env,n);
		if(Double.isNaN(d) || d<=0) {
			return 0L;
		}
		return (long)Math.min(d, MAX_PRECISE_DOUBLE-1);
	}
	public static BigInteger toBigInteger(JSEnvironment env, Object n) {
		return TypeUtil.toBigInteger(toNumber(env,n));
	}	
	public static BigDecimal toBigDecimal(JSEnvironment env, Object n) {
		return TypeUtil.toBigDecimal(toNumber(env,n));
	}	

	
    private static final long MAX_PRECISE_DOUBLE = 1L << 53;
    private static final long MIN_PRECISE_DOUBLE = -MAX_PRECISE_DOUBLE;
    private static final double INT32_LIMIT = 4294967296.0;    
    
	public static byte toInt8(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		if(n instanceof Double d) {
	        return toInt8(d.doubleValue());
		} else if(n instanceof Float f) {
	        return toInt8(f.doubleValue());
		}
		return n.byteValue();
	}
    public static byte toInt8(int i) {
        return (byte)(i & 0xFF);
    }	
    public static byte toInt8(long l) {
        return (byte)(l & 0xFFL);
    }	
    public static byte toInt8(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			return 0;
		}
        return (byte)(DoubleConversion.doubleToInt32(d) & 0xFF);
    }
    
	public static short toUInt8(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		if(n instanceof Double d) {
	        return toUInt8(d.doubleValue());
		} else if(n instanceof Float f) {
	        return toUInt8(f.doubleValue());
		}
		return (short)(n.shortValue() & 0xFF);
	}
    public static short toUInt8(int i) {
        return (short)(i & 0xFF);
    }	
    public static short toUInt8(long l) {
        return (short)(l & 0xFFL);
    }	
    public static short toUInt8(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			return 0;
		}
        return (short)(DoubleConversion.doubleToInt32(d) & 0xFF);
    }	
    
    
	public static short toInt16(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		if(n instanceof Double d) {
	        return toInt16(d.doubleValue());
		} else if(n instanceof Float f) {
	        return toInt16(f.doubleValue());
		}
		return n.shortValue();
	}
    public static short toInt16(int i) {
        return (short)(i & 0xFFFF);
    }	
    public static short toInt16(long l) {
        return (short)(l & 0x0000FFFFL);
    }	
    public static short toInt16(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			return 0;
		}
        return (short)(DoubleConversion.doubleToInt32(d) & 0xFFFF);
    }
    
	public static int toUInt16(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		if(n instanceof Double d) {
	        return toUInt16(d.doubleValue());
		} else if(n instanceof Float f) {
	        return toUInt16(f.doubleValue());
		}
		return n.intValue() & 0xFFFF;
	}
    public static int toUInt16(int i) {
        return i & 0xFFFF;
    }	
    public static int toUInt16(long l) {
        return (int)(l & 0xFFFFL);
    }	
    public static int toUInt16(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			return 0;
		}
        return (int)DoubleConversion.doubleToInt32(d) & 0xFFFF;
    }	


	// https://262.ecma-international.org/5.1/#sec-9.5
	public static int toInt32(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		return toInt32(n);
	}
	public static int toInt32(Number n) {
		if(n instanceof Double d) {
	        return toInt32(d.doubleValue());
		} else if(n instanceof Float f) {
	        return toInt32(f.doubleValue());
		}
		return n.intValue();
	}
	public static int toInt32(int i) {
        return i;
	}
	public static int toInt32(long l) {
		// If this right??
        return (int)(l >= MIN_PRECISE_DOUBLE && l <= MAX_PRECISE_DOUBLE ? l : (long)(l % INT32_LIMIT));
		//return (int)l;
	}
	public static int toInt32(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			return 0;
		}
		// This is needed as the java intValue() behaves differently
		return DoubleConversion.doubleToInt32(d);
	}
	
	public static long toUInt32(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		return toUInt32(n);
	}
	public static long toUInt32(Number n) {
		if(n instanceof Double d) {
	        return toUInt32(d.doubleValue());
		} else if(n instanceof Float f) {
	        return toUInt32(f.doubleValue());
		}
		return n.longValue() & 0xFFFF_FFFFL;
	}
    public static long toUInt32(int i) {
        return ((long)i) & 0xFFFF_FFFFL;
    }
    public static long toUInt32(long l) {
        return l & 0xFFFF_FFFFL;
    }

    // Spec's ToUint8Clamp (7.1.11): clamps against the real numeric value
    // BEFORE any narrowing, then rounds half-to-even - NOT a bitwise
    // truncation like the other TypedArray element conversions. Narrowing
    // via Number.shortValue()/intValue() first (as Uint8ClampedArray used
    // to) wraps an out-of-short-range value (e.g. 32768 -> -32768) before
    // the clamp ever sees it, producing a wrong result.
    public static int toUint8Clamp(Number n) {
        double d = n.doubleValue();
        if(Double.isNaN(d)) {
            return 0;
        }
        if(d<=0) {
            return 0;
        }
        if(d>=255) {
            return 255;
        }
        double f = Math.floor(d);
        if(f+0.5<d) {
            return (int)f+1;
        }
        if(d<f+0.5) {
            return (int)f;
        }
        int fi = (int)f;
        return (fi%2==0) ? fi : fi+1;
    }	
    public static long toUInt32(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			return 0;
		}
        return DoubleConversion.doubleToInt32(d) & 0xFFFF_FFFFL;
    }	

	public static long toInt64(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		if(n instanceof Double d) {
	        return (long)d.doubleValue();
		} else if(n instanceof Float f) {
	        return (long)f.doubleValue();
		}
		return n.longValue();
	}
	
	public static long toUInt64(JSEnvironment env, Object v) {
		Number n = toNumber(env,v);
		if(n instanceof Double d) {
	        return (long)d.doubleValue();
		} else if(n instanceof Float f) {
	        return (long)f.doubleValue();
		}
		return n.longValue();
	}
    
	public static boolean isBigNumber(Object n) {
		return n instanceof BigInteger || n instanceof BigDecimal;
	}

    private static final BigInteger TWO_64 = BigInteger.TWO.pow(64);

    public static BigInteger bigIntegerInt64(long value) {
        return BigInteger.valueOf(value);
    }
    public static BigInteger bigIntegerUint64(long value) {
        BigInteger n = BigInteger.valueOf(value);
        return n.signum() >= 0 ? n : n.add(TWO_64);
    }
    
    public static long int64BigInteger(BigInteger n) {
        return n.longValue();
    }
    public static long uint64BigInteger(BigInteger n) {
        return n.longValue();
    }
    
    
	public static boolean isIntegerNumber(Object o) {
		// Use switch with types, when available
		if(o instanceof Number) {
			if(o instanceof Integer || o instanceof Long  || o instanceof BigInteger || o instanceof Byte || o instanceof Short) {
				return true;
			}
			if(o instanceof Double d) {
				double dv = d.doubleValue();
				return Double.isFinite(dv) && !Double.isNaN(dv) && (Math.floor(dv)==dv);
			} else if(o instanceof Float f) {
				float fv = f.floatValue();
				return Float.isFinite(fv) && !Float.isNaN(fv) && (Math.floor(fv)==fv);
			} else if(o instanceof BigDecimal bd) {
				// https://stackoverflow.com/questions/1078953/check-if-bigdecimal-is-an-integer-in-java
				return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
			}
			return true;
		}
		return false;
	}
	public static boolean isNaN(Number o) {
		if(o instanceof Double d) {
			return d.isNaN();
		}
		if(o instanceof Float f) {
			return f.isNaN();
		}
		return false;
	}

	// https://262.ecma-international.org/5.1/#sec-9.8
	public static String toString(Object v) {
		return toString(JSEnvironment.getEnvironment(),v);
	}
	public static String toString(JSEnvironment env, Object v) {
		if(v==null) {
			return "null";
		}
		if(v==UNDEFINED) {
			return "undefined";
		}
		if(v instanceof CharSequence s) {
			if(isBoxedString(env,v)) {
				return toString(env,toPrimitive(env, v, HINT.STRING));
			}
			return s.toString();
		}
		if(v instanceof Boolean b) { 
			if(isBoxedBoolean(env,v)) {
				return toString(env,toPrimitive(env, v, HINT.STRING));
			}
			return b ? "true" : "false";
		}
		if(v instanceof Number n) {
			if(isBoxedNumber(env,v)) {
				return toString(env,toPrimitive(env, v, HINT.STRING));
			}
			return NumberFormatting.numberToString(n,10);
		}
		if(v instanceof Symbol) {
			// A boxed Symbol (typeof "object", via Object(sym)) goes through ToPrimitive
			// like any other wrapper - only a raw Symbol primitive is rejected outright.
			// Note: by default this still ends up throwing (Symbol.prototype[Symbol.toPrimitive]
			// always unwraps to the raw Symbol regardless of hint or a toString/valueOf
			// override, and ToString of a raw Symbol always throws per spec) - this guard
			// only changes behavior if Symbol.prototype[Symbol.toPrimitive] itself is
			// overridden to return something else.
			if(isBoxedSymbol(env,v)) {
				return toString(env,toPrimitive(env, v, HINT.STRING));
			}
			throw RuntimeUtil.typeError("Cannot convert a Symbol value to a string");
		}
		return toString(env,toPrimitive(env, v, HINT.STRING));
	}
	public static String primitiveToString(Object v) {
		if(v==null) {
			return "null";
		}
		if(v==UNDEFINED) {
			return "undefined";
		}
		if(v instanceof CharSequence s) {
			return s.toString();
		}
		if(v instanceof Boolean b) { 
			return b ? "true" : "false";
		}
		if(v instanceof Number n) {
			return NumberFormatting.numberToString(n,10);
		}
		if(v instanceof Symbol) {
			throw RuntimeUtil.typeError("Cannot convert a Symbol value to a string");
		}
		throw RuntimeUtil.typeError("Value is not a primitive");
	}
	public static String toLocaleString(JSEnvironment env, Object v) {
		if(v==null) {
			return "null";
		}
		if(v==UNDEFINED) {
			return "undefined";
		}
		//var toLocaleString = RuntimeUtil.getMember(env,v,"toLocaleString",RuntimeUtil.NOT_AVAILABLE);
		JSAccessor acc = env.getAccessor(v);
		var toLocaleString = acc.getProperty(v,"toLocaleString",RuntimeUtil.NOT_AVAILABLE);
		if(toLocaleString!=null) {
			if(toLocaleString instanceof Callable c) {
				Object res = c.call(v,RuntimeUtil.EMPTY_PARAMS);
				return toString(env,res);	
			} else {
				throw RuntimeUtil.typeError("toLocaleString() is not a method");
			}
		}
		return toString(env,v);	
	}

	public static String toString(JSEnvironment env, Object v, boolean nulls) {
		if(nulls && v==null) {
			return null;
		}
		return toString(env,v);	
	}

	// https://262.ecma-international.org/5.1/#sec-9.2
	public static boolean toBoolean(Object v) {
		return toBoolean(JSEnvironment.getEnvironment(),v);
	}
	public static boolean toBoolean(JSEnvironment env, Object v) {
		if(v==null) {
			return false;
		}
		if(v==UNDEFINED) {
			return false;
		}
		if(env.isHTMLDDAObject(v)) {
			// Annex B.3.7 [[IsHTMLDDA]]: ToBoolean is false, unlike every
			// other object (which is unconditionally true).
			return false;
		}
		if(v instanceof Boolean b) {
			// All Objects, including primitives, return true regardless of their value
			if(isBoxedBoolean(env,v)) {
				return true;
			}
			return b;
		}
		if(v instanceof Number n) {
			// All Objects, including primitives, return true regardless of their value
			if(isBoxedNumber(env,v)) {
				return true;
			}
			if(n instanceof Integer || n instanceof Short || n instanceof Byte) {
				return n.intValue()!=0;
			}
			if(n instanceof Long) {
				return n.longValue()!=0;
			}
			double d = n.doubleValue();
			return d!=0.0 && !Double.isNaN(d);
		}
		if(v instanceof CharSequence c) {
			// All Objects, including primitives, return true regardless of their value
			if(isBoxedString(env,v)) {
				return true;
			}
			return c.length()>0;
		}
		// All Objects, return true regardless of their value
		return true; // not null (include symbols)
	}
	
	
	// https://262.ecma-international.org/5.1/#sec-9.1
	public static Object toPrimitive(JSEnvironment env, Object v) {
		return toPrimitive(env,v,HINT.DEFAULT);
	}
	public static Object toPrimitive(JSEnvironment env, Object v, HINT hint) {
		if(v==null || v==UNDEFINED) {
			return v;
		}
		if(v instanceof CharSequence || v instanceof Number || v instanceof Boolean || v instanceof Symbol) {
			if(!isBoxedPrimitive(env, v)) {
				return v;
			}
		}
		return objectToPrimitive(env, v, hint);
	}

	public static Object objectToPrimitive(JSEnvironment env, Object v, HINT hint) {
		JSAccessor acc = env.getAccessor(v);
		Object toPrimitive = acc.getProperty(v, Symbol.TO_PRIMITIVE, RuntimeUtil.UNDEFINED);
		if(isNotNullOrUndefined(toPrimitive)) {
			if(toPrimitive instanceof Callable c) {
				Object res = c.call(v,hint.hintParams());
				if(isPrimitiveValue(env,res)) {
					return res;
				} else {
					throw RuntimeUtil.typeError("[toPrimitive] should return a primitive value");
				}
			} else {
				throw RuntimeUtil.typeError("toPrimitive() is not a method");
			}
		}
		return ordinaryToPrimitive(env, v, hint);
	}

	// OrdinaryToPrimitive(O, hint): tries valueOf/toString (or the reverse,
	// for HINT.STRING) directly, WITHOUT consulting Symbol.toPrimitive first -
	// for callers (e.g. Date.prototype[Symbol.toPrimitive] itself) that are
	// already inside a Symbol.toPrimitive implementation and would otherwise
	// recurse back into themselves.
	public static Object ordinaryToPrimitive(JSEnvironment env, Object v, HINT hint) {
		JSAccessor acc = env.getAccessor(v);
		if(hint==HINT.DEFAULT || hint==HINT.NUMBER) {
			var valueOf = acc.getProperty(v,"valueOf",RuntimeUtil.UNDEFINED);
			if(isNotNullOrUndefined(valueOf)) {
				if(valueOf instanceof Callable c) {
					Object res = c.call(v,RuntimeUtil.EMPTY_PARAMS);
					if(isPrimitiveValue(env,res)) {
						return res;
					}
				}
			}
			var toString = acc.getProperty(v,"toString",RuntimeUtil.UNDEFINED);
			if(isNotNullOrUndefined(toString)) {
				if(toString instanceof Callable c) {
					Object res = c.call(v,RuntimeUtil.EMPTY_PARAMS);
					if(isPrimitiveValue(env,res)) {
						return res;
					}
				}
			}
		} else {
			var toString = acc.getProperty(v,"toString",RuntimeUtil.UNDEFINED);
			if(isNotNullOrUndefined(toString)) {
				if(toString instanceof Callable c) {
					Object res = c.call(v,RuntimeUtil.EMPTY_PARAMS);
					// OrdinaryToPrimitive accepts any primitive result, not just a
					// string - the hint only controls try-order between toString and
					// valueOf, not the acceptable type of the result.
					if(isPrimitiveValue(env,res)) {
						return res;
					}
				}
			}
			var valueOf = acc.getProperty(v,"valueOf",RuntimeUtil.UNDEFINED);
			if(isNotNullOrUndefined(valueOf)) {
				if(valueOf instanceof Callable c) {
					Object res = c.call(v,RuntimeUtil.EMPTY_PARAMS);
					if(isPrimitiveValue(env,res)) {
						return res;
					}
				}
			}
		}
		throw RuntimeUtil.typeError("Cannot convert object {0} to a primitive", v.getClass());
	}
	
	public static boolean isPrimitiveType(Object v) {
		return v==null || v instanceof CharSequence || v instanceof Number || v instanceof Boolean || v instanceof Symbol || v==UNDEFINED;
	}
	public static boolean isPrimitiveValue(JSEnvironment env, Object v) {
		if(v==null ||  v==UNDEFINED) {
			return true;
		}
		if(v instanceof CharSequence || v instanceof Number || v instanceof Boolean || v instanceof Symbol) {
			// boxedMapFor() returns null for a ConsString (never boxable), so a
			// concatenation rope answers "primitive" without any map lookup.
			return !isBoxedPrimitive(env, v);
		}
		return false;
	}
	public static boolean isObject(JSEnvironment env, Object v) {
		return !isPrimitiveValue(env, v);
	}
	// CanBeHeldWeakly (used by WeakRef, WeakMap/WeakSet keys, and
	// FinalizationRegistry's target/unregisterToken): any ordinary object
	// (isObject), plus a non-registered (non-global) Symbol - a registered
	// Symbol (Symbol.for(...)) is excluded since it's interned for the
	// lifetime of the realm and can never actually become unreachable.
	public static boolean canBeHeldWeakly(JSEnvironment env, Object v) {
		if(v instanceof Symbol sy) {
			return !sy.isGlobal();
		}
		return isObject(env, v);
	}
	// Spec's IsArray (7.2.2): unlike a plain `instanceof JSArray` check, a
	// Proxy must be resolved to its ultimate target (a Proxy may wrap
	// another Proxy) - and accessing a revoked proxy's target is itself a
	// TypeError, not just "not an array". %Array.prototype% is also,
	// genuinely, an Array exotic object per spec 22.1.3 - it isn't backed by
	// real JSArray index/length storage in this engine (that's a separate,
	// larger gap - see exotic-array.js, still filtered), but IsArray itself
	// must recognize it regardless (confirmed via
	// isArray/15.4.3.2-0-5.js: `Array.isArray(Array.prototype)` must be true).
	public static boolean isArray(Object v) {
		while(v instanceof BuiltinProxy p) {
			if(p.isRevoked()) {
				throw RuntimeUtil.typeError("Cannot perform 'IsArray' on a proxy that has been revoked");
			}
			v = p.getTarget();
		}
		return v instanceof JSArray || v instanceof BuiltinArrayPrototype;
	}
	// -----------------------------------------------------------------------
	// Boxed-primitive tests
	//
	// The expando side tables are split by key type (see JSEnvironment's own
	// field comment). PREFER THE TYPED VARIANTS: when the caller already knows
	// the operand's type - which it almost always does, having just tested it
	// to pick a fast path - the check is one field read plus a null test, with
	// no dispatch at all, and it only consults the map for that type. Reach
	// for the generic isBoxedPrimitive()/boxedMapFor() only where the type is
	// genuinely unknown.
	// -----------------------------------------------------------------------

	public static boolean isBoxedNumber(JSEnvironment env, Object v) {
		PrimitivePropertyMap map = env.getNumberProperties();
		return map!=null && map.containsKey(v);
	}
	public static boolean isBoxedNumber(JSEnvironment env, Object v1, Object v2) {
		PrimitivePropertyMap map = env.getNumberProperties();
		return map!=null && (map.containsKey(v1) || map.containsKey(v2));
	}
	public static boolean isBoxedString(JSEnvironment env, Object v) {
		PrimitivePropertyMap map = env.getStringProperties();
		return map!=null && map.containsKey(v);
	}
	public static boolean isBoxedString(JSEnvironment env, Object v1, Object v2) {
		PrimitivePropertyMap map = env.getStringProperties();
		return map!=null && (map.containsKey(v1) || map.containsKey(v2));
	}
	public static boolean isBoxedBoolean(JSEnvironment env, Object v) {
		PrimitivePropertyMap map = env.getBooleanProperties();
		return map!=null && map.containsKey(v);
	}
	public static boolean isBoxedBoolean(JSEnvironment env, Object v1, Object v2) {
		PrimitivePropertyMap map = env.getBooleanProperties();
		return map!=null && (map.containsKey(v1) || map.containsKey(v2));
	}
	public static boolean isBoxedSymbol(JSEnvironment env, Object v) {
		PrimitivePropertyMap map = env.getSymbolProperties();
		return map!=null && map.containsKey(v);
	}

	// The side table `v` would live in if it were a boxed primitive, or null
	// when no such table exists yet OR `v` is of a kind that can never be one.
	// A ConsString deliberately falls through to null: primitiveAsObject()
	// always stores a real java.lang.String, so a concatenation rope can never
	// be a key and must not pay for a lookup (String concatenation being one
	// of the most common operations there is).
	public static PrimitivePropertyMap boxedMapFor(JSEnvironment env, Object v) {
		if(v instanceof String) {
			return env.getStringProperties();
		}
		if(v instanceof Number) {
			return env.getNumberProperties();
		}
		if(v instanceof Boolean) {
			return env.getBooleanProperties();
		}
		if(v instanceof Symbol) {
			return env.getSymbolProperties();
		}
		return null;
	}
	public static boolean isBoxedPrimitive(JSEnvironment env, Object v) {
		PrimitivePropertyMap map = boxedMapFor(env, v);
		return map!=null && map.containsKey(v);
	}
	public static boolean isBoxedPrimitive(JSEnvironment env, Object v1, Object v2) {
		return isBoxedPrimitive(env, v1) || isBoxedPrimitive(env, v2);
	}

	public static boolean hasPropertyMap(JSEnvironment env, Object v) {
		return isBoxedPrimitive(env, v);
	}
	public static JSObjectImpl getPrimitiveObject(JSEnvironment env, Object v) {
		PrimitivePropertyMap map = boxedMapFor(env, v);
		return map!=null ? map.get(v) : null;
	}

	// A plain `instanceof Integer` isn't enough to tell a genuine JS number
	// apart from a boxed `new Number(5)`/`Object(5)` wrapper - primitiveAsObject()
	// deliberately boxes via `new Integer(...)` (not Integer.valueOf()) for a
	// distinguishable identity, so `new Number(5) instanceof Integer` is also
	// true, yet it's a JS OBJECT (comparisons/arithmetic on it must go through
	// ToPrimitive - e.g. a user-overridden valueOf() - not shortcut straight to
	// the boxed int). hasPropertyMap() already short-circuits to false when no
	// primitive has ever been boxed anywhere in this environment (the
	// overwhelming common case), so this stays a single cheap null check then.
	public static boolean isInteger(JSEnvironment env, Object v) {
		return v instanceof Integer && !isBoxedNumber(env, v);
	}

	public static boolean isValidPrototype(JSEnvironment env, Object v) {
		if(isPrimitiveValue(env,v)) {
			return false;
		}
		return true;
	}
	
	
	public static Object objectAsPrimitive(JSEnvironment env, Object v) {
		if(v==null || v==UNDEFINED) {
			return v;
		}
		if(v instanceof CharSequence s) {
			return objectAsPrimitive(env,s);
		}
		if(v instanceof Number n) {
			return objectAsPrimitive(env,n);
		}
		if(v instanceof Boolean b) {
			return objectAsPrimitive(env,b);
		}
		if(v instanceof Symbol s) {
			return objectAsPrimitive(env,s);
		}
		throw new IllegalStateException("Value is not a primitive");
	}
	public static String objectAsPrimitive(JSEnvironment env, CharSequence v) {
		if(v instanceof String) { // Consstring cannot be an object
			PrimitivePropertyMap map = env.getStringProperties();
			if(map!=null) {
				if(map.containsKey(v)) {
					return (String)objectToPrimitive(env,v,HINT.STRING); 
//					// Make a physical copy of the string
//					return new String(v.toString());
				}
			}
		}
		return v.toString();
	}
	public static Number objectAsPrimitive(JSEnvironment env, Number v) {
		PrimitivePropertyMap map = env.getNumberProperties();
		if(map!=null) {
			if(map.containsKey(v)) {
				return (Number)objectToPrimitive(env,v,HINT.NUMBER); 
//				if(v instanceof Integer n) {
//					v = Integer.valueOf(n.intValue());
//				} else if(v instanceof Long n) {
//					v = Long.valueOf(n.longValue());
//				} else if(v instanceof Double n) {
//					v = Double.valueOf(n.doubleValue());
//				} else if(v instanceof BigInteger n) {
//					v = new BigInteger(n.toByteArray());
//				} else if(v instanceof BigDecimal n) {
//					v = new BigDecimal(n.unscaledValue(), n.scale(),env.getMathContext());
//				} else if(v instanceof Byte n) {
//					v = Byte.valueOf(n.byteValue());
//				} else if(v instanceof Short n) {
//					v = Short.valueOf(n.shortValue());
//				} else if(v instanceof Float n) {
//					v = Float.valueOf(n.floatValue());
//				}
			}
		}
		return v;
	}
	public static Boolean objectAsPrimitive(JSEnvironment env, Boolean v) {
		PrimitivePropertyMap map = env.getBooleanProperties();
		if(map!=null) {
			if(map.containsKey(v)) {
				return Boolean.valueOf(v);
			}
		}
		return v;
	}

	
	public static Object toObject(JSEnvironment env, Object v) {
		if(v==null || v==UNDEFINED) {
			throw typeError("Value can be converted to an object, {0}",objectTypeName(env, v));
		}
		if(!hasPropertyMap(env, v)) {
			if(v instanceof CharSequence s) {
				return primitiveAsObject(env,s);
			}
			if(v instanceof Number n) {
				return primitiveAsObject(env,n);
			}
			if(v instanceof Boolean b) {
				return primitiveAsObject(env,b);
			}
			if(v instanceof Symbol s) {
				return primitiveAsObject(env,s);
			}
		}
		return v;
	}
	
	public static Object primitiveAsObject(JSEnvironment env, Object v) {
		if(v==null || v==UNDEFINED) {
			return v;
		}
		if(v instanceof CharSequence s) {
			return primitiveAsObject(env,s);
		}
		if(v instanceof Number n) {
			return primitiveAsObject(env,n);
		}
		if(v instanceof Boolean b) {
			return primitiveAsObject(env,b);
		}
		if(v instanceof Symbol s) {
			return primitiveAsObject(env,s);
		}
		throw new IllegalStateException("Value is not a primitive");
	}
	public static String primitiveAsObject(JSEnvironment env, CharSequence v) {
		if(v!=null) {
			String stringObject = new String(v.toString());
			env.getStringProperties(true).create(stringObject,BuiltinStringPrototype.get(env));
			return stringObject;
		}
		return null;
	}
	@SuppressWarnings("removal")
	public static Number primitiveAsObject(JSEnvironment env, Number v) {
		if(v!=null) {
			Object prototype = BuiltinNumberPrototype.get(env);
			if(v instanceof Integer n) {
				v = new Integer(n.intValue());
			} else if(v instanceof Double n) {
				v = new Double(n.doubleValue());
			} else if(v instanceof Long n) {
				v = new Long(n.longValue());
			} else if(v instanceof BigInteger n) {
				// NOT n.add(BigInteger.ZERO) - the JDK short-circuits that to `return
				// this` unchanged when the addend is zero, so it would silently fail to
				// create the fresh, distinguishable identity boxing depends on (the
				// PrimitivePropertyMap is keyed by identity - reusing `n`'s identity would
				// make this box indistinguishable from whatever raw BigInteger it came from).
				v = new BigInteger(n.toByteArray());
				// A boxed BigInt must chain to BigInt.prototype, not Number.prototype -
				// otherwise its [[Prototype]] is wrong (e.g. instanceof/getPrototypeOf)
				// and any BigInt.prototype method/property override is invisible to it.
				prototype = BuiltinBigIntPrototype.get(env);
			} else if(v instanceof BigDecimal n) {
				v = new BigDecimal(n.unscaledValue(), n.scale(), env.getMathContext());
				prototype = BuiltinBigDecimalPrototype.get(env);
			} else if(v instanceof Byte n) {
				v = new Byte(n.byteValue());
			} else if(v instanceof Short n) {
				v = new Short(n.shortValue());
			} else if(v instanceof Float n) {
				v = new Float(n.floatValue());
			} else {
				throw new IllegalStateException("Unknown number class "+v.getClass());
			}
			env.getNumberProperties(true).create(v,prototype);
		}
		return v;
	}
	@SuppressWarnings("removal")
	public static Boolean primitiveAsObject(JSEnvironment env, Boolean v) {
		if(v!=null) {
			Boolean b = new Boolean(v);
			env.getBooleanProperties(true).create(b,BuiltinBooleanPrototype.get(env));
			return b;
		}
		return v;
	}
	public static Symbol primitiveAsObject(JSEnvironment env, Symbol v) {
		if(v!=null) {
			Symbol s = Symbol.wrap(v);
			env.getSymbolProperties(true).create(s,BuiltinSymbolPrototype.get(env));
			return s;
		}
		return v;
	}
	
	
	
	//
	// A "not callable" failure is always a spec TypeError (e.g. calling a
	// tagged-template tag, or any other transpiled call-site, when the
	// callee evaluates to a non-callable value) - these three previously
	// threw a generic RuntimeUtil.error(...), surfacing as plain Error
	// instead of TypeError. Confirmed via test262 language/expressions/
	// dynamic-import/syntax/valid/callexpression-templateliteral.js
	// (`import(...)\`\`` - a Promise used as a tag function).
	public static Callable toCallable(Object v) {
		if(v instanceof Callable callable) {
			return callable;
		}
		throw RuntimeUtil.typeError("Value is not a callable, {0}",v!=null?v.getClass():"<null>");
	}
	public static BaseCallableObject toBaseCallableObject(Object v) {
		if(v instanceof BaseCallableObject function) {
			return function;
		}
		throw RuntimeUtil.typeError("Value is not a function, {0}",v!=null?v.getClass():"<null>");
	}
	public static BuiltinFunction toFunction(Object v) {
		if(v instanceof BuiltinFunction function) {
			return function;
		}
		throw RuntimeUtil.typeError("Value is not a function, {0}",v!=null?v.getClass():"<null>");
	}
	
	public static Object executeIsolated(JSRuntimeContext context, Supplier<Object> supplier) {
		return supplier.get();
	}

	
	//
	// Access to super
	//
	
	public static Object getSuper(JSRuntimeContext context) {
		JSFunctionContext functionContext = context.getFunctionContext();
		// Arrow functions have no [[HomeObject]] of their own - super is lexically
		// inherited from the nearest enclosing non-arrow function, same as this.
		while(functionContext!=null && functionContext.getFunction().isArrow()) {
			JSRuntimeContext parent = functionContext.getParent();
			functionContext = parent!=null ? parent.getFunctionContext() : null;
		}
		if(functionContext!=null) {
			Object homeObject = functionContext.getFunction().getHomeObject();
			if(homeObject!=null) {
				JSAccessor acc = context.getEnvironment().getAccessor(homeObject);
				return acc.getPrototype(homeObject);
			}
		}
		throw RuntimeUtil.syntaxError("super() called outside of a member function");
	}

	// SuperProperty (spec 13.3.7.1 MakeSuperPropertyReference step 1-2):
	// GetThisBinding() - throwing ReferenceError if `this` is still TDZ (a
	// derived constructor's own super.prop/super[expr] read before its own
	// super() has run) - must happen BEFORE the super base (home object's
	// prototype) is even resolved, let alone a computed property key
	// evaluated. Transpiled-mode-only overload: the interpreter's own
	// ASTMember.readProperty()/getSingleValue() already call
	// context.getThis() (which itself performs this same check) directly,
	// so they keep using the unchecked getSuper(context) above.
	public static Object getSuper(JSRuntimeContext context, Object _this) {
		checkThisBinding(_this);
		return getSuper(context);
	}

	public static Object superCtor(JSRuntimeContext context, Object[] parameters) {
		JSFunctionContext functionContext = context.getFunctionContext();
		// Arrow functions have no [[Construct]]/class binding of their own - super()
		// is lexically inherited from the nearest enclosing non-arrow function.
		while(functionContext!=null && functionContext.getFunction().isArrow()) {
			JSRuntimeContext parent = functionContext.getParent();
			functionContext = parent!=null ? parent.getFunctionContext() : null;
		}
		if(functionContext==null) {
			throw RuntimeUtil.typeError("super() can only be used in a constructor method");
		}
		Constructor classConstructor = functionContext.getFunction().getClassConstructor();
		if(classConstructor==null) {
			throw RuntimeUtil.typeError("super() can only be used in a constructor method");
		}
		
		// We should have a reference to the constructor, including the members
		Constructor superConstructor = functionContext.getFunction().getSuperConstructor();
		if(superConstructor==null) {
			throw RuntimeUtil.typeError("Class does extend another class, so super() cannot be used");
		}

		Constructor newTarget = functionContext.getNewTarget();
		Object _this = superConstructor.constructObject(parameters, newTarget);
		// Bind `this` on the constructor's own context - InterpretedFunctionRuntimeContext
		// overrides setThis() to gate/mark it rather than delegate to its parent, so this
		// must be the (possibly walked-up, past arrows) constructor context, not the
		// original call-site context (which may be a nested arrow's own context).
		functionContext.setThis(_this);
		if(classConstructor instanceof BuiltinClassConstructor bcc) {
			BuiltinClassConstructor.Initializer initializer = bcc.getInitializer();
			if(initializer!=null) {
				initializer.initInstance(bcc,_this);
			}
		}
		return _this;
	}
    
	
	// ToIndex(value): ToNumber (which itself throws TypeError for a BigInt/
	// Symbol argument), then ToIntegerOrInfinity, then range-check against
	// [0, 2^53-1].
	/**
	 * A stop request or an interrupt must not be turned into a JavaScript
	 * value (a promise rejection, a suppressed error, an ignored close
	 * failure): code that catches everything calls this first.
	 */
	public static void rethrowIfUncatchable(Throwable t) {
		if(t instanceof JSRuntimeUncatchableException u) {
			throw u;
		}
	}

	/**
	 * CanonicalizeKeyedCollectionKey: Map and Set store a -0 key as +0.
	 */
	public static Object canonicalizeKeyedCollectionKey(Object key) {
		if(key instanceof Double d && isNegativeZero(d)) {
			return 0.0;
		}
		if(key instanceof Float f && isNegativeZero(f)) {
			return 0.0f;
		}
		return key;
	}

	public static long toIndex(JSEnvironment env, Object value) {
		Number n = RuntimeUtil.toNumber(env,value);
		double d = n.doubleValue();
		double integer = Double.isNaN(d) ? 0 : Math.signum(d)*Math.floor(Math.abs(d));
		if(integer<0 || integer>9007199254740991.0) { // 2^53-1
			throw RuntimeUtil.rangeError("Invalid index {0}",value);
		}
		return (long)integer;
	}

	public static Constructor speciesConstructor(JSEnvironment env, Object obj, Constructor defaultConstructor) {
        Object constructor = env.getAccessor(obj).getProperty(obj, "constructor", RuntimeUtil.NOT_AVAILABLE);
        if (constructor == RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.isUndefined(constructor)) {
            return defaultConstructor;
        }
        if (!RuntimeUtil.isObject(env,constructor)) {
            throw RuntimeUtil.typeError("constructor must be an object");
        }
        Object species = env.getAccessor(constructor).getProperty(constructor, Symbol.SPECIES, RuntimeUtil.NOT_AVAILABLE);
        if (species==RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.isNullOrUndefined(species)) {
            return defaultConstructor;
        }
        if (species instanceof Constructor ctor) {
        	return ctor;
		}
		throw RuntimeUtil.typeError("Object species is not a constructor");
    }

	// ArraySpeciesCreate(originalArray, length): if originalArray isn't a
	// genuine Array (e.g. Array.prototype.slice.call(arrayLikeObj)), species
	// is skipped entirely and a plain Array is created directly. Otherwise
	// resolves originalArray.constructor[Symbol.species] (defaulting to the
	// real %Array% constructor) and constructs via it, EXCEPT when that
	// resolves back to %Array% itself - spec takes a fast path
	// (ArrayCreate(length), no [[Construct]] call) in that case, which is
	// also what every call site here already did before species support was
	// added, so the common (no-species) case is unaffected.
	public static JSArray arraySpeciesCreate(JSEnvironment env, Object originalArray, long length) {
		// Spec step 1: "Let isArray be ? IsArray(originalArray)" - a
		// RECURSIVE check (through any number of nested Proxy wrappers,
		// throwing for a revoked one along the way), not a simple Java
		// `instanceof JSArray` - a Proxy directly wrapping (transitively)
		// a genuine Array must still consult @@species via the Proxy's
		// OWN "constructor" Get (which correctly triggers its traps,
		// unlike this check) - confirmed via
		// map/create-proxy.js and its concat/filter/slice/splice siblings,
		// which all expect species to be consulted through a
		// Proxy-wrapping-a-Proxy-wrapping-an-Array receiver.
		if(!RuntimeUtil.isArray(originalArray)) {
			return JSArray.createSparse(env, arrayCreateLength(length));
		}
		Constructor defaultConstructor = (Constructor)env.getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayConstructor.CLASSNAME);
		// Not the shared speciesConstructor(): spec step 5.b-c sits BETWEEN the
		// "constructor" Get and the @@species Get - a constructor from ANOTHER
		// realm that is that realm's own %Array% counts as "no species", and
		// its @@species must then never be read at all (test262
		// create-proto-from-ctor-realm-array.js counts that getter's calls).
		Object c = env.getAccessor(originalArray).getProperty(originalArray, "constructor", RuntimeUtil.NOT_AVAILABLE);
		if(c instanceof Constructor cc) {
			JSEnvironment realmC = getFunctionRealm(env, cc);
			if(realmC!=env && cc==realmC.getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayConstructor.CLASSNAME)) {
				c = RuntimeUtil.UNDEFINED;
			}
		}
		if(c!=RuntimeUtil.NOT_AVAILABLE && !RuntimeUtil.isUndefined(c)) {
			if(!RuntimeUtil.isObject(env,c)) {
				throw RuntimeUtil.typeError("constructor must be an object");
			}
			c = env.getAccessor(c).getProperty(c, Symbol.SPECIES, RuntimeUtil.NOT_AVAILABLE);
			if(c==RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.isNullOrUndefined(c)) {
				c = RuntimeUtil.UNDEFINED;
			} else if(!(c instanceof Constructor)) {
				throw RuntimeUtil.typeError("Object species is not a constructor");
			}
		}
		if(c==RuntimeUtil.NOT_AVAILABLE || c==RuntimeUtil.UNDEFINED || c==defaultConstructor) {
			return JSArray.createSparse(env, arrayCreateLength(length));
		}
		Object newObj = ((Constructor)c).constructObject(new Object[]{Long.valueOf(length)}, (Constructor)c);
		return getArrayLike(env, newObj);
	}
	// Spec 9.4.2.2 ArrayCreate(length): "If length > 2^32-1, throw a
	// RangeError exception" - only applies to the DEFAULT (genuine Array)
	// creation path above, not a custom species constructor (which is
	// invoked directly with no such cap). Missing this check let a huge
	// array-like `length` (clamped only at ToLength's 2^53-1 ceiling, e.g.
	// an object with `length: 2**32`) flow straight into a caller's O(len)
	// element-copy loop (confirmed via
	// slice/create-non-array-invalid-len.js: a REPRODUCED multi-billion-
	// iteration hang in Array.prototype.slice, only surfaced once
	// JSArrayJSObject.arrayLength() stopped throwing for huge lengths).
	private static long arrayCreateLength(long length) {
		if(length>4294967295L) {
			throw RuntimeUtil.rangeError("Invalid array length {0}",length);
		}
		return length;
	}

	
	//
	// Iterators
	// Rename these functions!
	//
	
	
	// Extension for for...of
	// TODO push the java specific entries to Java lib?
	public static Iterator<Object> valueIterator(JSEnvironment env, Object o) {
		Iterator<Object> it = valueIteratorUnchecked(env,o);
		if(it==null) {
			// for...of should fail when not an iterable
			throw RuntimeUtil.typeError("Object is not an iterable, {0}",objectTypeName(env,o));
		}
		return it;
	}
	public static Iterator<Object> valueIteratorUnchecked(JSEnvironment env, Object o) {
		if(o==null || o==RuntimeUtil.UNDEFINED) {
			return null;
		}
		JSAccessor acc = env.getAccessor(o);
		Object itFactory = acc.getProperty(o, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
		return valueIteratorUnchecked(env, o, itFactory);
	}

	// Overload for callers that already read @@iterator themselves to decide
	// which branch to take (e.g. Array.fromAsync's sync-vs-async-iterable
	// check) - avoids a second, separately-observable property read.
	// Confirmed needed via asyncitems-iterator-{exists,promise}.js, which
	// assert Symbol.iterator is read from `items` EXACTLY once.
	@SuppressWarnings("unchecked")
	public static Iterator<Object> valueIteratorUnchecked(JSEnvironment env, Object o, Object itFactory) {
		if(o==null || o==RuntimeUtil.UNDEFINED) {
			return null;
		}
		// GetMethod(o, @@iterator): an explicit null or undefined value (as
		// opposed to the property being absent entirely) must ALSO be
		// treated as "no iterator method" rather than throwing - confirmed
		// via iterator-is-null-as-array-like.js, which requires
		// `obj[Symbol.iterator] = null` to fall through to the array-like
		// path below, not throw.
		if(itFactory!=RuntimeUtil.NOT_AVAILABLE && itFactory!=null && itFactory!=RuntimeUtil.UNDEFINED) {
			if(itFactory instanceof Callable cl) {
				Object i = cl.call(o,RuntimeUtil.EMPTY_PARAMS);
				if(i instanceof BuiltinIterator it) {
					// Fast path only applies while "next" is still this
					// iterator's own built-in implementation - if user code
					// has monkey-patched it (directly, or via its shared
					// prototype, e.g. %ArrayIteratorPrototype%.next = ...;
					// confirmed via
					// iterated-array-with-modified-array-iterator.js),
					// calling Java's Iterator.next() directly on `it` would
					// silently bypass that override, so fall back to
					// driving it through the real JS-level "next" property
					// instead.
					Object nextMethod = env.getAccessor(it).getProperty(it, "next", RuntimeUtil.NOT_AVAILABLE);
					if(org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIteratorHelperPrototype.isDefaultNextMethod(nextMethod)
							|| org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGeneratorPrototype.isDefaultNextMethod(nextMethod)) {
						return it;
					}
					return new BuiltinIteratorHelper(env,new JavaIterator(env,it));
				}
				// Should have a next() method. Deliberately RuntimeUtil.isObject
				// (spec's plain "is this an Object" check), not instanceof
				// JSObjectInternal specifically - a Proxy (BuiltinProxy) is a
				// perfectly valid iterator-protocol receiver too (confirmed via
				// a plain `for-of` over `new Proxy({[Symbol.iterator](){return
				// this;}, next(){...}}, {})`, previously misfiring straight to
				// the "not an iterator" throw below since BuiltinProxy
				// implements neither BuiltinIterator nor JSObjectInternal).
				if(RuntimeUtil.isObject(env,i)) {
					return new BuiltinIteratorHelper(env,new JavaIterator(env,i));
				}
			}
			throw RuntimeUtil.typeError("[Symbol.iterator] is not an iterator");
		}
		
		if(o instanceof JSObject) { // Ignore built-in Object or Array as iterable/iterator
			return null;
		}

		// TODO: Can this be moved to the Java accessor as Symbol.ITERATOR.
		// This would prevent the JSContainer check below
		if(o instanceof Iterator<?> i) {
			return (Iterator<Object>)i;
		} else if(o instanceof Iterable<?> i) {
			return (Iterator<Object>)i.iterator();
		} else if(o instanceof Enumeration<?> e) {
			return (Iterator<Object>)Iterators.enumeration(e);
		} else if(o instanceof Map<?,?> m) {
			// is that needed?
			return (Iterator<Object>) m.values().iterator();
		}

		return null;
	}

	// GetIterator(obj, async) (7.4.11) for `for await (... of obj)`: prefers
	// obj's own [Symbol.asyncIterator], falling back to wrapping its SYNC
	// [Symbol.iterator] (via valueIterator() - reusing that for the "is this
	// even an iterable" validation/Java-interop fallbacks, exactly like
	// plain for-of) per-value through Await, matching
	// %AsyncFromSyncIteratorPrototype% (25.1.4.4) - see AsyncJavaIterator/
	// AsyncFromSyncJavaIterator's own docs. `awaitInGenerator` mirrors
	// ASTAwait's own choice between RuntimeUtil.await_/awaitInGenerator_:
	// true only when the immediately-enclosing function is an async
	// generator (`async function*`), to avoid the same deadlock a raw
	// blocking await_() would cause there.
	public static Iterator<Object> valueIteratorAsync(JSRuntimeContext context, Object o, boolean awaitInGenerator) {
		JSEnvironment env = context.getEnvironment();
		if(o==null || o==RuntimeUtil.UNDEFINED) {
			throw RuntimeUtil.typeError("Object is not an iterable, {0}", objectTypeName(env,o));
		}
		JSAccessor acc = env.getAccessor(o);
		Object asyncItFactory = acc.getProperty(o, Symbol.ASYNC_ITERATOR, RuntimeUtil.NOT_AVAILABLE);
		if(asyncItFactory!=RuntimeUtil.NOT_AVAILABLE && !RuntimeUtil.isNullOrUndefined(asyncItFactory)) {
			if(!(asyncItFactory instanceof Callable cl)) {
				throw RuntimeUtil.typeError("[Symbol.asyncIterator] is not a function");
			}
			Object asyncIterator = cl.call(o, RuntimeUtil.EMPTY_PARAMS);
			if(RuntimeUtil.isPrimitiveType(asyncIterator)) {
				throw RuntimeUtil.typeError("Result of the [Symbol.asyncIterator]() call is not an object");
			}
			return new AsyncJavaIterator(context, asyncIterator, awaitInGenerator);
		}
		Iterator<Object> sync = valueIterator(env, o);
		return new AsyncFromSyncJavaIterator(context, sync, awaitInGenerator);
	}

//	@SuppressWarnings("unchecked")
//	public static Iterator<Object> valueIterable(JSEnvironment env, Object o) {
//		Iterator<Object> it = valueIteratorUnchecked(env,o);
//		if(it==null) {
//			// for...of should fail when not an iterable
//			throw RuntimeUtil.typeError("Object is not an iterable, {0}",objectTypeName(env,o));
//		}
//		return it;
//	}
//	@SuppressWarnings("unchecked")
//	public static Iterator<Object> valueIterableUnchecked(JSEnvironment env, Object o) {
//		if(o==null || o==RuntimeUtil.UNDEFINED) {
//			return null;
//		}
//		
//		ObjectAccessor acc = env.getAccessor(o);
//		Object itFactory = acc.getMember(o, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
//		if(itFactory!=RuntimeUtil.NOT_AVAILABLE) {
//			if(itFactory instanceof Callable cl) {
//				Object i = cl.call(o,RuntimeUtil.EMPTY_PARAMS);
//				if(i instanceof BuiltinIterator it) {
//					return it;
//				}
//				// Should have a next() method
//				if(i instanceof JSObject jso) {
//					return new BuiltinIteratorHelper(env,new JavaIterator(env,jso));
//				}
//			}
//			throw RuntimeUtil.typeError("[Symbol.iterator] is not an iterator");
//		}
//		
//		if(o instanceof JSContainer) { // Ignore Object or Array as iterable/iterator
//			return null;
//		}
//
//		// TODO: Can this be moved to the Java accessor as Symbol.ITERATOR.
//		// This would prevent the JSContainer check below
//		if(o instanceof Iterable<?> i) {
//			return (Iterator<Object>) i.iterator();
//		}
//
//		return null;
//	}
	
	
	// Key with inherited enumerable properties
	// Used by for...in, includes the inherited properties from the proto
	public static Iterator<String> keyIterator(JSEnvironment env, Object o) {
		if(o==null || o==RuntimeUtil.UNDEFINED) {
			return Iterators.empty();
		}
		// EnumerateObjectProperties: a property already seen at a shallower
		// level of the prototype chain shadows a same-named property further
		// up, REGARDLESS of the shallower one's own [[Enumerable]] value -
		// a non-enumerable own property still blocks an enumerable same-
		// named property on the prototype from being visited (confirmed via
		// language/statements/for-in/order-enumerable-shadowed.js and
		// 12.6.4-2.js). So every own key at each level must be tracked for
		// shadowing purposes, even though only enumerable ones are yielded.
		// Queried via TWO separate ownStringEntries() calls (all keys, and
		// enumerable-only keys) rather than one enumerableOnly=false call
		// plus a per-key getOwnPropertyDescriptor() check - some JSAccessor
		// implementations (e.g. JavaArrayAccessor) only special-case
		// getOwnPropertyDescriptor for a couple of well-known members and
		// return null for everything else (e.g. element indices), relying
		// on ownStringEntries' own enumerableOnly filtering to already be
		// authoritative - a null descriptor there does NOT mean "absent".
		Set<String> seen = new LinkedHashSet<>();
		List<String> names = new ArrayList<>();
		for(Object p=o; p!=null;) {
			JSAccessor a = env.getAccessor(p);
			Set<String> enumerableHere = new LinkedHashSet<>();
			for(var it = a.ownStringEntries(p,true); it.hasNext(); ) {
				enumerableHere.add(it.next().getKey());
			}
			for(var it = a.ownStringEntries(p,false); it.hasNext(); ) {
				String k=it.next().getKey();
				if(seen.add(k) && enumerableHere.contains(k)) {
					names.add(k);
				}
			}
			p=a.getPrototype(p);
		}
		return names.iterator();
	}

	// Single-key counterpart to keyIterator() above, for re-checking one
	// already-collected name against the CURRENT (possibly since-mutated)
	// state of `o`'s own-property/prototype chain - used by for-in to
	// respect a deletion that happens mid-enumeration (see ASTForIn's own
	// comment). Mirrors keyIterator()'s exact shadowing rule: an own
	// property at a shallower level blocks a same-named property further
	// up regardless of the shallower one's own enumerability, so the first
	// level (own or inherited) that has ANY property (enumerable or not)
	// under this name is authoritative - key is valid only if enumerable
	// AT THAT level.
	public static boolean isStillEnumerableProperty(JSEnvironment env, Object o, String key) {
		for(Object p=o; p!=null;) {
			JSAccessor a = env.getAccessor(p);
			boolean foundOwn = false;
			for(var it = a.ownStringEntries(p,false); it.hasNext(); ) {
				if(it.next().getKey().equals(key)) {
					foundOwn = true;
					break;
				}
			}
			if(foundOwn) {
				for(var it = a.ownStringEntries(p,true); it.hasNext(); ) {
					if(it.next().getKey().equals(key)) {
						return true;
					}
				}
				return false;
			}
			p=a.getPrototype(p);
		}
		return false;
	}

	//
	// for spread initializer
	public static Iterator<Map.Entry<Object,Object>> keyValueIterator(JSEnvironment env, Object o) {
		if(isNullOrUndefined(o)) {
			return null;
		}
		return env.getAccessor(o).ownEntries(o,true);
	}
	
	
	//
	// String functions
	//
	
	public static String stringConcat(JSEnvironment env, Object...params) {
		StringBuilder b = new StringBuilder(64);
		for(int i=0; i<params.length; i++) {
			String s = toString(env,params[i]);
			b.append(s);
		}
		return b.toString();
	}
	
	
	//
	// Class functions
	//
	// Sentinel passed by callers (ASTClassDecl et al.) when the class has NO
	// ClassHeritage ("extends") clause at all - distinct from a real Java
	// `null`, which means the ClassHeritage expression WAS present and
	// evaluated to the JS value null (`class C extends null {}`). Per spec
	// (ClassDefinitionEvaluation), the two cases must NOT collapse together:
	// no heritage leaves protoParent at %Object.prototype% (ClassPrototype's
	// own default below, untouched), while `extends null` must explicitly
	// set protoParent to null - confirmed via test262 super/prop-*-cls-null-
	// proto.js, where `super.x` inside a method of `class C extends null {}`
	// must throw TypeError (RequireObjectCoercible on a null super base) but
	// previously never did, since both cases were indistinguishable once
	// passed in as Java null.
	public static final Object NO_SUPERCLASS = new Object();
	public static BuiltinClassConstructor createClass(JSEnvironment env, String className, Object _superClass, BuiltinClassConstructor.Initializer initializer) {
		BaseInternalObject prototype = new ClassPrototype(env,className);

		Constructor superClass=null;
		if(_superClass==null) {
			// ClassHeritage present, evaluated to JS null (`extends null`):
			// protoParent is explicitly null (spec step 12.b), not
			// ClassPrototype's %Object.prototype% default.
			prototype.setPrototype(null);
		} else if(_superClass!=NO_SUPERCLASS) {
			// A Java-level `instanceof Constructor` tag alone isn't enough -
			// BuiltinFunction implements the interface unconditionally (for
			// dispatch convenience) even for an async/generator/arrow
			// function, none of which are spec-constructible - must also
			// consult the real, per-instance isConstructor() (same
			// distinction already made for Reflect.construct's target/
			// newTarget validation - see Constructor.isConstructor()'s own
			// doc). Confirmed via language/statements/class/subclass/
			// superclass-{async,async-generator,generator}-function.js:
			// `class A extends (async function(){}) {}` must throw
			// TypeError.
			if(!(_superClass instanceof Constructor c) || !c.isConstructor()) {
				throw RuntimeUtil.typeError("Class must extend a Constructor, like a Class or a Function");
			}
			superClass = (Constructor)_superClass;
			// Real Get(superClass, "prototype") - UNDEFINED (not Java null) means
			// genuinely not found anywhere in the chain, matching spec's [[Get]];
			// a bound function (which never has an own "prototype") or a
			// getter-only-via-setter accessor both resolve to UNDEFINED this way,
			// not to JS null, so they correctly fall into the TypeError branch
			// below rather than being silently treated as "no prototype to set".
			Object superPrototype = ((JSObject)superClass).getProperty(Constructor.PROTOTYPE);
			if(superPrototype!=null) {
				// ClassDefinitionEvaluation: "If protoParent is not an Object and
				// protoParent is not null, throw a TypeError exception." JS null
				// is raw Java null here (handled by the surrounding if), so
				// anything reaching this branch that isn't a real object
				// (including UNDEFINED) must throw rather than silently being
				// skipped.
				if(!(superPrototype instanceof JSObject)) {
					throw RuntimeUtil.typeError("Class extends value does not have valid prototype property " + RuntimeUtil.toString(env, superPrototype));
				}
				prototype.setPrototype(superPrototype);
			}
		}

		// Then construct the class object
		// [[ConstructorKind]] is "derived" whenever a ClassHeritage clause is
		// textually present, even for `extends null` (_superClass==null) -
		// distinct from `superClass!=null` above (the actual superclass
		// Constructor OBJECT, which stays Java null for "no heritage" too).
		// See BuiltinClassConstructor's `derived` field doc.
		boolean hasHeritage = _superClass!=NO_SUPERCLASS;
		BuiltinClassConstructor clazz = new BuiltinClassConstructor(env,className, prototype, superClass, hasHeritage, initializer);
		return clazz;
	}

	
	
	//
	// Module functions
	//
	
	public static JSModule importModule(JSRuntimeContext context, String moduleName) {
		JSUnitContext mainContext = context.getMainContext();
		JSGlobalContext gc = context.getGlobalContext();
		// Suppress draining for the duration of loading/evaluating just THIS
		// ONE dependency - a static import reached synchronously, outside
		// any coroutine's own drain loop (e.g. a transpiled module's own
		// generated import sequence, run directly inside execute()'s non-
		// async branch), can synchronously start a dependency's own async
		// execute(...,true) call (an ordinary static import of a module with
		// top-level await). Without this, that nested call sees
		// draining==false and drains the WHOLE microtask queue to
		// completion right there - running the dependency's ENTIRE async
		// body before control ever returns here to import the NEXT sibling
		// (test262 top-level-await/async-module-does-not-block-sibling-
		// modules.js). Mirrors JSInterpretedUnit.executeWithContext()'s
		// identical runWithDrainSuppressed(() -> linkModule(context)) wrap,
		// at the granularity of one dependency instead of the whole link
		// phase. Paired with ASTProgram's own `_pendingModuleDeps`
		// mechanism (see its own doc comment) - THAT is what keeps this
		// draining suppression from regressing a module whose own later
		// code genuinely needs a dependency's value to be ready (test262
		// top-level-await/module-import-resolution.js,
		// module-sync-import-async-resolution-ticks.js, dfs-invariant.js,
		// pending-async-dep-from-cycle.js) - see KnownGaps.md's own account
		// of why an earlier, standalone attempt at just this one wrap was
		// reverted.
		return gc.getExecutor().runWithDrainSuppressed(() -> gc.importModule(mainContext,moduleName));
	}

	// Import/export attributes (`with { ... }`) - see JSGlobalContext.
	// importAttributedModule()'s own doc comment for the general contract.
	public static JSModule importAttributedModule(JSRuntimeContext context, String moduleName, java.util.Map<String,String> attributes) {
		JSUnitContext mainContext = context.getMainContext();
		return context.getGlobalContext().importAttributedModule(mainContext, moduleName, attributes);
	}

	// `import defer * as ns from '...'` - see JSGlobalContext.
	// importDeferredNamespace()'s own doc comment for the general contract.
	public static JSObject importDeferredNamespace(JSRuntimeContext context, String moduleName, java.util.Map<String,String> attributes) {
		JSUnitContext mainContext = context.getMainContext();
		return context.getGlobalContext().importDeferredNamespace(mainContext, moduleName, attributes);
	}

	// Static `import defer * as ns from '...'` (and its attributed form) -
	// the SYNCHRONOUS gather-then-link sequence a ROOT program's own static
	// import-defer statement needs at module-linking time (unlike
	// dynamicImportDefer() above, which wraps the same underlying pieces in
	// a queued microtask/Promise for the dynamic `import.defer(...)` form).
	// Shared by ASTImport's interpreted (hoistBindings()) and transpiled
	// (transpileJavaStatement()) codegen so this non-trivial gather+loop
	// sequence lives in exactly one place. See JSGlobalContext.
	// gatherAsynchronousTransitiveDependencies()'s own doc comment for why a
	// deferred target's (transitively) async dependencies must be started
	// eagerly even though the target itself stays lazy.
	public static JSObject importDeferredNamespaceSync(JSRuntimeContext context, String moduleName, java.util.Map<String,String> attributes) {
		JSGlobalContext gc = context.getGlobalContext();
		JSUnitContext mainContext = context.getMainContext();
		// A `with { type: ... }` import resolves to a SYNTHETIC module record
		// (JSON/text/bytes) - fully realized the instant it is constructed,
		// with no requested modules of its own and no asynchronous evaluation,
		// so there is nothing to gather. The walk must be skipped rather than
		// merely returning nothing: gatherAsynchronousTransitiveDependencies()
		// inspects an uncached dependency by parsing its raw content as
		// JavaScript (createScript(..., SCRIPT_MODULE)), which is simply wrong
		// for a JSON/text/bytes payload - the same concern ASTProgram's
		// module-request bookkeeping already documents for the non-deferred
		// path. test262 import-defer/deferred-namespace-object/json-module.js
		// only ever passed because a JSON object body happens to be a sole
		// top-level object literal, which GaltaJS used to parse as an object
		// expression; that masked the bad parse rather than avoiding it.
		if(attributes==null || attributes.get("type")==null) {
			gatherAndStartAsyncDependencies(context, moduleName);
		}
		return gc.importDeferredNamespace(mainContext, moduleName, attributes);
	}

	// Factored out of importDeferredNamespaceSync() above so a caller that
	// needs the gathered list itself (ASTImport's transpiled-root codegen,
	// to track any still-EVALUATING entry into its own `_pendingModuleDeps`
	// - see that codegen's own doc comment) doesn't have to duplicate the
	// gather+start sequence. Wraps the start loop in
	// runWithDrainSuppressed() for the same reason importModule() above
	// does: a gathered dependency's own execute(supplier,true) call,
	// reached synchronously here with draining==false, would otherwise
	// eagerly drain that ONE dependency to full completion before this
	// loop can even start the NEXT gathered dependency - collapsing what
	// should be several independently-suspended async modules back into
	// one full completion at a time (test262 top-level-await/import-defer/
	// evaluation-top-level-await/flattening-order/main.js). A no-op when
	// already draining (e.g. interpreted mode's linkModule(), already
	// wrapped by executeWithContext()'s own runWithDrainSuppressed()).
	public static java.util.List<org.monflabs.galtajs.modules.JSInterpretedUnit> gatherAndStartAsyncDependencies(JSRuntimeContext context, String moduleName) {
		JSGlobalContext gc = context.getGlobalContext();
		JSUnitContext mainContext = context.getMainContext();
		List<org.monflabs.galtajs.modules.JSInterpretedUnit> asyncDeps =
				gc.gatherAsynchronousTransitiveDependencies(mainContext, moduleName);
		gc.getExecutor().runWithDrainSuppressed(() -> {
			for(org.monflabs.galtajs.modules.JSInterpretedUnit dep: asyncDeps) {
				gc.startAsyncDependencyEvaluation(dep, gc);
			}
			return null;
		});
		return asyncDeps;
	}

	// spec 1.4 ParseJSONModule / import-bytes' CreateBytesModule / GaltaJS's
	// own "text" module extension - converts a module's raw content into
	// its (single) default export value, based on the "type" import
	// attribute. "json" mirrors JSON.parse with NO reviver (spec: "Let json
	// be ? Call(%JSON.parse%, undefined, « source »)."); "text" (not a TC39
	// proposal - a test262 host-defined extension some engines opt into,
	// exercised by its own `text-*.js` test file family) is the raw source
	// text itself, unparsed; "bytes" (import-bytes proposal) is a Uint8Array
	// over an immutable ArrayBuffer holding the module's raw bytes
	// (JSModuleDescriptor.getBytes() - the file as-is for a file-backed
	// module, so a binary file works). Any other "type" value is
	// unsupported - GaltaJS's own HostGetSupportedImportAttributes is
	// exactly {"type": {"json", "text", "bytes"}} - and throws a TypeError,
	// matching AllImportAttributesSupported.
	public static Object parseAttributedModuleContent(JSEnvironment env, String type, org.monflabs.galtajs.JSModuleDescriptor descriptor, String moduleName) {
		switch(type) {
			case "json": {
				org.monflabs.json.parser.JsonParser.StringParser p = new org.monflabs.json.parser.JsonParser.StringParser(env.getJsonFactory());
				p.setStrict(true);
				try {
					return p.parse(descriptor.getScript());
				} catch(Exception ex) {
					rethrowIfUncatchable(ex);
					if(ex instanceof JSRuntimeException) {
						throw (JSRuntimeException)ex;
					}
					throw RuntimeUtil.syntaxError("Error while parsing JSON module {0}", moduleName);
				}
			}
			case "text": {
				return descriptor.getScript();
			}
			case "bytes": {
				byte[] bytes = descriptor.getBytes();
				org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer buffer =
						org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer.immutableOf(bytes);
				return new org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint8.Uint8Array(env, buffer, 0, bytes.length);
			}
			default: {
				throw RuntimeUtil.typeError("Unsupported import attribute type {0}", type);
			}
		}
	}

	// The resolver walk importModule()/importAttributedModule() perform
	// inline, for callers that only need the DESCRIPTOR (never loading the
	// module): the first resolver that knows `resolvedName`, or null.
	public static org.monflabs.galtajs.JSModuleDescriptor findModuleDescriptor(JSEnvironment env, String resolvedName) {
		List<org.monflabs.galtajs.modules.JSModuleResolver> moduleResolvers = env.getModuleResolvers();
		if(moduleResolvers!=null) {
			for(org.monflabs.galtajs.modules.JSModuleResolver moduleResolver: moduleResolvers) {
				org.monflabs.galtajs.JSModuleDescriptor descriptor = moduleResolver.getModule(resolvedName);
				if(descriptor!=null) {
					return descriptor;
				}
			}
		}
		return null;
	}

	// Static `import source x from '...'` (ASTImport.hoistBindings()) and
	// the re-export resolution of such a binding (JSInterpretedUnit/
	// JSTranspiledUnit.resolveExport()) - see JSGlobalContext.
	// importModuleSource()'s own doc comment.
	public static org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource importModuleSource(JSRuntimeContext context, String moduleName) {
		return context.getGlobalContext().importModuleSource(context.getMainContext(), moduleName);
	}

	// Dynamic `import.source(specifier)` - resolves to the Module Source
	// Object, or rejects (a SyntaxError for a source text module, the
	// "Cannot find module" TypeError for an unresolvable specifier). Same
	// microtask shape as dynamicImportDefer() below; nothing is evaluated.
	public static Object dynamicImportSource(JSRuntimeContext context, String specifier) {
		org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
				new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
		context.getGlobalContext().getExecutor().queueMicrotask(new org.monflabs.galtajs.rt.executors.MicroTask("import.source()", context, null) {
			@Override
			public void run() {
				try {
					p.resolvePromise(importModuleSource(context, specifier));
				} catch(Throwable ex) {
					rethrowIfUncatchable(ex);
					p.reject(JSRuntimeException.exceptionObject(ex));
				}
			}
		});
		return p;
	}

	// Transpiler entry point for import.source() - see dynamicImportChecked()
	// above for why ToString(specifier) is done here, rejecting the promise
	// on an abrupt completion.
	public static Object dynamicImportSourceChecked(JSRuntimeContext context, Object specifierValue) {
		String specifier;
		try {
			specifier = RuntimeUtil.toString(context.getEnvironment(), specifierValue);
		} catch(Throwable ex) {
			rethrowIfUncatchable(ex);
			org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
					new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
			p.reject(JSRuntimeException.exceptionObject(ex));
			return p;
		}
		return dynamicImportSource(context, specifier);
	}

	// Dynamic import() (ImportCall) - shared by both the interpreter
	// (ASTImportCall.evaluate()) and the transpiler
	// (ASTImportCall.transpileJavaExpression()). `specifier` and
	// `attributesValue` must already be fully evaluated by the caller, in
	// that order (a throwing specifier/attributes expression must propagate
	// as a real synchronous throw from the caller, before this method ever
	// runs). Unlike the specifier/attributes EXPRESSIONS' own evaluation,
	// everything extractImportAttributes() does with the resulting VALUE
	// (type-checking `options`, reading/enumerating/type-checking `.with`)
	// happens AFTER the promise capability conceptually already exists
	// (spec 13.3.10.1 EvaluateImportCall step 6 onward) - IfAbruptRejectPromise
	// applies, so any abrupt completion there must REJECT the returned
	// promise, not propagate as a real synchronous throw (test262
	// dynamic-import/import-attributes/2nd-param-*.js - a synchronous
	// throw here was otherwise escaping the same way the specifier
	// ToString bug did, see ASTImportCall.evaluate()'s own doc comment).
	public static Object dynamicImport(JSRuntimeContext context, String specifier, Object attributesValue) {
		java.util.Map<String,String> attributes;
		try {
			attributes = extractImportAttributes(context.getEnvironment(), attributesValue);
		} catch(Throwable ex) {
			rethrowIfUncatchable(ex);
			org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
					new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
			p.reject(JSRuntimeException.exceptionObject(ex));
			return p;
		}
		return dynamicImportWithAttributes(context, specifier, attributes);
	}

	public static Object dynamicImport(JSRuntimeContext context, String specifier) {
		return dynamicImportWithAttributes(context, specifier, null);
	}

	// Attributes-less import()/import.defer() entry points for the
	// TRANSPILER (ASTImportCall.transpileJavaExpression()): unlike the
	// two overloads above, `specifierValue` here is the RAW (not yet
	// ToString()'d) specifier value - this method does that coercion
	// itself, wrapped in the same try/reject-the-promise pattern
	// ASTImportCall.evaluate() already applies in interpreted mode
	// (IfAbruptRejectPromise, spec 13.3.10.1 step 6/7 - a throwing
	// `toString()` must reject the returned promise, not propagate as a
	// real synchronous throw). Needed because generated Java code has no
	// equivalent of evaluate()'s own try/catch around a single sub-
	// expression - see test262 specifier-tostring-abrupt-rejects.js and
	// its import.defer siblings.
	public static Object dynamicImportChecked(JSRuntimeContext context, Object specifierValue) {
		String specifier;
		try {
			specifier = RuntimeUtil.toString(context.getEnvironment(), specifierValue);
		} catch(Throwable ex) {
			rethrowIfUncatchable(ex);
			org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
					new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
			p.reject(JSRuntimeException.exceptionObject(ex));
			return p;
		}
		return dynamicImportWithAttributes(context, specifier, null);
	}

	// Two-argument `import(specifier, attributes)` counterpart to
	// dynamicImportChecked(context, specifierValue) above - same
	// ToString-abrupt-rejects-the-promise fix, but for the attributes-
	// present form specifically (test262 dynamic-import/import-attributes/
	// 2nd-param-trailing-comma-reject.js and siblings: ASTImportCall.
	// transpileJavaExpression()'s attributes-present branch previously
	// called the RAW RuntimeUtil.toString(env,specifierValue) directly,
	// with no checked wrapper at all, so an abrupt ToString propagated as
	// a real synchronous throw instead of rejecting the promise).
	// `attributesValue` is passed as a Supplier, not a plain value,
	// because it must be evaluated AFTER ToString(specifier) succeeds
	// (mirrors ASTImportCall.evaluate()'s own interpreted-mode ordering:
	// specifier expression, then ToString(specifier) [checked], then the
	// attributes expression [a genuine synchronous throw, not checked,
	// since spec's IfAbruptRejectPromise coverage for it doesn't start
	// until dynamicImport()'s own attributes-extraction below]) - a plain
	// Object parameter would have Java evaluate it as an argument BEFORE
	// this method's own body runs, ahead of the ToString check.
	public static Object dynamicImportChecked(JSRuntimeContext context, Object specifierValue, Supplier<Object> attributesValueSupplier) {
		String specifier;
		try {
			specifier = RuntimeUtil.toString(context.getEnvironment(), specifierValue);
		} catch(Throwable ex) {
			rethrowIfUncatchable(ex);
			org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
					new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
			p.reject(JSRuntimeException.exceptionObject(ex));
			return p;
		}
		Object attributesValue = attributesValueSupplier.get();
		return dynamicImport(context, specifier, attributesValue);
	}

	public static Object dynamicImportDeferChecked(JSRuntimeContext context, Object specifierValue) {
		String specifier;
		try {
			specifier = RuntimeUtil.toString(context.getEnvironment(), specifierValue);
		} catch(Throwable ex) {
			rethrowIfUncatchable(ex);
			org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
					new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
			p.reject(JSRuntimeException.exceptionObject(ex));
			return p;
		}
		return dynamicImportDefer(context, specifier);
	}

	// spec 13.3.10.1 EvaluateImportCall's own options-object handling.
	// null/undefined options (the ordinary, non-attributed
	// `import(specifier)` case) or a missing/nullish `.with` property both
	// mean "no attributes" (null, not an empty map - importAttributedModule()
	// only special-cases a REAL "type" key). Every other abrupt case
	// (options/with not an Object, a throwing `.with`/value getter, a
	// throwing ownKeys trap, a non-string attribute value) throws - the
	// caller (dynamicImport() above) converts that into a promise
	// rejection, matching spec's IfAbruptRejectPromise at each of these
	// exact steps. Non-string attribute values are a genuine TypeError
	// (test262 2nd-param-with-value-non-string.js), NOT coerced via
	// toString() as this used to do - a static WithClause's own
	// STRING_LITERAL values are separately always-strings by construction,
	// so this only affects the dynamic import() options-object path.
	private static java.util.Map<String,String> extractImportAttributes(JSEnvironment env, Object optionsValue) {
		// Spec gates BOTH checks on "is not undefined" specifically, NOT
		// "is not null or undefined" - a genuine `null` (for either the
		// options argument itself or its own `.with` property) must still
		// hit the Type-is-not-Object TypeError below, not be silently
		// treated the same as "absent" (test262 2nd-param-non-object.js/
		// 2nd-param-with-non-object.js both include `null` in their own
		// list of values that must reject).
		if(isUndefined(optionsValue)) {
			return null;
		}
		if(!isObject(env, optionsValue)) {
			throw RuntimeUtil.typeError("The second argument to import() must be an object");
		}
		Object withObj = RuntimeUtil.getProperty(env, optionsValue, "with");
		if(isUndefined(withObj)) {
			return null;
		}
		if(!isObject(env, withObj)) {
			throw RuntimeUtil.typeError("The 'with' import attributes option must be an object");
		}
		java.util.Map<String,String> result = new java.util.LinkedHashMap<>();
		// Dispatched via the accessor (not a `withObj instanceof JSObject`
		// cast + JSObject.ownPropertyKeys()) so a Proxy's own "ownKeys"/
		// "getOwnPropertyDescriptor"/"get" traps are correctly observed
		// (ProxyAccessor.ownPropertyEntries()) - test262's own with-
		// enumeration*.js family exercises exactly this (a Proxy `with`
		// object whose traps must be called in the right order, on the
		// right keys only).
		for(java.util.Iterator<Map.Entry<String,Object>> it = env.getAccessor(withObj).ownStringEntries(withObj, true); it.hasNext(); ) {
			Map.Entry<String,Object> e = it.next();
			Object value = e.getValue();
			if(!(value instanceof String str)) {
				throw RuntimeUtil.typeError("Import attribute value for {0} must be a string", e.getKey());
			}
			result.put(e.getKey(), str);
		}
		return result;
	}

	private static Object dynamicImportWithAttributes(JSRuntimeContext context, String specifier, java.util.Map<String,String> attributes) {
		org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
				new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
		context.getGlobalContext().getExecutor().queueMicrotask(new org.monflabs.galtajs.rt.executors.MicroTask("import()", context, null) {
			@Override
			public void run() {
				try {
					JSModule module = attributes!=null
							? RuntimeUtil.importAttributedModule(context, specifier, attributes)
							: RuntimeUtil.importModule(context, specifier);
					settleOnceModuleReady(context, module, p);
				} catch(Throwable ex) {
					p.reject(JSRuntimeException.exceptionObject(ex));
				}
			}
		});
		return p;
	}

	// importModule() returning does NOT mean `module`'s own evaluation has
	// genuinely finished - for a module with its own top-level await,
	// reached here via THIS microtask (always a NESTED execute() call,
	// since this runs from inside the outer drainPendingTasks() loop),
	// JSAsyncExecutor.execute()'s nested-call path correctly returns after
	// only the synchronous prefix, not full completion (see its own doc).
	// Resolving the import() promise right away in that case reads the
	// module's exports/default before they're genuinely set (test262
	// await-dynamic-import-resolution.js: "Cannot access 'default' before
	// initialization"). JSInterpretedUnit's own addEvaluationCompletionCallback()
	// fires immediately (synchronously, right here) in the common case
	// (already done), or later, once genuinely done, otherwise - either
	// way this settles `p` at the right time, never early. Any OTHER
	// JSModule kind (no ModuleStatus tracking at all) has no such gap -
	// importModule() already only ever returns it fully formed.
	private static void settleOnceModuleReady(JSRuntimeContext context, JSModule module, org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p) {
		if(module instanceof org.monflabs.galtajs.modules.JSInterpretedUnit unit
				&& unit.getModuleStatus()==org.monflabs.galtajs.modules.JSInterpretedUnit.ModuleStatus.EVALUATING) {
			unit.addEvaluationCompletionCallback(() -> {
				if(unit.getModuleStatus()==org.monflabs.galtajs.modules.JSInterpretedUnit.ModuleStatus.ERRORED) {
					p.reject(unit.getEvaluationError());
				} else {
					p.resolvePromise(buildImportNamespaceObject(context.getEnvironment(), module));
				}
			});
			return;
		}
		p.resolvePromise(buildImportNamespaceObject(context.getEnvironment(), module));
	}

	// `import.defer(specifier)` (dynamic form of `import defer * as ns from
	// '...'`) - unlike plain import()/dynamicImportWithAttributes() above,
	// this must resolve to the SAME cached DEFERRED namespace object a
	// static `import defer` of the same specifier would get (spec:
	// GetModuleNamespace's [[DeferredNamespace]] slot is per-Module-Record,
	// not per-import-site - test262 import-defer/deferred-namespace-object/
	// identity.js: static and dynamic deferred imports of the same module
	// must be THE SAME object), not the plain, already-evaluated namespace
	// import()/buildImportNamespaceObject() produces. Reuses
	// importDeferredNamespace() - the exact same synchronous, cached
	// mechanism the static `import defer` path already uses (ASTImport) -
	// just invoked from inside a queued microtask instead of at module-
	// linking time, to keep the same "always at least one tick" async
	// timing as an ordinary dynamic import().
	// See gatherAsynchronousTransitiveDependencies()'s own doc comment
	// (InterpretedGlobalRuntimeContext) - `import.defer` causing eager
	// evaluation of a top-level-await target (or transitive dependency)
	// is spec-required (13.3.10.1.1 ContinueDynamicImport's own ~defer~
	// phase branch), not optional: a deferred namespace's whole point is
	// producing its evaluated bindings lazily, on first property access -
	// but an ASYNC module's own completion can't be produced synchronously
	// from a plain property get, so staying lazy for it would leave a
	// property access with no way to ever observe the (still-pending)
	// result. test262 import-defer/import-defer-async-module/main.js,
	// import-defer-transitive-async-module/{main,promise-prototype-then-
	// not-called}.js, sync-dependency-of-deferred-async-module/main.js.
	public static Object dynamicImportDefer(JSRuntimeContext context, String specifier) {
		org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
				new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
		context.getGlobalContext().getExecutor().queueMicrotask(new org.monflabs.galtajs.rt.executors.MicroTask("import.defer()", context, null) {
			@Override
			public void run() {
				try {
					JSObject ns = RuntimeUtil.importDeferredNamespace(context, specifier, null);
					JSUnitContext mainContext = context.getMainContext();
					java.util.List<org.monflabs.galtajs.modules.JSInterpretedUnit> asyncDeps =
							context.getGlobalContext().gatherAsynchronousTransitiveDependencies(mainContext, specifier);
					if(asyncDeps.isEmpty()) {
						p.resolvePromise(ns);
						return;
					}
					JSGlobalContext gc = context.getGlobalContext();
					// SafePerformPromiseAll-equivalent (spec's own name for
					// this, quoted in promise-prototype-then-not-called.js:
					// waits for every gathered dependency's own evaluation
					// to settle WITHOUT ever going through the JS-visible,
					// patchable Promise.prototype.then - addEvaluationCompletionCallback()
					// is a plain Java callback, same as every other
					// internal `.then_()` use elsewhere in this codebase).
					// Every dep still gets initModule() called even if an
					// EARLIER one throws or rejects (spec: Evaluate() is
					// invoked on every gathered module unconditionally,
					// before any combined waiting/rejection happens -
					// Promise.all-style semantics, not stop-on-first-error).
					int total = asyncDeps.size();
					java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(total);
					java.util.concurrent.atomic.AtomicBoolean settled = new java.util.concurrent.atomic.AtomicBoolean(false);
					for(org.monflabs.galtajs.modules.JSInterpretedUnit dep: asyncDeps) {
						Runnable onDepSettled = () -> {
							if(settled.get()) {
								return;
							}
							if(dep.getModuleStatus()==org.monflabs.galtajs.modules.JSInterpretedUnit.ModuleStatus.ERRORED) {
								if(settled.compareAndSet(false,true)) {
									p.reject(dep.getEvaluationError());
								}
								return;
							}
							if(remaining.decrementAndGet()==0 && settled.compareAndSet(false,true)) {
								p.resolvePromise(ns);
							}
						};
						try {
							dep.initModule(gc, false);
						} catch(Throwable ex) {
							// A synchronous throw (dep's own body errored
							// entirely within its synchronous prefix, before
							// any suspension) - moduleStatus/evaluationError
							// are already set by initModule()'s own internal
							// completion handling by the time this is
							// reached (see JSInterpretedUnit.runBody()'s own
							// doc), so onDepSettled() above still reads the
							// right thing.
						}
						dep.addEvaluationCompletionCallback(onDepSettled);
					}
				} catch(Throwable ex) {
					p.reject(JSRuntimeException.exceptionObject(ex));
				}
			}
		});
		return p;
	}

	// The Promise import() resolves to must be the module's full Module
	// Namespace Exotic Object (spec 9.4.6) - the SAME cached instance a
	// static `import * as ns from X` of the same module would get (spec's
	// GetModuleNamespace [[Namespace]] slot caching - see
	// JSModule.getModuleNamespaceObject()'s own doc comment), not a
	// separately hand-built plain JSObject snapshot.
	public static JSObject buildImportNamespaceObject(JSEnvironment env, JSModule module) {
		return module.getModuleNamespaceObject();
	}

	//
	// using / await using (Explicit Resource Management) - transpiler support
	//

	// Mirrors ASTVariableDeclUsing.createVariable() (interpreter path):
	// AddDisposableResource - a null/undefined value registers no disposal at
	// all for a SYNC `using` (spec's own early-return-unused is gated on
	// "hint is sync-dispose" specifically); for `await using` it still
	// records a no-op resource (DisposeMethod undefined) - see
	// DisposeResourcesUtil.dispose()'s own doc for why that no-op entry
	// still matters (needsAwait/DisposeResources step 4a: even an
	// all-null-valued disposal boundary must take the same extra microtask
	// tick a real async disposal would - explicit-await-for-{null,undefined}.js).
	// Any other non-object value is a TypeError; an object value must
	// resolve a callable [Symbol.dispose] ([Symbol.asyncDispose] first, for
	// `await using`), resolved and captured here, once. Called by the
	// transpiler for each `using`/`await using` binding, against whichever
	// enclosing disposal-boundary container (block/function body/...)
	// declared `resources`.
	public static void registerDisposableResource(JSRuntimeContext context, List<DisposableResource> resources, Object value, boolean isAwait) {
		if(!isNullOrUndefined(value)) {
			JSEnvironment env = context.getEnvironment();
			if(!isObject(env, value)) {
				throw typeError("Cannot dispose non-object value, {0}", objectTypeName(env,value));
			}
			Callable disposeMethod = DisposeResourcesUtil.resolveDisposeMethod(env, value, isAwait);
			resources.add(new DisposableResource(value, disposeMethod, isAwait));
		} else if(isAwait) {
			resources.add(new DisposableResource(value, null, true));
		}
	}

	// Rethrows a Throwable that isn't statically known to be unchecked
	// (mirrors the same instanceof-chain every interpreted disposal call site
	// - ASTBlock, ASTFor, ASTForOf - already uses) without wrapping a
	// RuntimeException/Error in another layer.
	public static void rethrowUnchecked(Throwable t) {
		if(t instanceof RuntimeException re) {
			throw re;
		}
		if(t instanceof java.lang.Error e) {
			throw e;
		}
		throw new RuntimeException(t);
	}


	//
	// Assign properties
	//
	public static Object assign(JSEnvironment env, Object leftValue, Object member, Object value) {
		RuntimeUtil.setProperty(env,leftValue,member,value);
		return value;
	}

	public static Object assignAdd(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = add(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// Lazy variant used by the transpiler for member/array-member compound-
	// assignment targets (+=, *=, ...): the null-base check and ToPropertyKey
	// coercion of `member` (see toPropertyKeyChecked()) must both happen
	// BEFORE `value` is ever evaluated - per spec, they're part of the
	// initial GetValue(lref) (6.2.4.6 step 5a), which runs before the RHS is
	// evaluated (13.15.2). A plain Object `value` parameter can't guarantee
	// that ordering, since the caller's own Java argument-evaluation would
	// have already run it before this method's body even starts - so the RHS
	// is always passed as a Supplier here, called only after the check
	// (test262 S11.13.2_A7.*_T1/T3/T4 - "Compound Assignment Operator
	// evaluates its operands from left to right").
	public static Object assignAdd(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = add(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignAnd(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		if( !toBoolean(env,v) ) {
			return v;
		}
		RuntimeUtil.setProperty(env,leftValue,member,value);
		return value;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignAnd(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		if( !toBoolean(env,v) ) {
			return v;
		}
		Object rv = value.get();
		RuntimeUtil.setProperty(env,leftValue,key,rv);
		return rv;
	}
	public static Object assignBitAnd(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = bitAnd(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignBitAnd(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = bitAnd(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignBitOr(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = bitOr(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignBitOr(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = bitOr(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignBitXor(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = bitXor(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignBitXor(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = bitXor(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignDiv(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = div(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignDiv(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = div(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignMod(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = mod(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignMod(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = mod(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignMul(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = mul(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignMul(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = mul(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignPower(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = power(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignPower(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = power(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignNullCoalescing(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		if(isNullOrUndefined(v)) {
			RuntimeUtil.setProperty(env,leftValue,member,value);
			return value;
		}
		return v;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignNullCoalescing(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		if(isNullOrUndefined(v)) {
			Object rv = value.get();
			RuntimeUtil.setProperty(env,leftValue,key,rv);
			return rv;
		}
		return v;
	}
	public static Object assignOr(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		if( v!=UNDEFINED && toBoolean(env,v) ) {
			return v;
		}
		RuntimeUtil.setProperty(env,leftValue,member,value);
		return value;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignOr(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		if( v!=UNDEFINED && toBoolean(env,v) ) {
			return v;
		}
		Object rv = value.get();
		RuntimeUtil.setProperty(env,leftValue,key,rv);
		return rv;
	}
	public static Object assignLShift(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = lshift(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignLShift(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = lshift(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignRShift(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = rshift(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignRShift(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = rshift(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignRunShift(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = runshift(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignRunShift(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = runshift(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object assignSub(JSEnvironment env, Object leftValue, Object member, Object value) {
		Object v = RuntimeUtil.getProperty(env, leftValue, member);
		Object vi = sub(env,v,value);
		RuntimeUtil.setProperty(env,leftValue,member,vi);
		return vi;
	}
	// See assignAdd(...,Supplier) above.
	public static Object assignSub(JSEnvironment env, Object leftValue, Object member, Supplier<Object> value) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object rv = value.get();
		Object vi = sub(env,v,rv);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}

	// Support for sequences - we need an extra param to match the other assignment functions.
	// `member` must be coerced via toPropertyKeyChecked (null-base check THEN
	// ToPropertyKey), not passed raw to getProperty/setProperty - a raw
	// Object member re-coerced by their own generic dispatcher coerces the
	// key BEFORE checking the base for null/undefined, backwards from spec
	// (6.2.4.6 GetValue step 5a's ToObject(base) precedes ToPropertyKey).
	// Confirmed via test262 language/expressions/{prefix,postfix}-{increment,
	// decrement}/S11.{3,4}.{1,2,4,5}_A6_T{1,2,3}.js.
	public static Object preInc(JSEnvironment env, Object leftValue, Object member, Object unused) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object vi = incNumber(env,v);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object postInc(JSEnvironment env, Object leftValue, Object member, Object unused) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		// Needs to be converted so it returns a number - toNumeric, not
		// toNumber: toNumber rejects BigInt (spec: no implicit BigInt->Number
		// coercion), but x++/x-- on a BigInt must return/store a BigInt, not
		// throw. Mirrors the VarAccessor/JSVar postInc variants above, which
		// already use toNumeric correctly. Confirmed via test262 language/
		// expressions/postfix-{increment,decrement}/bigint.js.
		v = RuntimeUtil.toNumeric(env,v);
		Object vi = incNumber(env,v);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return v;
	}
	public static Object preDec(JSEnvironment env, Object leftValue, Object member, Object unused) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		Object vi = decNumber(env,v);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return vi;
	}
	public static Object postDec(JSEnvironment env, Object leftValue, Object member, Object unused) {
		Object key = toPropertyKeyChecked(env, leftValue, member);
		Object v = RuntimeUtil.getProperty(env, leftValue, key);
		// See postInc's identical comment - toNumeric, not toNumber, to
		// preserve BigInt instead of throwing on it.
		v = RuntimeUtil.toNumeric(env,v);
		Object vi = decNumber(env,v);
		RuntimeUtil.setProperty(env,leftValue,key,vi);
		return v;
	}

	//
	// Private (#name) counterparts of the member get/assign/in operations above,
	// used by TRANSPILED-mode codegen (ASTMember/ASTIn) when the member name is
	// statically known to be private - resolves the enclosing class evaluation's
	// PrivateName token via the runtime context chain (JSRuntimeContext.
	// resolvePrivateName), then goes straight to [[PrivateElements]]
	// (getPrivateField/setPrivateField), bypassing JSAccessor/prototype chain/
	// Proxy traps entirely, per spec - mirrors each sibling above exactly,
	// substituting private storage for ordinary getProperty/setProperty.
	//

	public static Object privateGet(JSRuntimeContext ctx, Object instance, String member) {
		return getPrivateField(instance, ctx.resolvePrivateName(member));
	}

	public static String privateTypeof(JSRuntimeContext ctx, Object instance, String member) {
		return typeof(ctx.getEnvironment(), getPrivateField(instance, ctx.resolvePrivateName(member)));
	}

	public static boolean privateIn(JSRuntimeContext ctx, Object rightValue, String member) {
		if(!isObject(ctx.getEnvironment(), rightValue)) {
			throw RuntimeUtil.typeError("Cannot use 'in' operator to search for private member {0} in a non-object value", member);
		}
		PrivateName pn = ctx.resolvePrivateName(member);
		return pn!=null && hasPrivateField(rightValue, pn);
	}

	public static Object privateAssign(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		setPrivateField(leftValue, ctx.resolvePrivateName(member), value);
		return value;
	}
	public static Object privateAssignAdd(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = add(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignAnd(JSRuntimeContext ctx, Object leftValue, String member, Supplier<Object> value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		if( !toBoolean(env,v) ) {
			return v;
		}
		Object rv = value.get();
		setPrivateField(leftValue,pn,rv);
		return rv;
	}
	public static Object privateAssignBitAnd(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = bitAnd(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignBitOr(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = bitOr(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignBitXor(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = bitXor(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignDiv(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = div(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignMod(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = mod(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignMul(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = mul(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignPower(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = power(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignNullCoalescing(JSRuntimeContext ctx, Object leftValue, String member, Supplier<Object> value) {
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		if(isNullOrUndefined(v)) {
			Object rv = value.get();
			setPrivateField(leftValue,pn,rv);
			return rv;
		}
		return v;
	}
	public static Object privateAssignOr(JSRuntimeContext ctx, Object leftValue, String member, Supplier<Object> value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		if( v!=UNDEFINED && toBoolean(env,v) ) {
			return v;
		}
		Object rv = value.get();
		setPrivateField(leftValue,pn,rv);
		return rv;
	}
	public static Object privateAssignLShift(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = lshift(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignRShift(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = rshift(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignRunShift(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = runshift(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privateAssignSub(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = sub(env,v,value);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}

	public static Object privatePreInc(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = incNumber(env,v);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privatePostInc(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		// Needs to be converted to it returns a number
		v = toNumber(env,v);
		Object vi = incNumber(env,v);
		setPrivateField(leftValue,pn,vi);
		return v;
	}
	public static Object privatePreDec(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		Object vi = decNumber(env,v);
		setPrivateField(leftValue,pn,vi);
		return vi;
	}
	public static Object privatePostDec(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		JSEnvironment env = ctx.getEnvironment();
		PrivateName pn = ctx.resolvePrivateName(member);
		Object v = getPrivateField(leftValue, pn);
		// Needs to be converted to it returns a number
		v = toNumber(env,v);
		Object vi = decNumber(env,v);
		setPrivateField(leftValue,pn,vi);
		return v;
	}

	//
	// Proxy Object
	//
	
	public static Object unproxy(Object o) {
		if(o instanceof BuiltinProxy proxy) {
			return proxy.getTarget();
		}
		return o;
	}
	
	
	
	//
	// Sequence utilities
	//
	
	public static enum MODE {
		ONE,
		ALL
	}
	
	public static JSResult seq(JSEnvironment env, BiFunction<JSEnvironment,Object,Object> unaryOp, Object o1) {
		JSResult r1 = o1 instanceof JSResult ? (JSResult)o1 : new JSResult(o1);
		return seq(env,unaryOp,r1,new JSResult());
	}
	public static JSResult seq(JSEnvironment env, BiFunction<JSEnvironment,Object,Object> unaryOp, Object o1, JSResult result) {
		JSResult r1 = o1 instanceof JSResult ? (JSResult)o1 : new JSResult(o1);
		return seq(env,unaryOp,r1,result);
	}
	public static JSResult seq(JSEnvironment env, BiFunction<JSEnvironment,Object,Object> unaryOp, JSResult r1, JSResult result) {
		result.initSequence();
		int sz = r1.size();
		if(sz>0) {
			for(int i=0; i<sz; i++) {
				Object v = r1.get(i);
				result.addToSequence(env,unaryOp.apply(env,v));
			}
		}
		return result;
	}
	
	// Sequence operations with arithmetic operators 
	public static JSResult seq(JSEnvironment env, TriFunction<JSEnvironment,Object,Object,Object> binaryOp, Object o1, Object o2) {
		JSResult r1 = o1 instanceof JSResult ? (JSResult)o1 : new JSResult(o1);
		JSResult r2 = o2 instanceof JSResult ? (JSResult)o2 : new JSResult(o2);
		return seq(env,binaryOp,r1,r2,new JSResult());
	}
	public static JSResult seq(JSEnvironment env, TriFunction<JSEnvironment,Object,Object,Object> binaryOp, Object o1, Object o2, JSResult result) {
		JSResult r1 = o1 instanceof JSResult ? (JSResult)o1 : new JSResult(o1);
		JSResult r2 = o2 instanceof JSResult ? (JSResult)o2 : new JSResult(o2);
		return seq(env,binaryOp,r1,r2,result);
	}
	public static JSResult seq(JSEnvironment env, TriFunction<JSEnvironment,Object,Object,Object> binaryOp, JSResult r1, JSResult r2, JSResult result) {
		result.initSequence();
		int laSize = r1.size();
		int raSize = r2.size();
		if(laSize>0 && raSize>0) {
			int sz = Math.max(laSize, raSize);
			for(int i=0; i<sz; i++) {
				Object lv = r1.get(Math.min(i, laSize-1));
				Object rv = r2.get(Math.min(i, raSize-1));
				result.addToSequence(env,binaryOp.apply(env,lv, rv));
			}
		}
		return result;
	}

	// Sequence operations with comparison operators 
	public static boolean seqCmp(JSEnvironment env, TriPredicate<JSEnvironment,Object,Object> binaryOp, Object o1, Object o2, RuntimeUtil.MODE mode) {
		JSResult r1 = o1 instanceof JSResult ? (JSResult)o1 : new JSResult(o1);
		JSResult r2 = o2 instanceof JSResult ? (JSResult)o2 : new JSResult(o2);
		return seqCmp(env,binaryOp,r1,r2,mode);
	}
	public static boolean seqCmp(JSEnvironment env, TriPredicate<JSEnvironment,Object,Object> binaryOp, JSResult r1, JSResult r2, RuntimeUtil.MODE mode) {
		// Wondering if this can be optimized and avoid the creation of temporary arrays
		// The problem is actually more complex than it seems...
		List<Object> leftArray = r1.derefList(new ArrayList<>());
		List<Object> rightArray = r2.derefList(new ArrayList<>());
		
		int laSize = leftArray.size();
		int raSize = rightArray.size();
		if(laSize>0 && raSize>0) {
			int sz = Math.max(laSize, raSize);
			for(int i=0; i<sz; i++) {
				Object lv = leftArray.get(Math.min(i, laSize-1));
				Object rv = rightArray.get(Math.min(i, raSize-1));
				boolean test = binaryOp.test(env,lv, rv);
				switch(mode) {
					case ONE-> {
						if(test) {
							return true;
						}
					}
					case ALL-> {
						if(!test) {
							return false;
						}
					}
				}
			}		
		}
		switch(mode) {
			case ONE-> { return false; }
			case ALL-> { return true; }
		}
		throw new JSException(null,"Internal error");
	}

    public static JSResult arrayDeepScan(JSEnvironment env, JSResult result) {
		result.reduceToSequence( (v,res) -> {
			deepScan(env,v,res);
		});
		return result;
    }

    public static JSResult arrayFlatten(JSEnvironment env, JSResult result, boolean deepscan) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				deepScan(env,v,res);
			});
		}
		
		// If the result is a sequence, then we should flatten the sequence
		result.reduceToSequence( (seqValue,res) -> {
			// Should we add a null op to this operator as well?
			// Should we go beyond just JsonArray but an ArrayLike?
			if(seqValue instanceof JsonContainer c) {
				for(Object o: c.values()) {
					res.addToSequence(env,o);
				}
			}
		});
		
		return result;
    }

    public static JSResult arrayMap(JSRuntimeContext context, JSResult result, Function<Object,Object> mapper) {
		// If the result is a sequence, then we should flatten the sequence
		result.map( context.getEnvironment(), (val) -> {
			Object r = mapper.apply(val);
			if(r instanceof Callable callable) {
				r = callable.call(val, EMPTY_PARAMS);
			}
			return r;
		});
		
		return result;
    }

	
	// Deep scan an object
	public static void deepScan(JSEnvironment env, Object _this, JSResult result) {
		if(_this instanceof JsonContainer jc) {
			result.addToSequence(env,_this);
			for(Object v: jc.values()) {
				deepScan(env,v, result);
			}
		}
    }


    @SuppressWarnings("unchecked")
	public static void _find(JSEnvironment env, MemberAccessor accessor, Object value, Object member, boolean forUpdate) {
    	Object key = member;
		if(member instanceof Function fct) {
			key = fct.apply(value);
		}
    	if(value instanceof JSObjectInternal o) {
    		if(key==null) {
    			JSObject clone = forUpdate ? copyJSObject(env,o) : o;
        		for(Iterator<Map.Entry<String,Object>> it=clone.ownPropertyEntries(true); it.hasNext(); ) {
        			Map.Entry<String,Object> e=it.next();
        			String k = e.getKey();
	        		accessor.apply(o, k,
	        				() -> e.getValue(), 
	        				forUpdate ? (val) -> o.setOwnProperty(k,e.getValue()) : null,
	        				forUpdate ? () -> { if(o.hasProperty(k)) { o.deleteProperty(k); return true; } else { return false; } } : null
		        		);
        		}
    		} else if(key instanceof String k) {
	    		if(o.hasProperty(k)) {
	        		accessor.apply(o, k,
	        				() -> o.getProperty(k), 
	        				forUpdate ? (val) -> o.setOwnProperty(k, val) : null,
	        				forUpdate ? () -> { if(o.hasProperty(k)) { o.deleteProperty(k); return true; } else { return false; } } : null
		        		);
        		}
    		}
			for(Iterator<Object> it=o.ownPropertyValues(true); it.hasNext(); ) {
				Object v=it.next();
				_find(env,accessor,v,key,forUpdate);
			}
    	} else if(value instanceof JSArray a) {
    		if(key==null) {
    			JSArray clone = forUpdate ? copyJSArray(env,a) : a;
    			int sz = (int)clone.arrayLength();
        		for(int i=0; i<sz; i++) {
        			final int idx = i;
	        		accessor.apply(a, idx,
	        				() -> a.getProperty(idx), 
	        				forUpdate ? (val) -> a.setOwnProperty(idx,val) : null,
	        				forUpdate ? () -> { if(a.arrayHas(idx)) { a.arrayRemove(idx); return true; } else { return false; } } : null
		        		);
        		}
    		} else if(key instanceof Number n) {
    			int idx = n.intValue();
	    		if(a.arrayHas(idx)) {
	        		accessor.apply(a, idx,
	        				() -> a.getProperty(idx), 
	        				forUpdate ? (val) -> a.setOwnProperty(idx, val) : null,
	        				forUpdate ? () -> { if(a.arrayHas(idx)) { a.getProperty(idx); return true; } else { return false; } } : null
		        		);
	    		}
    		}
			for(Iterator<Object> it=a.ownPropertyValues(true); it.hasNext(); ) {
				Object v=it.next();
				_find(env,accessor,v,key,forUpdate);
			}
    	}
    }
	private static JSObject copyJSObject(JSEnvironment env, JSObject src) {
		JSObject a = JSObject.create(env);
		for(Iterator<Map.Entry<String, Object>> it=src.ownPropertyEntries(false); it.hasNext(); ) {
			Map.Entry<String, Object> e=it.next();
			// Should we copy the descriptor as well?
			a.setOwnProperty(e.getKey(), e.getValue());
		}
		return a;
    }
	private static JSArray copyJSArray(JSEnvironment env, JSArray src) {
		JSArray a = JSArray.create(env,(int)src.arrayLength());
		for(Iterator<Object> it=src.ownPropertyValues(true); it.hasNext(); ) {
			Object v=it.next();
			a.arrayAdd(v);
		}
		return a;
	}

    public static void _arrayFilter(JSRuntimeContext context, JSResult result, Function<Object,Object> cond, MemberAccessor accessor, boolean forUpdate) {
    	result.forEach( (base) -> {
			if(base==null) {
				return; // propagate null as this is a sequence
			}
			if(base instanceof JSObjectInternal o) {
		        for(Iterator<Map.Entry<String,Object>> it=o.ownPropertyEntries(true); it.hasNext(); ) {
		        	Map.Entry<String,Object> e=it.next();
		        	if(evaluateFilter(context, cond, e.getValue())) {
		        		final String idx = e.getKey();
		        		accessor.apply(o, idx, 
	        				() -> o.getProperty(idx), 
	        				forUpdate ? (val) -> o.setOwnProperty(idx, val) : null,
	        				forUpdate ? () -> { if(o.hasProperty(idx)) { o.deleteProperty(idx); return true; } else { return false; } } : null
		        		);
		        	}
		        }
		        return;
			}
			if(base instanceof JSArray a) {
				long sz = a.arrayLength();
		        for(long i=0; i<sz; i++) {
		        	Object v = a.getProperty(i);
		        	if(evaluateFilter(context, cond, v)) {
		        		final long idx = i;
		        		accessor.apply(a, idx, 
	        				() -> a.getProperty(idx), 
	        				forUpdate ? (val) -> a.setOwnProperty(idx, val) : null,
	        				forUpdate ? () -> { if(a.arrayHas(idx)) { a.arrayRemove(idx); return true; } else { return false; } } : null
		        		);
		        	}
		        }
		        return;
			}
		});
	}
    
    private static boolean evaluateFilter(JSRuntimeContext context, Function<Object,Object> cond, Object v) {
    	Object filterResult = cond.apply(v) ;
		if(filterResult instanceof Callable callable) {
			Object[] parameters = new Object[] {v};
			filterResult = callable.call(callable, parameters);
		}
		return toBoolean(context.getEnvironment(), filterResult);
    }

    
	/////////////////////////////////////////////////////////////////////////
	// Generators Support
	/////////////////////////////////////////////////////////////////////////
	
    public static Object yield_(JSRuntimeContext context) {
    	return yield_(context,null);
    }
    // Returns whatever is passed to the subsequent next(value) call - this is what a
    // "yield" expression evaluates to once the generator resumes.
    public static Object yield_(JSRuntimeContext context, Supplier<Object> param) {
		JSFunctionContext fc = nearestYielderContext(context);
		if(fc!=null) {
			Yielder<Object> yielder = fc.getYielder();
			if(yielder!=null) {
				Object value = param!=null ? param.get() : RuntimeUtil.UNDEFINED;
				return yielder.yield(value);
			}
		}
		throw RuntimeUtil.syntaxError("yield must be used in a generator function");
	}

	// A ComputedPropertyName (e.g. `class C { [yield 9]() {} }`) or a class
	// field initializer's synthetic HomeObject-bearing frame
	// (InterpretedFieldInitializerRuntimeContext) is not itself a new
	// [[GeneratorState]]-bearing execution context per spec -
	// ClassDefinitionEvaluation evaluates these expressions as part of the
	// SURROUNDING context's own execution, so a `yield` there must resolve
	// to the nearest ACTUAL enclosing generator, skipping over any
	// intervening non-generator JSFunctionContext frame (mirrors the
	// existing arrow-skipping pattern in getSuper()/superCtor()).
	private static JSFunctionContext nearestYielderContext(JSRuntimeContext context) {
		JSFunctionContext fc = context.getFunctionContext();
		while(fc!=null && fc.getYielder()==null) {
			JSRuntimeContext parent = fc.getParent();
			fc = parent!=null ? parent.getFunctionContext() : null;
		}
		return fc;
	}

    // Implements the YieldExpression : yield* AssignmentExpression delegation algorithm:
    // forwards next/throw/return completions (however this yield* itself gets resumed)
    // into the delegate's iterator, and returns the delegate's final (done) value - the
    // value that "yield* expr" itself evaluates to.
    //
    // `async` (true only for `yield*` immediately inside an `async function*`
    // body - see ASTYieldStar's own static flag) switches iterator lookup to
    // prefer [Symbol.asyncIterator] over [Symbol.iterator] (falling back to
    // the sync one when absent, same as valueIteratorAsync()'s own GetIterator
    // (obj, async) fallback). A genuine async delegate's raw step result is
    // just Awaited directly (a real async iterator's next() already returns
    // a plain, non-getter-laden object, so this is safe). A SYNC delegate
    // used under `async=true` instead needs a real
    // %AsyncFromSyncIteratorPrototype%-style wrap: `done`/`value` must be
    // read from the delegate's raw (possibly getter-backed) result EXACTLY
    // ONCE EACH, here, eagerly, done-before-value - producing a FRESH plain
    // `{value,done}` object - rather than letting the raw result's live
    // getters leak out to be read again later by whoever inspects the
    // yielded/returned value. Confirmed needed via yield-star-{a,}sync-
    // {next,return,throw}.js, whose observed getter-read log expects
    // `value`/`done` already read (in that order) by the time `yield*`'s
    // own outer `next()` promise settles, not lazily on later access.
    public static Object yieldStar_(JSRuntimeContext context, Supplier<Object> param, boolean async) {
		JSFunctionContext fc = nearestYielderContext(context);
		Yielder<Object> yielder = fc!=null ? fc.getYielder() : null;
		if(yielder==null) {
			throw RuntimeUtil.syntaxError("yield* must be used in a generator function");
		}
		JSEnvironment env = context.getEnvironment();
		IteratorLookup lookup = async ? getIteratorPreferAsync(env, param.get()) : new IteratorLookup(getIterator(env, param.get()), false);
		Object iterator = lookup.iterator();
		boolean wrapSyncResult = async && lookup.fromSync();
		// GetIteratorFromMethod caches [[NextMethod]] ONCE, at iterator
		// acquisition time - every subsequent IteratorNext call in the loop
		// below reuses this SAME reference, it never re-reads "next" off
		// the iterator object again (unlike "throw"/"return", which per
		// spec ARE freshly looked up via GetMethod on every use, in the
		// mode==1/2 branches below - not cached). Confirmed needed via
		// yield-star-{a,}sync-next.js, whose observed getter-read log
		// expects exactly one "get next" read total, before the loop's
		// first next() call, not one per iteration.
		Object nextMethod = RuntimeUtil.getProperty(env, iterator, "next");

		Object receivedValue = RuntimeUtil.UNDEFINED;
		int mode = 0; // 0=normal (next), 1=throw, 2=return
		while(true) {
			Object innerResult;
			if(mode==1) {
				Object throwMethod = getMethod(env, iterator, "throw");
				if(throwMethod==null) {
					iteratorClose(env, iterator);
					throw RuntimeUtil.typeError("iterator does not have a throw method");
				}
				innerResult = RuntimeUtil.call(env, throwMethod, iterator, new Object[]{receivedValue});
			} else if(mode==2) {
				Object returnMethod = getMethod(env, iterator, "return");
				if(returnMethod==null) {
					// Spec YieldExpression:yield* AssignmentExpression, the
					// `received.[[Type]] is return` branch, "If return is
					// undefined" sub-step: "If generatorKind is async, then
					// set received.[[Value]] to ? Await(received.[[Value]])"
					// - even with no delegate "return" method to call, an
					// async generator's own yield* must still unwrap the
					// return value through PromiseResolve/Await before
					// finally completing via the return signal below
					// (test262 statements/async-generator/yield-star-
					// return-then-getter-ticks.js).
					if(async) {
						receivedValue = awaitInGenerator_(context, receivedValue);
					}
					throw new GeneratorReturnSignal(receivedValue);
				}
				innerResult = RuntimeUtil.call(env, returnMethod, iterator, new Object[]{receivedValue});
			} else {
				innerResult = RuntimeUtil.call(env, nextMethod, iterator, new Object[]{receivedValue});
			}
			if(async) {
				// Await the raw next()/throw()/return() call result itself
				// first (a real Promise for a genuine async delegate; a
				// harmless no-op settle for a non-thenable sync-delegate
				// result) to get the settled IteratorResult object, THEN -
				// for EITHER kind of delegate - eagerly read its done/value
				// (done-before-value, matching the observed getter-read
				// order) into a FRESH plain object, so nothing downstream
				// (this method's own done-branch below, or whoever the
				// forwarded YieldStarDelegateResult's raw result reaches)
				// re-triggers those getters a second time. Only a
				// sync-delegate's extracted value additionally needs its
				// own await (%AsyncFromSyncIteratorPrototype%'s
				// AsyncFromSyncIteratorContinuation wraps it through a
				// fresh promise capability) - a genuine async delegate's
				// value was already produced by an async body/native
				// iterator and is used as-is.
				innerResult = awaitInGenerator_(context, innerResult);
				if(!RuntimeUtil.isObject(env, innerResult)) {
					throw RuntimeUtil.typeError("Iterator result {0} is not an object", innerResult);
				}
				boolean stepDone = RuntimeUtil.toBoolean(env, RuntimeUtil.getProperty(env, innerResult, "done"));
				Object stepValue = RuntimeUtil.getProperty(env, innerResult, "value");
				if(wrapSyncResult) {
					try {
						stepValue = awaitInGenerator_(context, stepValue);
					} catch(Throwable t) {
						// AsyncFromSyncIteratorContinuation step 6: `done`
						// is false here (this branch is only reached for a
						// non-final step of the delegation loop above) and
						// the value-wrapper Await rejected - the
						// underlying SYNC delegate must be closed before
						// the rejection propagates, same as
						// AsyncFromSyncJavaIterator.next() does for a
						// plain `for await` loop (test262 built-ins/
						// AsyncFromSyncIteratorPrototype/{next,throw}/
						// *-poisoned-wrapper.js /
						// yield-*-rejected-promise-close.js, reached here
						// via `yield*` delegating to a sync generator
						// inside an async generator).
						RuntimeUtil.iteratorCloseQuietly(env, iterator);
						throw t;
					}
				}
				innerResult = JSObject.of(env, "value", stepValue, "done", stepDone);
			}
			if(!RuntimeUtil.isObject(env, innerResult)) {
				throw RuntimeUtil.typeError("Iterator result {0} is not an object", innerResult);
			}
			boolean done = RuntimeUtil.toBoolean(env, RuntimeUtil.getProperty(env, innerResult, "done"));
			if(done) {
				Object finalValue = RuntimeUtil.getProperty(env, innerResult, "value");
				if(mode==2) {
					// The delegate accepted the forced return (or had no return method):
					// yield* itself completes the enclosing generator via return, rather
					// than becoming a normal expression value.
					throw new GeneratorReturnSignal(finalValue);
				}
				return finalValue;
			}
			// Per spec, a non-final step forwards the delegate's raw result object
			// straight through to the outer next()/throw()/return() caller - "value"
			// is not read here at all (only once done is true, above).
			try {
				receivedValue = yielder.yield(new YieldStarDelegateResult(innerResult));
				mode = 0;
			} catch(GeneratorReturnSignal grs) {
				receivedValue = grs.getValue();
				mode = 2;
			} catch(JSRuntimeException jre) {
				receivedValue = JSRuntimeException.exceptionObject(jre);
				mode = 1;
			}
		}
	}

    // GetMethod(V, P): null/undefined property values (either is absent) are treated as
    // "no method" (returns null); anything else must be callable when actually invoked.
    private static Object getMethod(JSEnvironment env, Object v, String property) {
    	Object method = RuntimeUtil.getProperty(env, v, property);
    	return RuntimeUtil.isNullOrUndefined(method) ? null : method;
    }

    // GetIterator(value): looks up and calls @@iterator, requiring an object result.
    public static Object getIterator(JSEnvironment env, Object value) {
    	Object method = env.getAccessor(value).getProperty(value, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
    	if(method==RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.isNullOrUndefined(method)) {
    		throw RuntimeUtil.typeError("Object is not iterable, {0}", objectTypeName(env,value));
    	}
    	Object iterator = RuntimeUtil.call(env, method, value, RuntimeUtil.EMPTY_PARAMS);
    	if(!RuntimeUtil.isObject(env, iterator)) {
    		throw RuntimeUtil.typeError("Result of the Symbol.iterator method is not an object");
    	}
    	return iterator;
    }

    // GetIterator(value, async): like getIterator() above, but for a `yield*`
    // delegation inside an async generator - prefers [Symbol.asyncIterator],
    // falling back to the plain sync [Symbol.iterator] result when absent
    // (mirrors valueIteratorAsync()'s own GetIterator(obj,async) fallback).
    // `fromSync` tells the caller whether the fallback was taken, so it
    // knows whether this delegate's step results need the
    // %AsyncFromSyncIteratorPrototype%-style eager done/value wrap (see
    // yieldStar_()'s own doc comment).
    private record IteratorLookup(Object iterator, boolean fromSync) {}

    private static IteratorLookup getIteratorPreferAsync(JSEnvironment env, Object value) {
    	Object method = env.getAccessor(value).getProperty(value, Symbol.ASYNC_ITERATOR, RuntimeUtil.NOT_AVAILABLE);
    	if(method==RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.isNullOrUndefined(method)) {
    		return new IteratorLookup(getIterator(env, value), true);
    	}
    	Object iterator = RuntimeUtil.call(env, method, value, RuntimeUtil.EMPTY_PARAMS);
    	if(!RuntimeUtil.isObject(env, iterator)) {
    		throw RuntimeUtil.typeError("Result of the Symbol.asyncIterator method is not an object");
    	}
    	return new IteratorLookup(iterator, false);
    }

    // IteratorStep: calls next() on the iterator, returning NOT_AVAILABLE once done is true,
    // else the step's value. Used where callers need to track [[Done]] across multiple steps
    // (destructuring's IteratorBindingInitialization), rather than a plain Java Iterator.
    public static Object iteratorStep(JSEnvironment env, Object iterator) {
    	Object nextMethod = RuntimeUtil.getProperty(env, iterator, "next");
    	Object stepResult = RuntimeUtil.call(env, nextMethod, iterator, RuntimeUtil.EMPTY_PARAMS);
    	if(!RuntimeUtil.isObject(env, stepResult)) {
    		throw RuntimeUtil.typeError("Iterator result {0} is not an object", stepResult);
    	}
    	boolean done = RuntimeUtil.toBoolean(env, RuntimeUtil.getProperty(env, stepResult, "done"));
    	if(done) {
    		return RuntimeUtil.NOT_AVAILABLE;
    	}
    	return RuntimeUtil.getProperty(env, stepResult, "value");
    }

    // IteratorClose: best-effort call to the iterator's "return" method, per spec called
    // when abandoning an iterator early without exhausting it (e.g. when yield*'s delegate
    // has no "throw" method and the delegation must be aborted, or array destructuring
    // doesn't consume the whole iterator).
    public static void iteratorClose(JSEnvironment env, Object iterator) {
    	Object returnMethod = getMethod(env, iterator, "return");
    	if(returnMethod!=null) {
    		Object innerResult = RuntimeUtil.call(env, returnMethod, iterator, RuntimeUtil.EMPTY_PARAMS);
    		if(!RuntimeUtil.isObject(env, innerResult)) {
    			throw RuntimeUtil.typeError("Iterator's return() result {0} is not an object", innerResult);
    		}
    	}
    }

    // Like iteratorClose(), but for a plain Java Iterator<?> (e.g. from
    // valueIterator()) rather than a JS-level object directly - unwraps
    // through BuiltinIteratorHelper/JavaIterator to reach the real
    // user-supplied JS iterator object that "return" needs to be called on
    // (the wrapper itself has no "return" property of its own). A plain
    // Java-backed iterator with no JS presence has nothing to close.
    public static void iteratorClose(JSEnvironment env, Iterator<?> it) {
    	if(it instanceof org.monflabs.galtajs.rt.builtins.standard.iterator.ExternallyCloseable ec) {
    		ec.closeAll(env);
    	} else if(it instanceof JavaIterator ji) {
    		iteratorClose(env, ji.getIteratorObject());
    	} else if(it instanceof BuiltinIterator bi) {
    		Iterator<Object> inner = bi.getIterator();
    		if(inner instanceof JavaIterator ji) {
    			iteratorClose(env, ji.getIteratorObject());
    		} else {
    			// BuiltinIterator itself implements Iterator<Object>, so an
    			// unqualified call here would resolve back to THIS overload
    			// (infinite recursion) instead of the JS-object one below -
    			// the cast forces the intended overload.
    			iteratorClose(env, (Object)bi);
    		}
    	} else if(it instanceof AsyncJavaIterator aji) {
    		// AsyncIteratorClose (7.4.14): best-effort call to the real async
    		// iterator's own "return" - not awaiting its (possibly-Promise)
    		// result is a known, narrower gap than the rest of this
    		// for-await-of support (see AsyncJavaIterator's own doc); the
    		// call itself still happens, which is what matters for the
    		// overwhelmingly common case of a synchronously-observable close.
    		iteratorClose(env, aji.getIteratorObject());
    	} else if(it instanceof AsyncFromSyncJavaIterator afsi) {
    		// Per spec 25.1.4.4's own note: closing an async-from-sync
    		// wrapper closes the underlying SYNC iterator - delegate back
    		// into this same dispatch for it.
    		iteratorClose(env, afsi.getInner());
    	}
    }

    // Like iteratorClose(Iterator), but swallows any secondary error from the
    // close itself - for use when closing on the way out of handling an
    // already-in-flight exception, so the ORIGINAL exception still propagates.
    public static void iteratorCloseQuietly(JSEnvironment env, Iterator<?> it) {
    	try {
    		iteratorClose(env, it);
    	} catch(Throwable ignore) {
    		rethrowIfUncatchable(ignore);
    	}
    }

    // Like iteratorClose(Object), but swallows any secondary error from the
    // close itself - see iteratorCloseQuietly(Iterator)'s own doc comment.
    public static void iteratorCloseQuietly(JSEnvironment env, Object iterator) {
    	try {
    		iteratorClose(env, iterator);
    	} catch(Throwable ignore) {
    		rethrowIfUncatchable(ignore);
    	}
    }

    public static Object await_(JSRuntimeContext context, Object o) {
		return context.getGlobalContext().getExecutor().await(o);
    }

    // await INSIDE AN ASYNC GENERATOR's own body - ASTAwait statically
    // determines (at init(), from the immediately-enclosing ASTFunctionDecl
    // - never a runtime "nearest" search, since a plain async function
    // NESTED inside a generator's body must keep using the ordinary
    // blocking await_() above for its OWN await expressions, not this one)
    // whether to call this instead. See AwaitYieldSignal's own doc comment
    // for the full deadlock this avoids and how BuiltinAsyncGeneratorPrototype
    // drives the result back asynchronously.
    public static Object awaitInGenerator_(JSRuntimeContext context, Object o) {
    	JSFunctionContext fc = context.getFunctionContext();
    	Yielder<Object> yielder = fc!=null ? fc.getYielder() : null;
    	if(yielder==null) {
    		// Unreachable given ASTAwait's own static check - defensive only.
    		throw RuntimeUtil.syntaxError("await must be used in a generator function");
    	}
    	return yielder.yield(new AwaitYieldSignal(o));
    }

    
	/////////////////////////////////////////////////////////////////////////
	// Exception Helpers
	/////////////////////////////////////////////////////////////////////////

	public static JSRuntimeException wrap(Object v) {
		if(v instanceof JSRuntimeException e) {
			return e;
		}
		// Reported by the class constructor's [[Construct]], never wrapped
		if(v instanceof ConstructResultError e) {
			throw e;
		}
		if(v instanceof OutOfMemoryError me) {
			return rangeError(me,"Java Exception: {0}",me.getLocalizedMessage());
		}
		if(v instanceof ArithmeticException me) {
			return rangeError(me,"Java Exception: {0}",me.getLocalizedMessage());
		}
		// A parse failure - e.g. an imported module's early error, surfacing
		// while a transpiled unit links its imports - is a SyntaxError, as
		// JSRuntimeException.exceptionObject() already reports it, not a
		// generic "Java Exception" Error.
		if(v instanceof org.monflabs.galtajs.JSParseException pe) {
			return syntaxError(pe,"{0}",pe.getLocalizedMessage());
		}
		if(v instanceof Throwable t) {
			return error(t,"Java Exception: {0}",t.getLocalizedMessage());
		}
		return JSRuntimeException.asJavascriptException(null,v);
	}

	public static JSRuntimeException error(String msg, Object...params) {
		return error(null,msg,params);
	}
	public static JSRuntimeException evalError(String msg, Object...params) {
		return evalError(null,msg,params);
	}
	public static JSRuntimeException rangeError(String msg, Object...params) {
		return rangeError(null,msg,params);
	}
	public static JSRuntimeException referenceError(String msg, Object...params) {
		return referenceError(null,msg,params);
	}

	// SuperReferences may never be deleted - used in transpiled code (as an
	// expression) where a Java `throw` statement isn't syntactically valid.
	public static Object throwSuperDeleteReferenceError() {
		throw referenceError("Unsupported reference to 'delete' super property");
	}

	// Annex B.1.2 sec-runtime-errors-for-function-call-assignment-targets: a
	// CallExpression is syntactically a valid (non-strict) assignment target
	// ("web-compat"), but its evaluation result is a Value, not a Reference,
	// so any actual assignment attempt is a runtime ReferenceError - mirrors
	// ASTBaseCall.evaluateAssign()'s identical interpreted-mode behavior
	// (evaluate the call for its side effect, discard the result, throw).
	// `evaluatedTarget` is the call's own already-transpiled expression,
	// passed as this method's argument so Java's own evaluation order runs
	// it BEFORE this method's throw - same "expression, not statement"
	// pattern as throwSuperDeleteReferenceError() above.
	public static Object invalidAssignmentTarget(Object evaluatedTarget) {
		throw referenceError("Invalid left-hand side in assignment");
	}

	// A named class's own ClassHeritage (`extends <expr>`) referencing the
	// class's OWN name (`class x extends x {}`) must ALWAYS throw - per
	// spec (14.7.14 ClassDefinitionEvaluation step 4-5) the class's own
	// inner binding is created, in the TDZ, before the heritage clause
	// evaluates, so a bare self-reference there always finds the TDZ
	// placeholder, regardless of whatever an OUTER, unrelated same-named
	// binding might independently hold (test262 language/statements/class/
	// name-binding/in-extends-expression-assigned.js: `var x = (class x
	// extends x {})` must throw even though an outer `var x` exists) - see
	// ASTClassDecl's transpiled heritage codegen, which special-cases
	// exactly this one unambiguous shape (a bare identifier equal to the
	// class's own name) as a narrow slice of the broader, NOT-yet-fixed
	// "a class's own name isn't bound in its own separate, immutable inner
	// scope" gap (KnownGaps.md) - every OTHER heritage expression shape
	// still falls through to that gap's existing (wrong, but unambiguously
	// scoped-out-of-here) behavior. Message matches checkTDZ()'s own
	// wording for consistency with every other TDZ violation.
	public static Object throwClassSelfHeritageReferenceError(String className) {
		throw referenceError("Cannot access '{0}' before initialization", className);
	}

	// UnaryExpression : delete UnaryExpression - when the operand isn't a Reference
	// (e.g. a function call, a literal), Java already evaluated it (as this method's
	// argument, for side effects/exceptions) by the time this is called; delete
	// trivially succeeds.
	public static boolean deleteNonReference(Object evaluatedValue) {
		return true;
	}
	public static JSRuntimeException syntaxError(String msg, Object...params) {
		return syntaxError(null,msg,params);
	}
	public static JSRuntimeException typeError(String msg, Object...params) {
		return typeError(null,msg,params);
	}
	public static JSRuntimeException uriError(String msg, Object...params) {
		return uriError(null,msg,params);
	}
	public static JSRuntimeException aggregateError(String msg, Object...params) {
		return aggregateError(null,msg,params);
	}

	public static JSRuntimeException error(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.Error.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, cause);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException evalError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.EvalError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException rangeError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.RangeError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException referenceError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.ReferenceError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException syntaxError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.SyntaxError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException typeError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.TypeError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException uriError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.URIError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	public static JSRuntimeException aggregateError(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		JSObject jsException = (JSObject)JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.AggregateError.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		if(cause!=null) {
			jsException.setOwnProperty(Error.JAVA_EXCEPTION, jsException);
		}
		return JSRuntimeException.asJavascriptException(cause,jsException);
	}
	
	
	public static JSRuntimeException unary(String op, Object p1) {
		return typeError("Cannot execute operation {0} on parameter {1}",op,errParam(p1));
	}
	public static JSRuntimeException binary(String op, Object p1, Object p2) {
		return typeError("Cannot execute operation {0} on parameters {1} and {2}",op,errParam(p1),errParam(p2));
	}
	public static JSRuntimeException object(String name) {
		return typeError("Cannot find object {0}",name);
	}
//	public final JSRuntimeException member(Exception ex, Object instance, String memberName) {
//		return JSRuntimeException.jsRuntimeException(ex,"Cannot access member {0} of value {1}",memberName,errParam(instance));
//	}
	public static JSRuntimeException index(Object instance, int index) {
		return rangeError("Cannot access indexed value {0} of value {1}",index,errParam(instance));
	}
	
	public static JSRuntimeException notImplemented() {
		return error("Not implemented for now...");
	}
	public static JSRuntimeException illegalState() {
		return error("Illegal state");
	}
	public static JSRuntimeException deprecated(String name) {
		return error("{0} is deprecated and is not available in GaltaJS",name);
	}
	public static JSRuntimeException notAvailable(String name) {
		return error("{0} is not yet available in GaltaJS",name);
	}
	
	public static JSRuntimeUncatchableException uncatchable(String msg, Object...params) {
		return uncatchable(null,msg,params);
	}
	public static JSRuntimeUncatchableException uncatchable(Throwable cause, String msg, Object...params) {
		String m = StringFormat.format(msg,params);
		Object jsException = JSEnvironment.getEnvironment().getStandardObjects().getConstructor(org.monflabs.galtajs.rt.builtins.errors.Error.ConstructorImpl.CLASSNAME).constructObject(new Object[] {m});
		return JSRuntimeUncatchableException.asJavascriptException(cause,jsException);
	}


	protected static String errParam(Object o) {
		if(o!=null) {
			String s = o.toString();
			if(s.length()>32) {
				s = s.substring(0,30)+"...";
			}
			String type;
			if( o instanceof Integer ) {
				type = "Integer";
			} else if( o instanceof Number) {
				type = "Number";
			} else if( o instanceof CharSequence) {
				type = "String";
			} else if( o instanceof Boolean) {
				type = "Boolean";
			} else if( o instanceof Date) {
				type = "Date";
			} else {
				type = o.getClass().toString();
			}
			return s + "[" + type + "]";
		}
		return "null";
	}

	
	//
	// Utilities
	//
	
	public static String objectTypeName(Object o) {
		return objectTypeName(JSEnvironment.getEnvironmentUnchecked(), o);
	}
	public static String objectTypeName(JSEnvironment env,Object o) {
		if(o==null) {
			return "null";
		}
		if(o==UNDEFINED) {
			return "undefined";
		}
		if(o==NOT_AVAILABLE) {
			return "<Internal Error: not available value>";
		}
		if(o instanceof CharSequence) {
			if(env!=null && RuntimeUtil.isBoxedString(env, o)) {
				return "String Object";
			}
			return "String";
		}
		if(o instanceof Boolean) {
			if(env!=null && RuntimeUtil.isBoxedBoolean(env, o)) {
				return "Boolean Object";
			}
			return "Boolean";
		}
		if(o instanceof Number) {
			if(env!=null && RuntimeUtil.isBoxedNumber(env, o)) {
				return "Number Object";
			}
			return "Number";
		}

		if(env!=null) {
			try {
				JSAccessor acc = env.getAccessor(o);
				
				Object toStringTag = acc.getProperty(o, Symbol.TO_STRING_TAG, RuntimeUtil.NOT_AVAILABLE);
				if(toStringTag!=RuntimeUtil.NOT_AVAILABLE) {
					return RuntimeUtil.toString(env,toStringTag);
				}
				
				Object ctor = acc.getProperty(o, Constructor.CONSTRUCTOR, null);
				if(RuntimeUtil.isObject(env, ctor)) {
					JSAccessor acc2 = env.getAccessor(ctor);
					Object name = acc2.getProperty(ctor, "name", null);
					if(name instanceof String s) {
						return s;
					}
				}
				
				Object p = o;
				do {
					String className = acc.getClassName(p);
					if(className!=null) {
						return className;
					}
					p = acc.getPrototype(p);
					if(p==null) {
						break;
					}
				} while(true);
			} catch(Throwable t) {}
		}
		
		if(o instanceof JSObject jo) {
			return jo.getClassName();
		}

		return o.getClass().getName();
	}
	
	
	
	
	
	//
	//
	// RuntimeContextUtil
	//
	//
	
	public static Object getIdentifierValue(JSRuntimeContext context, String name, boolean throwError) {
		// Note: this includes the with context as well
		Object var = context.getVariableValue(name,RuntimeUtil.NOT_AVAILABLE);
		if(var!=RuntimeUtil.NOT_AVAILABLE) {
			return var;
		}
		var = context.getGlobalContext().getGlobalThis().getProperty(name,RuntimeUtil.NOT_AVAILABLE);
		if(var!=RuntimeUtil.NOT_AVAILABLE) {
			return var;
		}
		if(throwError) {
			throw RuntimeUtil.referenceError("Unknown identifier {0}",name);
		}
		return RuntimeUtil.NOT_AVAILABLE;
	}

	// Assigns to an identifier binding, auto-creating it as a global if it
	// doesn't already resolve to one (sloppy-mode `x = value` semantics) - shared
	// by ASTIdentifier.evaluateAssign()'s simple-assignment case and destructuring
	// assignment targets (which are always simple assignments, never
	// read-modify-write). Doesn't replicate every nuance of evaluateAssign() (no
	// eval-context parent lookup) - that remains the caller's responsibility
	// where it applies.
	public static void assignIdentifierOrCreateGlobal(JSRuntimeContext context, String name, Object value) {
		org.monflabs.galtajs.rt.transpiler.VarAccessor e = context.getVariableEntry(name);
		if(e==null) {
			JSEnvironment env = context.getEnvironment();
			var globalThis = context.getGlobalContext().getGlobalThis();
			boolean exists = globalThis.hasProperty(name);
			if(!exists) {
				exists = RuntimeUtil.getIdentifierValue(context, name, false)!=RuntimeUtil.NOT_AVAILABLE;
			}
			boolean autoCreate = (!context.isStrictMode() && !env.mustDeclareAllVariables()) || exists;
			e = globalThis.getOwnVariableAccessor(name, autoCreate);
			if(e==null) {
				throw RuntimeUtil.referenceError("{0} is not defined", name);
			}
		}
		// TDZ (ReferenceError) takes precedence over the const/using
		// reassignment check below (TypeError), same ordering as
		// ASTIdentifier.evaluateAssign().
		if((e.getType()==VAR_TYPE.LET || e.getType()==VAR_TYPE.CONST || e.getType()==VAR_TYPE.USING) && e.getValue()==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		// A destructuring assignment target (`({a: c} = ...)`) rejecting a
		// `const` binding is a genuine RUNTIME check (SetMutableBinding on
		// an immutable binding throws TypeError) - unlike a PLAIN identifier
		// target, where an obviously-same-scope const violation is instead
		// a STATIC early SyntaxError (ASTIdentifier.evaluateAssign()'s own
		// check, a different code path entirely: a destructuring pattern
		// isn't subject to that same early-error static analysis, per
		// test262's array-elem-put-const.js et al. expecting TypeError).
		// `using` is immutable the same way, but has no static-early-error
		// path at all (ASTIdentifier.evaluateAssign() throws TypeError for
		// it too, not SyntaxError - see there), so this check applies
		// identically to both a plain identifier and a destructuring target.
		if(e.getType()==VAR_TYPE.CONST || e.getType()==VAR_TYPE.USING) {
			throw RuntimeUtil.typeError("Assignment to constant variable {0}", name);
		}
		e.setValue(value);
	}

	// Used by the transpiler's destructuring-leaf assignment codegen
	// (ASTIdentifier.getIdentifierWriteAccessor) for a compile-time-known
	// const local: a destructuring-assignment target rejecting `const` is a
	// genuine RUNTIME check - unlike a plain identifier target's own
	// same-scope violation, which is instead caught earlier as a static
	// SyntaxError by ASTAbstractAssign.init() (a check that never sees a
	// destructuring leaf, since it only looks at ASTAssign's left node being
	// a bare ASTIdentifier - see test262's array-elem-put-const.js et al.).
	// discardedValue is still evaluated as a Java method argument (for its
	// own side effects, matching spec's evaluate-RHS-before-PutValue order)
	// even though it's never used, since this unconditionally throws.
	public static Object throwAssignmentToConstant(Object discardedValue, String name) {
		throw RuntimeUtil.typeError("Assignment to constant variable {0}", name);
	}

	// Used by ASTIdentifier.getIdentifierWriteAccessor for a sloppy-mode
	// assignment to a named function expression's own (VAR_TYPE.FUNCTION_SELF)
	// self-reference binding: the store is a silent no-op (spec: the binding
	// is immutable but this isn't a SyntaxError), yet the assignment
	// EXPRESSION itself must still evaluate to the assigned value. A bare
	// "{0} = value" can't be emitted (nothing to assign into) and returning
	// the raw value expression string directly isn't safe when the
	// assignment is itself a top-level ExpressionStatement (some value
	// shapes, e.g. a literal, aren't valid Java statements on their own) -
	// wrapping in this identity call keeps it a valid statement-expression
	// either way.
	public static Object identity(Object value) {
		return value;
	}


	
	//
	// [[GetPrototypeOf]]
	//
	public static Object getPrototype(JSEnvironment env, Object instance) {
		if(instance instanceof JSObject jo) {
			return jo.getPrototype();
		}
		JSAccessor acc = env.getAccessor(instance);
		return acc.getPrototype(instance); 
	}
	public static boolean setPrototype(JSEnvironment env, Object instance, Object proto) {
		if(RuntimeUtil.isPrimitiveValue(env,instance)) {
			throw RuntimeUtil.typeError("Value is not an Object");
		}
		if(proto!=null && RuntimeUtil.isPrimitiveValue(env,proto)) {
			throw RuntimeUtil.typeError("Prototype {0} is not an object", RuntimeUtil.objectTypeName(env,proto));
		}
		if(instance instanceof JSObject jo) {
			return jo.setPrototype(proto);
		}
		JSAccessor acc = env.getAccessor(instance);
		return acc.setPrototype(instance,proto);
	}

	// GetPrototypeFromConstructor(newTarget, intrinsicDefaultProto): reads
	// newTarget's own "prototype" property, falling back to the caller-supplied
	// intrinsic default when it isn't an Object (e.g. it was deleted or
	// overwritten with a primitive).
	public static Object getPrototypeFromConstructor(JSEnvironment env, Constructor newTarget, Object intrinsicDefaultProto) {
		Object proto = RuntimeUtil.getProperty(env, newTarget, Constructor.PROTOTYPE);
		if(!RuntimeUtil.isObject(env, proto)) {
			// GetFunctionRealm(newTarget): if newTarget genuinely belongs to
			// a DIFFERENT JSEnvironment (a real second realm, e.g. via
			// $262.createRealm() - see js-test-test262's Test262TestLibrary)
			// the fallback default prototype must be THAT realm's own
			// version, not the calling environment's `intrinsicDefaultProto`
			// (which is always pre-resolved against the ambient `env` by
			// every caller). Every builtin prototype singleton in this
			// codebase already registers itself via `env.registerPrototype
			// (SomeBuiltinXxxPrototype.class, instance)` (see e.g.
			// BuiltinWeakRefPrototype.get()) - reuse that SAME registry,
			// keyed off intrinsicDefaultProto's own concrete class, to find
			// the equivalent instance in newTarget's own environment,
			// entirely generically (no per-constructor plumbing needed).
			// Falls back to the old (single-realm) behavior whenever that
			// environment doesn't have a same-class prototype registered.
			JSEnvironment realm = getFunctionRealm(env, newTarget);
			if(realm!=env) {
				Object otherRealmProto = realm.getRegisteredPrototype(intrinsicDefaultProto.getClass());
				if(otherRealmProto==null) {
					otherRealmProto = createIntrinsic(realm, intrinsicDefaultProto.getClass());
				}
				if(otherRealmProto!=null) {
					return otherRealmProto;
				}
			}
			return intrinsicDefaultProto;
		}
		return proto;
	}

	// GetFunctionRealm(obj): a bound function's realm is its target's, a
	// Proxy's is its target's (a revoked one throws TypeError, per spec), an
	// ordinary function/object's is the environment it was created in;
	// anything else resolves to the ambient environment.
	// The intrinsic prototype of class clazz in realm, created there when the
	// realm has not used it yet: every intrinsic prototype class has a
	// static get(JSEnvironment) factory
	private static Object createIntrinsic(JSEnvironment realm, Class<?> clazz) {
		try {
			return clazz.getMethod("get", JSEnvironment.class).invoke(null, realm);
		} catch(ReflectiveOperationException e) {
			return null;
		}
	}

	public static JSEnvironment getFunctionRealm(JSEnvironment env, Object fn) {
		while(true) {
			if(fn instanceof org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionBind bound) {
				fn = bound.getBoundedCallable();
			} else if(fn instanceof BuiltinProxy proxy) {
				if(proxy.isRevoked()) {
					throw RuntimeUtil.typeError("Cannot access a revoked proxy");
				}
				fn = proxy.getTarget();
			} else if(fn instanceof JSObjectImpl jsi && jsi.getEnvironment()!=null) {
				return jsi.getEnvironment();
			} else {
				return env;
			}
		}
	}


	public static Object getProperty(JSEnvironment env, Object instance, Object memberName, boolean nullop) {
		if(instance==null && nullop) {
			return RuntimeUtil.UNDEFINED;
		}
		return getProperty(env, instance, memberName, RuntimeUtil.UNDEFINED);
	}
	public static Object getProperty(JSEnvironment env, Object instance, Function<Object,Object> memberFunc) {
		return getProperty(env, instance, memberFunc.apply(instance),RuntimeUtil.UNDEFINED);
	}
	public static Object getProperty(JSEnvironment env, Object instance, Function<Object,Object> memberFunc, boolean nullop) {
		if(instance==null && nullop) {
			return RuntimeUtil.UNDEFINED;
		}
		return getProperty(env, instance, memberFunc.apply(instance), RuntimeUtil.UNDEFINED);
	}

	
	//
	// [[Get]]
	//
	
	// TODO: remove getPropertyUnavailable
	public static Object getPropertyUnavailable(JSEnvironment env, Object instance, String memberName) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,RuntimeUtil.NOT_AVAILABLE);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,RuntimeUtil.NOT_AVAILABLE);
	}
	public static Object getPropertyUnavailable(JSEnvironment env, Object instance, Object member) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(member,RuntimeUtil.NOT_AVAILABLE);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,member,RuntimeUtil.NOT_AVAILABLE);
	}
	
	public static Object getProperty(JSEnvironment env, Object instance, String memberName) {
		// TRANSPILED-mode fallback only: interpreted mode resolves a private
		// access through ASTMember's own PrivateName-based path (see
		// getPrivateField below), which never reaches this string-keyed
		// method at all. Transpiled code still represents private members as
		// ordinary "#name"-keyed properties (a real, if narrower, gap -
		// see KnownGaps.md's "Private fields" entry) - brand checking (an
		// object must have been constructed by a class declaring #name to
		// access it) is enforced here for that path: reading a private
		// member that isn't present on the instance is a TypeError, not
		// undefined, per spec (PrivateFieldGet/PrivateMethodOrAccessorGet).
		if(isPrivateMemberName(memberName)) {
			Object v = getPropertyUnavailable(env, instance, memberName);
			if(v==RuntimeUtil.NOT_AVAILABLE) {
				throw RuntimeUtil.typeError("Cannot read private member {0} from an object whose class did not declare it", memberName);
			}
			return v;
		}
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,RuntimeUtil.UNDEFINED);
	}

	// Public: also used by transpiled-mode codegen (ASTCall) to decide, at
	// transpile time, whether a member-call target needs the PrivateName-
	// resolving privateInvokeMethod path instead of the ordinary one.
	public static boolean isPrivateMemberName(String memberName) {
		return memberName.length()>0 && memberName.charAt(0)=='#';
	}

	// Interpreted-mode private member access (PrivateFieldGet/
	// PrivateMethodOrAccessorGet/PrivateFieldSet/PrivateMethodOrAccessorSet/
	// HasPrivateElement) - called by ASTMember/ASTIn once a #name reference
	// has already been resolved (via JSRuntimeContext.resolvePrivateName) to
	// its per-class-evaluation PrivateName token. Deliberately bypasses
	// JSAccessor/ProxyAccessor entirely: private access never triggers a
	// Proxy trap, never walks the prototype chain, and is never affected by
	// [[Extensible]]/frozen/sealed state, per spec.
	public static Object getPrivateField(Object instance, PrivateName name) {
		// PrivateElementsHolder, not JSObjectImpl specifically: a Proxy can
		// itself become `this` for a class construction (a base constructor
		// returning `new Proxy(this, {...})`), and private access on it must
		// work directly against the Proxy's own storage, per spec never
		// going through any of its trap handlers.
		if(!(instance instanceof PrivateElementsHolder holder)) {
			throw RuntimeUtil.typeError("Cannot read private member {0} from a non-object value", name);
		}
		Object v = holder.getPrivateElementValue(name);
		if(v==RuntimeUtil.NOT_AVAILABLE) {
			throw RuntimeUtil.typeError("Cannot read private member {0} from an object whose class did not declare it", name);
		}
		return v;
	}
	public static boolean setPrivateField(Object instance, PrivateName name, Object value) {
		if(!(instance instanceof PrivateElementsHolder holder) || !holder.setPrivateElementValue(name, value)) {
			throw RuntimeUtil.typeError("Cannot write private member {0} to an object whose class did not declare it", name);
		}
		return true;
	}
	public static boolean hasPrivateField(Object instance, PrivateName name) {
		return instance instanceof PrivateElementsHolder holder && holder.hasPrivateElement(name);
	}

	public static Object getProperty(JSEnvironment env, Object instance, long index) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(index,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,index, RuntimeUtil.UNDEFINED);
	}
	public static Object getProperty(JSEnvironment env, Object instance, Symbol symbol) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(symbol,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,symbol, RuntimeUtil.UNDEFINED);
	}
	public static Object getProperty(JSEnvironment relm, Object instance, Object member) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(member,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = relm.getAccessor(instance);
		return accessor.getProperty(instance,member,RuntimeUtil.UNDEFINED);
	}

	// Receiver-aware [[Get]]: see JSAccessor.getProperty(...,receiver) - needed for
	// SuperProperty access, where the search starts at the home object's prototype
	// but `this` for an invoked getter must remain the actual `this`.
	public static Object getPropertyWithReceiver(JSEnvironment env, Object instance, Object member, Object receiver) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(member,RuntimeUtil.UNDEFINED,receiver);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,member,RuntimeUtil.UNDEFINED,receiver);
	}

	// ToPropertyKey: a String/Number/Symbol key is used as-is, anything else (e.g. an
	// object with a custom toString()) is converted to a string. Both getProperty and
	// setProperty do this same conversion internally on every call; callers doing a
	// read-modify-write on the same key (increment/decrement, compound assignment)
	// must convert once and reuse the result, or a side-effecting key gets evaluated
	// twice.
	public static Object toPropertyKey(JSEnvironment env, Object member) {
		if(member instanceof String || member instanceof Number || member instanceof Symbol) {
			// Fast path for the overwhelmingly common case - but a BOXED wrapper of one
			// of these types (e.g. `obj[Object(Symbol())]`) must still go through
			// ToPrimitive first per spec (ToPropertyKey: 1. key = ToPrimitive(argument,
			// string); 2. if Symbol, return key; 3. return ToString(key)) - it isn't
			// automatically usable as-is just because its underlying Java type matches.
			if(!hasPropertyMap(env, member)) {
				return member;
			}
		}
		Object key = toPrimitive(env, member, HINT.STRING);
		if(key instanceof Symbol) {
			return key;
		}
		return RuntimeUtil.toString(env, key);
	}

	// Like toPropertyKey(), but for a COMPUTED member/array-member compound
	// assignment target (base[key] OP= rhs): checks base for null/undefined
	// FIRST, before coercing key - matching ASTArrayMember.coerceKey()'s
	// explicit two-step order on the interpreter side (spec 6.2.4.6 GetValue
	// step 5a runs before ToPropertyKey is ever reached for a still-null
	// base). Plain toPropertyKey()/getProperty()'s generic dispatcher does
	// the opposite (coerces key first, checks base only once it dispatches
	// on the coerced key's type) - correct for a plain read, but wrong for
	// compound assignment, where a side-effecting key expression's own
	// coercion (e.g. a custom toString()) must never run before the
	// null-base TypeError is thrown (test262 S11.13.2_A7.*_T3/T4).
	public static Object toPropertyKeyChecked(JSEnvironment env, Object leftValue, Object member) {
		if(leftValue==null || leftValue==UNDEFINED) {
			throw RuntimeUtil.typeError("Left part of index is null, {0}", leftValue);
		}
		return toPropertyKey(env, member);
	}

	// Like toPropertyKeyChecked(), but for a STATIC member-name target
	// (base.name) - there's no key to coerce, just the null/undefined base
	// check, but it must still happen at the point the target's own
	// reference is RESOLVED, not deferred to whatever eventually reads/
	// writes through it - see ASTArrayLiteral's iterator-assign codegen,
	// which resolves a destructuring element's target reference before
	// stepping its source iterator (test262 dstr/array-elem-iter-thrw-
	// close.js and neighbors). Mirrors ASTMember.resolveReference()'s
	// identical immediate check/message on the interpreter side.
	public static Object requireNonNullMemberBase(Object base, String memberName) {
		if(base==null || base==UNDEFINED) {
			throw RuntimeUtil.typeError("Left part of member {0} is null or undefined", memberName);
		}
		return base;
	}

	// Like toPropertyKey(), but the result is always a Symbol or a String,
	// never left as a bare Number/Boolean primitive - needed wherever the
	// result is later compared via plain .equals() against property keys
	// already stored as Strings (e.g. a destructuring rest element's
	// "already consumed" exclusion set, matched against ownStringEntries()'s
	// String keys). toPropertyKey()'s fast path above deliberately leaves a
	// bare Number/Boolean as-is for the common property-READ case, where
	// getProperty()'s own internal coercion already handles it correctly -
	// this is for the narrower case where no such implicit coercion happens.
	public static Object toPropertyKeyString(JSEnvironment env, Object member) {
		// Spec ToPropertyKey: "key = ToPrimitive(argument, string); if Type(key)
		// is Symbol return key; else return ToString(key)" - this was missing
		// the ToPrimitive step entirely, checking `instanceof Symbol` on the
		// ORIGINAL argument rather than on ToPrimitive's result. An object
		// with a Symbol.toPrimitive/toString/valueOf that returns a genuine
		// Symbol must use THAT Symbol as the key, not attempt to stringify it
		// (confirmed via hasOwnProperty/symbol_property_toPrimitive.js and its
		// toString/valueOf/propertyIsEnumerable siblings).
		if(member instanceof Symbol) {
			return member;
		}
		Object key = toPrimitive(env, member, HINT.STRING);
		if(key instanceof Symbol) {
			return key;
		}
		return RuntimeUtil.toString(env, key);
	}


	public static Object getProperty(JSEnvironment env, Object instance, String memberName, Object defaultValue) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,defaultValue);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,defaultValue);
	}
	public static Object getProperty(JSEnvironment env, Object instance, long index, Object defaultValue) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(index,defaultValue);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,index, defaultValue);
	}
	public static Object getProperty(JSEnvironment env, Object instance, Symbol symbol, Object defaultValue) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(symbol,defaultValue);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,symbol, defaultValue);
	}
	public static Object getProperty(JSEnvironment env, Object instance, Object member, Object defaultValue) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(member,defaultValue);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,member,defaultValue);
	}

	
	//
	// [[Set]]
	//
	public static boolean setProperty(JSEnvironment env, Object instance, Object member, Object value, DESC_CHECK check) {
		if(value instanceof ConsWrapper<?>) {
			value = ConsWrapper.unwrap(value);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.setProperty(instance, member, value, null, check);
	}
	
	public static boolean setProperty(JSEnvironment env, Object instance, Object member, Object value) {
		if(value instanceof ConsWrapper<?>) {
			value = ConsWrapper.unwrap(value);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.setProperty(instance, member, value, null, DESC_CHECK.CHECK);
	}

	// Receiver-aware [[Set]]: see JSAccessor.setProperty(...,receiver) - needed for
	// SuperProperty assignment (e.g. super.x = v), where the search for an existing
	// property starts at the home object's prototype but the actual write (when
	// nothing is found, or an inherited setter is invoked) targets the real `this`.
	public static boolean setPropertyWithReceiver(JSEnvironment env, Object instance, Object member, Object value, Object receiver) {
		if(value instanceof ConsWrapper<?>) {
			value = ConsWrapper.unwrap(value);
		}
		if(instance instanceof JSObject jo) {
			return jo.setProperty(member, value, null, DESC_CHECK.CHECK, receiver);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.setProperty(instance, member, value, null, DESC_CHECK.CHECK, receiver);
	}
	public static boolean setProperty(JSEnvironment env, Object instance, String memberName, Object value) {
		if(value instanceof ConsWrapper<?>) {
			value = ConsWrapper.unwrap(value);
		}
		// TRANSPILED-mode fallback only - see getProperty(env,instance,String)
		// above. Brand check on write too: a private field's value can be
		// changed, but the field itself is never created by assignment
		// (unlike a dynamic public property).
		if(isPrivateMemberName(memberName) && getPropertyUnavailable(env, instance, memberName)==RuntimeUtil.NOT_AVAILABLE) {
			throw RuntimeUtil.typeError("Cannot write private member {0} to an object whose class did not declare it", memberName);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.setProperty(instance, memberName, value, null, DESC_CHECK.CHECK);
	}
	public static boolean setProperty(JSEnvironment env, Object instance, long index, Object value) {
		if(value instanceof ConsWrapper<?>) {
			value = ConsWrapper.unwrap(value);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.setProperty(instance, index, value, null, DESC_CHECK.CHECK);
	}
	public static boolean setProperty(JSEnvironment env, Object instance, Symbol symbol, Object value) {
		if(value instanceof ConsWrapper<?>) {
			value = ConsWrapper.unwrap(value);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.setProperty(instance, symbol, value, null, DESC_CHECK.CHECK);
	}
	
	
	public static boolean deleteProperty(JSEnvironment env, Object instance, Object member) {
		if(instance instanceof JSObject jo) {
			return jo.deleteProperty(member);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.deleteProperty(instance, member, DESC_CHECK.CHECK);
	}
	// Reflect.deleteProperty's own contract: always report a boolean, NEVER
	// throw - unlike the plain `delete` operator (above), whose throw-in-
	// strict-mode behavior is intentionally tied to the CALLING code's own
	// strict-mode-ness via DESC_CHECK.CHECK. Reflect.deleteProperty must not
	// inherit that from whatever context happens to be calling it.
	public static boolean deleteProperty(JSEnvironment env, Object instance, Object member, DESC_CHECK check) {
		if(instance instanceof JSObject jo) {
			return jo.deleteProperty(member, check);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.deleteProperty(instance, member, check);
	}
	public static boolean deleteProperty(JSEnvironment env, Object instance, String memberName) {
		if(instance instanceof JSObject jo) {
			return jo.deleteProperty(memberName);
		}
		if(instance instanceof JSRuntimeContext thisContext) {
			if(thisContext.deleteVariable(memberName)) {
				return true;
			}
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.deleteProperty(instance, memberName, DESC_CHECK.CHECK);
	}
	public static boolean deleteProperty(JSEnvironment env, Object instance, long index) {
		if(instance instanceof JSObject jo) {
			return jo.deleteProperty(index);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.deleteProperty(instance, index, DESC_CHECK.CHECK);
	}
	public static boolean deleteProperty(JSEnvironment env, Object instance, Symbol symbol) {
		if(instance instanceof JSObject jo) {
			return jo.deleteProperty(symbol);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.deleteProperty(instance, symbol, DESC_CHECK.CHECK);
	}	

	
	public static PropertyDescriptor getPropertyDescriptor(JSEnvironment env, Object instance, Object member) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getPropertyDescriptor(instance,member);
	}
	public static PropertyDescriptor getPropertyDescriptor(JSEnvironment env, Object instance, String memberName) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getPropertyDescriptor(instance,memberName);
	}
	public static PropertyDescriptor getPropertyDescriptor(JSEnvironment env, Object instance, long index) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getPropertyDescriptor(instance,index);
	}
	public static PropertyDescriptor getPropertyDescriptor(JSEnvironment env, Object instance, Symbol symbol) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getPropertyDescriptor(instance,symbol);
	}
	
	public static PropertyDescriptor getOwnPropertyDescriptor(JSEnvironment env, Object instance, Object member) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getOwnPropertyDescriptor(instance,member);
	}
	public static PropertyDescriptor getOwnPropertyDescriptor(JSEnvironment env, Object instance, String memberName) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getOwnPropertyDescriptor(instance,memberName);
	}
	public static PropertyDescriptor getOwnPropertyDescriptor(JSEnvironment env, Object instance, long index) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getOwnPropertyDescriptor(instance,index);
	}
	public static PropertyDescriptor getOwnPropertyDescriptor(JSEnvironment env, Object instance, Symbol symbol) {
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getOwnPropertyDescriptor(instance,symbol);
	}

	public static boolean hasProperty(JSEnvironment env, Object instance, String member) {
		if(instance instanceof JSObject jo) {
			return jo.hasProperty(member);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.hasProperty(instance,member);
	}
	public static boolean hasProperty(JSEnvironment env, Object instance, long index) {
		if(instance instanceof JSObject jo) {
			return jo.hasProperty(index);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.hasProperty(instance,index);
	}
	public static boolean hasProperty(JSEnvironment env, Object instance, Symbol symbol) {
		if(instance instanceof JSObject jo) {
			return jo.hasProperty(symbol);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.hasProperty(instance,symbol);
	}
	public static boolean hasProperty(JSEnvironment env, Object instance, Object member) {
		if(instance instanceof JSObject jo) {
			return jo.hasProperty(member);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.hasProperty(instance,member);
	}

	
	// The third argument is to handle these use cases:
	//   Array.toSorted() first calls ToObject(), so primitive are converted
	//   Function.aplly() does not
	public static JSArray getArrayLike(JSEnvironment env, Object _this) {
		return getArrayLike(env, _this, false);
	}
	public static JSArray getArrayLike(JSEnvironment env, Object _this, boolean objectOnly) {
		if(RuntimeUtil.isNullOrUndefined(_this)) {
			throw RuntimeUtil.typeError("Cannot convert undefined or null to array like");
		} 
		JSArray a = getArrayLikeUnchecked(env,_this,objectOnly);
		if(a==null) {
			throw RuntimeUtil.typeError("Object '{0}' is not an array", objectTypeName(_this));
		}
		return a;
	}
	public static JSArray getArrayLikeUnchecked(JSEnvironment env, Object _this, boolean objectOnly) {
		// Spec: array-like access on a Proxy must dispatch through its
		// traps (Get/Set/Has/Delete) for every element and for "length" -
		// unwrapping straight to p.getTarget() (as this used to do) reads
		// the target's real backing storage directly, silently bypassing
		// the traps entirely (confirmed via slice/create-proxied-array-
		// invalid-len.js and its various proxy-access-count.js/
		// property-traps-order-with-species.js siblings, where a length
		// getter trap returning a poisoned value must be observed, and a
		// set trap's call count must be provably zero when a RangeError
		// aborts before element copying starts). A BuiltinProxy falls
		// through the instanceof chain below to the generic
		// JSAccessor-dispatching JSArrayAccessor branch on its own, since
		// it implements neither JSArray, Arguments, CharSequence, a Java
		// array, List, nor JSObject.
		if(RuntimeUtil.isObject(env, _this)) { // Only objects, including string objects
			if(_this instanceof JSArray a) {
				return a;
			}
			if(_this instanceof Arguments args) { 
				return JSArrayArguments.of(args);
			}
			if(_this instanceof CharSequence) {
				return JSArrayString.of(env,_this.toString());
			}
			if(_this.getClass().isArray()) {
				return JSArrayJavaArray.of(env,_this);
			}
			if (_this instanceof List<?> list) {
				return JSArrayList.of(env,list);
			}
			if(_this instanceof JSObject o) {
				// It is a potential arrey
				return JSArrayJSObject.of(o);
			}
			JSAccessor accessor = env.getAccessor(_this);
			// It is a potential arrey
			return JSArrayAccessor.of(accessor,_this);
		}
		if(_this instanceof CharSequence && !objectOnly) {
			return JSArrayString.of(env,_this.toString());
		}
		return null;
	}
	
	public static SetLike getSetLike(JSEnvironment env, Object _this) {
		if(RuntimeUtil.isNullOrUndefined(_this)) {
			throw RuntimeUtil.typeError("Cannot convert undefined or null to set like");
		} 
		if(_this instanceof BuiltinProxy p) {
			_this = p.getTarget();
		}
		if(_this instanceof SetLike set) {
			return set;
		}
		if(_this instanceof Set<?> set) {
			return JSSetJavaSet.of(env, set);
		}
		
		JSAccessor accessor = env.getAccessor(_this);
		return JSSetJSObject.of(env,accessor,_this);
	}


	public static Object call(JSEnvironment env, Object function, Object _this, Object[] parameters) {
		if(function instanceof Callable c) {
			return c.call(_this, parameters);
		}
		throw RuntimeUtil.typeError("Object is not a callable, {0}", RuntimeUtil.objectTypeName(env,function));
	}
	public static Object constructObject(JSEnvironment env, Object ctor, Object[] parameters) {
		if(ctor instanceof Constructor c) {
			return c.constructObject(parameters);
		}
		throw RuntimeUtil.typeError("Object is not a constructor, {0}", RuntimeUtil.objectTypeName(env,ctor));
	}
	public static Object constructArray(JSEnvironment env, Object ctor, int dimensions, int size) {
		if(ctor instanceof Constructor c) {
			return c.constructArray(dimensions, size);
		}
		throw RuntimeUtil.typeError("Object is not an array constructor, {0}", RuntimeUtil.objectTypeName(env,ctor));
	}

	// String as indexes
	public static long memberIndex(String member) {
		int len = member.length();
		if(len>0) {
		    char c = member.charAt(0);
        	if(c>='0' && c<='9') {
				if(len==1) {
					return c-'0';
			    }
				if(c!='0') {
					long result = c - '0';
			        for (int i = 1; i<len; i++) {
			        	c = member.charAt(i);
			        	if(c>='0' && c<='9') {
			        		result = result * 10 + (c- '0');
			        	} else {
			        		return Long.MIN_VALUE;
			        	}
			        }
			        return result<=SparseList.MAX_INDEX ? result : Long.MIN_VALUE;
				}
			}
		}
		return Long.MIN_VALUE;
	}
	public static String memberIndex(long index) {
		return NumberFormatting.numberToString((double)index);
	}
	public static boolean isMemberIndex(long index) {
		return index>=0 && index<=SparseList.MAX_INDEX;
	}
	public static boolean isMemberIndex(String member) {
		return memberIndex(member)!=Long.MIN_VALUE;
	}

	// Spec 7.1.22 CanonicalNumericIndexString(argument): the numeric value a
	// string round-trips to via ToNumber<->NumberToString, or null if it
	// doesn't round-trip at all - used by Integer-Indexed Exotic Object
	// (TypedArray) internal methods. Deliberately NOT memberIndex()/
	// isMemberIndex() (array-index-only: non-negative integers, no leading
	// zero, capped at SparseList.MAX_INDEX) - a typed array must recognize
	// (and reject as "not present", never falling through to an ordinary
	// property) ANY canonical numeric string key: negative, fractional,
	// "-0", "NaN", out-of-range, etc., not just valid in-bounds indices.
	public static Double canonicalNumericIndexString(JSEnvironment env, String member) {
		if("-0".equals(member)) {
			// ToString(-0) is "0", so "-0" would never round-trip on its own -
			// spec explicitly special-cases it as canonical (mapping to -0).
			return -0.0;
		}
		double n = RuntimeUtil.toNumber(env, member).doubleValue();
		return member.equals(NumberFormatting.numberToString(n)) ? Double.valueOf(n) : null;
	}

	// Returns n as a valid array index, or Long.MIN_VALUE if it is not one (negative,
	// fractional, NaN, Infinity, or too large) - such a Number key must go through
	// ToString instead (e.g. `Infinity in obj` checks the property "Infinity", not
	// some numeric index).
	public static long numberAsMemberIndex(Number n) {
		double d = n.doubleValue();
		long l = (long)d;
		if((double)l==d && isMemberIndex(l)) {
			return l;
		}
		return Long.MIN_VALUE;
	}


	
    public static boolean defineProperty(JSEnvironment env, Object instance, Object prop, JSObject desc) {
		return defineProperty(env, instance, prop, desc, DESC_CHECK.CHECK);
    }
    // check: how a property that cannot be added or changed is reported -
    // Reflect.defineProperty passes NO_EXCEPTION ([[DefineOwnProperty]]
    // returning false never throws there, whatever the caller's strictness)
    public static boolean defineProperty(JSEnvironment env, Object instance, Object prop, JSObject desc, DESC_CHECK check) {
		// Object.defineProperty's first step is "If O is not an Object,
		// throw a TypeError" - not just a null/undefined check, EVERY
		// primitive (boolean/number/string/symbol/bigint) is rejected too.
		if(!RuntimeUtil.isObject(env, instance)) {
			throw RuntimeUtil.typeError("Object.defineProperty called on non-object");
		}
		JSAccessor acc = env.getAccessor(instance);
		return defineProperty(env, acc, instance, prop, desc, check);
    }
    // ObjectDefineProperties (10.1.13): the Properties argument is
    // ToObject-COERCED, not required to already be an object - e.g.
    // `Object.defineProperties({}, 5)` wraps 5 into a Number object (which
    // has no own enumerable properties, so it's a no-op) rather than
    // throwing. Only null/undefined are rejected, via ToObject's own
    // semantics. Genuinely non-object Properties arguments (a raw
    // primitive) therefore can't be typed JSObject up front - routed
    // through the generic accessor system instead of JSObject-specific
    // methods so a boxed primitive (not JSObject-backed) works too.
    public static void defineProperties(JSEnvironment env, Object instance, Object propertiesArg) {
		if(!RuntimeUtil.isObject(env, instance)) {
			throw RuntimeUtil.typeError("Object.defineProperties called on non-object");
		}
		Object props = RuntimeUtil.toObject(env, propertiesArg);
		JSAccessor propsAcc = env.getAccessor(props);
		JSAccessor acc = env.getAccessor(instance);
		for(Iterator<Map.Entry<Object,Object>> it=propsAcc.ownEntries(props, true); it.hasNext(); ) {
			Map.Entry<Object,Object> e = it.next();
			Object descVal = e.getValue();
			// JSObjectInternal deliberately marks "an Object, as opposed to
			// an Array" (see its own doc comment) - too narrow here, since
			// each property descriptor value is coerced via
			// ToPropertyDescriptor, which accepts ANY object: an Array, a
			// boxed primitive (GaltaJS has no wrapper type for those), or a
			// host object like Date - JSObject.from(env,...) already
			// implements the generic "wrap any accessor-backed value as a
			// JSObject" adapter needed here (confirmed via
			// defineProperties/15.2.3.7-5-b-126.js, whose descriptor is a
			// plain Array with a "value" property set on it).
			JSObject jv = descVal instanceof JSObject jo ? jo
					: (RuntimeUtil.isObject(env, descVal) ? JSObject.from(env, descVal) : null);
			if(jv!=null) {
				if(!defineProperty(env, acc, instance, e.getKey(), jv, DESC_CHECK.CHECK)) {
					throw RuntimeUtil.typeError("Cannot define property {0}", e.getKey());
				}
			} else {
				throw RuntimeUtil.typeError("Invalid property definition for {0}, must be an object",e.getKey());
			}
		}
    }
    // ValidateAndApplyPropertyDescriptor (10.1.6.3): correctly distinguishing
    // "field absent from Desc" (keep whatever the current descriptor already
    // has, or fall back to the spec's type-appropriate default when there is
    // no current descriptor / the kind is changing) from "field present" is
    // the crux of this algorithm - unlike a naive `desc.getProperty(key,
    // currentValue)` default-substitution (the previous implementation),
    // which conflates the two and, notably, silently corrupted an existing
    // ACCESSOR property into a bare data property whenever a generic or even
    // completely EMPTY descriptor was applied to it (getter/setter have no
    // Java-level "default" to fall back to the way a boolean does).
    //
    // The actual current.[[Configurable]]===false REJECTION rules are left
    // to the existing putEnty()/isRejectedNonConfigurableChange() machinery
    // downstream - this method's job is solely to build the correctly
    // MERGED "next" descriptor (and the value to apply, using NOT_AVAILABLE
    // as the "don't touch the stored value" sentinel already understood by
    // putEntry) so that machinery has accurate inputs to validate against.
    private static boolean defineProperty(JSEnvironment env, JSAccessor acc , Object instance, Object prop, JSObject desc, DESC_CHECK check) {
		PropertyDescriptor current = acc.getOwnPropertyDescriptor(instance, prop);

		boolean hasValue = desc.hasProperty("value");
		boolean hasWritable = desc.hasProperty("writable");
		boolean hasGet = desc.hasProperty("get");
		boolean hasSet = desc.hasProperty("set");
		boolean hasEnumerable = desc.hasProperty("enumerable");
		boolean hasConfigurable = desc.hasProperty("configurable");

		if((hasGet || hasSet) && (hasValue || hasWritable)) {
			throw RuntimeUtil.typeError("Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
		}

		// ToPropertyDescriptor's own field coercions - only performed for
		// fields actually present, since e.g. a poisoned "writable" getter
		// on a Desc that never specifies "get"/"set" must still run (its
		// side effect is observable) even though the value ends up unused
		// for an accessor-shaped Desc... except the accessor/data mix check
		// above already rejects that combination first, matching spec order.
		Object rawValue = hasValue ? desc.getProperty("value",RuntimeUtil.UNDEFINED) : null;
		boolean writableVal = hasWritable && RuntimeUtil.toBoolean(env, desc.getProperty("writable",false));
		// Per spec, only `undefined` means "no getter/setter" - a `get`/`set`
		// of `null` (or any other non-callable, non-undefined value) must
		// still throw, unlike the more permissive isNotNullOrUndefined used
		// elsewhere for "is this a real object" checks.
		BaseCallableObject getterVal = null;
		if(hasGet) {
			Object g = desc.getProperty("get",RuntimeUtil.UNDEFINED);
			if(g!=RuntimeUtil.UNDEFINED) {
				if(!(g instanceof Callable)) {
					throw RuntimeUtil.typeError("Getter must be a function");
				}
				getterVal = RuntimeUtil.toBaseCallableObject(g);
			}
		}
		BaseCallableObject setterVal = null;
		if(hasSet) {
			Object s = desc.getProperty("set",RuntimeUtil.UNDEFINED);
			if(s!=RuntimeUtil.UNDEFINED) {
				if(!(s instanceof Callable)) {
					throw RuntimeUtil.typeError("Setter must be a function");
				}
				setterVal = RuntimeUtil.toBaseCallableObject(s);
			}
		}
		boolean enumerableVal = hasEnumerable && RuntimeUtil.toBoolean(env, desc.getProperty("enumerable",false));
		boolean configurableVal = hasConfigurable && RuntimeUtil.toBoolean(env, desc.getProperty("configurable",false));

		boolean descIsAccessor = hasGet || hasSet;
		boolean descIsGeneric = !hasValue && !hasWritable && !hasGet && !hasSet;

		if(current==null) {
			// Property doesn't exist yet - extensibility is checked
			// downstream by putEntry()'s canAddEntry(). Unspecified fields
			// get the spec's type-appropriate default (CompletePropertyDescriptor),
			// not any Java-level default.
			if(descIsAccessor) {
				PropertyDescriptor next = PropertyDescriptor.of(configurableVal,enumerableVal,getterVal,setterVal,hasConfigurable,hasEnumerable);
				return acc.setOwnProperty(instance, prop, RuntimeUtil.NOT_AVAILABLE, next, check, instance);
			}
			PropertyDescriptor next = PropertyDescriptor.of(writableVal,configurableVal,enumerableVal,hasWritable,hasConfigurable,hasEnumerable);
			Object value = hasValue ? rawValue : RuntimeUtil.UNDEFINED;
			return acc.setOwnProperty(instance, prop, value, next, check, instance);
		}

		// An empty Desc ({} - no recognized fields at all) is a no-op success,
		// regardless of the current property's configurability.
		if(descIsGeneric && !hasEnumerable && !hasConfigurable) {
			return true;
		}

		boolean newEnumerable = hasEnumerable ? enumerableVal : current.isEnumerable();
		boolean newConfigurable = hasConfigurable ? configurableVal : current.isConfigurable();
		// Only a non-generic Desc can change a property's data/accessor kind,
		// and only fields the Desc doesn't specify itself need a decision
		// between "keep current" (same kind) and "spec default" (kind changing).
		boolean kindChanging = !descIsGeneric && (descIsAccessor == current.isData());

		// Presence propagates from the CURRENT descriptor when this Desc
		// doesn't mention the field at all - redefining a property without
		// mentioning "configurable" doesn't retroactively make an already-
		// present field "absent" again, it just leaves it unchanged.
		boolean newHasConfigurable = hasConfigurable || current.hasConfigurable();
		boolean newHasEnumerable = hasEnumerable || current.hasEnumerable();

		PropertyDescriptor next;
		Object value;
		if(descIsGeneric) {
			// Kind and all kind-specific fields are left entirely untouched;
			// only enumerable/configurable can move.
			if(current.isAccessor()) {
				next = PropertyDescriptor.of(newConfigurable,newEnumerable,current.getGetter(),current.getSetter(),newHasConfigurable,newHasEnumerable);
			} else {
				next = PropertyDescriptor.of(current.isWritable(),newConfigurable,newEnumerable,current.hasWritable(),newHasConfigurable,newHasEnumerable);
			}
			value = RuntimeUtil.NOT_AVAILABLE;
		} else if(descIsAccessor) {
			BaseCallableObject newGetter = hasGet ? getterVal : (kindChanging ? null : current.getGetter());
			BaseCallableObject newSetter = hasSet ? setterVal : (kindChanging ? null : current.getSetter());
			next = PropertyDescriptor.of(newConfigurable,newEnumerable,newGetter,newSetter,newHasConfigurable,newHasEnumerable);
			value = RuntimeUtil.NOT_AVAILABLE;
		} else {
			boolean newWritable = hasWritable ? writableVal : (kindChanging ? false : current.isWritable());
			boolean newHasWritable = hasWritable || (!kindChanging && current.hasWritable());
			next = PropertyDescriptor.of(newWritable,newConfigurable,newEnumerable,newHasWritable,newHasConfigurable,newHasEnumerable);
			if(hasValue) {
				value = rawValue;
			} else if(kindChanging) {
				value = RuntimeUtil.UNDEFINED;
			} else {
				value = RuntimeUtil.NOT_AVAILABLE;
			}
		}
		return acc.setOwnProperty(instance, prop, value, next, check, instance);
    }

	// Spec 6.2.6.4 IsCompatiblePropertyDescriptor(extensible, Desc, current) -
	// same rejection rules as ValidateAndApplyPropertyDescriptor's redefinition
	// checks, but without ever applying anything. `desc`/`current` are always
	// COMPLETE descriptors by the time callers reach this (already merged/
	// defaulted upstream), so there's no field-presence tracking to thread
	// through - mirrors CustomLinkedMap's private isRejectedNonConfigurableChange,
	// generalized for the current==null and current.isConfigurable() cases that
	// method's caller (putEntry) already handles separately itself. currentValue
	// is a Supplier so callers (e.g. a Proxy target) can avoid an extra,
	// potentially-observable value read except in the one case that needs it.
	public static boolean isCompatiblePropertyDescriptor(JSEnvironment env, boolean extensible, PropertyDescriptor current, Supplier<Object> currentValue, PropertyDescriptor desc, Object descValue) {
		if(current==null) {
			return extensible;
		}
		if(current.isConfigurable()) {
			return true;
		}
		if(desc.isConfigurable() != current.isConfigurable() || desc.isEnumerable() != current.isEnumerable()) {
			return false;
		}
		if(desc.isData() != current.isData()) {
			return false;
		}
		if(!desc.isData()) {
			return desc.getGetter()==current.getGetter() && desc.getSetter()==current.getSetter();
		}
		if(current.isWritable()) {
			return true;
		}
		if(desc.isWritable()) {
			return false;
		}
		return descValue==RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.eqSameValue(env, descValue, currentValue.get());
	}

	// Spec 7.3.15 TestIntegrityLevel(O, level) - Object.isFrozen/isSealed
	// must be computed STRUCTURALLY (extensibility + every own property's
	// actual descriptor), not via a cached "was .freeze()/.seal() ever
	// called" flag - an object can independently reach the same state via
	// Object.preventExtensions() plus per-property Object.defineProperty
	// calls, without either builtin ever running (confirmed via
	// isFrozen/15.2.3.12-2-1.js/-2-2.js/-3-28.js: preventExtensions() alone,
	// or combined with manually non-configurable/non-writable properties,
	// must already read back as frozen).
	public static boolean testIntegrityLevel(JSEnvironment env, Object o, boolean frozen) {
		JSAccessor accessor = env.getAccessor(o);
		if(accessor.isExtensible(o)) {
			return false;
		}
		JSObject descs = accessor.getOwnPropertyDescriptors(JSObject.create(env),o);
		for(Iterator<Map.Entry<String,Object>> it=descs.ownPropertyEntries(true); it.hasNext(); ) {
			PropertyDescriptor d = (PropertyDescriptor)it.next().getValue();
			if(d.isConfigurable() || (frozen && d.isData() && d.isWritable())) {
				return false;
			}
		}
		for(Iterator<Map.Entry<Symbol,Object>> it=descs.ownPropertySymbolEntries(true); it.hasNext(); ) {
			PropertyDescriptor d = (PropertyDescriptor)it.next().getValue();
			if(d.isConfigurable() || (frozen && d.isData() && d.isWritable())) {
				return false;
			}
		}
		return true;
	}
}
