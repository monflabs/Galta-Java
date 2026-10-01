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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitivePrototype;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypeArrayIterator;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.json.JsonArray;
import org.monflabs.util.iterators.Iterators;


/**
 * Eqv of the JavaScript Array prototype.
 */
public class BuiltinArrayPrototype extends BasePrimitivePrototype {

	// The %Array.prototype.values% intrinsic (spec: also aliased as
	// %ArrayProto_values%/Array.prototype[Symbol.iterator]) - captured ONCE
	// here, at realm-creation time, so callers needing the genuine spec
	// INTRINSIC (e.g. Arguments' own [Symbol.iterator], which per spec 9.4.4
	// CreateUnmappedArgumentsObject/CreateMappedArgumentsObject is always
	// %Array.prototype.values% regardless of what Array.prototype[Symbol.
	// iterator]/.values happen to hold LATER) can use this fixed reference
	// instead of re-reading Array.prototype's own (mutable, user-
	// reassignable) property every time. See getValuesMethod().
	private final BaseMethod valuesMethod;

	public static BuiltinArrayPrototype get(JSEnvironment env) {
		BuiltinArrayPrototype proto = (BuiltinArrayPrototype)env.getRegisteredPrototype(BuiltinArrayPrototype.class);
		if(proto==null) {
			proto = new BuiltinArrayPrototype(env);
			env.registerPrototype(BuiltinArrayPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinArrayPrototype(JSEnvironment env) {
		super(env);
		// Length is an instance property of an array, not its prototype
		// but we still define it here as an Array prototype should be an array
		setOwnProperty("length",0,PropertyDescriptor.DESC_HIDDEN_PROP);
	
		setOwnMethod(new Method(env,MethodId.at,1));
		setOwnMethod(new Method(env,MethodId.concat,1));
		setOwnMethod(new Method(env,MethodId.copyWithin,2));
		setOwnMethod(new Method(env,MethodId.entries,0));
		setOwnMethod(new Method(env,MethodId.every,1));
		setOwnMethod(new Method(env,MethodId.fill,1));
		setOwnMethod(new Method(env,MethodId.filter,1));
		setOwnMethod(new Method(env,MethodId.find,1));
		setOwnMethod(new Method(env,MethodId.findIndex,1));
		setOwnMethod(new Method(env,MethodId.findLast,1));
		setOwnMethod(new Method(env,MethodId.findLastIndex,1));
		setOwnMethod(new Method(env,MethodId.flat,0));
		setOwnMethod(new Method(env,MethodId.flatMap,1));
		setOwnMethod(new Method(env,MethodId.forEach,1));
		setOwnMethod(new Method(env,MethodId.includes,1));
		setOwnMethod(new Method(env,MethodId.indexOf,1));
		setOwnMethod(new Method(env,MethodId.join,1));
		setOwnMethod(new Method(env,MethodId.keys,0));
		setOwnMethod(new Method(env,MethodId.lastIndexOf,1));
		setOwnMethod(new Method(env,MethodId.map,1));
		setOwnMethod(new Method(env,MethodId.pop,0));
		setOwnMethod(new Method(env,MethodId.push,1));
		setOwnMethod(new Method(env,MethodId.reduce,1));
		setOwnMethod(new Method(env,MethodId.reduceRight,1));
		setOwnMethod(new Method(env,MethodId.reverse,0));
		setOwnMethod(new Method(env,MethodId.shift,0));
		setOwnMethod(new Method(env,MethodId.slice,2));
		setOwnMethod(new Method(env,MethodId.some,1));
		setOwnMethod(new Method(env,MethodId.sort,1));
		setOwnMethod(new Method(env,MethodId.splice,2));
		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		setOwnMethod(new Method(env,MethodId.toReversed,0));
		setOwnMethod(new Method(env,MethodId.toSorted,1));
		setOwnMethod(new Method(env,MethodId.toSpliced,2));
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.unshift,1));
		Method values = new Method(env,MethodId.values,0);
		setOwnMethod(values);
		setOwnAlias(MethodId.values.id,MethodId.iterator.id);
		this.valuesMethod = values;
		setOwnMethod(new Method(env,MethodId.with,2));
		
		// Spec: "Let unscopableList be ObjectCreate(null)" - a genuinely
		// null-prototype object, not one inheriting from Object.prototype
		// (confirmed via Symbol.unscopables/value.js's
		// `Object.getPrototypeOf(unscopables) === null` assertion).
		JSObjectImpl unscopableList = JSObject.of(getEnvironment(),"at",
			true, "copyWithin",
			true, "entries",
			true, "fill",
			true, "find",
			true, "findIndex",
			true, "findLast",
			true, "findLastIndex",
			true, "flat",
			true, "flatMap",
			true, "includes",
			true, "keys",
			true, "toReversed",
			true, "toSorted",
			true, "toSpliced",
			true, "values",
			true
		);
		unscopableList.setPrototype(null);
		setOwnProperty(Symbol.UNSCOPABLES, unscopableList, PropertyDescriptor.DESC_PROP_UNSCOPABLE);
	}
	
	@Override
	public String getClassName() {
		return BuiltinArrayConstructor.CLASSNAME;
	}
	
	@Override
	public Class<?> getNativeClass() {
		return JsonArray.class;
	}

	// See the `valuesMethod` field's own doc.
	public BaseMethod getValuesMethod() {
		return valuesMethod;
	}

	// %Array.prototype% is genuinely an Array exotic object per spec
	// 23.1.3 - `Array.prototype[2] = 42` must auto-extend its own "length"
	// to 3, matching ArraySetLength/the Array exotic [[DefineOwnProperty]]
	// algorithm's index>=length case (9.4.2.1 steps 3.c/3.g). This engine
	// doesn't give %Array.prototype% genuine JSArray sparse/dense index
	// storage (BasePrimitivePrototype and JSArrayImpl sit in separate,
	// Java-single-inheritance-incompatible class hierarchies - a real
	// architectural constraint, not attempted) - but the auto-length-update
	// BEHAVIOR itself doesn't actually need that storage, just a hook on
	// the ALREADY-EXISTING generic string-keyed property write path this
	// class already has (its own methods are stored the same way). Confirmed
	// via prototype/exotic-array.js, which only ever accesses a handful of
	// small indices - real embedders relying on genuinely sparse index
	// storage directly on %Array.prototype% (a vanishingly rare pattern in
	// practice) would still not be served by this, but that's out of reach
	// without the class-hierarchy restructuring noted above.
	@Override
	public boolean setOwnProperty(String key, Object value, PropertyDescriptor desc, JSObject.DESC_CHECK check, Object receiver) {
		boolean result = super.setOwnProperty(key, value, desc, check, receiver);
		if(result) {
			long idx = RuntimeUtil.memberIndex(key);
			if(idx>=0) {
				long len = RuntimeUtil.toLong(getEnvironment(), getOwnProperty("length", 0L, this));
				if(idx>=len) {
					super.setOwnProperty("length", idx+1, null, JSObject.DESC_CHECK.NONE, this);
				}
			}
		}
		return result;
	}

	private static enum MethodId {
		at,
		concat,
		copyWithin,
		entries,
		every,
		fill,
		filter,
		find,
		findIndex,
		findLast,
		findLastIndex,
		flat,
		flatMap,
		forEach,
		includes,
		indexOf,
		join,
		keys,
		lastIndexOf,
		map,
		pop,
		push,
		reduce,
		reduceRight,
		reverse,
		shift,
		slice,
		some,
		sort,
		splice,
		toLocaleString,
		toReversed,
		toSorted,
		toSpliced,
		toString,
		unshift,
		values,
		with,
		// Symbols
		iterator(Symbol.ITERATOR)
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	
	// helpers
	private static long actualIndex(JSArray list, long index) {
		return actualIndex(list.arrayLength(), index);
	}
	private static long actualIndex(long len, long index) {
		return index<0 ? index + len : index;
	}
	private static long boundActualIndex(JSArray list, long index) {
		return boundActualIndex(list.arrayLength(), index);
	}
	// Callers that must observe "length" read exactly once, at a specific
	// point relative to other argument coercions (e.g. indexOf/lastIndexOf's
	// fromIndex), need to pass an already-cached length through here rather
	// than letting this re-invoke arrayLength() (and thus a user's length
	// getter) a second time out of order - confirmed via
	// indexOf/15.4.4.14-5-26.js, whose length getter throws if it observes
	// itself being called again after fromIndex's own coercion already ran.
	private static long boundActualIndex(long len, long index) {
		if(index<0) return 0;
		if(index>len) return len;
		return index;
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

		// `paramLong`/`RuntimeUtil.toLong` clamps +-Infinity to
		// Integer.MAX_VALUE/MIN_VALUE (a deliberate choice for callers that
		// only ever need int-range results) - WRONG for a fromIndex-style
		// argument being compared against a genuine array length, which can
		// legitimately exceed Integer.MAX_VALUE (up to 2**32-1 for a real
		// Array). Using the int-clamped value here previously went
		// unnoticed because the resulting incorrect (too-small) fromIndex
		// only manifested as a wrong answer AFTER walking billions of
		// indices - a call that hung (and was killed) before ever
		// producing output. Now that JSArrayImpl's sparse fast path makes
		// that walk fast, the wrong answer surfaces directly (confirmed via
		// indexOf/15.4.4.14-5-12.js/-16.js: `arr.indexOf(true, Infinity)`
		// on an array whose length is near 2**32).
		private long paramLongUnclamped(Object[] args, int pos, long defaultValue) {
			if(pos>=args.length) {
				return defaultValue;
			}
			Object a = args[pos];
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			double d = RuntimeUtil.toDouble(getEnvironment(), a);
			if(Double.isNaN(d)) {
				return 0;
			}
			if(d>=Long.MAX_VALUE) {
				return Long.MAX_VALUE;
			}
			if(d<=Long.MIN_VALUE) {
				return Long.MIN_VALUE;
			}
			return (long)d;
		}

	    @Override
		protected Object invoke(Object obj, final Object[] args) {
	    	if(RuntimeUtil.isNullOrUndefined(obj)) {
	    		throw nullThis();
	    	}
	    	// Spec's universal first step for every generic Array.prototype
	    	// method: "Let O be ? ToObject(this value)." - a primitive
	    	// receiver (e.g. `Array.prototype.every.call(false, fn)`) must be
	    	// auto-boxed into its wrapper object (whose OWN prototype chain,
	    	// e.g. Boolean.prototype, may supply "length"/indexed properties)
	    	// rather than being treated as "not array-like" and skipped
	    	// entirely (confirmed via every/15.4.4.16-1-3.js). A no-op for an
	    	// already-object receiver.
	    	final Object thisObj = RuntimeUtil.toObject(getEnvironment(), obj);

			final JSArray _this = RuntimeUtil.getArrayLikeUnchecked(getEnvironment(),thisObj,false);

	        switch(methodId) {
	        	case at -> {
	        		if(_this==null) {
	        			return RuntimeUtil.UNDEFINED;
	        		}
	        		// Spec: "Let len be ? LengthOfArrayLike(O)" happens BEFORE
	        		// "Let relativeIndex be ? ToIntegerOrInfinity(index)" - a
	        		// poisoned index argument that resizes the underlying
	        		// buffer mid-conversion (a TypedArray backed by a
	        		// resizable ArrayBuffer) must not change which length
	        		// `at` computes k against (confirmed via
	        		// at/coerced-index-resize.js). Read via a genuine
	        		// ToLength (2**53-1 cap), NOT _this.arrayLength()'s
	        		// iteration-safety-capped (Integer.MAX_VALUE) version -
	        		// `at` never iterates (a single indexed read), so
	        		// unlike indexOf/forEach/etc it's safe to accept a huge
	        		// array-like length without the O(n)-walk hang risk
	        		// that cap exists to prevent (confirmed via rhino's
	        		// es2020/array-at.js: `{length:0x80000001}.at(...)`
	        		// must not throw merely from reading a huge length).
	        		long len = RuntimeUtil.toLength(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), thisObj, "length"));
	        		long idx = RuntimeUtil.toLong(getEnvironment(),param(args, 0, 0L));
	        		if(idx<0) idx += len;
	        		if(idx<0 || idx>=len) {
	        			return RuntimeUtil.UNDEFINED;
	        		}
                    return _this.getProperty(idx,RuntimeUtil.UNDEFINED);
	        	}
	            case concat -> {
	            	JSArray newArray = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	            	
	            	Object[] params = new Object[args.length+1];
	            	params[0] = thisObj;
	            	System.arraycopy(args,0,params,1,args.length);
	            	
	            	// arrays or array-like objects with the property Symbol.isConcatSpreadable 
	            	// array default to true
	            	// array-like default to false
	    	    	int paramsCount = params.length;
	                for(int param=0; param<paramsCount; param++){
	                	Object a = params[param];
	                	if(param==0 && RuntimeUtil.isPrimitiveValue(getEnvironment(), a)) {
	                		a = RuntimeUtil.primitiveAsObject(getEnvironment(), a);
	                	}
	                	// IsConcatSpreadable step 1: "If Type(O) is not Object,
	                	// return false" - a raw, still-unboxed primitive
	                	// ARGUMENT (param>0; param==0/thisObj was already
	                	// boxed above) must short-circuit here with NO
	                	// @@isConcatSpreadable Get at all, since our engine's
	                	// generic getProperty auto-boxes for the lookup and
	                	// would otherwise observe a global
	                	// `Boolean.prototype[Symbol.isConcatSpreadable]`
	                	// override that a raw primitive value must never be
	                	// affected by (confirmed via
	                	// concat_spreadable-boolean-wrapper.js's final
	                	// `[].concat(true)` case: a boxed `new Boolean(true)`
	                	// legitimately DOES inherit that override, but the bare
	                	// primitive `true` must not).
	                	//
	                	// Otherwise, Get(@@isConcatSpreadable) happens FIRST;
	                	// Get can't distinguish "property absent" from "property
	                	// present with value undefined" - both must fall back to
	                	// spec's IsArray(O) (7.2.2, Proxy-aware - a plain `instanceof
	                	// JSArrayImpl` never unwraps a Proxy, and misses the
	                	// revoked-proxy TypeError) - the ordering matters since a
	                	// revoked-target Proxy's IsArray throw must happen AFTER the
	                	// @@isConcatSpreadable Get, not before.
	                	boolean spreadArg;
	                	if(RuntimeUtil.isPrimitiveValue(getEnvironment(), a)) {
	                		spreadArg = false;
	                	} else {
	                		Object symbArg = RuntimeUtil.getProperty(getEnvironment(), a, Symbol.IS_CONCAT_SPREDABLE, RuntimeUtil.NOT_AVAILABLE);
	                		spreadArg = (symbArg!=RuntimeUtil.NOT_AVAILABLE && symbArg!=RuntimeUtil.UNDEFINED)
	                				? RuntimeUtil.toBoolean(getEnvironment(), symbArg)
	                				: RuntimeUtil.isArray(a);
	                	}
                		if(spreadArg) {
                			long baseIndex = newArray.arrayLength();
    	                	JSArray al = RuntimeUtil.getArrayLike(getEnvironment(),a);
                			// Spec step 5.c.iii: "If n + len > 2^53-1, throw a
                			// TypeError exception" - checked BEFORE any
                			// element is copied. Without this, a spreadable
                			// with a huge declared length (e.g.
                			// Number.MAX_SAFE_INTEGER, no real elements) sent
                			// arrayForEach's emptyItems=true walk (which has
                			// no early-exit) into a genuine, REPRODUCED
                			// multi-billion-iteration hang (confirmed via
                			// concat/arg-length-exceeding-integer-limit.js).
                			if(baseIndex+al.arrayLength()>9007199254740991L) {
                				throw RuntimeUtil.typeError("Invalid array length {0}",baseIndex+al.arrayLength());
                			}
			            	al.arrayForEach( (idx,v) -> {
			            		if(v!=RuntimeUtil.NOT_AVAILABLE) { // In case of sparse arrays
			            			// CreateDataPropertyOrThrow - always throws on
			            			// failure (e.g. a non-extensible species-created
			            			// target), regardless of caller strict-mode-ness
			            			// (confirmed via concat/create-species-non-extensible.js
			            			// and its siblings).
			            			newArray.setOwnProperty(baseIndex+idx,v,PropertyDescriptor.DESC_PROP_ARRAYINDEX,JSObject.DESC_CHECK.STRICT);
			            		} else {
			            			newArray.arrayAddLength(1,JSObject.DESC_CHECK.STRICT);
			            		}
			            	}, true, RuntimeUtil.NOT_AVAILABLE);
                		} else {
                			newArray.setOwnProperty(newArray.arrayLength(),a,PropertyDescriptor.DESC_PROP_ARRAYINDEX,JSObject.DESC_CHECK.STRICT);
                		}
	                }
	                return newArray;
	            }
	        	case copyWithin -> {
	        		if(_this!=null) {
	                    // Spec steps 2-14: "len" is read exactly ONCE, before
	                    // target/start/end are coerced, and `to`/`from`/`final`
	                    // all clamp against that SAME frozen value - not a
	                    // fresh arrayLength() re-read per argument (which
	                    // would observe a coercion side effect's own array
	                    // mutation out of order, confirmed via
	                    // coerced-values-start-change-start.js/-target.js: a
	                    // `{valueOf: ...}` start argument that shrinks the
	                    // array mid-coercion must still have `to`/`from`
	                    // computed against the ORIGINAL length, with the
	                    // shrink only affecting the later copy loop's
	                    // HasProperty/Get/Set/Delete calls).
	                    long len = _this.arrayLength();
	                    long target = boundActualIndex(len,actualIndex(len,paramLong(args, 0, 0)));
	                    long start = boundActualIndex(len,actualIndex(len,paramLong(args, 1, 0)));
	                    long end = boundActualIndex(len,actualIndex(len,paramLong(args, 2, len)));
	                    long max = Math.min(end-start, len-target);
	                    // Spec steps 12.c/d/e: fromPresent is a genuine,
	                    // SEPARATE HasProperty(O, fromKey) MOP call - not a
	                    // combined "get and check NOT_AVAILABLE" - since a
	                    // Proxy's `has` trap must fire (and its exception
	                    // propagate) even when no `get` trap is defined at
	                    // all (confirmed via
	                    // copyWithin/return-abrupt-from-has-start.js). A
	                    // present source index is copied via Set, an ABSENT
	                    // one must DeletePropertyOrThrow the target index
	                    // instead - not silently become an own `undefined`
	                    // (confirmed via copyWithin/length-near-integer-limit.js:
	                    // source index startIndex+1 is a genuine hole, and the
	                    // target index it copies to must end up absent too,
	                    // `1 in arrayLike` false, not an own undefined).
	                    // dispatched on the ORIGINAL receiver (`thisObj`), not
	                    // `_this` (a wrapper with no trap-aware hasProperty of
	                    // its own).
	                    if(start>target) {
		                    for(long i=0; i<max; i++) {
		                    	if(RuntimeUtil.hasProperty(getEnvironment(), thisObj, start+i)) {
		                    		Object v = _this.getProperty(start+i,RuntimeUtil.UNDEFINED);
		                    		_this.setOwnProperty(target+i, v);
		                    	} else {
		                    		_this.arrayDelete(target+i,JSObject.DESC_CHECK.STRICT);
		                    	}
		                    }
	                    } else {
		                    for(long i=max-1; i>=0; i--) {
		                    	if(RuntimeUtil.hasProperty(getEnvironment(), thisObj, start+i)) {
		                    		Object v = _this.getProperty(start+i,RuntimeUtil.UNDEFINED);
		                    		_this.setOwnProperty(target+i, v);
		                    	} else {
		                    		_this.arrayDelete(target+i,JSObject.DESC_CHECK.STRICT);
		                    	}
		                    }
	                    }
	        		}
                    return thisObj; // Initial object, not _this (temporary array)
	        	}
	        	case entries -> {
	        		final Iterator<Object> it = _this!=null ? _this.arrayIterator() : Iterators.empty();
	        		Iterator<Object> entryIt = new Iterator<Object>() {
	        			private long current;
	        			@Override
	        			public boolean hasNext() {
	        				return it.hasNext();
	        			}
	        			@Override
	        			public Object next() {
	        				if(hasNext()) {
	        					long idx = current++;
	        					return JSArray.of(getEnvironment(),idx,it.next());
	        				}
        					return JSArray.of(getEnvironment(),current,null);
	        			}
	        			@Override
	        			public void remove() {
	        				it.remove();
	        			}
                    };
	        		// Spec: %ArrayIteratorPrototype%.next() (shared by Array
	        		// and %TypedArray% - both entries/keys/values are spec'd
	        		// as CreateArrayIterator) has a TypedArray-specific step
	        		// that throws once the iterated object is a TypedArray
	        		// whose view has gone detached/out-of-bounds - a plain
	        		// keys() iterator, which never reads element values,
	        		// must still throw (confirmed via
	        		// entries|keys|values/resizable-buffer*.js's
	        		// `Array.prototype.keys.call(typedArray)` case, which
	        		// must behave identically to %TypedArray%.prototype.keys
	        		// even though it's reached via the generic Array method,
	        		// not routed through a TypedArray-specific "case" of its
	        		// own).
	        		return thisObj instanceof TypedArray ta ? new TypeArrayIterator(getEnvironment(),ta,entryIt) : new BuiltinArrayIterator(getEnvironment(),entryIt);
	        	}
	        	case every -> {
	        		if(_this!=null) {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse
                    // for the iteration bound below, rather than letting
                    // arrayForEachWhile call arrayLength() again itself.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    boolean result = _this.arrayForEachWhile( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(!RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	return false;
	                        }
	                    	return true;
	                    }, 0, false, RuntimeUtil.UNDEFINED, len);
		    			return result;
	        		}
	        		return true;
	        	}
	        	case fill -> {
	        		if(_this!=null) {
	        			// `value` merely being OMITTED (as opposed to
	        			// explicitly `undefined`) must default, not throw
	        			// (confirmed via fill/call-with-boolean.js and
	        			// fill/return-this.js's `Array.prototype.fill.call(x)`
	        			// with no further arguments). Length cached once
	        			// before start/end coercion, same rationale as
	        			// elsewhere in this file.
	        			long len = _this.arrayLength();
	                    Object value = param(args, 0, RuntimeUtil.UNDEFINED);
	                    long start = boundActualIndex(len,actualIndex(len,paramLong(args, 1, 0)));
	                    long end = boundActualIndex(len,actualIndex(len,paramLong(args, 2, len)));
	                    for(long i=start; i<end; i++) {
	                    	_this.setOwnProperty(i, value);
	                    }
	        		}
                    return thisObj; // Initial object, not _this (temporary array)
	        	}
	        	case filter -> {
	        		JSArray result = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	        		if(_this!=null) {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    // Spec tracks `to` as an INDEPENDENT counter starting
	                    // at 0, not `result`'s own current length - a custom
	                    // species constructor's result can already have a
	                    // non-zero length/pre-existing own properties before
	                    // this loop even starts (confirmed via
	                    // target-array-with-non-writable-property.js, whose
	                    // species result already owns index 0), so appending
	                    // via arrayAdd (which appends AFTER the current
	                    // length) would target the wrong index; write to the
	                    // explicit counter instead via setOwnProperty, which
	                    // performs a genuine CreateDataProperty-style define
	                    // (overwrites even a non-writable-but-configurable
	                    // existing property).
	                    AtomicLong to = new AtomicLong(0);
	                    _this.arrayForEach( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	// CreateDataPropertyOrThrow - always throws on a
	                        	// non-extensible species-created target
	                        	// (confirmed via filter/target-array-non-extensible.js).
	                        	result.setOwnProperty(to.getAndIncrement(), v, PropertyDescriptor.DESC_PROP_ARRAYINDEX, JSObject.DESC_CHECK.STRICT);
	                        }
	                    }, false, RuntimeUtil.UNDEFINED, len);
	        		}
	    			return result;
	        	}
	        	case find -> {
	        		if(_this!=null) {
		        		AtomicReference<Object> result = new AtomicReference<>(RuntimeUtil.UNDEFINED);
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    // Omitted thisArg defaults to `undefined`, not Java
	                    // `null` - this engine treats the two as genuinely
	                    // distinct values, and a strict-mode predicate must
	                    // observe `this === undefined` (confirmed via
	                    // find/predicate-call-this-strict.js).
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    _this.arrayForEachWhile( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	result.set(v);
	                        	return false;
	                        }
	                    	return true;
	                    }, 0, true, RuntimeUtil.UNDEFINED, len);
		    			return result.get();
	        		}
	        		return RuntimeUtil.UNDEFINED;
	        	}
	        	case findIndex -> {
	        		if(_this!=null) {
		        		AtomicLong result = new AtomicLong(-1);
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    _this.arrayForEachWhile( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	result.set(i);
	                        	return false;
	                        }
	                    	return true;
	                    }, 0, true, RuntimeUtil.UNDEFINED, len);
		    			return result.get();
	        		}
	        		return -1;
	        	}
	        	case findLast -> {
	        		if(_this!=null) {
		        		AtomicReference<Object> result = new AtomicReference<>(RuntimeUtil.UNDEFINED);
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    _this.arrayForEachWhileReverse( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	result.set(v);
	                        	return false;
	                        }
	                    	return true;
	                    }, len, true, RuntimeUtil.UNDEFINED, len);
		    			return result.get();
	        		}
	        		return RuntimeUtil.UNDEFINED;
	        	}
	        	case findLastIndex -> {
	        		if(_this!=null) {
		        		AtomicLong result = new AtomicLong(-1);
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    _this.arrayForEachWhileReverse( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	result.set(i);
	                        	return false;
	                        }
	                    	return true;
	                    }, len, true, RuntimeUtil.UNDEFINED, len);
		    			return result.get();
	        		}
	        		return -1;
	        	}
	        	case flat -> {
	        		// Spec order: LengthOfArrayLike, then ToIntegerOrInfinity(depth),
	        		// THEN ArraySpeciesCreate (confirmed via
	        		// flat/proxy-access-count.js's exact expected trap-call log:
	        		// "length" precedes "constructor").
	        		if(_this!=null) {
	        			long len = _this.arrayLength();
	                    int depth = paramInt(args, 0, 1);
	                    JSArray result = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	                    flat(result, new AtomicLong(0), thisObj, len, depth);
	                    return result;
	        		}
	        		return RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	        	}
	        	case flatMap -> {
	        		if(_this!=null) {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse
                    // (a second read inside arrayForEach would violate a
                    // "length getter called exactly once" test, confirmed
                    // via flatMap/array-like-objects.js). ArraySpeciesCreate
                    // comes AFTER both the length read and the IsCallable
                    // check (confirmed via flatMap/proxy-access-count.js's
                    // exact expected trap-call log).
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    JSArray result = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    // Same explicit-counter requirement as flat() above -
	                    // `result`'s own length may already be non-zero.
	                    AtomicLong targetIndex = new AtomicLong(0);
	                    JSEnvironment env = getEnvironment();
	                    // Standalone (non-shared) two-step HasProperty-then-Get
	                    // loop, dispatched on `thisObj` (the receiver) - see
	                    // flat()'s matching comment for why this can't reuse
	                    // the shared arrayForEachWhile hole-skipping path
	                    // (confirmed via flatMap/proxy-access-count.js's exact
	                    // expected has/get trap-call logs).
	                    for(long i=0; i<len; i++) {
	                    	if(!RuntimeUtil.hasProperty(env, thisObj, i)) {
	                    		continue;
	                    	}
	                    	Object v = RuntimeUtil.getProperty(env, thisObj, i, RuntimeUtil.UNDEFINED);
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                    	// Spec's IsArray (7.2.2), not a plain `instanceof
	                    	// JSArray` - a Proxy wrapping an array must still be
	                    	// flattened one level, recursing through its own
	                    	// traps. flatMap's depth is always 1, so this
	                    	// recursive call (depth=0) flattens exactly the one
	                    	// level spec requires and no further.
	                    	if(RuntimeUtil.isArray(r)) {
	                    		long elementLen = RuntimeUtil.getArrayLike(env, r).arrayLength();
	                    		flat(result, targetIndex, r, elementLen, 0);
	                    	} else {
	                        	result.setOwnProperty(targetIndex.getAndIncrement(), r, PropertyDescriptor.DESC_PROP_ARRAYINDEX, JSObject.DESC_CHECK.STRICT);
	                    	}
	                    }
	                    return result;
	        		}
	    			return RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	        	}
	        	case forEach -> {
	        		if(_this!=null) {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    _this.arrayForEach( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        function.call(thisArg, cbArgs);
	                    }, false, RuntimeUtil.UNDEFINED, len);
	        		}
                    return RuntimeUtil.UNDEFINED;
	        	}
	        	case includes -> {
	        		if(_this!=null) {
	                    // Same length-once-cached-then-reused pattern as
	                    // indexOf/lastIndexOf.
	                    long len = _this.arrayLength();
	                    if(len==0) {
	                    	return false;
	                    }
	                    Object searchElement = param(args, 0, RuntimeUtil.UNDEFINED);
	                    long fromIndex = boundActualIndex(len,actualIndex(len,paramLongUnclamped(args, 1, 0)));
		        		AtomicLong found = new AtomicLong(-1);
	                    // Unlike indexOf/lastIndexOf (which use HasProperty-
	                    // gated [[Get]] and so may legitimately skip a
	                    // missing index), includes always does a raw Get
	                    // with no presence check at all - a missing index
	                    // (a real hole, OR a TypedArray index that's gone
	                    // out-of-bounds due to a resizable buffer shrinking
	                    // mid-coercion) must still be VISITED and treated as
	                    // `undefined`, never silently skipped - confirmed
	                    // via includes/coerced-searchelement-fromindex-
	                    // resize.js, where searching for `undefined` on a
	                    // now-fully-out-of-bounds TypedArray view must
	                    // return true (every in-range index reads as
	                    // undefined). Previously skipped (emptyItems=false)
	                    // specifically when searching for undefined - the
	                    // one case that most needed every index visited.
	                    _this.arrayForEachWhile( (i,v) -> {
	                    	if(RuntimeUtil.eqSameValueZero(getEnvironment(),searchElement,v)) {
	                    		found.set(i);
	                    		return false;
	                    	}
	                    	return true;
	                    }, fromIndex, true, RuntimeUtil.UNDEFINED, len );
		    			return found.get()>=0;
	        		}
	        		return false;
	        	}
	        	case indexOf -> {
	        		if(_this!=null) {
		            	{
		            		// Length must be checked (and the search short-circuited)
		            		// BEFORE fromIndex is coerced, so a poisoned fromIndex
		            		// valueOf() is never invoked against an empty array
		            		// (confirmed via indexOf/length-zero-returns-minus-one.js).
		            		// Cached and reused below (not re-read via arrayLength())
		            		// so a length getter is observably called exactly once,
		            		// before fromIndex's own coercion (confirmed via
		            		// indexOf/15.4.4.14-5-26.js). Unconditional now - the
		            		// previous `if(args.length>0)` guard wrongly skipped
		            		// the WHOLE search (not just the length read) for a
		            		// zero-argument call, when searchElement should
		            		// simply default to undefined (`[undefined].indexOf()`
		            		// must still find it at index 0, confirmed via
		            		// 15.4.4.14-9-b-ii-2.js).
		            		long len = _this.arrayLength();
		            		if(len==0) {
		            			return -1L;
		            		}
		                    Object searchElement = param(args, 0, RuntimeUtil.UNDEFINED);
		                    long fromIndex = boundActualIndex(len,actualIndex(len,paramLongUnclamped(args, 1, 0)));
		                    // Standalone (non-shared) two-step HasProperty-then-Get
		                    // loop, dispatched on the RECEIVER (thisObj, not an
		                    // unwrapped array-like) - see flat()'s matching comment
		                    // for why this can't reuse the shared arrayForEachWhile
		                    // hole-skipping path (confirmed via
		                    // indexOf/calls-only-has-on-prototype-after-length-zeroed.js).
		                    // ONLY for ordinary (int-range) lengths - a huge `len`
		                    // (e.g. a plain object with length:"Infinity"/2**53-1)
		                    // must keep going through the shared arrayForEachWhile,
		                    // whose JSArrayJSObject override has its own dedicated
		                    // sparse-safe fast path (walking only the object's REAL
		                    // own index-shaped properties) - reimplementing that
		                    // here too would duplicate real complexity for no
		                    // benefit; a naive `(int)len` truncation on this path
		                    // silently visits ZERO indices instead (confirmed via
		                    // indexOf/15.4.4.14-3-14.js and
		                    // indexOf/length-near-integer-limit.js, both regressed
		                    // by an earlier version of this fix that didn't gate).
		                    if(len<=Integer.MAX_VALUE) {
		                    	JSEnvironment env = getEnvironment();
		                    	int size = (int)len;
		                    	for(int i=(int)Math.max(0,fromIndex); i<size; i++) {
		                    		if(!RuntimeUtil.hasProperty(env, thisObj, i)) {
		                    			continue;
		                    		}
		                    		Object v = RuntimeUtil.getProperty(env, thisObj, i, RuntimeUtil.UNDEFINED);
		                    		if(RuntimeUtil.eqStrict(env, searchElement, v)) {
		                    			return (long)i;
		                    		}
		                    	}
		                    	return -1L;
		                    }
			        		AtomicLong found = new AtomicLong(-1);
		                    _this.arrayForEachWhile( (i,v) -> {
		                    	if(RuntimeUtil.eqStrict(getEnvironment(),searchElement,v)) {
		                    		found.set(i);
		                    		return false;
		                    	}
		                    	return true;
		                    }, fromIndex, false, RuntimeUtil.UNDEFINED, len );
			    			return found.get();
		            	}
	        		}
	        		return -1;
	        	}
	            case join -> {
	        		if(_this!=null) {
	                    // Spec: "Let len be ? LengthOfArrayLike(O)" (step 2)
	                    // happens BEFORE "let sepStr be ? ToString(sep)"
	                    // (step 4) - a poisoned separator's toString()/
	                    // valueOf() that resizes the underlying buffer (a
	                    // TypedArray backed by a resizable ArrayBuffer) must
	                    // NOT be observed by the join itself, which keeps
	                    // iterating over the length read BEFORE the
	                    // separator was coerced (confirmed via
	                    // join/coerced-separator-grow.js/-shrink.js).
	                    long len = _this.arrayLength();
	                    String sep = paramString(args,0, ",");
	                    StringBuilder b = new StringBuilder();
	                    AtomicBoolean first = new AtomicBoolean(true);
	                    _this.arrayForEach( (i,v) -> {
	                    	if(first.get()) {
	                    		first.set(false);
	                    	} else {
	                    		b.append(sep);
	                    	}
	                    	// v is already the resolved value for index i - re-fetching
	                    	// via getProperty() would call an index getter a second time,
	                    	// an observable double-invocation, not just wasted dispatch.
	                		if(RuntimeUtil.isNotNullOrUndefined(v)) {
	                    		b.append(RuntimeUtil.toString(getEnvironment(),v));
	                    	}
	                    }, true, RuntimeUtil.UNDEFINED, len);
	                    return b.toString();
	        		}
	        		return "";
	            }
	            case keys -> {
	            	// Length is re-checked LIVE per hasNext() call (matching
	            	// JSArray.arrayIterator()'s own established pattern, not
	            	// a range snapshotted once at Iterators.longSequence()
	            	// construction time) - required for a length-tracking
	            	// TypedArray view to correctly observe a mid-iteration
	            	// buffer grow/shrink (confirmed via
	            	// keys/resizable-buffer-{grow,shrink}-mid-iteration.js).
	            	Iterator<Object> keysIt = _this!=null ? new Iterator<Object>() {
	            		private long i;
	            		private boolean exhausted;
	            		@Override
	            		public boolean hasNext() {
	            			if(exhausted) {
	            				return false;
	            			}
	            			if(i>=_this.arrayLength()) {
	            				exhausted = true;
	            				return false;
	            			}
	            			return true;
	            		}
	            		@Override
	            		public Object next() {
	            			if(hasNext()) {
	            				return i++;
	            			}
	            			throw new NoSuchElementException();
	            		}
	            	} : Iterators.<Object>empty();
	            	// See entries' matching comment above.
	            	return thisObj instanceof TypedArray ta ? new TypeArrayIterator(getEnvironment(),ta,keysIt) : new BuiltinArrayIterator(getEnvironment(),keysIt);
	            }
	            case lastIndexOf -> {
	        		if(_this!=null) {
		            	{
		            		// Same length-before-coercion ordering as indexOf -
		            		// cached and reused, not re-read (confirmed via
		            		// lastIndexOf's own 15.4.4.15-5-* siblings of
		            		// indexOf/15.4.4.14-5-26.js). Unconditional now -
		            		// see indexOf's matching comment above: a
		            		// zero-argument call must still search (for
		            		// undefined), not skip straight to -1.
		            		long len = _this.arrayLength();
		            		if(len==0) {
		            			return -1L;
		            		}
		                    Object searchElement = param(args, 0, RuntimeUtil.UNDEFINED);
		                    // Spec step 4/5: "If fromIndex is present" means the
		                    // ARGUMENT was passed at that position at all - not
		                    // "is truthy"/"is not undefined". An explicitly-
		                    // passed `undefined` fromIndex still counts as
		                    // present, coercing via ToIntegerOrInfinity(undefined)
		                    // = 0 (paramLongUnclamped's own defaultValue=0 here
		                    // happens to already match that), which differs from
		                    // an OMITTED fromIndex's own default of len-1
		                    // (confirmed via 15.4.4.15-5-4.js:
		                    // `[1,2,1].lastIndexOf(2, undefined)` must search
		                    // only index 0, not len-1=2).
		                    long fi = args.length>1 ? paramLongUnclamped(args, 1, 0) : (len - 1);
		                    // Normalize negative fromIndex relative to end
		                    long fromIndex = fi < 0 ? fi + len : fi;
		                    // Per spec: if still negative, no element can match
		                    if(fromIndex < 0) return -1L;
		                    // `arrayForEachWhileReverse`'s own sparse-array fast
		                    // path (JSArrayImpl, engaged only for huge `len`
		                    // beyond int range below) treats `start==len`
		                    // (a value that's never itself a valid index) as a
		                    // SENTINEL meaning "full reverse walk from the true
		                    // last real entry" - distinct from any genuine
		                    // index, including `len-1`. A `fromIndex` that
		                    // reaches or exceeds `len-1` (the common case: no
		                    // explicit fromIndex, or one clamped down from
		                    // Infinity/a huge value) IS exactly that full-walk
		                    // case, so it must be reported as `len`, not
		                    // `len-1`, or the fast path silently never engages
		                    // and a huge-length sparse array falls back to the
		                    // unsafe `(int)len`-truncating default instead
		                    // (confirmed via lastIndexOf/15.4.4.15-5-12.js/
		                    // -5-16.js/-8-9.js, previously mis-assessed as
		                    // needing genuinely new sparse storage - the
		                    // storage and fast path already existed, only this
		                    // call site's own `start` argument was off by one
		                    // relative to what that fast path requires). A
		                    // genuine PARTIAL walk (an explicit fromIndex well
		                    // short of the end) keeps passing the exact index,
		                    // since the fast path has no way to resume mid-range.
		                    long searchStart = fromIndex>=len-1 ? len : fromIndex;
		                    // Clamp to last valid index for the standalone loop below.
		                    if(fromIndex >= len) fromIndex = len - 1;
		                    // See indexOf's matching comment above - same
		                    // standalone two-step loop, same int-range gate
		                    // (falling back to the shared arrayForEachWhileReverse
		                    // for a huge `len`, whose JSArrayImpl/JSArrayJSObject
		                    // overrides each have their own dedicated
		                    // sparse-safe fast path).
		                    if(len<=Integer.MAX_VALUE) {
		                    	JSEnvironment env = getEnvironment();
		                    	for(int i=(int)fromIndex; i>=0; i--) {
		                    		if(!RuntimeUtil.hasProperty(env, thisObj, i)) {
		                    			continue;
		                    		}
		                    		Object v = RuntimeUtil.getProperty(env, thisObj, i, RuntimeUtil.UNDEFINED);
		                    		if(RuntimeUtil.eqStrict(env, searchElement, v)) {
		                    			return (long)i;
		                    		}
		                    	}
		                    	return -1L;
		                    }
			        		AtomicLong found = new AtomicLong(-1);
		                    _this.arrayForEachWhileReverse( (i,v) -> {
		                    	if(RuntimeUtil.eqStrict(getEnvironment(),searchElement,v)) {
		                    		found.set(i);
		                    		return false;
		                    	}
		                    	return true;
		                    }, searchStart, false, RuntimeUtil.UNDEFINED, len );
			    			return found.get();
		            	}
	        		}
	            	return -1;
	            }
	        	case map -> {
	        		// Length is read once here (used for both the species-create
	        		// size hint and the iteration bound below) rather than
	        		// re-reading it inside arrayForEachWhile, so a length getter
	        		// only fires once per spec (confirmed via
	        		// flatMap/array-like-objects.js's sibling requirement).
	        		long len = _this!=null ? _this.arrayLength() : 0;
	        		JSArray result = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, len);
	        		if(_this!=null) {
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];
	                    _this.arrayForEachWhile( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        result.setOwnProperty(i, r, PropertyDescriptor.DESC_PROP_ARRAYINDEX, JSObject.DESC_CHECK.STRICT);
	                    	return true;
	                    }, 0, false, RuntimeUtil.UNDEFINED, len );
	        		}
	    			return result;
	        	}
	            case pop -> {
	        		if(_this!=null) {
	            		long sz = _this.arrayLength();
		            	if(sz>0) {
		            		Object r = _this.getProperty(sz-1,RuntimeUtil.UNDEFINED);
		            		// pop/push/shift/unshift's underlying Set/DeletePropertyOrThrow
		            		// always throw on failure regardless of caller strict-mode-ness
		            		// (unlike a plain script-level assignment) - a frozen array or
		            		// non-writable "length" must surface a TypeError here, not
		            		// silently no-op (confirmed via set-length-array-is-frozen.js
		            		// and its siblings across all four methods).
		            		_this.arrayRemove(sz-1, JSObject.DESC_CHECK.STRICT);
		            		return r;
		            	}
		            	// Even the empty-array case still does Set(O,"length",0,true)
		            	// per spec, which can still throw for a non-writable
		            	// "length" (confirmed via pop/throws-with-string-receiver.js's
		            	// `Array.prototype.pop.call('')`).
		            	_this.arraySetLength(0, JSObject.DESC_CHECK.STRICT);
	        		}
	            	return RuntimeUtil.UNDEFINED;
	            }
	            case push -> {
	        		if(_this!=null) {
		            	long len = _this.arrayLength();
		            	int argsCount = args.length;
		            	// Spec step: "If len + argCount > 2^53-1, throw a
		            	// TypeError exception" - checked BEFORE any element is
		            	// written (confirmed via
		            	// push/throws-if-integer-limit-exceeded.js, which
		            	// requires the throw with zero side effects).
		            	if(len+argsCount>9007199254740991L) {
		            		throw RuntimeUtil.typeError("Invalid array length {0}",len+argsCount);
		            	}
		            	// Each element write is a genuine (prototype-chain-aware)
		            	// Set(O,ToString(len),E,true), NOT an opaque append -
		            	// an inherited accessor at the target index (e.g. on
		            	// Array.prototype) must be invoked/rejected rather
		            	// than silently shadowed by a new own property
		            	// (confirmed via
		            	// push/set-length-array-length-is-non-writable.js and
		            	// its set-length-array-is-frozen.js sibling, whose
		            	// Array.prototype[0] setter side effect must run
		            	// during the write itself).
		            	for(int i=0; i<argsCount; i++) {
		            		_this.setProperty(len, args[i], PropertyDescriptor.DESC_PROP_ARRAYINDEX, JSObject.DESC_CHECK.STRICT);
		            		len++;
		            	}
		            	// Spec's final step (Set(O,"length",len,true)) always
		            	// runs, even with zero items pushed - a non-writable
		            	// "length" must throw regardless (confirmed via
		            	// push/throws-with-string-receiver.js's
		            	// `Array.prototype.push.call('')`, which pushes
		            	// nothing but still must throw).
		            	_this.arraySetLength(len, JSObject.DESC_CHECK.STRICT);
		            	return len;
	        		}
	        		return args.length;
	            }
	            case reduce -> {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this!=null ? _this.arrayLength() : 0;
                    Callable function = paramCallableNotNull(args, 0);
                    boolean hasInitialValue = args.length>=2;
	        		if(_this!=null) {
	                    Object[] cbArgs = new Object[4];
	                    // When no initial value is given, the accumulator must
	                    // be seeded from the FIRST element that actually HAS a
	                    // property (skipping leading holes), not blindly
	                    // index 0 - confirmed via reduceRight's sparse-array
	                    // tests (this is reduce's forward-direction twin of
	                    // that same bug).
		        		AtomicBoolean first = new AtomicBoolean(!hasInitialValue);
	                    AtomicReference<Object> p = new AtomicReference<>(hasInitialValue ? param(args, 1) : RuntimeUtil.UNDEFINED);
	                    _this.arrayForEachWhile( (i,v) -> {
	                    	if(first.get()) {
	                    		p.set(v);
	                    		first.set(false);
	                    		return true;
	                    	}
	                    	cbArgs[0]=p.get();
	                    	cbArgs[1]=v;
	                    	cbArgs[2]=i;
	                    	cbArgs[3]=thisObj;
	                        p.set(function.call(RuntimeUtil.UNDEFINED, cbArgs));
	                    	return true;
	                    }, 0, false, RuntimeUtil.UNDEFINED, len);
	                    if(!hasInitialValue && first.get()) {
	                    	throw RuntimeUtil.typeError("Reduce of empty array with no initial value");
	                    }
		    			return p.get();
	        		}
	    			return param(args, 1) ;
	            }
	            case reduceRight -> {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this!=null ? _this.arrayLength() : 0;
	            	Callable function = paramCallableNotNull(args, 0);
                    boolean hasInitialValue = args.length>=2;
	        		if(_this!=null) {
	                    Object[] cbArgs = new Object[4];
		        		AtomicBoolean first = new AtomicBoolean(!hasInitialValue);
	                    AtomicReference<Object> p = new AtomicReference<>(hasInitialValue ? param(args, 1) : RuntimeUtil.UNDEFINED);
	                    _this.arrayForEachWhileReverse( (i,v) -> {
	                    	if(first.get()) {
	                    		p.set(v);
	                    		first.set(false);
	                    		return true;
	                    	}
	                    	cbArgs[0]=p.get();
	                    	cbArgs[1]=v;
	                    	cbArgs[2]=i;
	                    	cbArgs[3]=thisObj;
	                        p.set(function.call(RuntimeUtil.UNDEFINED, cbArgs));
	                    	return true;
	                    }, len, false, RuntimeUtil.UNDEFINED, len);
	                    if(!hasInitialValue && first.get()) {
	                    	throw RuntimeUtil.typeError("Reduce of empty array with no initial value");
	                    }
		    			return p.get();
	        		}
	    			return param(args, 1) ;
	            }
	            case toReversed -> {
	        		if(_this!=null) {
	        			// Spec steps 2/5: len is cached ONCE, then elements are
	        			// read from the ORIGINAL receiver in DESCENDING order
	        			// (from = len-k-1) and written into the new array in
	        			// ASCENDING order - NOT reverse's own in-place swap
	        			// algorithm (which used to be shared here via a
	        			// clone-then-swap, but that clones with an ASCENDING
	        			// read first, observably wrong order - confirmed via
	        			// get-descending-order.js). A missing source index
	        			// (Get returns undefined, per spec - not a hole) still
	        			// becomes a real own `undefined` entry in the result,
	        			// matching length-decreased-while-iterating.js's
	        			// expectation of the CACHED len driving the loop even
	        			// as the receiver shrinks mid-iteration.
	        			long len = _this.arrayLength();
	        			if(len>4294967295L) {
	        				throw RuntimeUtil.rangeError("Invalid array length {0}",len);
	        			}
	        			final JSArray target = JSArray.create(getEnvironment());
	        			for(long k=0; k<len; k++) {
	        				Object v = _this.getProperty(len-k-1, RuntimeUtil.UNDEFINED);
	        				target.setOwnProperty(k,v);
	        			}
	        			return target;
	        		}
	        		return JSArray.create(getEnvironment());
	            }
	            case reverse -> {
	        		if(_this!=null) {
	            		long sz = _this.arrayLength();
	                    long m = sz/2;
	                    // Spec steps 7.d-7.k: lowerExists/upperExists are
	                    // genuine, SEPARATE HasProperty MOP calls (dispatched
	                    // on the ORIGINAL receiver, not `_this` - a wrapper
	                    // with no trap-aware hasProperty of its own), each
	                    // followed by its own Get ONLY when present - not a
	                    // combined "get and check NOT_AVAILABLE". The
	                    // four lowerExists/upperExists combinations each have
	                    // their own distinct Set/Delete sequence (confirmed
	                    // via reverse/length-exceeding-integer-limit-with-proxy.js's
	                    // exact expected trap-call trace).
	                    for(long lower=0; lower<m; lower++){
	                        long upper = sz-lower-1;
	                        boolean lowerExists = RuntimeUtil.hasProperty(getEnvironment(), thisObj, lower);
	                        Object lowerValue = lowerExists ? _this.getProperty(lower,RuntimeUtil.UNDEFINED) : null;
	                        boolean upperExists = RuntimeUtil.hasProperty(getEnvironment(), thisObj, upper);
	                        Object upperValue = upperExists ? _this.getProperty(upper,RuntimeUtil.UNDEFINED) : null;
	                        if(lowerExists) {
	                        	if(upperExists) {
	                        		_this.setOwnProperty(lower,upperValue);
	                        		_this.setOwnProperty(upper,lowerValue);
	                        	} else {
	                        		_this.arrayDelete(lower);
	                        		_this.setOwnProperty(upper,lowerValue);
	                        	}
	                        } else if(upperExists) {
	                        	_this.setOwnProperty(lower,upperValue);
	                        	_this.arrayDelete(upper);
	                        }
	                    }
	                    return thisObj;
	        		}
                    return thisObj;
	            }
	            case shift -> {
	        		if(_this!=null) {
	            		long sz = _this.arrayLength();
	                    if(sz>0) {
	                        Object first = _this.getProperty(0,RuntimeUtil.UNDEFINED);
	                        // Spec reads each source element via a genuine
	                        // [[Get]] (prototype-chain-aware) during the
	                        // shift-down, not an opaque internal move -
	                        // arrayRemove's own in-place compaction doesn't
	                        // consult the prototype for a hole, so an
	                        // inherited value (e.g. Array.prototype[1]=1)
	                        // was lost instead of being copied down
	                        // (confirmed via shift/S15.4.4.9_A4_T1.js).
	                        for(long k=1; k<sz; k++) {
	                        	Object v = _this.getProperty(k,RuntimeUtil.NOT_AVAILABLE);
	                        	if(v!=RuntimeUtil.NOT_AVAILABLE) {
	                        		_this.setOwnProperty(k-1,v,PropertyDescriptor.DESC_PROP_ARRAYINDEX,JSObject.DESC_CHECK.STRICT);
	                        	} else {
	                        		_this.arrayDelete(k-1,JSObject.DESC_CHECK.STRICT);
	                        	}
	                        }
	                        _this.arrayDelete(sz-1,JSObject.DESC_CHECK.STRICT);
	                        _this.arraySetLength(sz-1,JSObject.DESC_CHECK.STRICT);
	                        return first;
	                    }
	                    // Even the empty-array case still does Set(O,"length",0,true)
	                    // per spec (confirmed via
	                    // shift/throws-when-this-value-length-is-writable-false.js).
	                    _this.arraySetLength(0, JSObject.DESC_CHECK.STRICT);
	        		}
	                return RuntimeUtil.UNDEFINED;
	            }
	            case slice -> {
	        		if(_this!=null) {
	                    // Spec: "Let len be ? LengthOfArrayLike(O)" happens
	                    // BEFORE relativeStart/relativeEnd are coerced - a
	                    // poisoned start/end argument that resizes the
	                    // underlying buffer mid-conversion (a TypedArray
	                    // backed by a resizable ArrayBuffer) must not change
	                    // which length slice computes against (confirmed
	                    // via slice/coerced-start-end-grow.js/-shrink.js).
	                    long len = _this.arrayLength();
	                    long start = boundActualIndex(len,actualIndex(len,paramLong(args, 0, 0)));
	                    long end = boundActualIndex(len,actualIndex(len,paramLong(args, 1, len)));
	                    JSArray newArray = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, Math.max(end-start,0));
	                    for(long i=start; i<end; i++) {
	                    	Object v = _this.getProperty(i, RuntimeUtil.NOT_AVAILABLE);
	                    	if(v!=RuntimeUtil.NOT_AVAILABLE) {
	                    		newArray.setOwnProperty(i-start, v, PropertyDescriptor.DESC_PROP_ARRAYINDEX, JSObject.DESC_CHECK.STRICT);
	                    	}
	                    }
	                    return newArray;
	        		}
                    return RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	            }
	        	case some -> {
	        		if(_this!=null) {
                    // Spec: LengthOfArrayLike(O) is read BEFORE the
                    // IsCallable(callbackfn) check - a length getter's
                    // side effect must be observable even when the call
                    // then throws for a non-callable callback (confirmed
                    // via e.g. every/15.4.4.16-4-8.js). Read once and reuse.
                    long len = _this.arrayLength();
	                    Callable function = paramCallableNotNull(args, 0);
	                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
	                    Object[] cbArgs = new Object[3];

		        		AtomicBoolean result = new AtomicBoolean(false);
	                    _this.arrayForEachWhile( (i,v) -> {
	                    	cbArgs[0]=v;
	                    	cbArgs[1]=i;
	                    	cbArgs[2]=thisObj;
	                        Object r = function.call(thisArg, cbArgs);
	                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
	                        	result.set(true);
	                        	return false;
	                        }
	                    	return true;
	                    }, 0, false, RuntimeUtil.UNDEFINED, len );
	                    
		    			return result.get();
	        		}
	        		return false;
	        	}
	            case sort, toSorted -> {
	        		if(_this!=null) {
	                    // Spec step 1: "If comparefn is not undefined and
	                    // IsCallable(comparefn) is false, throw a TypeError"
	                    // happens BEFORE step 3's LengthOfArrayLike - checked
	                    // here BEFORE arrayClone touches _this.length at all
	                    // (confirmed via toSorted/comparefn-not-a-function.js:
	                    // a receiver whose "length" getter throws must never
	                    // be reached when comparefn is already invalid).
	                    Callable function = paramCallable(args, 0, null);
	                	Comparator<Object> cp = RuntimeUtil.comparatorStrings(getEnvironment(), function);
	                	if(methodId==MethodId.toSorted) {
	                		// Spec: toSorted collects via a dense Get(O,k) for
	                		// EVERY k (a hole reads as undefined - no hole-to-
	                		// trailing-hole special case like plain sort()
	                		// below), sorts, then builds a FRESH array from the
	                		// result. Collecting directly from `_this` and
	                		// writing straight into the new result array (2
	                		// full passes) replaces the previous clone-then-
	                		// arraySort combo (clone pass + arraySort's own
	                		// internal collect+write-back passes on the clone -
	                		// 3 full passes over the same data for no benefit,
	                		// since the clone eliminates every hole upfront,
	                		// making arraySort's own hole-handling on it a
	                		// no-op every time).
	                		long len = _this.arrayLength();
	                		// Same bound arrayClone used to enforce (ArrayCreate's
	                		// own limit) - checked before any element is read.
	                		if(len>4294967295L) {
	                			throw RuntimeUtil.rangeError("Invalid array length {0}",len);
	                		}
	                		List<Object> items = new ArrayList<>();
	                		_this.arrayForEach( (i,v) -> items.add(v), true, RuntimeUtil.UNDEFINED, len);
	                		items.sort(cp);
	                		JSArray result = JSArray.create(getEnvironment());
	                		int n = items.size();
	                		for(int i=0; i<n; i++) {
	                			result.setOwnProperty(i, items.get(i));
	                		}
	                		return result;
	                	}
	                	// Plain in-place sort() must collect/write back through
	                	// the genuine RECEIVER (thisObj), not `_this`'s own
	                	// storage directly - see JSArrayImpl.arraySort's
	                	// matching comment.
	                	_this.arraySort(cp, JSObject.DESC_CHECK.NONE, thisObj);
	                    return thisObj;
	        		}
                    return methodId==MethodId.toReversed ? JSArray.create(getEnvironment()): thisObj; // Initial object, not _this (temporary array)
	            }
	            case splice -> {
	        		if(_this!=null) {
	                    long len = _this.arrayLength();
	                    // Cached `len` reused (not the JSArray-typed
	                    // actualIndex/boundActualIndex overloads, which each
	                    // re-invoke arrayLength() internally) - a length
	                    // getter must be observed exactly once per spec
	                    // (confirmed via splice/set_length_no_args.js:
	                    // getCallCount must be 1, not 3).
	                    long start = boundActualIndex(len,actualIndex(len,paramLong(args, 0, 0)));
	                    // Spec step: with NO arguments at all (not just a
	                    // missing deleteCount), actualDeleteCount is 0, not
	                    // len-start - confirmed via
	                    // splice/clamps-length-to-integer-limit.js, which
	                    // calls splice() with zero arguments and expects
	                    // nothing to be deleted (a huge len-start deleteCount
	                    // would otherwise flow into ArraySpeciesCreate's
	                    // ArrayCreate bound and throw RangeError).
	                    long skipCount = args.length==0 ? 0 : len-start;
	                    if (args.length>=2) {
	                    	long delprm = paramLong(args, 1);
	                    	if(delprm<0) {
	                    		skipCount=0;
	                    	} else {
	                    		skipCount = Math.min(delprm, skipCount);
	                    	}
	                    }
	                    JSArray newArray = RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, skipCount);

	                    // "args.length-2" (items to insert) goes NEGATIVE
	                    // when called with fewer than 2 arguments (start
	                    // and/or deleteCount omitted) - clamp to 0, since a
	                    // negative argCnt fed into `slots = argCnt-skipCount`
	                    // below made `slots` MORE negative than it should
	                    // be, triggering spurious arrayRemove() calls that
	                    // don't correspond to any real deletion. Each
	                    // arrayRemove() is its own O(len) shift-down loop,
	                    // so on a huge-length array-like receiver this was a
	                    // genuine, REPRODUCED multi-billion-iteration hang
	                    // (confirmed via
	                    // splice/clamps-length-to-integer-limit.js's
	                    // zero-argument `Array.prototype.splice.call
	                    // (arrayLike)` calls, which must do nothing at all).
	                    int argCnt = Math.max(args.length-2, 0);
	                    int slots = (int)(argCnt - skipCount);

	                    // Spec step 11.c: "Let fromPresent be ? HasProperty(O,
	                    // from)" then, only if true, "Let fromValue be ? Get(O,
	                    // from)" then CreateDataPropertyOrThrow(A,...) - a
	                    // genuine two-step HasProperty-then-Get (an absent
	                    // source index is skipped entirely, not copied as
	                    // `undefined`), and CreateDataPropertyOrThrow is a
	                    // genuine [[DefineOwnProperty]] - `setOwnProperty`
	                    // (any arity) on a species-created array ultimately
	                    // means plain [[Set]] once the target is Proxy-backed
	                    // (JSArrayAccessor.arraySet is intentionally [[Set]]-
	                    // flavored, correct for ordinary index-assignment
	                    // call sites elsewhere) - use the dedicated
	                    // arrayDefineDataProperty instead, whose JSArrayAccessor
	                    // override genuinely reaches the wrapped Proxy's own
	                    // "defineProperty" trap (confirmed via
	                    // splice/property-traps-order-with-species.js and
	                    // splice/create-species-length-exceeding-integer-limit.js's
	                    // exact expected trap-call logs).
	                    JSEnvironment env = getEnvironment();
	                    for(int i=0; i<skipCount; i++) {
	                    	if(RuntimeUtil.hasProperty(env, thisObj, start+i)) {
	                    		Object v = RuntimeUtil.getProperty(env, thisObj, start+i, RuntimeUtil.UNDEFINED);
	                    		newArray.arrayDefineDataProperty(i, v, JSObject.DESC_CHECK.STRICT);
	                    	}
	                    }
	                    // Spec step 12: Perform ? Set(A, "length", actualDeleteCount,
	                    // true) - a genuine [[Set]] (not [[DefineOwnProperty]]),
	                    // unconditionally, even though a species-created array's
	                    // own constructor typically already set this value
	                    // natively (confirmed via
	                    // splice/property-traps-order-with-species.js's trailing
	                    // "set"/"getOwnPropertyDescriptor"/"defineProperty" trio -
	                    // this step was previously missing entirely).
	                    newArray.arraySetLength(skipCount, JSObject.DESC_CHECK.STRICT);

	                    if(slots>0) {
	                    	for(int i=0; i<slots; i++) {
	                    		_this.arrayAdd(start+i,RuntimeUtil.UNDEFINED);
	                    	}
	                    }
	                    if(slots<0) {
	                    	// We could remove a range of elements
	                    	// This exists in sparse array and as protected to ArrayList
	                    	for(int i=0; i<-slots; i++) {
	                    		_this.arrayRemove(start);
	                    	}
	                    }
	                    for(int i=0; i<argCnt; i++) {
	                    	_this.setOwnProperty(start+i,args[i+2]);
	                    }
	                    // Spec's final step (Set(O,"length",len-actualDeleteCount+
	                    // itemCount,true)) always runs, even when nothing was
	                    // actually inserted or removed (skipCount==argCnt==0) -
	                    // arrayAdd/arrayRemove's own length side effects only
	                    // fire when actually called, so a net-zero splice never
	                    // wrote "length" back at all, leaving an UNCLAMPED
	                    // declared length (e.g. 2**53) instead of the
	                    // ToLength-clamped value ArraySetLength always trims to
	                    // (confirmed via
	                    // splice/clamps-length-to-integer-limit.js's `length =
	                    // 2**53` case, which must read back as 2**53-1 after a
	                    // zero-argument splice() call, mirroring push/unshift's
	                    // same unconditional trailing length write).
	                    _this.arraySetLength(len-skipCount+argCnt, JSObject.DESC_CHECK.STRICT);
	                    return newArray;
	        		}
                    return RuntimeUtil.arraySpeciesCreate(getEnvironment(), thisObj, 0);
	            }
	            case toSpliced -> {
	            	final JSArray target = JSArray.create(getEnvironment());
	        		if(_this!=null) {
	                    long len = _this.arrayLength();
	                    // Cached `len` reused - see splice's matching comment
	                    // above.
	                    long start = boundActualIndex(len,actualIndex(len,paramLong(args, 0, 0)));
	                    // If start is not present, then
	                    // Let actualSkipCount be 0.
	                    // Else if skipCount is not present, then
	                    // Let actualSkipCount be len - actualStart.
	                    long skipCount = args.length>0 ? len-start : 0;
	                    if (args.length>=2) {
	                    	long delprm = paramLong(args, 1);
	                    	if(delprm<0) {
	                    		skipCount=0;
	                    	} else {
	                    		skipCount = Math.min(delprm, skipCount);
	                    	}
	                    }
	                    long argCnt = Math.max(args.length-2, 0);
	                    // toSpliced ALWAYS creates a genuine new Array
	                    // (target above), ignoring @@species entirely per
	                    // spec (confirmed already-passing via
	                    // with/ignores-species.js's identical pattern) - so
	                    // it needs its OWN ArrayCreate(newLen) bound check
	                    // (RangeError if newLen>2^32-1), not covered by
	                    // RuntimeUtil.arraySpeciesCreate's cap since that's
	                    // never called here. Missing this let a huge
	                    // declared length with a net-zero delete/insert flow
	                    // straight into the tail-copy loop below, a genuine
	                    // REPRODUCED multi-billion-iteration hang (confirmed
	                    // via js-test-rhino's
	                    // es2023/array-toSpliced.js's
	                    // toSplicedMaxLengthExceedingArrayLengthLimit case:
	                    // `{length: 2**32}` with a zero-length splice).
	                    long newLen = len-skipCount+argCnt;
	                    if(newLen>9007199254740991L) {
	                    	throw RuntimeUtil.typeError("Invalid array length {0}",newLen);
	                    }
	                    if(newLen>4294967295L) {
	                    	throw RuntimeUtil.rangeError("Invalid array length {0}",newLen);
	                    }
	                    for(long i=0; i<start; i++) {
	                    	target.arrayAdd(_this.getProperty(i,RuntimeUtil.UNDEFINED));
	                    }
	                    for(long i=0; i<argCnt; i++) {
	                    	target.arrayAdd(start+i, args[(int)(i+2)]);
	                    }
	                    for(long i=start+skipCount; i<len; i++) {
	                    	target.arrayAdd(_this.getProperty(i,RuntimeUtil.UNDEFINED));
	                    }
	        		}
                    return target;
	            }
	        	case toLocaleString -> {
	        		if(_this!=null) {
	    		StringBuilder b = new StringBuilder();
	    		long sz = _this.arrayLength();
	    		for(int i=0; i<sz; i++) {
	    			if(i>0) {
	    				b.append(',');
	    			}
	    			// Spec step 6c: "If nextElement is not undefined or
	    			// null" - such elements are SKIPPED entirely
	    			// (contribute nothing, not the literal word "null"/
	    			// "undefined"), unlike RuntimeUtil.toLocaleString's
	    			// own null/undefined handling (designed for a
	    			// different, JSON-stringification-flavored use case)
	    			// - confirmed via invoke-element-tolocalestring.js:
	    			// `[undefined].toLocaleString()` must be "", not
	    			// "undefined". Each element's toLocaleString is
	    			// invoked with NO ARGUMENTS regardless of what was
	    			// passed to the array's own toLocaleString - already
	    			// correct via RuntimeUtil.toLocaleString's use of
	    			// EMPTY_PARAMS.
	    			Object el = _this.getProperty(i,RuntimeUtil.UNDEFINED);
	    			if(!RuntimeUtil.isNullOrUndefined(el)) {
	    				b.append(RuntimeUtil.toLocaleString(getEnvironment(),el));
	    			}
	    		}
	            		return b.toString();
	        		}
	        		return "";
            	}
	        	case toString -> {
	        		if(_this!=null) {
	        			// No length==0 shortcut here - per spec, toString must
	        			// ALWAYS delegate to the real "join" call and let ITS
	        			// algorithm decide the result; a TypedArray whose buffer
	        			// is detached externally reports length 0 (see the
	        			// byteLength/length accessors above) but must still
	        			// THROW via join's own ValidateTypedArray check rather
	        			// than short-circuit to "" here (confirmed via
	        			// toString/detached-buffer.js).
	        			// Spec's Array.prototype.toString does Get(array,"join")
	        			// on the ORIGINAL receiver (this method is also shared
	        			// as %TypedArray%.prototype.toString - confirmed via
	        			// toString.js's `=== Array.prototype.toString` check),
	        			// not on the generic array-like `_this` wrapper - for a
	        			// non-native-Array receiver (e.g. a TypedArray) the
	        			// wrapper's own accessor doesn't resolve "join" to the
	        			// real TypedArray.prototype.join (with its
	        			// ValidateTypedArray detached-buffer check), so a
	        			// detached buffer would silently fail to throw
	        			// (confirmed via toString/detached-buffer.js).
	        			JSAccessor accObj = getEnvironment().getAccessor(thisObj);
	        			Object join = accObj.getProperty(thisObj,"join",RuntimeUtil.NOT_AVAILABLE);
	        			if(join instanceof Callable cb) {
	        				return cb.call(thisObj, RuntimeUtil.EMPTY_PARAMS);
	        			}
	        			// Spec: "If IsCallable(func) is false, set func to the
	        			// intrinsic %Object.prototype.toString%" - NOT a
	        			// hardcoded "[object Array]" (confirmed via
	        			// toString/call-with-boolean.js, whose boxed-Boolean
	        			// receiver has no "join" anywhere on its prototype
	        			// chain and must report "[object Boolean]").
	        			return BuiltinObjectPrototype.toString(getEnvironment(), thisObj);
	        		}
	        		return "";
            	}
	            case unshift -> {
	        		if(_this!=null) {
		            	int argsCount = args.length;
		            	long len = _this.arrayLength();
		            	if(argsCount>0) {
		            		// Spec step 4a: "If len+argCount > 2^53-1, throw a
		            		// TypeError exception" - checked before any
		            		// shifting happens (confirmed via
		            		// unshift/throws-if-integer-limit-exceeded.js).
		            		if(len+argsCount>9007199254740991L) {
		            			throw RuntimeUtil.typeError("Invalid array length {0}",len+argsCount);
		            		}
		            		// Spec shifts existing elements UP via genuine
		            		// (prototype-chain-aware) [[Get]]/[[Set]] pairs,
		            		// iterating DESCENDING so a not-yet-read source
		            		// isn't clobbered - arrayAdd's own internal
		            		// insert-with-shift (like arrayRemove for shift())
		            		// doesn't consult the prototype for a hole
		            		// (confirmed via unshift/S15.4.4.13_A4_T2.js).
		            		for(long k=len; k>0; k--) {
		            			long from = k-1, to = k+argsCount-1;
		            			Object v = _this.getProperty(from,RuntimeUtil.NOT_AVAILABLE);
		            			if(v!=RuntimeUtil.NOT_AVAILABLE) {
		            				_this.setOwnProperty(to,v,PropertyDescriptor.DESC_PROP_ARRAYINDEX,JSObject.DESC_CHECK.STRICT);
		            			} else {
		            				_this.arrayDelete(to,JSObject.DESC_CHECK.STRICT);
		            			}
		            		}
		            		// Genuine (prototype-chain-aware) Set, same
		            		// rationale as push - confirmed via
		            		// unshift/read-only-property.js, whose
		            		// receiver's index 0 is an inherited getter-only
		            		// accessor with no own property to shadow it.
		            		for(int i=0; i<argsCount; i++) {
		            			_this.setProperty(i,args[i],PropertyDescriptor.DESC_PROP_ARRAYINDEX,JSObject.DESC_CHECK.STRICT);
		            		}
		            	}
	                    // Spec's final step (Set(O,"length",len,true)) always
	                    // runs, even with zero items unshifted - mirrors
	                    // push's same trailing-length-write requirement.
	                    _this.arraySetLength(len+argsCount, JSObject.DESC_CHECK.STRICT);
	                    return len+argsCount;
	        		}
	            	return args.length;
	            }
	            case values -> {
	            	Iterator<Object> it = _this!=null ? _this.arrayIterator() : Iterators.empty();
	            	// See entries' matching comment above.
	            	return thisObj instanceof TypedArray ta ? new TypeArrayIterator(getEnvironment(),ta,it) : new BuiltinArrayIterator(getEnvironment(),it);
	            }
	            case with -> {
	        		if(_this!=null) {
	                	long len = _this.arrayLength();
	                	// Cached `len` reused - see splice's matching comment
	                	// above (actualIndex(_this,...) would otherwise
	                	// re-invoke arrayLength() a second time).
	                	long index = actualIndex(len,paramLong(args, 0, 0));
	                	if(index<0 || index>=len) {
	                		throw RuntimeUtil.rangeError("Invalid index {0}",index);
	                	}
	                	// Spec step 6: "Let A be ? ArrayCreate(len)" - ArrayCreate
	                	// throws RangeError for len>2^32-1, BEFORE any element is
	                	// copied (confirmed via
	                	// with/length-exceeding-array-length-limit.js, whose
	                	// getters at "0"/huge indices must never be invoked - a
	                	// REPRODUCED multi-billion-iteration hang without this
	                	// check, once arrayLength() stopped throwing for huge
	                	// array-like lengths).
	                	if(len>4294967295L) {
	                		throw RuntimeUtil.rangeError("Invalid array length {0}",len);
	                	}
	                	Object v = param(args, 1, RuntimeUtil.UNDEFINED);
	                	// Per spec, Get() is never invoked at the replaced index - the
	                	// supplied value is used directly, so a getter defined there
	                	// (or one that throws) must not run.
	                	final JSArray res = JSArray.create(getEnvironment());
	                	for(long i=0; i<len; i++) {
	                		res.setOwnProperty(i, i==index ? v : _this.getProperty(i,RuntimeUtil.UNDEFINED));
	                	}
	                    return res;
	        		}
	        		throw RuntimeUtil.rangeError("Invalid array '{0}'",RuntimeUtil.objectTypeName(thisObj));
	            }

	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }

		// Spec's FlattenIntoArray threads an explicit targetIndex counter,
		// starting at 0, through the whole (possibly recursive) flattening -
		// NOT `r`'s own current length, which can already be non-zero from
		// a custom species constructor's own side effects before this ever
		// runs (confirmed via flat/target-array-with-non-writable-property.js).
		// setOwnProperty with a CreateDataProperty-shaped descriptor
		// overwrites even a non-writable-but-configurable existing property.
		// Standalone (non-shared) two-step HasProperty-then-Get loop, unlike
		// the shared arrayForEachWhile hole-skipping path (whose own genuine
		// two-step rewrite was attempted and reverted after causing ~59
		// regressions across every/some/forEach/filter/map/reduce/etc. - see
		// JSArray.arrayForEachWhile's comment). Spec's FlattenIntoArray
		// literally requires "exists = HasProperty(source,P); if exists,
		// element = Get(source,P)" as two separate MOP calls dispatched on
		// the RECEIVER (the Proxy itself, not an unwrapped array-like) -
		// confirmed via flat/proxy-access-count.js's exact expected
		// has/get trap-call logs. `source` is the ORIGINAL receiver (thisObj
		// at the top level, or a nested array-like element during
		// recursion), not a JSArray wrapper.
		private void flat(JSArray r, AtomicLong targetIndex, Object source, long len, int depth) {
			JSEnvironment env = getEnvironment();
			for(long i=0; i<len; i++) {
				if(!RuntimeUtil.hasProperty(env, source, i)) {
					continue;
				}
				Object v = RuntimeUtil.getProperty(env, source, i, RuntimeUtil.UNDEFINED);
				// Spec's IsArray (7.2.2), not a plain `instanceof JSArray` -
				// a Proxy wrapping an array must still be recursed into,
				// through its own traps, with a FRESH LengthOfArrayLike read
				// at the start of each recursive level.
				if(depth>0 && RuntimeUtil.isArray(v)) {
					long elementLen = RuntimeUtil.getArrayLike(env, v).arrayLength();
					flat(r, targetIndex, v, elementLen, depth-1);
				} else {
					r.setOwnProperty(targetIndex.getAndIncrement(), v, PropertyDescriptor.DESC_PROP_ARRAYINDEX, JSObject.DESC_CHECK.STRICT);
				}
			}
		}
	}
}
