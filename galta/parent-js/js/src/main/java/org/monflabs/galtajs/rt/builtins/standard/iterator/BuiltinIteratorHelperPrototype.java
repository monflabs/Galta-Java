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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.YieldStarDelegateResult;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypeArrayIterator;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorExecutingException;

/**
 * Base custom iterator prototype.
 */
public class BuiltinIteratorHelperPrototype extends BasePrototype {

	public static BuiltinIteratorHelperPrototype get(JSEnvironment env) {
		BuiltinIteratorHelperPrototype proto = (BuiltinIteratorHelperPrototype)env.getRegisteredPrototype(BuiltinIteratorHelperPrototype.class);
		if(proto==null) {
			proto = new BuiltinIteratorHelperPrototype(env);
			env.registerPrototype(BuiltinIteratorHelperPrototype.class,proto);
		}
		return proto;
	}
	
	protected BuiltinIteratorHelperPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.next,0));
		// %IteratorHelperPrototype% (map()/filter()/etc.'s RESULT objects)
		// has its own return() per spec - but several UNRELATED prototypes
		// (Array/String/Map/Set/RegExp/TypedArray iterator prototypes) share
		// this same base class purely to inherit its next() logic, and per
		// spec must NOT have a return() of their own. Only add it when
		// actually constructing %IteratorHelperPrototype% itself.
		if(getClass()==BuiltinIteratorHelperPrototype.class) {
			setOwnMethod(new Method(env,MethodId.return_,0));
			// %IteratorHelperPrototype%[Symbol.toStringTag] = "Iterator
			// Helper" - previously only ever set as an OWN property on each
			// BuiltinIteratorHelper INSTANCE (via BuiltinIterator's
			// constructor), never on this shared prototype; moving it here
			// (and removing the redundant per-instance one, see
			// BuiltinIterator) is required so a later redefinition/deletion
			// targeting the prototype is actually observable, matching every
			// other builtin iterator's own toStringTag placement (confirmed
			// via Object/prototype/toString/symbol-tag-*-builtin.js).
			setOwnProperty(Symbol.TO_STRING_TAG,"Iterator Helper",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		}
	}

	// Per spec, %IteratorHelperPrototype% AND every other prototype that
	// shares this base class (Array/String/Map/Set/RegExp/TypedArray
	// iterator prototypes) has [[Prototype]] %Iterator.prototype%, not
	// Object.prototype (BasePrototype's own default) - without this, none
	// of %Iterator.prototype%'s inherited methods (map/filter/take/drop/...)
	// nor its Symbol.toStringTag="Iterator" fallback (once a subclass's own
	// toStringTag is deleted) were reachable (confirmed via
	// Object/prototype/toString/symbol-tag-*-builtin.js).
	@Override
	protected Object getDefaultPrototype() {
		return BuiltinIteratorPrototype.get(getEnvironment());
	}

	// A caller (e.g. RuntimeUtil.valueIteratorUnchecked's Java-level fast
	// path) must NOT bypass a user-monkey-patched "next" (e.g. reassigning
	// %ArrayIteratorPrototype%.next, confirmed via
	// iterated-array-with-modified-array-iterator.js) by calling straight
	// into BuiltinIterator's own Iterator.next() - this lets a caller check
	// whether the resolved "next" property is still this class's own
	// built-in implementation before taking that shortcut.
	public static boolean isDefaultNextMethod(Object m) {
		return m instanceof Method me && me.methodId==MethodId.next;
	}

	@Override
	public String getClassName() {
		return BuiltinIteratorConstructor.CLASSNAME;
	}
	
	private static enum MethodId {
		next("next"),
		return_("return"),
		;
		final String id;
		MethodId(String id) {
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
	    	if(!(obj instanceof Iterator<?>)) {
	    		throw RuntimeUtil.typeError("Method Iterator.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			@SuppressWarnings("unchecked")
			final Iterator<Object> _this = (Iterator<Object>)obj;
			JSEnvironment env = getEnvironment();

			// Spec: GeneratorValidate checks the "executing" state BEFORE the
			// "completed" state - a reentrant call (the source's own return()
			// callback calling back into THIS SAME helper's next()/return()
			// while the first call is still mid-resume) must throw TypeError
			// even though return_'s OWN body below already called markClosed()
			// on the outer call before this reentrant one arrives (confirmed
			// via test262 Iterator/concat/throws-typeerror-when-generator-is-
			// running-return.js: without this check running first, the
			// isClosed() short-circuit right below fired instead, silently
			// swallowing the reentrant call instead of propagating the
			// TypeError the outer return() call itself must throw).
			if(obj instanceof BuiltinIteratorHelper bihExec && bihExec.isExecuting()) {
				throw RuntimeUtil.typeError("Generator is already running");
			}

			// Once return() has been called on a BuiltinIteratorHelper, it must
			// stay permanently done - never resume the underlying computation.
			if(obj instanceof BuiltinIteratorHelper bih && bih.isClosed()) {
				return JSObject.of(env,"value",RuntimeUtil.UNDEFINED,"done",true);
			}

	    	switch(methodId) {
	    		//
	    		case next-> {
					// %ArrayIteratorPrototype%.next()'s own algorithm (spec
					// 23.1.5.1 step 8): if the iterated object is backed by a
					// TypedArray whose buffer has since been detached OR
					// gone out of bounds (a resizable buffer shrunk after
					// this iterator was created), throw - even for a kind
					// (e.g. keys()) that never itself reads a buffer-backed
					// value (confirmed via detach-typedarray-in-progress.js
					// and entries|keys|values/resizable-buffer*.js). This is
					// a stricter, TypedArray-iterator-specific rule than the
					// tolerant "silently yield undefined" behavior
					// TypedArray.jsForEach* uses elsewhere for mid-call
					// detach.
					// Once the iterator has already reached PERMANENT natural
					// exhaustion (tai.isDone()), that takes precedence over
					// this out-of-bounds check - a resize to out-of-bounds
					// happening AFTER exhaustion must not start throwing
					// (confirmed via values/make-out-of-bounds-after-
					// exhausted.js).
					if(_this instanceof TypeArrayIterator tai && !tai.isDone() && tai.getSource().isOutOfBounds()) {
						throw RuntimeUtil.typeError("TypedArray buffer is detached");
					}
					// Reentrancy guard (spec: GeneratorValidate - "If state is
					// executing, throw a TypeError exception") - a mapper/predicate
					// callback calling back into THIS SAME helper's own next()
					// while it's already mid-computation. See BuiltinIteratorHelper
					// .executing's field comment for why this can't just rely on
					// the underlying source's own Generator state.
					if(obj instanceof BuiltinIteratorHelper bih0) {
						if(bih0.isExecuting()) {
							throw RuntimeUtil.typeError("Generator is already running");
						}
						bih0.setExecuting(true);
						// GeneratorResume: state moves past "suspendedStart" the moment
						// next() actually resumes, regardless of outcome - see
						// isStarted()'s field comment.
						bih0.markStarted();
					}
					try {
					// A generator's "yield" expression must receive whatever is
					// passed to this next(value) call; a plain iterator has no such
					// concept and simply ignores its argument. BuiltinIterator wraps
					// the underlying Java iterator by delegation rather than
					// implementing it directly, so unwrap it to find the Generator.
					Iterator<Object> wrapped = _this instanceof BuiltinIterator bi ? bi.getIterator() : _this;
					if(wrapped instanceof Generator<Object,?> gen) {
						Object resumeValue = args.length>0 ? args[0] : RuntimeUtil.UNDEFINED;
						try {
							Object yielded = gen.next(resumeValue);
							if(yielded instanceof YieldStarDelegateResult ysr) {
								// A non-final yield* step: forward the delegate's raw
								// result object unchanged, not a freshly wrapped one.
								return ysr.rawResult();
							}
							return JSObject.of(env,"value",yielded,"done",false);
						} catch(NoSuchElementException e) {
							// Spec: once the helper's underlying generator completes
							// NORMALLY (falls off the end of its abstract closure, as
							// opposed to an explicit return()/throw()), its
							// [[GeneratorState]] becomes "completed" for good - a LATER
							// return() call must not re-enter it or touch the source
							// again (GeneratorResumeAbrupt on a "completed" generator
							// just returns {value:undefined,done:true} unconditionally,
							// matching this class's own isClosed() short-circuit above).
							// Confirmed via test262 Iterator/prototype/*/return-is-not-
							// forwarded-after-exhaustion.js.
							if(obj instanceof BuiltinIteratorHelper bih0b) {
								bih0b.markClosed();
							}
							Object value = _this instanceof IteratorEx<Object> it ? it.getDoneValue() : RuntimeUtil.UNDEFINED;
							return JSObject.of(env,"value",value,"done", true);
						} catch(GeneratorExecutingException e) {
							// Spec: GeneratorValidate - "If state is executing, throw a
							// TypeError exception" (e.g. the predicate/mapper callback
							// reentrantly calling this same helper's next()).
							throw RuntimeUtil.typeError("Generator is already running");
						}
					}
					// A BuiltinIteratorHelper's own computation (its mapper/
					// predicate callback, or the source's own next()) can throw
					// mid-step - the source must be closed (best-effort) before
					// the error propagates, per the Iterator Helpers proposal.
					try {
						if(_this.hasNext()) {
							return JSObject.of(env,"value",_this.next(),"done",false);
						} else {
							Object value;
							if(_this instanceof IteratorEx<Object> it) {
								value = it.getDoneValue();
							} else {
								value = RuntimeUtil.UNDEFINED;
							}
							// Same "completed, stay closed" rationale as the Generator
							// branch's NoSuchElementException catch above - natural
							// exhaustion here (_this.hasNext()==false) is this helper's
							// own equivalent of falling off the end of the abstract
							// closure.
							if(obj instanceof BuiltinIteratorHelper bih1) {
								bih1.markClosed();
							}
							return JSObject.of(env,"value",value,"done", true);
						}
					} catch(GeneratorExecutingException e) {
						// The predicate/mapper callback reentrantly called back into
						// this same helper's next(), which (indirectly, through a
						// plain Java Iterators.filter()/map()-style wrapper rather
						// than the Generator branch above) reached the underlying
						// SOURCE generator while IT was still mid-resume. Same
						// spec-mandated TypeError as the Generator branch above -
						// closing the source first would be wrong here (it's still
						// legitimately mid-computation on another frame, not actually
						// erroring out).
						throw RuntimeUtil.typeError("Generator is already running");
					} catch(RuntimeException|Error t) {
						if(obj instanceof BuiltinIteratorHelper bih) {
							RuntimeUtil.iteratorCloseQuietly(env, bih.getCloseSource());
						}
						throw t;
					}
					} finally {
						if(obj instanceof BuiltinIteratorHelper bih0) {
							bih0.setExecuting(false);
						}
					}
	    		}
	    		case return_-> {
	    			// (The isExecuting() reentrancy guard - spec: %IteratorHelper
	    			// Prototype%.return() also calls GeneratorResumeAbrupt ->
	    			// GeneratorValidate, "If state is executing, throw a TypeError
	    			// exception" - is already enforced by the top-of-method check
	    			// above, which runs before the isClosed() short-circuit so a
	    			// reentrant call arriving mid-close (test262 Iterator/concat/
	    			// throws-typeerror-when-generator-is-running-return.js) throws
	    			// instead of being silently swallowed by that short-circuit.)
	    			if(obj instanceof BuiltinIteratorHelper bih) {
	    				// GeneratorResumeAbrupt: from suspendedStart (never started)
	    				// or completed, state jumps straight to "completed" and the
	    				// close below runs WITHOUT ever entering "executing" - only
	    				// a helper that had already been resumed at least once (state
	    				// suspendedYield) goes through "executing" for this close. See
	    				// BuiltinIteratorHelper.isStarted()'s field comment.
	    				boolean wasStarted = bih.isStarted();
	    				bih.markClosed();
	    				if(wasStarted) {
	    					bih.setExecuting(true);
	    					try {
	    						RuntimeUtil.iteratorClose(env, bih.getCloseSource());
	    					} finally {
	    						bih.setExecuting(false);
	    					}
	    				} else {
	    					RuntimeUtil.iteratorClose(env, bih.getCloseSource());
	    				}
	    			}
	    			return JSObject.of(env,"value",RuntimeUtil.UNDEFINED,"done",true);
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}
}