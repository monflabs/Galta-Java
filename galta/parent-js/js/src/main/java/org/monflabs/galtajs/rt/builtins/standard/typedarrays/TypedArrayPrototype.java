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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.util.iterators.Iterators;

public class TypedArrayPrototype extends BasePrototype {

	public static TypedArrayPrototype get(JSEnvironment env) {
		TypedArrayPrototype proto = (TypedArrayPrototype)env.getRegisteredPrototype(TypedArrayPrototype.class);
		if(proto==null) {
			proto = new TypedArrayPrototype(env);
			env.registerPrototype(TypedArrayPrototype.class,proto);
		}
		return proto;
	}
	
	private TypedArrayPrototype(JSEnvironment env) {
		super(env);
		PropertyDescriptor pd = PropertyDescriptor.DESC_PROP_TOSTRINGTAG;
		setOwnProperty(Symbol.TO_STRING_TAG,pd.isConfigurable(),pd.isEnumerable(),
				(t,k) -> getClassName(),
				null
			);

		setOwnProperty("buffer",true,false, 
				(t,k) -> {
					if(t instanceof TypedArray v) {
						return v.getArrayBuffer();
					}
		    		throw RuntimeUtil.typeError("Property TypedArray.buffer requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				}, 
				null
			);
		setOwnProperty("byteLength",true,false,
				(t,k) -> {
					if(t instanceof TypedArray ab) {
						return ab.getArrayBuffer().isDetached() ? 0L : ab.getByteLength();
					}
		    		throw RuntimeUtil.typeError("Property TypedArray.byteLength requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("byteOffset",true,false,
				(t,k) -> {
					if(t instanceof TypedArray ab) {
						return ab.isOutOfBounds() ? 0L : ab.getByteOffset();
					}
		    		throw RuntimeUtil.typeError("Property TypedArray.byteOffset requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("length",true,false,
				(t,k) -> {
					if(t instanceof TypedArray ab) {
						return ab.getArrayBuffer().isDetached() ? 0L : ab.getLength();
					}
		    		throw RuntimeUtil.typeError("Property TypedArray.length requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);

		// .length values (the number of NAMED/required parameters, per
		// spec's function definitions - optional trailing params like
		// fill's start/end or set's offset don't count) - several of these
		// were wrong (copyWithin, keys, reduce, reverse, some, sort),
		// caught by test262's own prototype/<method>/length.js files.
		setOwnMethod(new Method(env,MethodId.at,1));
		setOwnMethod(new Method(env,MethodId.copyWithin,2));
		setOwnMethod(new Method(env,MethodId.entries,0));
		setOwnMethod(new Method(env,MethodId.every,1));
		setOwnMethod(new Method(env,MethodId.fill,1));
		setOwnMethod(new Method(env,MethodId.filter,1));
		setOwnMethod(new Method(env,MethodId.find,1));
		setOwnMethod(new Method(env,MethodId.findIndex,1));
		setOwnMethod(new Method(env,MethodId.findLast,1));
		setOwnMethod(new Method(env,MethodId.findLastIndex,1));
		setOwnMethod(new Method(env,MethodId.forEach,1));
		setOwnMethod(new Method(env,MethodId.includes,1));
		setOwnMethod(new Method(env,MethodId.indexOf,1));
		setOwnMethod(new Method(env,MethodId.join,1));
		setOwnMethod(new Method(env,MethodId.keys,0));
		setOwnMethod(new Method(env,MethodId.lastIndexOf,1));
		setOwnMethod(new Method(env,MethodId.map,1));
		setOwnMethod(new Method(env,MethodId.reduce,1));
		setOwnMethod(new Method(env,MethodId.reduceRight,1));
		setOwnMethod(new Method(env,MethodId.reverse,0));
		setOwnMethod(new Method(env,MethodId.set,1));
		setOwnMethod(new Method(env,MethodId.slice,2));
		setOwnMethod(new Method(env,MethodId.some,1));
		setOwnMethod(new Method(env,MethodId.sort,1));
		setOwnMethod(new Method(env,MethodId.subarray,2));
		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		setOwnMethod(new Method(env,MethodId.toReversed,0));
		setOwnMethod(new Method(env,MethodId.toSorted,1));
		// Per spec: "The initial value of the %TypedArray%.prototype.toString
		// data property is the SAME built-in function object as
		// Array.prototype.toString" - a literal shared reference, not a
		// separate implementation that happens to behave the same way
		// (confirmed via toString.js's `=== Array.prototype.toString`
		// check).
		setOwnMethod((BaseMethod)BuiltinArrayPrototype.get(env).getOwnProperty("toString"), PropertyDescriptor.DESC_METHOD);
		setOwnMethod(new Method(env,MethodId.values,0));
		setOwnMethod(new Method(env,MethodId.with,2));
		
		setOwnMethod(new Method(env,MethodId.values,0));
		setOwnAlias(MethodId.values.id,MethodId.iterator.id);

		setOwnProperty(Symbol.TO_STRING_TAG,true,false, 
				(t,k) -> {
					if(t instanceof TypedArray ta) {
						return ta.getTypedArrayConstructor().getId();
					}
			    	return RuntimeUtil.UNDEFINED;
				}, 
				null
			);
		setOwnProperty("BYTES_PER_ELEMENT",true,false, 
				(t,k) -> {
					if(t instanceof TypedArray ta) {
						return ta.getTypedArrayConstructor().getBytesPerElement();
					}
			    	throw RuntimeUtil.typeError("Property BYTES_PER_ELEMENT called on incompatible receiver {0}", t!=null?t.getClass():"null");
				}, 
				null
			);
	}

	@Override
	public String getClassName() {
		return "TypedArray";
	}
	
	private static enum MethodId {
		at,
		copyWithin,
		entries,
		every,
		fill,
		filter,
		find,
		findIndex,
		findLast,
		findLastIndex,
		forEach,
		includes,
		indexOf,
		join,
		keys,
		lastIndexOf,
		map,
		reduce,
		reduceRight,
		reverse,
		set,
		slice,
		some,
		sort,
		subarray,
		toLocaleString,
		toReversed,
		toSorted,
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
	
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	if(!(obj instanceof TypedArray)) {
	    		throw RuntimeUtil.typeError("Method TypedArray.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	// Current Object
			final TypedArray _this = (TypedArray)obj;

	    	// Spec's ValidateTypedArray: nearly every prototype method throws
	    	// TypeError up front if the buffer is ALREADY detached when
	    	// called, BEFORE any argument coercion (confirmed via
	    	// slice/detached-buffer.js, which uses a valueOf() that throws a
	    	// DIFFERENT error to prove coercion never runs) - INCLUDING
	    	// entries/keys/values themselves (confirmed via
	    	// entries/detached-buffer.js: `sample.entries()` must throw, not
	    	// just a later `.next()` call - once the iterator object exists,
	    	// per-step reads tolerate a buffer detached MID-iteration without
	    	// throwing, via TypedArray.getOrUndefined() in the shared
	    	// jsForEach* helpers, but the METHOD CALL that creates the
	    	// iterator does not get that leniency). subarray is the one
	    	// exception - it coerces its arguments FIRST and only surfaces
	    	// the detached buffer later, as a side effect of constructing
	    	// the resulting view (confirmed via subarray/detached-buffer.js,
	    	// which requires its own valueOf()s to observably run before the
	    	// eventual throw).
	    	if(methodId!=MethodId.subarray) {
	    		// ValidateTypedArray actually calls IsTypedArrayOutOfBounds,
	    		// not just "is the buffer detached" - a FIXED-LENGTH view
	    		// whose backing resizable ArrayBuffer has since been shrunk
	    		// (via ArrayBuffer.prototype.resize()) so the view's own
	    		// byteOffset+byteLength no longer fits is equally "invalid"
	    		// and must throw here too, not just report length 0
	    		// (confirmed via e.g. every/return-abrupt-from-this-out-of-
	    		// bounds.js - the SAME root cause independently affects ~30
	    		// prototype methods, since they all share this one check).
	    		if(_this.isOutOfBounds()) {
	    			throw RuntimeUtil.typeError("TypedArray is out of bounds");
	    		}
	    	}
	    	// ValidateTypedArray(O, order, ~write~) (Immutable ArrayBuffer
	    	// proposal): the handful of methods that actually WRITE through
	    	// the underlying buffer must additionally reject one backed by
	    	// an immutable ArrayBuffer - checked here, uniformly, BEFORE any
	    	// argument coercion below (test262 built-ins/TypedArray/
	    	// prototype/{copyWithin,fill,reverse,set,sort}/immutable-
	    	// buffer.js - each requires zero valueOf() calls on any
	    	// argument before the TypeError, and `sort` must throw even for
	    	// a length-0 array, i.e. even when there's nothing to actually
	    	// write).
	    	if(isWriteMethod(methodId) && _this.getArrayBuffer().isImmutable()) {
	    		throw RuntimeUtil.typeError("Cannot write to a TypedArray backed by an immutable ArrayBuffer");
	    	}

	    	switch(methodId) {
	    		case at -> {
	    			// Spec: len is captured (ValidateTypedArray/TypedArrayLength)
	    			// BEFORE ToIntegerOrInfinity(index) runs - a poisoned
	    			// index.valueOf() that resizes the buffer mid-coercion
	    			// must not change what length the relative-index bounds
	    			// check computes against. The final Get, however, DOES
	    			// observe the post-coercion state (via getOrUndefined's
	    			// fresh isValidIndex check), returning undefined rather
	    			// than throwing/misindexing if the coercion left the
	    			// array out of bounds at that index (confirmed via
	    			// at/coerced-index-resize.js, whose two cases each rely
	    			// on one half of this split).
	    			long len = _this.getLength();
	    			long index = paramLong(args,0,0);
	    			if(index<0) {
	    				index += len;
	    			}
	    			if(index<0 || index>=len) {
	    				return RuntimeUtil.UNDEFINED;
	    			}
	    			return _this.getOrUndefined(index);
	    		}
	    		case copyWithin -> {
                    // Length read ONCE before any argument coercion - a
                    // poisoned target/start/end argument that detaches/
                    // resizes the buffer mid-coercion must not change what
                    // length this "is there anything to copy" check
                    // computes against (confirmed via
                    // copyWithin/coerced-values-end-detached.js).
                    long len = _this.getLength();
                    long target = _this.boundIndex(len,_this.actualIndex(len,paramLong(args, 0)));
                    long start = _this.boundIndex(len,_this.actualIndex(len,paramLong(args, 1, 0)));
                    long end = _this.boundIndex(len,_this.actualIndex(len,paramLong(args, 2, len)));
                    long count = Math.min(end-start, len-target);
                    // Per spec this out-of-bounds check only runs "if
                    // count>0" - a detach/shrink mid-coercion with nothing
                    // left to actually copy must NOT throw (confirmed via
                    // copyWithin/return-abrupt-from-species-constructor-
                    // like zero-count cases in fill/slice below). It's
                    // IsTypedArrayOutOfBounds, not just "is the buffer
                    // detached" - a FIXED-LENGTH view whose resizable
                    // buffer shrunk mid-coercion so its own byteOffset+
                    // byteLength no longer fits is equally invalid
                    // (confirmed via copyWithin/coerced-target-start-end-
                    // shrink.js's fixed-length cases, which expect a
                    // TypeError here even though nothing detached).
                    if(count>0) {
                    	if(_this.isOutOfBounds()) {
                    		throw RuntimeUtil.typeError("TypedArray is out of bounds");
                    	}
                    	// A length-TRACKING view survives a mid-coercion
                    	// resize (it's never "out of bounds"), but its
                    	// CURRENT length may now be smaller than it was
                    	// when target/start/end (and count) were computed
                    	// above against the pre-coercion length - count
                    	// must be capped to what's still safely readable/
                    	// writable at BOTH the source (start) and
                    	// destination (target) ends, WITHOUT ever growing
                    	// beyond the originally-computed count: a buffer
                    	// that GROWS mid-coercion must not copy MORE than
                    	// was originally requested, even though there's now
                    	// "room" to (confirmed via copyWithin/coerced-
                    	// target-start-grow.js, whose expected copied count
                    	// stays exactly what it was against the pre-
                    	// coercion length despite the buffer being much
                    	// bigger by the time the copy runs) - only a SHRINK
                    	// can ever reduce it further (confirmed via
                    	// copyWithin/coerced-target-start-end-shrink.js's
                    	// "truncated copy" cases). target/start themselves
                    	// are deliberately left UNCLAMPED here - out-of-
                    	// range indices are naturally excluded by this
                    	// same count cap, since a smaller count keeps
                    	// every index the loop below actually touches
                    	// within newLen.
                    	long newLen = _this.getLength();
                    	count = Math.min(count, Math.min(newLen-target, newLen-start));
                    }
                    if(start>target) {
	                    for(long i=0; i<count; i++) {
	                    	_this.set(target+i, _this.get(start+i));
	                    }
                    } else {
	                    for(long i=count-1; i>=0; i--) {
	                    	_this.set(target+i, _this.get(start+i));
	                    }
                    }
                    return _this;
	    		}
	    		case entries -> {
	        		final Iterator<Number> it = _this.values();
                    return new TypeArrayIterator( getEnvironment(), _this, new Iterator<Object>() {
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
                    });
	    		}	  
	    		case every -> {
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
                    boolean result = _this.jsForEachWhile( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        if(!RuntimeUtil.toBoolean(getEnvironment(),r)) {
                        	return false;
                        }
                    	return true;
                    }, 0);
	    			return result;
	    		}	  
	    		case fill -> {
                    // Spec: len (TypedArrayLength) is captured BEFORE the
                    // fill value is even converted - not just before start/
                    // end - so a poisoned value.valueOf() that GROWS a
                    // length-tracking view's buffer must not make the
                    // absent start/end defaults (which fall back to len)
                    // observe the grown length (confirmed via fill/absent-
                    // indices-computed-from-initial-length.js: only the
                    // ORIGINAL single element gets filled even though the
                    // array is 4 elements long by the time fill returns).
                    long len = _this.getLength();
                    // The fill value is converted (ToNumber/ToBigInt)
                    // exactly ONCE, before start/end are even computed - not
                    // once per element (which would also let the conversion's
                    // side effects, e.g. a poisoned valueOf, observe a
                    // different length/buffer state at each iteration).
                    Number converted = RuntimeUtil.toTypedArrayElement(getEnvironment(),_this,param(args, 0));
                    long start = _this.boundIndex(len,_this.actualIndex(len,paramLong(args, 1, 0)));
                    long end = _this.boundIndex(len,_this.actualIndex(len,paramLong(args, 2, len)));
                    // Out-of-bounds check (not just "is the buffer
                    // detached") is unconditional (matches spec's own
                    // post-coercion IsTypedArrayOutOfBounds re-check, which
                    // always runs regardless of whether start<end), not
                    // gated on there being anything left to actually fill
                    // (confirmed via fill/coerced-start-detach.js/
                    // coerced-end-detach.js/coerced-value-detach.js, and
                    // fill/coerced-value-start-end-resize.js's fixed-length
                    // shrink-via-coercion cases, which throw even though
                    // nothing detached).
                    if(_this.isOutOfBounds()) {
                    	throw RuntimeUtil.typeError("TypedArray is out of bounds");
                    }
                    for(long i=start; i<end; i++) {
                    	_this.set(i, converted);
                    }
                    return _this;
	    		}
	    		case filter -> {
                    int len = (int)_this.getLength();
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
                    // Per spec, the passing elements are collected first and
                    // TypedArraySpeciesCreate is invoked exactly ONCE, with
                    // the final count - not pre-allocated at `len` and
                    // truncated/copied afterward (which would consult the
                    // species constructor either zero or twice, depending on
                    // whether every element passed).
                    List<Object> kept = new ArrayList<>(len);
					for (int i = 0; i < len; i++) {
                        Object v = _this.getOrUndefined(i);
                        Object r = function.call(thisArg, v, i, _this);
                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
                        	kept.add(v);
                        }
					}
	    			TypedArray result = AbstractTypedArrayConstructor.typedArraySpeciesCreate(getEnvironment(), _this, kept.size());
	    			for(int i=0; i<kept.size(); i++) {
	    				result.set(i, RuntimeUtil.toTypedArrayElement(getEnvironment(),result,kept.get(i)));
	    			}
	    			return result;
	    		}
	    		case find -> {
	        		AtomicReference<Object> result = new AtomicReference<>(RuntimeUtil.UNDEFINED);
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
                    _this.jsForEachWhile( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
                        	result.set(v);
                        	return false;
                        }
                    	return true;
                    }, 0 );
	    			return result.get();
	    		}	  
	    		case findIndex -> {
	        		AtomicLong result = new AtomicLong(-1);
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
                    _this.jsForEachWhile( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
                        	result.set(i);
                        	return false;
                        }
                    	return true;
                    }, 0 );
	    			return result.get();
	    		}	  
	    		case findLast -> {
	        		AtomicReference<Object> result = new AtomicReference<>(RuntimeUtil.UNDEFINED);
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
                    _this.jsForEachWhileReverse( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
                        	result.set(v);
                        	return false;
                        }
                    	return true;
                    }, _this.getLength() );
	    			return result.get();
	    		}	  
	    		case findLastIndex -> {
	        		AtomicLong result = new AtomicLong(-1);
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
                    _this.jsForEachWhileReverse( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        if(RuntimeUtil.toBoolean(getEnvironment(),r)) {
                        	result.set(i);
                        	return false;
                        }
                    	return true;
                    }, _this.getLength() );
	    			return result.get();
	    		}	  
	    		case forEach -> {
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
                    _this.jsForEach( (i,v) -> {
                        function.call(thisArg, v, i, _this);
                    });
                    
                    return RuntimeUtil.UNDEFINED;
	    		}	  
	    		case includes -> {
                    Object searchElement = param(args, 0, RuntimeUtil.UNDEFINED);
                    long len = _this.getLength();
                    // Spec: length is checked BEFORE ToIntegerOrInfinity(fromIndex)
                    // - a poisoned fromIndex.valueOf() must never be called on
                    // an empty array (confirmed via length-zero-returns-false.js).
                    if(len==0) {
                    	return false;
                    }
                    long n = paramLong(args, 1, 0);
                    long fromIndex = n>=0 ? n : Math.max(len+n, 0);
	        		AtomicLong found = new AtomicLong(-1);
	        		// Spec uses SameValueZero (+0 and -0 compare equal, unlike
	        		// SameValue) for the Number/Number case. Unlike indexOf/
	        		// lastIndexOf, `includes` does NOT gate on HasProperty -
	        		// it reads via [[Get]] unconditionally, so an index gone
	        		// invalid mid-search (e.g. a detach triggered by a
	        		// poisoned fromIndex valueOf) is compared AS `undefined`,
	        		// not skipped - confirmed via
	        		// detached-buffer-during-fromIndex-returns-true-for-undefined.js,
	        		// where searching for `undefined` after such a detach
	        		// must return true, not false.
                    _this.jsForEachWhile( (i,v) -> {
                    	boolean same = (searchElement instanceof Number sn && v instanceof Number vn)
                    		? RuntimeUtil.eqNumber(sn,vn,true)
                    		: RuntimeUtil.eqSameValue(getEnvironment(),searchElement,v);
                    	if(same) {
                    		found.set(i);
                    		return false;
                    	}
                    	return true;
                    }, fromIndex, len );
	    			return found.get()>=0;
	    		}
	    		case indexOf -> {
                    Object searchElement = param(args, 0, RuntimeUtil.UNDEFINED);
                    long len = _this.getLength();
                    // Length is checked BEFORE ToIntegerOrInfinity(fromIndex) -
                    // a poisoned fromIndex.valueOf() must never be called on
                    // an empty array.
                    if(len==0) {
                    	return -1L;
                    }
                    long n = paramLong(args, 1, 0);
                    long fromIndex = n>=0 ? n : Math.max(len+n, 0);
	        		AtomicLong found = new AtomicLong(-1);
                    _this.jsForEachWhile( (i,v) -> {
                    	if(!_this.isValidIndex(i)) {
                    		return true;
                    	}
                    	if(RuntimeUtil.eqStrict(getEnvironment(),searchElement,v)) {
                    		found.set(i);
                    		return false;
                    	}
                    	return true;
                    }, fromIndex, len );
	    			return found.get();
	    		}
	    		case join -> {
                    // Length read ONCE before separator coercion - same
                    // rationale as Array.prototype.join's matching fix
                    // (confirmed via join/coerced-separator-grow.js: a
                    // poisoned separator that resizes the underlying
                    // buffer must not change how many elements join
                    // itself visits).
                    long len = _this.getLength();
                    String sep = paramString(args,0, ",");
                    StringBuilder b = new StringBuilder();
                    AtomicBoolean first = new AtomicBoolean(true);
                    _this.jsForEach( (i,v) -> {
                    	if(first.get()) {
                    		first.set(false);
                    	} else {
                    		b.append(sep);
                    	}
                		if(RuntimeUtil.isNotNullOrUndefined(v)) {
                    		b.append(RuntimeUtil.toString(getEnvironment(),v));
                    	}
                    }, len );
                    return b.toString();
	    		}
	    		case keys -> {
                    // Unlike values()/entries() (which iterate via
                    // TypedArray.values(), whose hasNext()/next() already
                    // re-read getLength() FRESH on every step),
                    // Iterators.longSequence(0, len) bakes its upper bound
                    // once at call() time - a length-tracking view's bound
                    // must instead be re-evaluated on every step, or a mid-
                    // iteration resize (grow OR shrink) of the auto-tracked
                    // length is not observed (confirmed via keys/resizable-
                    // buffer-grow-mid-iteration.js and its shrink sibling).
                    return new TypeArrayIterator(getEnvironment(),_this,new Iterator<Object>() {
                    	private long i = 0;
                    	@Override
                    	public boolean hasNext() {
                    		return i<_this.getLength();
                    	}
                    	@Override
                    	public Object next() {
                    		if(hasNext()) {
                    			return i++;
                    		}
                    		throw new java.util.NoSuchElementException();
                    	}
                    });
	    		}
	    		case lastIndexOf -> {
	            	if(args.length>0) {
	                    Object searchElement = param(args, 0);
	                    long len = _this.getLength();
	                    // Length is checked BEFORE ToIntegerOrInfinity(fromIndex) -
	                    // a poisoned fromIndex.valueOf() must never be called on
	                    // an empty array.
	                    if(len==0) {
	                    	return -1L;
	                    }
	                    // Unlike most optional trailing params, fromIndex's
	                    // default (len-1) applies only when the argument was
	                    // literally OMITTED - an explicitly-passed `undefined`
	                    // still goes through ToIntegerOrInfinity (-> NaN -> 0),
	                    // it does NOT fall back to len-1 (confirmed via
	                    // tointeger-fromindex.js: lastIndexOf(x, undefined)
	                    // must behave like fromIndex=0, not fromIndex=len-1).
	                    long n = args.length>1 ? RuntimeUtil.toLong(getEnvironment(), param(args,1)) : len-1;
	                    long k = n>=0 ? Math.min(n, len-1) : len+n;
		        		AtomicLong found = new AtomicLong(-1);
	                    _this.jsForEachWhileReverse( (i,v) -> {
	                    	if(!_this.isValidIndex(i)) {
	                    		return true;
	                    	}
	                    	if(RuntimeUtil.eqStrict(getEnvironment(),searchElement,v)) {
	                    		found.set(i);
	                    		return false;
	                    	}
	                    	return true;
	                    }, k+1 );
		    			return found.get();
	            	}
	            	return -1;
	    		}
	    		case map -> {
                    // len cached BEFORE TypedArraySpeciesCreate (which can
                    // run arbitrary user code - a custom species
                    // constructor that grows the source's resizable
                    // buffer) - the loop below must stay bounded by the
                    // length read up front, not a larger one re-derived
                    // mid-call, or it walks past the result array's own
                    // (len-sized) allocated bounds (confirmed via
                    // map/speciesctor-resizable-buffer-grow.js).
                    int len = (int)_this.getLength();
	        		TypedArray result = AbstractTypedArrayConstructor.typedArraySpeciesCreate(getEnvironment(), _this, len);
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
                    _this.jsForEachWhile( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        result.set((int)i,RuntimeUtil.toTypedArrayElement(getEnvironment(),result,r));
                    	return true;
                    }, 0, len );
	    			return result;
	    		}
	    		case reduce -> {
                    Callable function = paramCallableNotNull(args, 0);
                    boolean hasInitialValue = args.length>=2;
                    if(!hasInitialValue && _this.getLength()==0) {
                    	throw RuntimeUtil.typeError("Empty value");
                    }
                    Object initialValue = hasInitialValue ? param(args, 1) : _this.get(0);                    
                    AtomicReference<Object> p = new AtomicReference<>(initialValue);
	        		AtomicBoolean first = new AtomicBoolean(true);
                    _this.jsForEachWhile( (i,v) -> {
                    	if(first.get() && !hasInitialValue) {
                    		first.set(false);
                    		return true;
                    	}
                        p.set(function.call(RuntimeUtil.UNDEFINED, p.get(), v, i, _this));
                    	return true;
                    }, 0 );
	    			return p.get();
	    		}	  
	    		case reduceRight -> {
	            	Callable function = paramCallableNotNull(args, 0);
                    boolean hasInitialValue = args.length>=2;
                    if(!hasInitialValue && _this.getLength()==0) {
                    	throw RuntimeUtil.typeError("Empty value");
                    }
                    Object initialValue = hasInitialValue ? param(args, 1) : _this.get(_this.getLength()-1);                    
                    AtomicReference<Object> p = new AtomicReference<>(initialValue);
	        		AtomicBoolean first = new AtomicBoolean(true);
                    _this.jsForEachWhileReverse( (i,v) -> {
                    	if(first.get() && !hasInitialValue) {
                    		first.set(false);
                    		return true;
                    	}
                        p.set(function.call(RuntimeUtil.UNDEFINED, p.get(), v, i, _this));
                    	return true;
                    }, _this.getLength());
	    			return p.get();
	    		}	  
	    		case reverse -> {
	    			reverse(_this);
                    return _this;
	    		}	  
	    		case set -> {
	    			Object p = param(args,0);
	    			long targetOffset = paramLong(args,1,0);
	    			if(targetOffset<0) {
	    				throw RuntimeUtil.rangeError("Invalid target offset {0}",targetOffset);
	    			}
	    			// Offset coercion above can detach TARGET's (_this) own
	    			// buffer via a poisoned valueOf, for either source shape.
	    			if(_this.getArrayBuffer().isDetached()) {
	    				throw RuntimeUtil.typeError("Target buffer is detached");
	    			}
	    			if(p instanceof TypedArray src) {
	    				// Offset coercion (above) runs BEFORE this check - its
	    				// own side effects (a poisoned valueOf) can detach
	    				// src's buffer, which must still be caught here.
	    				// SetTypedArrayFromTypedArray also unconditionally checks
	    				// IsTypedArrayOutOfBounds(srcRecord), not just "is the
	    				// buffer detached" - a source view whose resizable buffer
	    				// has since shrunk so its own fixed byteOffset+byteLength
	    				// no longer fits is equally invalid, even though nothing
	    				// detached and even though there'd be nothing left to
	    				// actually copy (confirmed via set/typedarray-arg-src-
	    				// backed-by-resizable-buffer.js: target.set(oobFixedLength
	    				// Source) must throw TypeError, not silently no-op).
	    				if(src.isOutOfBounds()) {
	    					throw RuntimeUtil.typeError("Source TypedArray is out of bounds");
	    				}
	    				// SetTypedArrayFromTypedArray: content-type mismatch
	    				// (Number vs BigInt) is a hard TypeError, not a coercion.
	    				if(_this.isBigIntTypedArray()!=src.isBigIntTypedArray()) {
	    					throw RuntimeUtil.typeError("Cannot mix BigInt and Number typed arrays in set()");
	    				}
	    				long srcLength = src.getLength();
	    				if(targetOffset+srcLength>_this.getLength()) {
	    					throw RuntimeUtil.rangeError("Source is too large for target array with offset {0}",targetOffset);
	    				}
	    				// Snapshot every source element BEFORE writing anything -
	    				// src and target may be overlapping views of the SAME
	    				// buffer, so writing into target could otherwise
	    				// overwrite part of src before it's been fully read.
	    				if(src.getConstructor()==_this.getConstructor() && srcLength>0) {
	    					// Same element type: a byte copy (System.arraycopy
	    					// handles overlapping views of the same buffer)
	    					int bpe = _this.getConstructor().getBytesPerElement();
	    					System.arraycopy(src.getArrayBuffer().getBytes(), (int)src.getByteOffset(),
	    							_this.getArrayBuffer().getBytes(), (int)(_this.getByteOffset()+targetOffset*bpe), (int)(srcLength*bpe));
	    				} else {
	    					Number[] values = new Number[(int)srcLength];
	    					for(int i=0; i<srcLength; i++) {
	    						values[i] = src.get(i);
	    					}
	    					for(int i=0; i<srcLength; i++) {
	    						_this.setIfValid(targetOffset+i, values[i]);
	    					}
	    				}
	    			} else {
	    				// SetTypedArrayFromArrayLike: unlike the typed-array
	    				// source case above, elements are read ONE AT A TIME as
	    				// the loop reaches them (never cached upfront) - a
	    				// getter with side effects must observe/be observed by
	    				// the write loop's own progress. A getter that detaches
	    				// TARGET's buffer mid-loop must not abort the loop
	    				// (later getters still run) or throw - the write
	    				// itself just silently becomes a no-op.
	    				// Spec: targetLength (TypedArrayLength) is captured
	    				// BEFORE LengthOfArrayLike(src) reads the source's own
	    				// "length" property - a getter with side effects (here,
	    				// resizing TARGET's own resizable buffer) must not
	    				// change what length THIS bounds check computes
	    				// against; it's checked against the length as it was
	    				// prior to that read (confirmed via set/target-grow-
	    				// source-length-getter.js and its shrink sibling).
	    				long targetLength = _this.getLength();
	    				Object srcObj = RuntimeUtil.toObject(getEnvironment(), p);
	    				JSArray src = RuntimeUtil.getArrayLike(getEnvironment(), srcObj, true);
	    				long srcLength = src.arrayLength();
	    				if(targetOffset+srcLength>targetLength) {
	    					throw RuntimeUtil.rangeError("Source is too large for target array with offset {0}",targetOffset);
	    				}
	    				for(long i=0; i<srcLength; i++) {
	    					Object v = src.getProperty(i, RuntimeUtil.UNDEFINED);
	    					_this.setIfValid(targetOffset+i, RuntimeUtil.toTypedArrayElement(getEnvironment(),_this,v));
	    				}
	    			}
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case slice -> {
	    			// Length read ONCE before begin/end coercion - same
	    			// rationale as Array.prototype.slice's matching fix.
	    			long length = _this.getLength();
	    			long begin = _this.boundIndex(length,_this.actualIndex(length,paramLong(args,0,0)));
	    			long end = _this.boundIndex(length,_this.actualIndex(length,paramLong(args,1,length)));
	    			if(end<begin) {
	    				end = begin;
	    			}
	    			return slice(_this,begin,end);
	    		}
	    		case some -> {
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);
                    boolean result = !_this.jsForEachWhile( (i,v) -> {
                        Object r = function.call(thisArg, v, i, _this);
                        return !RuntimeUtil.toBoolean(getEnvironment(),r);
                    }, 0 );
                	return result;
	    		}
	    		case sort -> {
                    Callable function = paramCallable(args, 0, null);
                    sort(_this,function);
                    return _this;
	    		}	  
	    		case subarray -> {
	    			// Length read ONCE before begin/end coercion - same
	    			// rationale as slice above (confirmed via
	    			// subarray/coerced-begin-end-shrink.js).
	    			long length = _this.getLength();
	    			long begin = _this.boundIndex(length,_this.actualIndex(length,paramLong(args,0,0)));
	    			// Spec: when `end` is omitted (undefined - either not
	    			// passed at all, or explicitly passed as undefined) AND
	    			// the source itself is length-tracking, the resulting
	    			// subarray must ALSO be length-tracking - not a fixed
	    			// snapshot of the length as it happened to be AT CALL
	    			// TIME - so it keeps reflecting the buffer's length
	    			// after later resizes (confirmed via subarray/resizable-
	    			// buffer.js's final assertions). A source with an
	    			// EXPLICIT end, or a fixed-length source, always
	    			// produces an ordinary fixed-length result.
	    			boolean endOmitted = param(args,1,RuntimeUtil.UNDEFINED)==RuntimeUtil.UNDEFINED;
	    			long end = _this.boundIndex(length,_this.actualIndex(length,paramLong(args,1,length)));
	    			if(end<begin) {
	    				end = begin;
	    			}
	    			long beginByteOffset = _this.byteIndex(begin);
	    			long resultLength = (endOmitted && _this.isLengthTracking()) ? TypedArray.LENGTH_TRACKING : end-begin;
	    			return AbstractTypedArrayConstructor.typedArraySpeciesCreate(getEnvironment(), _this, _this.getArrayBuffer(), beginByteOffset, resultLength);
	    		}
	    		case toLocaleString -> {
	    			// Length read ONCE before the loop (a user-provided
	    			// Number.prototype/BigInt.prototype.toLocaleString can
	    			// shrink the underlying resizable buffer mid-iteration) -
	    			// and each element is read tolerantly (getOrUndefined,
	    			// same as join's jsForEach above), contributing an empty
	    			// string once the array has gone out of bounds instead of
	    			// throwing TypedArray.get()'s own bounds-check RangeError
	    			// (confirmed via toLocaleString/user-provided-
	    			// tolocalestring-shrink.js).
	    			long sz = _this.getLength();
            		StringBuilder b = new StringBuilder();
            		AtomicBoolean first = new AtomicBoolean(true);
            		_this.jsForEach( (i,v) -> {
            			if(first.get()) {
            				first.set(false);
            			} else {
            				b.append(',');
            			}
            			if(RuntimeUtil.isNotNullOrUndefined(v)) {
            				b.append(RuntimeUtil.toLocaleString(getEnvironment(),v));
            			}
            		}, sz );
            		return b.toString();
	    		}
	    		case toReversed -> {
	            	final TypedArray res = arrayClone(_this);
	    			reverse(res);
                    return res;
	    		}	  
	    		case toSorted -> {
	            	// Spec: comparefn's callability is validated BEFORE
	            	// touching `this` at all - confirmed via
	            	// toSorted/comparefn-not-a-function.js.
	                Callable function = paramCallable(args, 0, null);
	            	final TypedArray res = arrayClone(_this);
                    sort(res,function);
                    return res;

	    		}
	    		case values -> {
                    return new TypeArrayIterator(getEnvironment(),_this,_this.values());
	    		}	  
	    		case with -> {
	            	// Spec: len (TypedArrayLength) is captured FIRST, before
	            	// index/value are even coerced, and is used both as the
	            	// SIZE of the newly-created result array (TypedArray-
	            	// CreateSameType - a same-TYPE clone, not species-aware,
	            	// hence _this.create() rather than typedArraySpeciesCreate)
	            	// and as the copy loop's upper bound - it is NOT re-read
	            	// after value's coercion, even though that coercion's side
	            	// effects (e.g. growing a length-tracking view's buffer)
	            	// may make the array look longer by the time this method
	            	// returns. The index-validity RangeError check, in
	            	// contrast, DOES use the fresh CURRENT length. So an index
	            	// initially/still out-of-range for the ORIGINAL len, but
	            	// back in-range against the grown CURRENT length, does not
	            	// throw - yet the result is still just a length-`len`
	            	// snapshot that never actually applies the replacement,
	            	// since actualIndex ends up beyond every copied position
	            	// (confirmed via with/index-validated-against-current-
	            	// length.js and with/valid-typedarray-index-checked-after-
	            	// coercions.js).
	            	//
	            	// Index validity is checked AFTER converting `value`
	            	// (ToNumber/ToBigInt, via toTypedArrayElement so a BigInt-
	            	// content array coerces correctly) - a poisoned valueOf's
	            	// side effects/thrown exception take precedence over the
	            	// RangeError.
	            	long len = _this.getLength();
                	long index = _this.actualIndex(len, paramLong(args, 0, 0));
                	Number n = RuntimeUtil.toTypedArrayElement(getEnvironment(),_this,param(args, 1, RuntimeUtil.UNDEFINED));
                	if(index<0 || index>=_this.getLength()) {
                		throw RuntimeUtil.rangeError("Invalid index {0}",index);
                	}
	            	final TypedArray res = _this.create(len);
	            	for(long i=0; i<len; i++) {
	            		Number v = (i==index) ? n : RuntimeUtil.toTypedArrayElement(getEnvironment(),res,_this.getOrUndefined(i));
	            		res.set(i, v);
	            	}
                    return res;
	    		}
	    		
	        	case iterator -> {
	    			return Iterators.<Object>map(Iterators.longSequence(0,_this.getLength()), (v) -> {
	    				return _this.get(v);
	    			});
	            }

	    		default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }

	    private TypedArray arrayClone(TypedArray _this) {
	    	return _this.clone();
	    }

	    private TypedArray slice(TypedArray _this, long begin, long end) {
			long count = end-begin;
			TypedArray ta = AbstractTypedArrayConstructor.typedArraySpeciesCreate(getEnvironment(), _this, count);
			// Spec: TypedArraySpeciesCreate can run arbitrary user code (a
			// custom species constructor) that detaches/resizes O's buffer
			// - re-validated with a proper TypeError afterward (but only
			// "if count>0" - a detach with nothing left to actually copy
			// must NOT throw, confirmed via slice/detached-buffer-zero-
			// count-custom-ctor-*.js), rather than letting the copy loop
			// below hit TypedArray.get()'s own RangeError bounds check
			// once getLength() has silently become 0 (confirmed via
			// slice/detached-buffer-get-ctor.js and its custom-ctor
			// siblings).
			if(count>0 && _this.isOutOfBounds()) {
				throw RuntimeUtil.typeError("TypedArray buffer is detached");
			}
			long copyEnd = Math.min(end, _this.getLength());
			if(copyEnd<=begin) {
				return ta;
			}
			// Same element type: the bytes are copied as is (spec step 14.b),
			// unless the target overlaps the source further in the same buffer,
			// where an ascending copy must observe its own writes
			if(ta.getConstructor()==_this.getConstructor() && !ta.isOutOfBounds()) {
				int bpe = _this.getConstructor().getBytesPerElement();
				long srcByte = _this.getByteOffset() + begin*bpe;
				long dstByte = ta.getByteOffset();
				if(ta.getArrayBuffer()!=_this.getArrayBuffer() || dstByte<=srcByte) {
					System.arraycopy(_this.getArrayBuffer().getBytes(), (int)srcByte, ta.getArrayBuffer().getBytes(), (int)dstByte, (int)((copyEnd-begin)*bpe));
					return ta;
				}
			}
			for (long i = 0; i < copyEnd - begin; i++) {
				ta.set(i, _this.get(begin + i));
			}
			return ta;
	    }
	    
	    private void reverse(TypedArray _this) {
    		long sz = _this.getLength();
            long m = sz/2;
            for(long i=0; i<m; i++){
                long vi = sz-i-1;
                Number v0 = _this.get(i);
                Number v1 = _this.get(vi);
                _this.set(i,v1);
                _this.set(vi,v0);
            }
	    }
	    
	    private void sort(TypedArray _this, Callable function) {
	    	// Stable merge sort that never validates the comparator: an
	    	// inconsistent comparefn gives an implementation-defined order,
	    	// never an exception (Arrays.sort's TimSort can throw).
	        int length = (int)_this.getLength();
	        if(function==null && !_this.isBigIntTypedArray()) {
	        	// Default numeric order on primitives: Arrays.sort(double[])
	        	// already sorts -0 before +0 and NaN last, as the spec requires.
	        	// No user code runs, so the buffer cannot change meanwhile.
	        	double[] values = new double[length];
	        	for (int i = 0; i < length; i++) {
	        		values[i] = _this.get(i).doubleValue();
	        	}
	        	Arrays.sort(values);
	        	for (int i = 0; i < length; i++) {
	        		_this.set(i, values[i]);
	        	}
	        	return;
	        }
	        Number[] elements = new Number[length];
	        for (int i = 0; i < length; i++) {
	            elements[i] = _this.get(i);
	        }
	        
        	Comparator<Object> cp = RuntimeUtil.comparatorNumbers(getEnvironment(), function);

            BuiltinUtil.mergeSort(elements,cp);

            // A custom comparefn can detach the buffer as a side effect
            // (mid-sort) - the write-back must tolerate that silently
            // (per IntegerIndexedElementSet), not propagate the low-level
            // BaseArrayBuffer.checkBuffer() exception.
            for (int i = 0; i < length; i++) {
                _this.setIfValid(i, elements[i]);
            }
	    }

	    private static boolean isWriteMethod(MethodId methodId) {
	    	return switch(methodId) {
	    		case copyWithin, fill, reverse, set, sort -> true;
	    		default -> false;
	    	};
	    }
	}
}