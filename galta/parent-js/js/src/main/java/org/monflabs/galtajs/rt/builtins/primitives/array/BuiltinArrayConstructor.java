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
package org.monflabs.galtajs.rt.builtins.primitives.array;

import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import java.util.Iterator;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.builtins.errors.TypeError;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitiveConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;
import org.monflabs.galtajs.util.SparseList;

/**
 * Array constructor.
 */
public class BuiltinArrayConstructor extends BasePrimitiveConstructor {
	
	public static final String CLASSNAME = "Array";


	public BuiltinArrayConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinArrayPrototype.get(env),1);
		setOwnMethod(new Method(env,MethodId.from,1));
		setOwnMethod(new Method(env,MethodId.fromAsync,1));
		setOwnMethod(new Method(env,MethodId.isArray,1));
		setOwnMethod(new Method(env,MethodId.of,0));

		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return JSArray.class;
	}

	@Override
	public Object constructObject(@NonNull Object[] parameters, Constructor topConstructor) {
		int count = parameters.length;
		if(count==1 && (parameters[0] instanceof Number num) && !RuntimeUtil.isBoxedNumber(getEnvironment(),num)) {
			// Spec: "If len is a Number and ToUint32(len) is not equal to
			// len, a RangeError exception is thrown" - Number.longValue()
			// silently maps NaN to 0 (matching ToUint32(NaN)'s own 0
			// result, but NaN!==0, so this specific case still must
			// throw), which this check was previously missing entirely
			// (confirmed via S15.4.2.2_A2.2_T2.js: `new Array(NaN)` must
			// throw RangeError, not silently create a length-0 array).
			double numberLen = num.doubleValue();
			long size = RuntimeUtil.toUInt32(numberLen);
	    	if(size!=numberLen || size>SparseList.MAX_LENGTH) {
	    		throw RuntimeUtil.rangeError("Invalid array length");
	    	}
			return applyNewTargetPrototype(JSArray.createSparse(getEnvironment(),size), topConstructor);
		} else {
			JSArray a = JSArray.create(getEnvironment());
			for(int i=0; i<count; i++) {
				a.arrayAdd(parameters[i]);
			}
			return applyNewTargetPrototype(a, topConstructor);
		}
	}

	private static enum MethodId {
		from,
		fromAsync,
		isArray,
		of,
		// Symbols
		species(Symbol.SPECIES)
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
		protected Object invoke(final Object obj, final @NonNull Object[] args) {
	        switch(methodId) {
	        	case from -> {
                    Object arrayLike = param(args, 0);
                    // Spec: GetMethod(items,@@iterator) -> GetV(items,...) ->
                    // ? ToObject(items) - null/undefined are rejected by
                    // ToObject itself with a TypeError, before any iterator
                    // or array-like handling is even attempted (confirmed
                    // via from/items-is-null-throws.js: previously fell
                    // through to the "no arrayLike" branch and silently
                    // returned an empty array instead of throwing).
                    if(RuntimeUtil.isNullOrUndefined(arrayLike)) {
                    	throw RuntimeUtil.typeError("Array.from called on null or undefined");
                    }
                    Callable function = paramCallable(args, 1, null);
                    Object thisArg = param(args, 2, RuntimeUtil.UNDEFINED);
                    // Spec: "If IsConstructor(C) is true, let A be
                    // Construct(C)." - Array.from must use the RECEIVER as
                    // constructor too (same pattern as Array.of, fixed
                    // earlier this session - confirmed via
                    // from/iter-cstm-ctor.js and its siblings). Writes are
                    // CreateDataPropertyOrThrow via an explicit counter
                    // (not arrayAdd's implicit-append, which breaks if the
                    // constructed A already has properties/length).
                    boolean isCtor = obj instanceof Constructor c && c.isConstructor();
                    if(arrayLike!=null) {
                    	// Spec step order: "If usingIterator is not
                    	// undefined, then if IsConstructor(C), let A be ?
                    	// Construct(C)" happens BEFORE the iterator is
                    	// actually obtained/invoked via
                    	// GetIteratorFromMethod - a throwing custom
                    	// constructor must fire even if the iterator
                    	// itself would later prove invalid (confirmed via
                    	// iter-cstm-ctor-err.js). Peek at the raw
                    	// @@iterator property (a Get, not a call) purely to
                    	// decide which branch this is - the real
                    	// call/validation still happens via
                    	// valueIteratorUnchecked below, after Construct(C).
                    	Object itFactoryPeek = getEnvironment().getAccessor(arrayLike).getProperty(arrayLike, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
                    	boolean usingIterator = itFactoryPeek!=RuntimeUtil.NOT_AVAILABLE && itFactoryPeek!=null && itFactoryPeek!=RuntimeUtil.UNDEFINED;
        				if(usingIterator) {
        					Object resultObj = isCtor ? ((Constructor)obj).constructObject(RuntimeUtil.EMPTY_PARAMS, (Constructor)obj) : JSArray.create(getEnvironment());
        					Iterator<Object> it = RuntimeUtil.valueIteratorUnchecked(getEnvironment(),arrayLike);
        					// Not every legitimate "object" result is a JSObject
        					// Java instance - e.g. Array.from.call(Object, ...)
        					// constructs via Object(len), which for a Number
        					// argument returns a boxed-primitive value tracked
        					// via the environment's primitive property map
        					// rather than a distinct JSObject-implementing
        					// wrapper (confirmed via
        					// from/source-object-missing.js). Write through
        					// the generic accessor instead of casting, so both
        					// shapes work.
        					if(!RuntimeUtil.isObject(getEnvironment(), resultObj)) {
        						throw RuntimeUtil.typeError("Array.from: constructor did not return an object");
        					}
        					JSAccessor resultAcc = getEnvironment().getAccessor(resultObj);
	                    	// Spec steps 6.g.vii-x: an abrupt completion from
	                    	// EITHER the map function call OR
	                    	// CreateDataPropertyOrThrow must IteratorClose the
	                    	// source iterator (calling its "return" method
	                    	// exactly once) before propagating - confirmed via
	                    	// iter-map-fn-err.js/iter-set-elem-prop-err.js.
	                    	try {
		                    	for( int i=0; it.hasNext(); i++ ) {
		                    		Object v = it.next();
		                    		if(function!=null) {
		                                v = function.call(thisArg, v, i);
		                    		}
		                    		resultAcc.setOwnProperty(resultObj,i,v,PropertyDescriptor.DESC_PROP_ARRAYINDEX,DESC_CHECK.STRICT,resultObj);
		                    	}
	                    	} catch(RuntimeException|Error e) {
	                    		RuntimeUtil.iteratorCloseQuietly(getEnvironment(), it);
	                    		throw e;
	                    	}
	                    	JSArray resultArr = RuntimeUtil.getArrayLikeUnchecked(getEnvironment(), resultObj, true);
	                    	if(resultArr!=null) {
	                    		resultArr.arraySetLength(resultArr.arrayLength(), DESC_CHECK.STRICT);
	                    	}
	                    	return resultObj;
        				} else {
                        	JSArray a = RuntimeUtil.getArrayLikeUnchecked(getEnvironment(), arrayLike, true);
                        	long len = a!=null ? a.arrayLength() : 0;
                        	Object resultObj = isCtor ? ((Constructor)obj).constructObject(new Object[]{Long.valueOf(len)}, (Constructor)obj) : JSArray.create(getEnvironment());
                        	if(!RuntimeUtil.isObject(getEnvironment(), resultObj)) {
                        		throw RuntimeUtil.typeError("Array.from: constructor did not return an object");
                        	}
                        	JSAccessor resultAcc = getEnvironment().getAccessor(resultObj);
                        	if(a!=null) {
	                    		Object finalThis = thisArg;
	                    		a.arrayForEach( (i,v) -> {
		                    		if(function!=null) {
		                                v = function.call(finalThis, v, i);
		                    		}
		                    		resultAcc.setOwnProperty(resultObj,i,v,PropertyDescriptor.DESC_PROP_ARRAYINDEX,DESC_CHECK.STRICT,resultObj);
	                    		} , true, RuntimeUtil.UNDEFINED);
                        	}
                        	JSArray resultArr = RuntimeUtil.getArrayLikeUnchecked(getEnvironment(), resultObj, true);
                        	if(resultArr!=null) {
                        		resultArr.arraySetLength(len, DESC_CHECK.STRICT);
                        	}
                        	return resultObj;
        				}
                    }
                    return isCtor ? ((Constructor)obj).constructObject(new Object[]{0L}, (Constructor)obj) : JSArray.create(getEnvironment());
	        	}
	        	case fromAsync -> {
	        		// Spec 23.1.2.1: async-iterator-consuming, Promise-returning
	        		// sibling of Array.from - genuinely constructible source
	        		// consumption (async-iterable / sync-iterable / array-like,
	        		// each value and each mapfn result individually awaited)
	        		// via BuiltinPromise's own then_() continuation machinery,
	        		// not real language-level await (this is native Java code).
	        		// `flags:[async]` test262 files run for real in this
	        		// harness (see Test262BaseTest's own $DONE()/async
	        		// support) and DO exercise this implementation's actual
	        		// iteration behavior, not just its shape.
	        		JSEnvironment env = getEnvironment();
	        		Object items = param(args, 0);
	        		Object thisArg = param(args, 2, RuntimeUtil.UNDEFINED);
	        		BuiltinPromise resultPromise = new BuiltinPromise(env);
	        		try {
	        			// Spec step 3 wraps the ENTIRE algorithm body - including
	        			// the "IsCallable(mapfn)" check below - in an Abstract
	        			// Closure run via AsyncFunctionStart, so an uncallable
	        			// mapfn must reject the returned promise, not throw
	        			// synchronously out of this native call. Confirmed via
	        			// mapfn-not-callable.js, which asserts the TypeError
	        			// arrives asynchronously (assert.throwsAsync).
	        			Callable mapfn = paramCallable(args, 1, null);
	        			if(RuntimeUtil.isNullOrUndefined(items)) {
	        				throw RuntimeUtil.typeError("Array.fromAsync called on null or undefined");
	        			}
	        			boolean isCtor = obj instanceof Constructor c && c.isConstructor();
	        			JSAccessor itemsAcc = env.getAccessor(items);
	        			Object asyncIterFn = itemsAcc.getProperty(items, Symbol.ASYNC_ITERATOR, RuntimeUtil.NOT_AVAILABLE);
	        			if(asyncIterFn!=RuntimeUtil.NOT_AVAILABLE && !RuntimeUtil.isNullOrUndefined(asyncIterFn)) {
	        				if(!BuiltinUtil.isCallable(asyncIterFn)) {
	        					throw RuntimeUtil.typeError("Array.fromAsync: @@asyncIterator is not a function");
	        				}
	        				// ArrayCreate(0) for the iterable/async-iterable
	        				// cases (spec 23.1.2.1 step 6.b.iii/8.a) - the
	        				// constructor is called with NO arguments, unlike
	        				// the array-like case below.
	        				Object resultObj = isCtor ? ((Constructor)obj).constructObject(RuntimeUtil.EMPTY_PARAMS, (Constructor)obj) : JSArray.create(env);
	        				if(!RuntimeUtil.isObject(env, resultObj)) {
	        					throw RuntimeUtil.typeError("Array.fromAsync: constructor did not return an object");
	        				}
	        				Object asyncIterator = ((Callable)asyncIterFn).call(items, RuntimeUtil.EMPTY_PARAMS);
	        				fromAsyncNextAsync(env, asyncIterator, resultObj, mapfn, thisArg, 0L, resultPromise);
	        				return resultPromise;
	        			}
	        			Object syncIterFn = itemsAcc.getProperty(items, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
	        			if(syncIterFn!=RuntimeUtil.NOT_AVAILABLE && !RuntimeUtil.isNullOrUndefined(syncIterFn)) {
	        				Object resultObj = isCtor ? ((Constructor)obj).constructObject(RuntimeUtil.EMPTY_PARAMS, (Constructor)obj) : JSArray.create(env);
	        				if(!RuntimeUtil.isObject(env, resultObj)) {
	        					throw RuntimeUtil.typeError("Array.fromAsync: constructor did not return an object");
	        				}
	        				JSAccessor resultAcc = env.getAccessor(resultObj);
	        				// Reuse the already-read syncIterFn (spec step 7)
	        				// rather than re-reading @@iterator a second time -
	        				// confirmed observable via asyncitems-iterator-
	        				// {exists,promise}.js.
	        				Iterator<Object> it = RuntimeUtil.valueIteratorUnchecked(env, items, syncIterFn);
	        				fromAsyncNextSync(env, it, resultObj, resultAcc, mapfn, thisArg, 0L, resultPromise);
	        				return resultPromise;
	        			}
	        			// Spec step 10: "Let arrayLike be ? ToObject(items)."
	        			// - a primitive `items` (BigInt/Boolean/Number/
	        			// Symbol - never null/undefined, already rejected
	        			// above) must be auto-boxed so its WRAPPER object's
	        			// own prototype-chain properties (length/indices) are
	        			// what gets iterated, not rejected outright as "not
	        			// an array" (confirmed via test262's own asyncitems-
	        			// {bigint,boolean,number,symbol}.js, which each set
	        			// indexed properties directly on e.g. BigInt.prototype).
	        			JSArray a = RuntimeUtil.getArrayLike(env, RuntimeUtil.toObject(env, items));
	        			long len = a.arrayLength();
	        			// ArrayCreate(len)'s own precondition (spec 10.4.2.2
	        			// step 1): length > 2**32-1 must be rejected BEFORE
	        			// any iteration starts, not discovered partway
	        			// through - confirmed needed via
	        			// asyncitems-arraylike-too-long.js, which otherwise
	        			// has this loop actually attempt ~4 billion
	        			// iterations instead of failing fast.
	        			if(len>SparseList.MAX_LENGTH) {
	        				throw RuntimeUtil.rangeError("Invalid array length {0}", len);
	        			}
	        			// Unlike the iterable cases above, the array-like path
	        			// (spec 23.1.2.1 step 10.a) constructs with the KNOWN
	        			// length as its own argument (`new C(len)`, matching
	        			// Array.from's own array-like handling just above in
	        			// this same switch) - confirmed via test262's own
	        			// this-constructor.js ("constructor is called with a
	        			// length argument").
	        			Object resultObj = isCtor ? ((Constructor)obj).constructObject(new Object[]{len}, (Constructor)obj) : JSArray.create(env);
	        			if(!RuntimeUtil.isObject(env, resultObj)) {
	        				throw RuntimeUtil.typeError("Array.fromAsync: constructor did not return an object");
	        			}
	        			JSAccessor resultAcc = env.getAccessor(resultObj);
	        			fromAsyncNextArrayLike(env, a, len, resultObj, resultAcc, mapfn, thisArg, 0L, resultPromise);
	        		} catch(RuntimeException e) {
	        			resultPromise.reject(JSRuntimeException.exceptionObject(e));
	        		}
	        		return resultPromise;
	        	}
	        	case isArray -> {
                    Object a = param(args, 0);
                    // Spec: Array.isArray(arg) is `? IsArray(arg)` - unlike a
                    // plain `instanceof JSArray` check, a Proxy must be
                    // resolved to its ultimate target (recursively), and a
                    // revoked proxy in the chain is itself a TypeError.
                    return RuntimeUtil.isArray(a);
	        	}
	        	case of -> {
	        		// Spec: "Let C be the this value. If IsConstructor(C),
	        		// let A be ? Construct(C, «len»)." - Array.of must use the
	        		// RECEIVER as the constructor (supporting a subclass
	        		// calling Array.of.call(SubClass, ...) and getting back a
	        		// SubClass instance), not always a plain Array - the same
	        		// pattern already fixed for %TypedArray%.of earlier this
	        		// session (confirmed via of/return-a-custom-instance.js,
	        		// of/sets-length.js). isConstructor() (not just
	        		// `instanceof Constructor`) matters: a non-constructible
	        		// builtin still implements the Java interface for call
	        		// dispatch (confirmed via of/return-a-new-array-object.js's
	        		// `Array.of.call(Math.cos.bind(Math))`, which must fall
	        		// back to a plain array, not throw). The constructed
	        		// result also need not be a genuine JSArray - any
	        		// ordinary object works, wrapped generically for the
	        		// index/length writes below (confirmed via
	        		// of/does-not-use-set-for-indices.js, whose custom
	        		// constructor returns a plain object). Each index write
	        		// is CreateDataPropertyOrThrow - an own-property DEFINE
	        		// (bypasses the prototype chain and overwrites a
	        		// non-writable-but-configurable own property) - done
	        		// directly via the constructed object's own
	        		// setOwnProperty, NOT the array-wrapper's arraySet/[[Set]]
	        		// path (confirmed via
	        		// of/does-not-use-prototype-properties.js and
	        		// of/does-not-use-set-for-indices.js).
	        		int argsCount = args.length;
	        		Object resultObj = (obj instanceof Constructor c && c.isConstructor())
	        			? c.constructObject(new Object[]{Long.valueOf(argsCount)}, c)
	        			: JSArray.create(getEnvironment());
	        		// A custom constructor's result need not be JSObject-backed
	        		// at all - a Proxy (BuiltinProxy implements neither JSObject
	        		// nor JSObjectDelegate) is just as valid, and its own
	        		// "defineProperty" trap must genuinely fire for each index
	        		// write below (confirmed via
	        		// of/return-abrupt-from-data-property-using-proxy.js). Route
	        		// through the generic accessor instead of hard-casting to
	        		// JSObject.
	        		if(!RuntimeUtil.isObject(getEnvironment(), resultObj)) {
	        			throw RuntimeUtil.typeError("Array.of: constructor did not return an object");
	        		}
	        		JSAccessor resultAcc = getEnvironment().getAccessor(resultObj);
	        		for(int i=0; i<argsCount; i++) {
	        			Object v = param(args, i);
	        			// The 2-arg setOwnProperty(index,value) overload used
	        			// here previously defaults to desc=null/check=NONE -
	        			// a [[Set]]-flavored, silently-non-throwing write,
	        			// NOT CreateDataPropertyOrThrow (confirmed via
	        			// of/does-not-use-set-for-indices.js, whose
	        			// non-writable own "0" must still be overwritten, and
	        			// of/return-abrupt-from-data-property.js, whose
	        			// non-extensible/non-configurable receiver must
	        			// throw TypeError instead of silently no-oping).
	        			resultAcc.setOwnProperty(resultObj, i, v, PropertyDescriptor.DESC_PROP_ARRAYINDEX, DESC_CHECK.STRICT, resultObj);
	        		}
	        		JSArray result = RuntimeUtil.getArrayLikeUnchecked(getEnvironment(), resultObj, true);
	        		if(result!=null) {
	        			result.arraySetLength(argsCount, DESC_CHECK.STRICT);
	        		}
	        		return resultObj;
	        	}
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }

		//
		// Array.fromAsync helpers
		//

		private void finishFromAsync(JSEnvironment env, Object resultObj, long finalLen, BuiltinPromise resultPromise) {
			JSArray resultArr = RuntimeUtil.getArrayLikeUnchecked(env, resultObj, true);
			if(resultArr!=null) {
				resultArr.arraySetLength(finalLen, DESC_CHECK.STRICT);
			}
			resultPromise.fulfill(resultObj);
		}

		// Awaits `rawValue`, then (if a mapfn was given) applies it and awaits
		// ITS result too, stores the final value at `index` via
		// CreateDataPropertyOrThrow, then invokes `continueNext` - each step
		// via then_()'s own microtask scheduling, so a long source iterates
		// without growing the Java call stack. `closeSource` (null for the
		// array-like source, which has no iterator) is invoked, best-effort,
		// on every abrupt-completion path here - per spec, awaiting a value
		// the iterator produced, calling mapfn, awaiting mapfn's result, or
		// storing the final value ALL require closing the source iterator on
		// failure (IfAbruptCloseIterator/IfAbruptCloseAsyncIterator) - unlike
		// the iterator's OWN next()/return() throwing, which is the close
		// signal itself and must not trigger a second close.
		private void fromAsyncStoreValue(JSEnvironment env, Object rawValue, Object resultObj, JSAccessor resultAcc, Callable mapfn, Object thisArg, long index, BuiltinPromise resultPromise, Runnable closeSource, Runnable continueNext) {
			fromAsyncStoreValue(env, rawValue, resultObj, resultAcc, mapfn, thisArg, index, resultPromise, closeSource, continueNext, true);
		}

		// `awaitRawValue`: per spec, a genuine async-iterator source (spec's
		// unified "iteratorRecord" loop, step "Let nextValue be ?
		// IteratorValue(next)") does NOT separately await the extracted
		// value - only the promise returned by next() itself was awaited.
		// The array-like path (step "Set kValue to ? Await(kValue)") and the
		// sync-iterator-adapted path (the extra await happens inside
		// %AsyncFromSyncIteratorPrototype%'s own internal value-wrapping,
		// which this engine simulates here since it drives a plain Java
		// Iterator directly rather than a real wrapper object) both DO await
		// the raw value. Confirmed via async-iterable-input-does-not-await-
		// input.js (async-iterator source yielding a Promise value, which
		// must come out UNawaited) vs asyncitems-iterator-promise.js
		// (sync-generator source yielding Promise values, which must be
		// awaited/unwrapped).
		private void fromAsyncStoreValue(JSEnvironment env, Object rawValue, Object resultObj, JSAccessor resultAcc, Callable mapfn, Object thisArg, long index, BuiltinPromise resultPromise, Runnable closeSource, Runnable continueNext, boolean awaitRawValue) {
			if(!awaitRawValue) {
				fromAsyncMapAndStore(env, rawValue, resultObj, resultAcc, mapfn, thisArg, index, resultPromise, closeSource, continueNext);
				return;
			}
			BuiltinPromiseConstructor.resolve(env, rawValue).then_(
				(t, valArgs) -> {
					Object value = valArgs.length>0 ? valArgs[0] : RuntimeUtil.UNDEFINED;
					fromAsyncMapAndStore(env, value, resultObj, resultAcc, mapfn, thisArg, index, resultPromise, closeSource, continueNext);
					return RuntimeUtil.UNDEFINED;
				},
				(t, errArgs) -> {
					closeQuietly(closeSource);
					resultPromise.reject(errArgs.length>0 ? errArgs[0] : RuntimeUtil.UNDEFINED);
					return RuntimeUtil.UNDEFINED;
				}
			);
		}

		private void fromAsyncMapAndStore(JSEnvironment env, Object value, Object resultObj, JSAccessor resultAcc, Callable mapfn, Object thisArg, long index, BuiltinPromise resultPromise, Runnable closeSource, Runnable continueNext) {
			if(mapfn==null) {
				storeAndContinue(resultObj, resultAcc, index, value, resultPromise, closeSource, continueNext);
				return;
			}
			Object mapped;
			try {
				mapped = mapfn.call(thisArg, new Object[] {value, index});
			} catch(RuntimeException e) {
				closeQuietly(closeSource);
				resultPromise.reject(JSRuntimeException.exceptionObject(e));
				return;
			}
			BuiltinPromiseConstructor.resolve(env, mapped).then_(
				(t2, mappedArgs) -> {
					Object finalValue = mappedArgs.length>0 ? mappedArgs[0] : RuntimeUtil.UNDEFINED;
					storeAndContinue(resultObj, resultAcc, index, finalValue, resultPromise, closeSource, continueNext);
					return RuntimeUtil.UNDEFINED;
				},
				(t2, errArgs) -> {
					closeQuietly(closeSource);
					resultPromise.reject(errArgs.length>0 ? errArgs[0] : RuntimeUtil.UNDEFINED);
					return RuntimeUtil.UNDEFINED;
				}
			);
		}

		private void storeAndContinue(Object resultObj, JSAccessor resultAcc, long index, Object value, BuiltinPromise resultPromise, Runnable closeSource, Runnable continueNext) {
			try {
				resultAcc.setOwnProperty(resultObj, index, value, PropertyDescriptor.DESC_PROP_ARRAYINDEX, DESC_CHECK.STRICT, resultObj);
				continueNext.run();
			} catch(RuntimeException e) {
				closeQuietly(closeSource);
				resultPromise.reject(JSRuntimeException.exceptionObject(e));
			}
		}

		private static void closeQuietly(Runnable closeSource) {
			if(closeSource==null) {
				return;
			}
			try {
				closeSource.run();
			} catch(RuntimeException ignore) {
			}
		}

		private void fromAsyncNextArrayLike(JSEnvironment env, JSArray a, long len, Object resultObj, JSAccessor resultAcc, Callable mapfn, Object thisArg, long index, BuiltinPromise resultPromise) {
			if(index>=len) {
				finishFromAsync(env, resultObj, len, resultPromise);
				return;
			}
			Object rawValue = a.getProperty(index, RuntimeUtil.UNDEFINED);
			fromAsyncStoreValue(env, rawValue, resultObj, resultAcc, mapfn, thisArg, index, resultPromise, null,
				() -> fromAsyncNextArrayLike(env, a, len, resultObj, resultAcc, mapfn, thisArg, index+1, resultPromise));
		}

		private void fromAsyncNextSync(JSEnvironment env, Iterator<Object> it, Object resultObj, JSAccessor resultAcc, Callable mapfn, Object thisArg, long index, BuiltinPromise resultPromise) {
			boolean hasNext;
			Object rawValue;
			try {
				hasNext = it.hasNext();
				rawValue = hasNext ? it.next() : null;
			} catch(RuntimeException e) {
				resultPromise.reject(JSRuntimeException.exceptionObject(e));
				return;
			}
			if(!hasNext) {
				finishFromAsync(env, resultObj, index, resultPromise);
				return;
			}
			fromAsyncStoreValue(env, rawValue, resultObj, resultAcc, mapfn, thisArg, index, resultPromise,
				() -> RuntimeUtil.iteratorCloseQuietly(env, it),
				() -> fromAsyncNextSync(env, it, resultObj, resultAcc, mapfn, thisArg, index+1, resultPromise));
		}

		private void fromAsyncNextAsync(JSEnvironment env, Object asyncIterator, Object resultObj, Callable mapfn, Object thisArg, long index, BuiltinPromise resultPromise) {
			JSAccessor resultAcc = env.getAccessor(resultObj);
			Object nextFn = env.getAccessor(asyncIterator).getProperty(asyncIterator, "next", RuntimeUtil.NOT_AVAILABLE);
			if(!BuiltinUtil.isCallable(nextFn)) {
				resultPromise.reject(new TypeError(env, "Array.fromAsync: iterator.next is not a function"));
				return;
			}
			Object nextResult;
			try {
				nextResult = ((Callable)nextFn).call(asyncIterator, RuntimeUtil.EMPTY_PARAMS);
			} catch(RuntimeException e) {
				resultPromise.reject(JSRuntimeException.exceptionObject(e));
				return;
			}
			BuiltinPromiseConstructor.resolve(env, nextResult).then_(
				(t, resArgs) -> {
					Object iterResult = resArgs.length>0 ? resArgs[0] : RuntimeUtil.UNDEFINED;
					if(!RuntimeUtil.isObject(env, iterResult)) {
						resultPromise.reject(new TypeError(env, "Array.fromAsync: iterator result is not an object"));
						return RuntimeUtil.UNDEFINED;
					}
					JSAccessor irAcc = env.getAccessor(iterResult);
					boolean done = RuntimeUtil.toBoolean(env, irAcc.getProperty(iterResult, "done", false));
					if(done) {
						finishFromAsync(env, resultObj, index, resultPromise);
						return RuntimeUtil.UNDEFINED;
					}
					Object rawValue = irAcc.getProperty(iterResult, "value", RuntimeUtil.UNDEFINED);
					fromAsyncStoreValue(env, rawValue, resultObj, resultAcc, mapfn, thisArg, index, resultPromise,
						() -> RuntimeUtil.iteratorClose(env, asyncIterator),
						() -> fromAsyncNextAsync(env, asyncIterator, resultObj, mapfn, thisArg, index+1, resultPromise),
						false);
					return RuntimeUtil.UNDEFINED;
				},
				(t, errArgs) -> {
					resultPromise.reject(errArgs.length>0 ? errArgs[0] : RuntimeUtil.UNDEFINED);
					return RuntimeUtil.UNDEFINED;
				}
			);
		}
	}

}
