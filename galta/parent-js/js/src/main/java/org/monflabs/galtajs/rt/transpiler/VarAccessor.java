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
package org.monflabs.galtajs.rt.transpiler;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.util.ArrayUtil;

/**
 */
// Note: map.entry is not respected as setValue() should return the new value, not the old one
public interface VarAccessor extends Map.Entry<String,Object> {
	
	public static VarAccessor ofStatic(String name, Object value) {
		return new VarAccessor() {
			@Override
			public String getKey() {
				return name;
			}
			@Override
			public Object getValue() {
				return value;
			}
		};
	}

	// A LIVE, READ-ONLY alias under a possibly-different local name - for
	// import bindings specifically. Per spec (CreateImportBinding), an
	// imported binding is ALWAYS an immutable indirect binding from the
	// IMPORTING module's own perspective (assignment must throw
	// TypeError), even when the exporting module's underlying variable is
	// itself ordinarily mutable (a plain `export var x`) - only code
	// running INSIDE the exporting module can ever change what an import
	// observes. Reports getType()==CONST unconditionally (not delegating
	// to target.getType()) so ASTIdentifier's own CONST-rejection check
	// fires regardless of the source's real declaration kind; setValue()
	// itself also throws, as a second line of defense for any write path
	// that doesn't check getType() first (e.g. VariableMap.set()).
	public static VarAccessor importBinding(String localName, VarAccessor target) {
		return new VarAccessor() {
			@Override
			public String getKey() {
				return localName;
			}
			@Override
			public Object getValue() {
				return target.getValue();
			}
			@Override
			public Object setValue(Object value) {
				throw RuntimeUtil.typeError("Assignment to constant variable '{0}'.", localName);
			}
			@Override
			public VAR_TYPE getType() {
				return VAR_TYPE.CONST;
			}
			@Override
			public boolean stillExists() {
				return target.stillExists();
			}
		};
	}

	@Override
	public String getKey();
	
	@Override
	public Object getValue();
	
	// Should return 'value' and NOT the old value
	@Override
	public default Object setValue(Object value) {
		throw RuntimeUtil.typeError("Variable '{0}' is read only", getKey());
	}

	public default VAR_TYPE getType() {
		return VAR_TYPE.VAR;
	}

	// For a binding backed by an actual object property (globalThis, a `with` object),
	// whether it still exists right now - without invoking any getter. Object
	// Environment Records' SetMutableBinding must re-check this after GetValue, since
	// a getter can delete its own property as a side effect; ordinary lexical
	// variables can't disappear this way, so they keep the default of always existing.
	public default boolean stillExists() {
		return true;
	}

	// Whether this specific VAR/FUNCTION binding is deletable. Only ever true for a
	// var/function binding hoisted into an already-existing scope by a direct eval
	// (see BaseEvalContext.createVariable and AbstractRuntimeContext.deleteVariable);
	// every other binding kind keeps the spec default of non-configurable.
	public default boolean isConfigurable() {
		return false;
	}

	// Only meaningful for an entry inside a direct-eval call site's bundled
	// free-variable VarAccessor[] snapshot (see ASTCall.getVariableJavaReferences) -
	// true when the entry was declared in the SAME function (or, if the eval call
	// site is itself at top level, the same global scope) that the eval call
	// lexically sits in, false when it was only found by walking OUT past that
	// function's own boundary into an enclosing one. EvalDeclarationInstantiation's
	// var/function-declaration step (ECMA-262 19.2.1.3 step 5) only ever checks
	// HasBinding against ONE specific environment record - the calling context's
	// own VariableEnvironment - never an outer ancestor; a same-named binding that
	// only exists further out must never be silently reused/overwritten by the
	// eval's own `var` declaration (it must instead create a genuinely NEW binding
	// local to the calling function, shadowing the outer one for the rest of that
	// invocation - see StandardLibrary.BaseEvalContext.createVariable's
	// findParentAccessor(...) call and KnownGaps.md's "a read AFTER a same-function
	// direct-eval doesn't see the eval's shadowing var" entry). Defaults to true so
	// every OTHER VarAccessor implementor (a real declared local, `arguments`
	// parameter-mapping entries, etc.) is completely unaffected - only the eval
	// bundle's own outer-ancestor entries are ever tagged false.
	public default boolean isOwnScope() {
		return true;
	}

	// Annex B.3.5 (18.2.1.3 EvalDeclarationInstantiation step 5.d.ii.2.a.i, as
	// amended): a SLOPPY direct eval's `var`/function declaration colliding
	// with an enclosing binding must throw SyntaxError UNLESS that binding is
	// specifically a Catch clause's OWN parameter - a legacy carve-out real
	// engines implement, distinct from an ordinary LET/CONST collision (which
	// must still throw). GaltaJS represents a catch parameter as an ordinary
	// VAR_TYPE.LET binding (see ASTCatch.bindException()) - structurally
	// indistinguishable from a real `let` at the point ASTProgram's collision
	// check runs, since catch blocks use the same generic block-scope runtime
	// context class as any other `{}` (no dedicated "this is a Catch
	// Environment Record" context type to check via instanceof) - so the
	// carve-out needs a marker on the BINDING itself instead. Set only by
	// ASTCatch.bindException() right after creating the parameter's own
	// binding (test262 annexB/language/eval-code/direct/
	// var-env-lower-lex-catch-non-strict.js). Defaults to false so every
	// OTHER VarAccessor implementor (an ordinary let/const, a real function
	// parameter, etc.) is completely unaffected.
	public default boolean isCatchParameter() {
		return false;
	}
	public default void markCatchParameter() {
		// no-op by default; only the interpreted VariableEntry implementation
		// (the only place a catch parameter's binding is ever created)
		// actually stores this.
	}


	//
	// Utilities
	//

	// Parameter destructuring emits `initArg(_args, {ZERO, ZERO, ...}, null)` -
	// the first hop's input is the raw Object[] bridge array (either the caller-
	// supplied _args or the ArrayPool-borrowed bridge from callVoidN). Object[]
	// has no registered JSAccessor (JavaLibrary is opt-in), so route integer
	// indexes into the raw array directly rather than through getProperty.
	private static Object destructStep(JSEnvironment env, Object value, Object index) {
		if(value instanceof Object[] arr) {
			int i;
			if(index instanceof Integer ii) {
				i = ii.intValue();
			} else if(index instanceof Long ll) {
				i = ll.intValue();
			} else {
				return RuntimeUtil.getProperty(env,value,index,RuntimeUtil.UNDEFINED);
			}
			return (i>=0 && i<arr.length) ? arr[i] : RuntimeUtil.UNDEFINED;
		}
		return RuntimeUtil.getProperty(env,value,index,RuntimeUtil.UNDEFINED);
	}

	// Simple case - 1 index
	public static Object destruct(JSEnvironment env, Object array, Object index) {
		return destruct(env,array,index,null,null);
	}
	public static Object destruct(JSEnvironment env, Object array, Object index, Object spread) {
		return destruct(env,array,index,spread,null);
	}
	public static Object destruct(JSEnvironment env, Object array, Object index, Object spread, Supplier<Object> defaultValue) {
		Object value = array;
		if(RuntimeUtil.isNullOrUndefined(value)) {
			// `array` here is the whole value that the enclosing pattern (an
			// array or object binding/assignment pattern) is being applied
			// to - both null and undefined always fail (GetIterator/
			// RequireObjectCoercible), regardless of this leaf's own default
			// - a default only ever substitutes for THIS leaf's own resolved
			// value further down, never for the pattern's own source.
			throw RuntimeUtil.typeError("Cannot destructure '{0}' as it is {1}.", value, value==null?"null":"undefined");
		}
		value = destructStep(env, value, index);
		if(value==RuntimeUtil.UNDEFINED) {
			if(defaultValue!=null) {
				value = defaultValue.get();
			} else {
				value = RuntimeUtil.UNDEFINED;
			}
			return value;
		}
		
		if(spread!=null) {
			if(spread instanceof Integer sp) {
				JSArray a = JSArray.create(env);
				JSArray list = RuntimeUtil.getArrayLike(env,value);
				long length = list.arrayLength();
				for(long i=sp.longValue(); i<length; i++) {
					Object v = list.getProperty(i,RuntimeUtil.UNDEFINED);
					a.arrayAdd(v);
				}
				value = a;
			} else if(spread instanceof Object[] sp) {
				value = collectObjectRest(env, value, sp);
			}
		}
		return value;
	}

	// General case - multiple indexes
	public static Object destruct(JSEnvironment env, Object array, Object[] indexes) {
		return destruct(env,array,indexes,null,(DefaultStep)null);
	}
	public static Object destruct(JSEnvironment env, Object array, Object[] indexes, Object spread) {
		return destruct(env,array,indexes,spread,(DefaultStep)null);
	}
	public static Object destruct(JSEnvironment env, Object array, Object[] indexes, Object spread, Supplier<Object> defaultValue) {
		// No explicit depth given: match a leaf's own default, which is
		// always introduced at (and only ever applies at) its own final
		// path position - see the chain-aware overload below.
		DefaultStep defaults = defaultValue!=null ? new DefaultStep(indexes!=null ? indexes.length : 0, defaultValue) : null;
		return destruct(env,array,indexes,spread,defaults);
	}
	// Each DefaultStep is tied to the number of indexes consumed at the
	// point it was introduced (see ASTArrayLiteral/ASTObjectLiteral.
	// destructParameters): a default belonging to an intermediate container
	// (e.g. the nested `[x,y,z]=[4,5,6]` in `[[x,y,z]=[4,5,6]] = []`) must
	// apply exactly at that position, not at a leaf's own (deeper) position,
	// since a deeper slot resolving to undefined for an unrelated reason
	// (e.g. an explicit `x: undefined` property) must NOT spuriously
	// re-trigger it. More than one default can apply to the same leaf at
	// different depths (e.g. a defaulted function parameter containing its
	// own further-nested-defaulted sub-pattern), hence a chain rather than
	// a single value.
	public static Object destruct(JSEnvironment env, Object array, Object[] indexes, Object spread, DefaultStep defaults) {
		Object value = array;
		if(indexes!=null) {
			for(int i=0; i<indexes.length; i++) {
				// `value` here is the container that pattern-access
				// indexes[i] is about to be applied to - null/undefined
				// always fails (GetIterator/RequireObjectCoercible)
				// regardless of any default, since a default only ever
				// substitutes for a step's OWN resolved value (handled
				// below, right after that step runs), never for the
				// container an earlier step already committed to. This is
				// only reachable here via the root `array` itself being
				// null/undefined - any null/undefined arising from a prior
				// step is resolved by the branch below (substitute a
				// matching default, throw, or return) before ever reaching
				// the top of another iteration.
				if(value==null) {
					throw RuntimeUtil.typeError("Cannot destructure '{0}' as it is null.", value);
				}
				if(value==RuntimeUtil.UNDEFINED) {
					throw RuntimeUtil.typeError("Cannot destructure '{0}' as it is undefined.", value);
				}
				value = destructStep(env,value,indexes[i]);
				if(value==RuntimeUtil.UNDEFINED) {
					DefaultStep match = DefaultStep.find(defaults, i+1);
					if(match!=null) {
						// The default applies exactly here - substitute it and
						// keep walking any REMAINING indexes into it (e.g. the
						// nested pattern in `[[x,y,z]=[4,5,6]] = []` must keep
						// indexing into [4,5,6] to reach y/z).
						value = match.value().get();
					} else if(i+1<indexes.length) {
						// No default here, but more indexes remain: those
						// would attempt to apply a NESTED pattern to this
						// (still-undefined) value, which always fails - e.g.
						// `[{x}] = []` (outer[0] is undefined, then `{x}`
						// would be applied to it).
						throw RuntimeUtil.typeError("Cannot destructure '{0}' as it is undefined.", value);
					} else {
						return RuntimeUtil.UNDEFINED;
					}
				}
			}
		}
		if(spread!=null) {
			if(spread instanceof Integer sp) {
				JSArray a = JSArray.create(env);
				JSArray list = RuntimeUtil.getArrayLike(env,value);
				long length = list.arrayLength();
				for(long i=sp.longValue(); i<length; i++) {
					Object v = list.getProperty(i,RuntimeUtil.UNDEFINED);
					a.arrayAdd(v);
				}
				value = a;
			} else if(spread instanceof Object[] sp) {
				value = collectObjectRest(env, value, sp);
			}
		}
		return value;
	}

	// CopyDataProperties (object-rest destructuring's runtime counterpart,
	// shared by both destruct() overloads above): collects every own
	// enumerable key of `value` not in the excluded-keys array `sp` -
	// symbols included, not just strings (test262 dstr/obj-rest-order.js: a
	// Symbol-keyed enumerable property's getter must still fire and its
	// value must still land in the rest object). Per spec (CopyDataProperties
	// step 6.b/6.c), an EXCLUDED key must never even reach [[GetOwnProperty]]
	// - so the key list is fetched WITHOUT the enumerable-only filter
	// (enumerableOnly=false; that filter, for a Proxy, is implemented by
	// calling the "getOwnPropertyDescriptor" trap eagerly per key, before
	// this method ever gets a chance to check exclusion) and exclusion is
	// checked FIRST, with getOwnPropertyDescriptor/getProperty only called
	// afterward for a key that survives - mirrors the interpreter's own
	// identical fix, ASTObjectLiteral's collectRestObject() (see its own
	// comment for the full history), which this previously diverged from
	// (test262 dstr/object-rest-proxy-gopd-not-called-on-excluded-keys.js).
	private static JSObject collectObjectRest(JSEnvironment env, Object value, Object[] excludedKeys) {
		JSObject o = JSObject.create(env);
		org.monflabs.galtajs.rt.builtins.JSAccessor acc = env.getAccessor(value);
		for(Iterator<Map.Entry<Object,Object>> it=acc.ownPropertyEntries(value,true,true,false); it.hasNext(); ) {
			Object key = it.next().getKey();
			if(ArrayUtil.contains(excludedKeys,key)) {
				continue;
			}
			org.monflabs.galtajs.rt.builtins.PropertyDescriptor desc = acc.getOwnPropertyDescriptor(value,key);
			if(desc!=null && desc.isEnumerable()) {
				o.setOwnProperty(key, acc.getProperty(value,key,RuntimeUtil.UNDEFINED));
			}
		}
		return o;
	}

}
