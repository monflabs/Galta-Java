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
package org.monflabs.galtajs.rt.builtins.standard.iterator;

import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.protocols.iterator.JavaIterator;
import org.monflabs.util.iterators.Iterators;

/**
 * %Iterator.prototype%. Its methods (map/filter/find/every/some/reduce/
 * forEach/flatMap/take/drop/toArray) accept ANY object receiver per spec's
 * GetIteratorDirect - not just a receiver already backed by a real Java
 * Iterator (e.g. a plain `{ __proto__: Iterator.prototype, next() {...} }`
 * object, or a `class Foo extends Iterator {}` instance, must work too).
 */
public class BuiltinIteratorPrototype extends BasePrototype {

	public static BuiltinIteratorPrototype get(JSEnvironment env) {
		BuiltinIteratorPrototype proto = (BuiltinIteratorPrototype)env.getRegisteredPrototype(BuiltinIteratorPrototype.class);
		if(proto==null) {
			proto = new BuiltinIteratorPrototype(env);
			env.registerPrototype(BuiltinIteratorPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinIteratorPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.chunks,1));
		setOwnMethod(new Method(env,MethodId.drop,1));
		setOwnMethod(new Method(env,MethodId.every,1));
		setOwnMethod(new Method(env,MethodId.filter,1));
		setOwnMethod(new Method(env,MethodId.find,1));
		setOwnMethod(new Method(env,MethodId.flatMap,1));
		setOwnMethod(new Method(env,MethodId.forEach,1));
		setOwnMethod(new Method(env,MethodId.includes,1));
		setOwnMethod(new Method(env,MethodId.join,1));
		setOwnMethod(new Method(env,MethodId.map,1));
		setOwnMethod(new Method(env,MethodId.reduce,1));
		setOwnMethod(new Method(env,MethodId.some,1));
		setOwnMethod(new Method(env,MethodId.take,1));
		setOwnMethod(new Method(env,MethodId.toArray,0));
		setOwnMethod(new Method(env,MethodId.windows,1));
		setOwnMethod(new Method(env,MethodId.iterator,0));
		setOwnMethod(new Method(env,MethodId.dispose,0));
		// [Symbol.toStringTag] (and "constructor") are installed as accessor
		// properties by BuiltinIteratorConstructor instead of here as a
		// plain data property - see that class's constructor for why.
	}

	@Override
	public String getClassName() {
		return BuiltinIteratorConstructor.CLASSNAME;
	}

	// GetIteratorDirect(O): O must be an Object; wraps it as a Java Iterator,
	// reading "next" exactly once (via JavaIterator) unless O is already a
	// real Java Iterator (e.g. a generator, or an existing helper result).
	@SuppressWarnings("unchecked")
	private static Iterator<Object> getIteratorDirect(JSEnvironment env, Object obj) {
		if(obj instanceof Iterator<?> it) {
			return (Iterator<Object>)it;
		}
		return new JavaIterator(env,obj);
	}

	// On argument-validation failure (before GetIteratorDirect is ever
	// reached - "next" must NOT be read), close the receiver directly via
	// its own "return" method (if any), swallowing any secondary error so
	// the original TypeError still wins, then throw.
	private static RuntimeException closeAndFail(JSEnvironment env, Object obj, String message) {
		try {
			RuntimeUtil.iteratorClose(env, obj);
		} catch(Throwable ignore) {
			RuntimeUtil.rethrowIfUncatchable(ignore);
		}
		throw RuntimeUtil.typeError(message);
	}

	// Same as closeAndFail(), but for %Iterator.prototype%.take/drop's own
	// limit-argument validation, which per spec throws a RangeError (not a
	// TypeError) for a NaN or negative limit (test262
	// built-ins/Iterator/prototype/take/limit-rangeerror.js and
	// .../drop/limit-rangeerror.js).
	private static RuntimeException closeAndFailRange(JSEnvironment env, Object obj, String message) {
		try {
			RuntimeUtil.iteratorClose(env, obj);
		} catch(Throwable ignore) {
			RuntimeUtil.rethrowIfUncatchable(ignore);
		}
		throw RuntimeUtil.rangeError(message);
	}

	// take()/drop()'s own limit-argument coercion (ToNumber, which can invoke
	// a user valueOf()/toString()) happens before GetIteratorDirect - if IT
	// throws, the receiver must still be closed the same way closeAndFail()
	// does for the callable/range checks (test262 take/drop's
	// argument-validation-failure-closes-underlying.js).
	private static double closingToDouble(JSEnvironment env, Object obj, Object arg) {
		try {
			return RuntimeUtil.toDouble(env, arg);
		} catch(RuntimeException|Error t) {
			try {
				RuntimeUtil.iteratorClose(env, obj);
			} catch(Throwable ignore) {
			}
			throw t;
		}
	}

	// Whether o is a plain JS "Number" (Integer/Long/Double/Float/Short/
	// Byte), as opposed to a non-Number type OR one of GaltaJS's extension
	// numeric types (BigInteger/BigDecimal represent BigInt/Decimal64 - a
	// DIFFERENT spec type from Number, never accepted where the spec
	// requires "is not a Number" to reject outright - e.g. windows()/
	// chunks()'s size argument, includes()'s skippedElements).
	private static boolean isPlainNumber(Object o) {
		return (o instanceof Integer) || (o instanceof Long) || (o instanceof Double) || (o instanceof Float) || (o instanceof Short) || (o instanceof Byte);
	}

	// Shared windowSize/chunkSize validation for windows()/chunks(): per
	// spec, the argument must already be a Number (never ToNumber-coerced
	// - test262 window/chunkSize-no-coercion.js: a valueOf()/toString()-
	// bearing object is rejected outright, never invoked) that is both
	// integral (a NaN or fractional value is a TypeError, not RangeError -
	// test262 window/chunkSize-not-a-number.js lists Infinity/-Infinity/
	// NaN/0.5 alongside non-Number types, all TypeError) and within
	// [1, 2^32-1] (test262 window/chunkSize-out-of-range.js). Like
	// closeAndFail()/closeAndFailRange(), this runs before GetIteratorDirect
	// and closes the receiver directly via its own "return" method.
	private static long validateChunkingSize(JSEnvironment env, Object obj, Object arg, String label) {
		if(!isPlainNumber(arg) || !RuntimeUtil.isIntegerNumber(arg)) {
			throw closeAndFail(env, obj, label+" must be an integer");
		}
		long size = ((Number)arg).longValue();
		if(size<1 || size>4294967295L) { // 2^32-1
			throw closeAndFailRange(env, obj, label+" out of range");
		}
		return size;
	}

	// CreateArrayFromList: windows()/chunks() must each yield a fresh,
	// distinct Array (test262 windows/yields-distinct-arrays.js) - never
	// a live view onto the sliding buffer itself.
	private static JSArray arrayFromBuffer(JSEnvironment env, Iterable<Object> values) {
		JSArray a = JSArray.create(env);
		for(Object v : values) {
			a.arrayAdd(v);
		}
		return a;
	}

	// Eager/terminal methods (every/find/forEach/reduce/some/toArray) consume
	// _this synchronously in one call - if the callback or the source's own
	// next() throws mid-iteration, the source must be closed (best-effort,
	// swallowing any secondary error) before the original error propagates.
	private static <T> T runClosingOnError(JSEnvironment env, Iterator<Object> _this, java.util.function.Supplier<T> body) {
		try {
			return body.get();
		} catch(RuntimeException|Error t) {
			RuntimeUtil.iteratorCloseQuietly(env, _this);
			throw t;
		}
	}

	private static enum MethodId {
		chunks,
		drop,
		every,
		filter,
		find,
		flatMap,
		forEach,
		includes,
		join,
		map,
		reduce,
		some,
		take,
		toArray,
		windows,
		// Symbols
		iterator(Symbol.ITERATOR),
		dispose(Symbol.DISPOSE),
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}


	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	JSEnvironment env = getEnvironment();
	    	// %Iterator.prototype%[Symbol.iterator]() just returns `this`
	    	// unconditionally, per spec - not even an object-type check,
	    	// works for any receiver including primitives/null/undefined.
	    	if(methodId==MethodId.iterator) {
	    		return obj;
	    	}
	    	if(!RuntimeUtil.isObject(env,obj)) {
	    		throw RuntimeUtil.typeError("Method Iterator.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	        switch(methodId) {
	    		case chunks-> {
	    			// Iterator.prototype.chunks ( chunkSize ) - TC39 Iterator
	    			// Helpers "chunking" addition (test262 features:
	    			// [iterator-chunking]). Lazy like map()/filter()/take()/
	    			// drop(): chunkSize is validated (Number-ness, integrality,
	    			// [1, 2^32-1] range - see validateChunkingSize()'s comment)
	    			// BEFORE GetIteratorDirect, matching test262
	    			// argument-effect-order.js's "next" getter only accessed
	    			// once validation fully passes. Non-overlapping (unlike
	    			// windows()): each yielded array consumes chunkSize fresh
	    			// elements; the FINAL chunk may be shorter if the source
	    			// isn't evenly divisible (test262 chunks-last-chunk-
	    			// partial.js) - there is no "undersized" opt-out for
	    			// chunks() the way windows() has one.
	    			Object sizeArg = param(args, 0, RuntimeUtil.UNDEFINED);
	    			long chunkSize = validateChunkingSize(env, obj, sizeArg, "chunks() chunkSize");
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return new BuiltinIteratorHelper(env, new ChunksIterator(env,_this,chunkSize), _this);
	    		}
	    		case drop-> {
	    			// Not paramDouble(args, 0, 0.0) - that treats a MISSING or
	    			// explicit `undefined` argument as if 0 had been passed,
	    			// but per spec ToNumber(undefined) is NaN, which must still
	    			// hit the NaN check below and throw RangeError (test262
	    			// drop/limit-rangeerror.js: "iterator.drop()"/"drop(undefined)").
	    			double skip = closingToDouble(env, obj, param(args, 0, RuntimeUtil.UNDEFINED));
	    			if(Double.isNaN(skip)) {
	    				throw closeAndFailRange(env, obj, "Invalid drop range");
	    			}
	    			// A finite limit beyond 2^53-1 (Number.MAX_SAFE_INTEGER) is
	    			// rejected before ToIntegerOrInfinity's truncation - spec
	    			// step 7, checked BEFORE the negative-range check below
	    			// (test262 drop/limit-rangeerror.js: MAX_SAFE_INTEGER+1 throws,
	    			// MAX_SAFE_INTEGER itself and Infinity don't).
	    			if(Double.isFinite(skip) && skip>9007199254740991.0) {
	    				throw closeAndFailRange(env, obj, "Invalid drop range");
	    			}
	    			// ToIntegerOrInfinity truncates toward zero (e.g. -0.5
	    			// becomes -0, which is not "< 0") before the range check -
	    			// checking the raw fractional double here would wrongly
	    			// reject e.g. drop(-0.5) (test262 drop/limit-rangeerror.js
	    			// calls it, unguarded, expecting it NOT to throw).
	    			if((long)skip<0) {
	    				throw closeAndFailRange(env, obj, "Invalid drop range");
	    			}
	    			Iterator<Object> _this = getIteratorDirect(env, obj);
	    			// A double count: drop(Infinity) or drop(2**40) must not clamp to an int
	    			return new BuiltinIteratorHelper(env,new DropIterator(_this,Math.floor(skip)),_this);
	    		}
	    		case every-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "every() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			try {
	    				boolean result = Iterators.every(_this, (v,idx) -> {
	    					Object r = c.call(null, new Object[]{v,idx});
	    					return RuntimeUtil.toBoolean(env, r);
	    				});
	    				// false means the predicate short-circuited (an element
	    				// was falsy) before the source was naturally exhausted.
	    				// Per spec IteratorClose: the completion being closed over
	    				// here is NORMAL (not a throw), so an exception from the
	    				// close itself (e.g. a throwing "return" method/getter)
	    				// must propagate rather than be swallowed - unlike the
	    				// catch block below, where the original in-flight
	    				// exception wins (test262 every/get-return-method-throws.js,
	    				// every/iterator-return-method-throws.js).
	    				if(!result) {
	    					RuntimeUtil.iteratorClose(env, _this);
	    				}
	    				return result;
	    			} catch(RuntimeException|Error t) {
	    				RuntimeUtil.iteratorCloseQuietly(env, _this);
	    				throw t;
	    			}
	    		}
	    		case filter-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "filter() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return new BuiltinIteratorHelper(env,Iterators.filter(_this, (v,idx) -> {
    					Object r = c.call(null, new Object[]{v,idx});
    					return RuntimeUtil.toBoolean(env, r);
	    			}),_this);
	    		}
	    		case find-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "find() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			try {
	    				Object result = Iterators.find(_this, (v,idx) -> {
	    					Object r = c.call(null, new Object[]{v,idx});
	    					return RuntimeUtil.toBoolean(env, r);
	    				}, RuntimeUtil.UNDEFINED);
	    				// A match closes the source early (even if it happened to
	    				// be the last element - closing an exhausted source is a
	    				// harmless no-op). Per spec IteratorClose, this completion
	    				// is NORMAL (not a throw), so an exception from the close
	    				// itself must propagate (see every()'s identical comment
	    				// above; test262 find/get-return-method-throws.js,
	    				// find/iterator-return-method-throws.js).
	    				if(result!=RuntimeUtil.UNDEFINED) {
	    					RuntimeUtil.iteratorClose(env, _this);
	    				}
	    				return result;
	    			} catch(RuntimeException|Error t) {
	    				RuntimeUtil.iteratorCloseQuietly(env, _this);
	    				throw t;
	    			}
	    		}
	    		case flatMap-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "flatMap() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
    				FlatMapIterator flatMapIterator = new FlatMapIterator(env,
    						Iterators.map(_this, (v,idx) -> c.call(null, new Object[]{v,idx})),
    						_this
    				);
	    			return new BuiltinIteratorHelper(env, flatMapIterator, flatMapIterator);
	    		}
	    		case forEach-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "forEach() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return runClosingOnError(env, _this, () -> {
	    				Iterators.forEach(_this, (v,idx) -> {
	    					c.call(null, new Object[]{v,idx});
	    				});
	    				return RuntimeUtil.UNDEFINED;
	    			});
	    		}
	    		case includes-> {
	    			// Iterator.prototype.includes ( searchElement [ , skippedElements ] )
	    			// - TC39 Iterator Helpers addition (test262 features:
	    			// [iterator-includes]). Eager/terminal like some()/every()/
	    			// find(): compares each yielded value to searchElement via
	    			// SameValueZero (never ToBoolean/a user predicate), closing
	    			// the source only on a MATCH (an early short-circuit), never
	    			// on natural exhaustion (test262
	    			// exhaustion-does-not-call-return.js) - same asymmetric
	    			// close as some()/every()/find() above.
	    			Object searchElement = param(args, 0, RuntimeUtil.UNDEFINED);
	    			// skippedElements is validated as a raw Number BEFORE
	    			// GetIteratorDirect - "not one of +Infinity, -Infinity, or
	    			// an integral Number" is a TYPE/SHAPE check, not ToNumber
	    			// coercion (no valueOf()/toString() call - test262
	    			// argument-validation-failure-closes-underlying.js expects
	    			// a plain string to throw TypeError outright rather than
	    			// being coerced). A NaN or non-integral finite Number also
	    			// fails this same check (TypeError, not RangeError) - only
	    			// an integral-or-infinite Number that's simply out of RANGE
	    			// reaches the RangeError checks below (test262
	    			// skipped-elements-nan-typeerror.js vs
	    			// skipped-elements-negative-infinity-rangeerror.js).
	    			Object skipArg = param(args, 1, RuntimeUtil.UNDEFINED);
	    			double toSkip;
	    			if(skipArg==RuntimeUtil.UNDEFINED) {
	    				toSkip = 0;
	    			} else {
	    				if(!isPlainNumber(skipArg)) {
	    					throw closeAndFail(env, obj, "includes() skippedElements must be a number");
	    				}
	    				double d = ((Number)skipArg).doubleValue();
	    				if(Double.isNaN(d) || (!Double.isInfinite(d) && Math.floor(d)!=d)) {
	    					throw closeAndFail(env, obj, "includes() skippedElements must be an integer");
	    				}
	    				toSkip = d;
	    			}
	    			if(toSkip<0) {
	    				throw closeAndFailRange(env, obj, "Invalid includes skip count");
	    			}
	    			if(!Double.isInfinite(toSkip) && toSkip>9007199254740991.0) { // 2^53-1
	    				throw closeAndFailRange(env, obj, "Invalid includes skip count");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			try {
	    				// Skipping consumes-and-discards elements without ever
	    				// comparing them (test262
	    				// skipped-elements-max-safe-integer.js: a huge toSkip
	    				// against a short source stops the moment the source
	    				// itself is exhausted, never over-consuming beyond
	    				// that).
	    				double skipped = 0;
	    				while(skipped<toSkip) {
	    					if(!_this.hasNext()) {
	    						return false;
	    					}
	    					_this.next();
	    					skipped++;
	    				}
	    				boolean found = false;
	    				while(_this.hasNext()) {
	    					if(RuntimeUtil.eqSameValueZero(env, _this.next(), searchElement)) {
	    						found = true;
	    						break;
	    					}
	    				}
	    				// See every()/some()/find()'s identical comment above: a
	    				// match's close is a NORMAL completion, so an exception
	    				// from the close itself must propagate rather than be
	    				// swallowed by the catch block below (test262
	    				// iterator-return-method-throws.js).
	    				if(found) {
	    					RuntimeUtil.iteratorClose(env, _this);
	    				}
	    				return found;
	    			} catch(RuntimeException|Error t) {
	    				RuntimeUtil.iteratorCloseQuietly(env, _this);
	    				throw t;
	    			}
	    		}
	    		case join-> {
	    			// Iterator.prototype.join ( separator ) - TC39 Iterator
	    			// Helpers addition (test262 features:
	    			// [Iterator.prototype.join]), mirrors Array.prototype.join's
	    			// semantics over an iterator instead of an array-like: the
	    			// separator is coerced to a string FIRST (paramString's
	    			// default only applies to a missing/explicit-undefined
	    			// argument - test262 separator-tostring.js: `null` is NOT
	    			// treated as "use the default", it's ToString'd to the
	    			// literal string "null", same as Array.prototype.join),
	    			// and only THEN is "next" looked up via GetIteratorDirect
	    			// (test262 next-lookup-after-separator-tostring.js).
	    			//
	    			// Unlike every other eager method in this switch, a failure
	    			// from the receiver's OWN next()/protocol (as opposed to a
	    			// value's toString() throwing) deliberately does NOT close
	    			// the source (test262 does-not-close-on-iterator-error.js,
	    			// does-not-close-on-iterator-protocol-violation.js,
	    			// does-not-close-on-next-getter-error.js) - so, unlike
	    			// find()/some()/every()/includes() above, there is no
	    			// blanket catch-and-close around the whole loop; only the
	    			// per-element RuntimeUtil.toString() call (and the
	    			// separator's own coercion, pre-GetIteratorDirect) are each
	    			// individually wrapped.
	    			String sep;
	    			try {
	    				sep = paramString(args, 0, ",");
	    			} catch(RuntimeException|Error t) {
	    				try {
	    					RuntimeUtil.iteratorClose(env, obj);
	    				} catch(Throwable ignore) {
	    				}
	    				throw t;
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			StringBuilder b = new StringBuilder();
	    			boolean first = true;
	    			while(_this.hasNext()) {
	    				Object v = _this.next();
	    				if(!first) {
	    					b.append(sep);
	    				}
	    				first = false;
	    				// Nullish contents contribute "" (test262
	    				// contents-nullish.js), same as Array.prototype.join.
	    				if(RuntimeUtil.isNotNullOrUndefined(v)) {
	    					try {
	    						b.append(RuntimeUtil.toString(env, v));
	    					} catch(RuntimeException|Error t) {
	    						RuntimeUtil.iteratorCloseQuietly(env, _this);
	    						throw t;
	    					}
	    				}
	    			}
	    			return b.toString();
	    		}
	    		case map-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "map() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return new BuiltinIteratorHelper(env,Iterators.map(_this, (v,idx) -> {
    					return c.call(null, new Object[]{v,idx});
	    			}),_this);
	    		}
	    		case reduce-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "reduce() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
    				// Per spec, "initialValue is not present" (no second argument AT
    				// ALL - even an explicit `undefined` counts as present) is
    				// different from an explicit undefined accumulator: with no
    				// initial value, the FIRST yielded element seeds the accumulator
    				// directly (never itself passed to the reducer, hence the
    				// counter offset below), and an iterator that's already
    				// exhausted must throw a TypeError rather than silently
    				// returning undefined (test262 reduce/iterator-already-
    				// exhausted-no-initial-value.js).
    				boolean hasInitial = args.length>1;
    				Object initialArg = hasInitial ? args[1] : RuntimeUtil.UNDEFINED;
		    		return runClosingOnError(env, _this, () -> {
		    			Object accumulator;
		    			int counterOffset;
		    			if(hasInitial) {
		    				accumulator = initialArg;
		    				counterOffset = 0;
		    			} else {
		    				if(!_this.hasNext()) {
		    					throw RuntimeUtil.typeError("Reduce of empty iterator with no initial value");
		    				}
		    				accumulator = _this.next();
		    				counterOffset = 1;
		    			}
		    			return Iterators.reduce(_this, (a,v,idx) -> c.call(null, new Object[]{a,v,idx+counterOffset}), accumulator);
		    		});
	    		}
	    		case some-> {
	    			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
	    			if(!(arg0 instanceof Callable c && c.isCallable())) {
	    				throw closeAndFail(env, obj, "some() parameter should be a callable");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			try {
	    				boolean result = Iterators.some(_this, (v,idx) -> {
	    					Object r = c.call(null, new Object[]{v,idx});
	    					return RuntimeUtil.toBoolean(env, r);
	    				});
	    				// true means the predicate short-circuited (an element
	    				// was truthy) before the source was naturally exhausted.
	    				// Per spec IteratorClose, this completion is NORMAL (not a
	    				// throw), so an exception from the close itself must
	    				// propagate (see every()'s identical comment above;
	    				// test262 some/get-return-method-throws.js,
	    				// some/iterator-return-method-throws.js).
	    				if(result) {
	    					RuntimeUtil.iteratorClose(env, _this);
	    				}
	    				return result;
	    			} catch(RuntimeException|Error t) {
	    				RuntimeUtil.iteratorCloseQuietly(env, _this);
	    				throw t;
	    			}
	    		}
	    		case take-> {
	    			// Same rationale as drop() above: a MISSING or explicit
	    			// `undefined` limit must still coerce to NaN and hit the
	    			// check below (test262 take/limit-rangeerror.js:
	    			// "iterator.take()"/"take(undefined)").
	    			double limit = closingToDouble(env, obj, param(args, 0, RuntimeUtil.UNDEFINED));
	    			if(Double.isNaN(limit)) {
	    				throw closeAndFailRange(env, obj, "Invalid take range");
	    			}
	    			// See drop()'s identical MAX_SAFE_INTEGER-upper-bound comment above.
	    			if(Double.isFinite(limit) && limit>9007199254740991.0) {
	    				throw closeAndFailRange(env, obj, "Invalid take range");
	    			}
	    			// See drop()'s identical comment above.
	    			if((long)limit<0) {
	    				throw closeAndFailRange(env, obj, "Invalid take range");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return new BuiltinIteratorHelper(env,new TakeIterator(env,_this,Math.floor(limit)),_this);
	    		}
	    		case toArray-> {
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return runClosingOnError(env, _this, () -> {
	    				JSArray a = JSArray.create(env);
	    				while(_this.hasNext()) {
	    					a.arrayAdd(_this.next());
	    				}
	    				return a;
	    			});
	    		}
	    		case windows-> {
	    			// Iterator.prototype.windows ( windowSize [ , undersized ] )
	    			// - TC39 Iterator Helpers "chunking" addition (test262
	    			// features: [iterator-chunking]). Lazy like map()/filter()/
	    			// take()/drop(): both arguments are fully validated BEFORE
	    			// GetIteratorDirect - windowSize's Number-ness/integrality/
	    			// [1, 2^32-1] range first (see validateChunkingSize()'s
	    			// comment), THEN undersized's enum-ness, matching test262
	    			// argument-effect-order.js (a bad windowSize throws before
	    			// a bad undersized is ever examined; a valid windowSize with
	    			// a bad undersized still throws before "next" is read).
	    			// Sliding (unlike chunks()): each successive window re-uses
	    			// all but the oldest element of the previous one (test262
	    			// windows-basic.js). undersized controls the FINAL,
	    			// possibly-short window: "only-full" (the default) drops it
	    			// entirely, "allow-partial" yields it once
	    			// (test262 windows-allow-partial.js).
	    			Object sizeArg = param(args, 0, RuntimeUtil.UNDEFINED);
	    			long windowSize = validateChunkingSize(env, obj, sizeArg, "windows() windowSize");
	    			Object undersizedArg = param(args, 1, RuntimeUtil.UNDEFINED);
	    			boolean allowPartial;
	    			if(undersizedArg==RuntimeUtil.UNDEFINED || "only-full".equals(undersizedArg)) {
	    				allowPartial = false;
	    			} else if("allow-partial".equals(undersizedArg)) {
	    				allowPartial = true;
	    			} else {
	    				throw closeAndFail(env, obj, "windows() undersized must be \"only-full\" or \"allow-partial\"");
	    			}
    				Iterator<Object> _this = getIteratorDirect(env, obj);
	    			return new BuiltinIteratorHelper(env, new WindowsIterator(env,_this,windowSize,allowPartial), _this);
	    		}
	    		// %IteratorPrototype% [ @@dispose ] ( ): GetMethod(O,"return"),
	    		// call it if present, discard its result - unlike iteratorClose(),
	    		// no object-type check on the result (confirmed via return-val.js:
	    		// calling on a receiver with no "return" method returns undefined).
	    		case dispose-> {
	    			Object returnMethod = RuntimeUtil.getProperty(env, obj, "return");
	    			if(returnMethod!=null && returnMethod!=RuntimeUtil.UNDEFINED) {
	    				if(!BuiltinUtil.isCallable(returnMethod)) {
	    					throw RuntimeUtil.typeError("return is not a function");
	    				}
	    				RuntimeUtil.call(env, returnMethod, obj, RuntimeUtil.EMPTY_PARAMS);
	    			}
	    			return RuntimeUtil.UNDEFINED;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}

	// GetIteratorFlattenable(mapped, reject-strings): flatMap's mapper result
	// must become an inner iterator EXACTLY one level deep - a raw (non-
	// object) value, string or not, is always rejected with a TypeError
	// (test262 flatMap/strings-are-not-flattened.js and
	// iterable-primitives-are-not-flattened.js: a String/Number WRAPPER
	// object is fine, only the bare primitive is rejected). An object with
	// no @@iterator method (absent, or explicitly null/undefined) falls back
	// to using the object itself AS the inner iterator, per spec - matches
	// test262 flatMap/iterable-to-iterator-fallback.js and
	// return-is-forwarded-to-mapper-result.js (a plain `{next(){...}}`
	// object, no @@iterator at all).
	private static Iterator<Object> getIteratorFlattenable(JSEnvironment env, Object mapped) {
		if(!RuntimeUtil.isObject(env, mapped)) {
			throw RuntimeUtil.typeError("flatMap mapper result {0} must be an object", mapped);
		}
		JSAccessor acc = env.getAccessor(mapped);
		Object itFactory = acc.getProperty(mapped, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
		if(itFactory==RuntimeUtil.NOT_AVAILABLE || itFactory==null || itFactory==RuntimeUtil.UNDEFINED) {
			return new JavaIterator(env, mapped);
		}
		if(!(itFactory instanceof Callable cl && cl.isCallable())) {
			throw RuntimeUtil.typeError("[Symbol.iterator] is not a function");
		}
		Object i = cl.call(mapped, RuntimeUtil.EMPTY_PARAMS);
		if(!RuntimeUtil.isObject(env, i)) {
			throw RuntimeUtil.typeError("Result of the [Symbol.iterator] call {0} is not an object", i);
		}
		return new JavaIterator(env, i);
	}

	// flatMap()'s own result iterator - deliberately NOT built on the generic
	// (recursively-flattening) Iterators.flatten() utility, which flattens
	// arbitrarily deep and is shared with an unrelated caller (FileSource's
	// recursive file-tree walk) with its own passing test coverage; flatMap
	// must flatten EXACTLY one level (test262
	// flatMap/flattens-only-depth-1.js: an inner iterator's own yielded
	// values, even if themselves iterators/iterables, are returned as-is,
	// never flattened further).
	//
	// Also doubles as the BuiltinIteratorHelper's closeSource (via
	// ExternallyCloseable) so that an explicit .return() on the result
	// forwards to whichever iterator is the CURRENTLY ACTIVE one - the open
	// inner iterator if mid-flattening one, else the outer source - per spec
	// (the abstract closure's Yield only ever has one of the two "open" at a
	// time; test262 flatMap/return-is-forwarded-to-mapper-result.js and
	// return-is-forwarded-to-underlying-iterator.js).
	private static final class FlatMapIterator implements Iterator<Object>, ExternallyCloseable {
		private final JSEnvironment env;
		private final Iterator<Object> outerMapped;
		private final Iterator<Object> outerSource;
		private Iterator<Object> inner;
		private boolean hasBuffered;
		private Object buffered;

		FlatMapIterator(JSEnvironment env, Iterator<Object> outerMapped, Iterator<Object> outerSource) {
			this.env = env;
			this.outerMapped = outerMapped;
			this.outerSource = outerSource;
		}

		private boolean advance() {
			if(hasBuffered) {
				return true;
			}
			while(true) {
				if(inner!=null) {
					if(inner.hasNext()) {
						buffered = inner.next();
						hasBuffered = true;
						return true;
					}
					inner = null;
				}
				if(!outerMapped.hasNext()) {
					return false;
				}
				Object mapped = outerMapped.next();
				inner = getIteratorFlattenable(env, mapped);
			}
		}

		@Override
		public boolean hasNext() {
			return advance();
		}

		@Override
		public Object next() {
			if(!advance()) {
				throw new NoSuchElementException();
			}
			hasBuffered = false;
			return buffered;
		}

		// Per spec (Iterator.prototype.flatMap's abstract closure, the
		// abrupt-completion branch of its inner Yield loop): closing on an
		// explicit early .return() closes BOTH the currently-open inner
		// iterator (if any - there is none if return() is called before the
		// first next(), matching %IteratorHelperPrototype%.return()'s
		// separate suspended-start path, which only ever closes [[Underlying
		// Iterators]] = the outer source) AND the outer source, inner
		// FIRST - if closing the inner iterator itself throws, that error
		// wins and the outer close becomes best-effort/quiet (test262
		// flatMap/get-return-method-throws.js: an inner array iterator with
		// no "return" closes as a no-op, so the OUTER's throwing return is
		// what's observed; return-is-forwarded-to-mapper-result.js: the
		// inner's own return() is what increments its counter, with the
		// outer plain generator closing silently alongside it).
		@Override
		public void closeAll(JSEnvironment env) {
			RuntimeException innerError = null;
			if(inner!=null) {
				try {
					RuntimeUtil.iteratorClose(env, inner);
				} catch(RuntimeException e) {
					innerError = e;
				}
			}
			if(innerError!=null) {
				RuntimeUtil.iteratorCloseQuietly(env, outerSource);
				throw innerError;
			}
			RuntimeUtil.iteratorClose(env, outerSource);
		}
	}

	// Iterator.prototype.take()'s own iterator. Unlike map()/filter() (which
	// simply stop pulling once the SOURCE runs out on its own - nothing to
	// close, the source's own natural completion already handles that),
	// take() choosing to stop EARLY (before the source is necessarily
	// exhausted) requires an EXPLICIT IteratorClose(iterated) at the exact
	// moment `remaining` reaches 0 (spec %Iterator.prototype%.take, step
	// 8.b.i) - the generic natural-exhaustion path in
	// BuiltinIteratorHelperPrototype's next() deliberately does NOT do this
	// (see Round 4's "return-is-not-forwarded-after-exhaustion" fix), and
	// the generic Iterators.limit() utility has no notion of a JS-level
	// "return" to call - hence this small dedicated wrapper (test262
	// take/exhaustion-calls-return.js and the three
	// next-method-returns-throwing-*.js siblings).
	private static final class DropIterator implements Iterator<Object> {
		private final Iterator<Object> source;
		private double remaining;

		DropIterator(Iterator<Object> source, double remaining) {
			this.source = source;
			this.remaining = remaining;
		}

		@Override
		public boolean hasNext() {
			while(remaining>0 && source.hasNext()) {
				remaining--;
				source.next();
			}
			return source.hasNext();
		}

		@Override
		public Object next() {
			if(!hasNext()) {
				throw new NoSuchElementException();
			}
			return source.next();
		}
	}

	private static final class TakeIterator implements Iterator<Object> {
		private final JSEnvironment env;
		private final Iterator<Object> source;
		// A double count, so take(Infinity) never runs out
		private double remaining;
		private boolean closed;

		TakeIterator(JSEnvironment env, Iterator<Object> source, double remaining) {
			this.env = env;
			this.source = source;
			this.remaining = remaining;
		}

		@Override
		public boolean hasNext() {
			if(remaining<=0) {
				if(!closed) {
					closed = true;
					RuntimeUtil.iteratorClose(env, source);
				}
				return false;
			}
			return source.hasNext();
		}

		@Override
		public Object next() {
			if(!hasNext()) {
				throw new NoSuchElementException();
			}
			remaining--;
			return source.next();
		}
	}

	// Iterator.prototype.windows()'s own result iterator - a sliding window
	// over `source`, per spec's abstract closure (test262
	// windows-basic.js's embedded algorithm steps): a buffer holds up to
	// windowSize elements; once full, each further source element evicts
	// the OLDEST buffered element (removeFirst) before being appended, and
	// a fresh copy of the buffer is yielded after every append that leaves
	// it at exactly windowSize. Unlike take() above, this never stops the
	// source EARLY on its own - it only reacts to the source's own natural
	// exhaustion, so (like map()/filter()) it needs no explicit
	// IteratorClose of its own; BuiltinIteratorHelper's shared closeSource
	// plumbing (this class passed `_this` as closeSource, not itself)
	// already covers an external .return() or a mid-computation error.
	private static final class WindowsIterator implements Iterator<Object> {
		private final JSEnvironment env;
		private final Iterator<Object> source;
		private final long windowSize;
		private final boolean allowPartial;
		private final ArrayDeque<Object> buffer = new ArrayDeque<>();
		private boolean hasBuffered;
		private Object buffered;
		private boolean sourceExhausted;
		private boolean done;

		WindowsIterator(JSEnvironment env, Iterator<Object> source, long windowSize, boolean allowPartial) {
			this.env = env;
			this.source = source;
			this.windowSize = windowSize;
			this.allowPartial = allowPartial;
		}

		private boolean advance() {
			if(hasBuffered) {
				return true;
			}
			if(done) {
				return false;
			}
			while(!sourceExhausted) {
				if(!source.hasNext()) {
					sourceExhausted = true;
					break;
				}
				Object v = source.next();
				if(buffer.size()==windowSize) {
					buffer.removeFirst();
				}
				buffer.addLast(v);
				if(buffer.size()==windowSize) {
					buffered = arrayFromBuffer(env, buffer);
					hasBuffered = true;
					return true;
				}
			}
			// Source exhausted without ever completing one more full
			// window: undersized=="allow-partial" yields whatever is left
			// in the buffer exactly once (test262
			// windows-allow-partial.js); the default "only-full" simply
			// drops it (test262 undersized-default.js).
			if(allowPartial && !buffer.isEmpty() && buffer.size()<windowSize) {
				buffered = arrayFromBuffer(env, buffer);
				buffer.clear();
				hasBuffered = true;
				done = true;
				return true;
			}
			done = true;
			return false;
		}

		@Override
		public boolean hasNext() {
			return advance();
		}

		@Override
		public Object next() {
			if(!advance()) {
				throw new NoSuchElementException();
			}
			hasBuffered = false;
			return buffered;
		}
	}

	// Iterator.prototype.chunks()'s own result iterator - non-overlapping
	// groups of up to chunkSize elements (test262 chunks-evenly-
	// divisible.js), unlike windows()'s sliding buffer: each yielded chunk
	// starts a brand-new buffer. If the source is exhausted mid-buffer, the
	// final short chunk is always yielded (no "undersized" opt-out the way
	// windows() has one - test262 chunks-last-chunk-partial.js). Same
	// closeSource delegation rationale as WindowsIterator above - never
	// stops the source early on its own.
	private static final class ChunksIterator implements Iterator<Object> {
		private final JSEnvironment env;
		private final Iterator<Object> source;
		private final long chunkSize;
		private final ArrayDeque<Object> buffer = new ArrayDeque<>();
		private boolean hasBuffered;
		private Object buffered;
		private boolean done;

		ChunksIterator(JSEnvironment env, Iterator<Object> source, long chunkSize) {
			this.env = env;
			this.source = source;
			this.chunkSize = chunkSize;
		}

		private boolean advance() {
			if(hasBuffered) {
				return true;
			}
			if(done) {
				return false;
			}
			while(true) {
				if(!source.hasNext()) {
					if(!buffer.isEmpty()) {
						buffered = arrayFromBuffer(env, buffer);
						buffer.clear();
						hasBuffered = true;
						done = true;
						return true;
					}
					done = true;
					return false;
				}
				buffer.addLast(source.next());
				if(buffer.size()==chunkSize) {
					buffered = arrayFromBuffer(env, buffer);
					buffer.clear();
					hasBuffered = true;
					return true;
				}
			}
		}

		@Override
		public boolean hasNext() {
			return advance();
		}

		@Override
		public Object next() {
			if(!advance()) {
				throw new NoSuchElementException();
			}
			hasBuffered = false;
			return buffered;
		}
	}
}
