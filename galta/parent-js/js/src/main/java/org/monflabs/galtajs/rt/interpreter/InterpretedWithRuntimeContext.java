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
package org.monflabs.galtajs.rt.interpreter;

import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.WithClosure;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 * With runtime context.
 */
public class InterpretedWithRuntimeContext extends InterpretedBlockRuntimeContext {
	
	protected Object _with;
	protected JSAccessor acc;
	// Every VarAccessor below is a pure facade over (this, varName) - its
	// methods re-check hasProperty/unscopable/etc LIVE on every call, so
	// reusing the same instance across repeated resolutions of the same
	// name (e.g. a `with` body's free variable accessed in a loop) is
	// behaviorally identical to allocating a fresh one each time. The
	// has-property/unscopable GATE below (which decides whether to return
	// a with-accessor at all vs falling through to the outer scope) still
	// runs on every call, unchanged - only the terminal accessor object
	// itself is cached.
	private java.util.Map<String,VarAccessor> withAccessorCache;

	public InterpretedWithRuntimeContext(JSInterpretedRuntimeContext parent, Object with) {
		super(parent);
		this._with = with;
		if(RuntimeUtil.isNotNullOrUndefined(with)) {
			this.acc = parent.getEnvironment().getAccessor(with);
		}
	}
	
	public Object getWith() {
		return _with;
	}
	
	// Object Environment Record's HasBinding (spec 9.1.1.2.1), the
	// [[IsWithEnvironment]]==true branch: a with-object's own
	// @@unscopables property, if it's an object, can mark individual names
	// as "not visible through this with" by mapping them to a truthy value.
	private boolean isUnscopable(String varName) {
		Object unscopables = acc.getProperty(_with, Symbol.UNSCOPABLES, RuntimeUtil.UNDEFINED);
		if(RuntimeUtil.isObject(getEnvironment(), unscopables)) {
			Object blocked = getEnvironment().getAccessor(unscopables).getProperty(unscopables, varName, RuntimeUtil.UNDEFINED);
			return RuntimeUtil.toBoolean(getEnvironment(), blocked);
		}
		return false;
	}

	@Override
	public VarAccessor resolveOwnIdentifierEntry(String varName) {
		if(acc!=null) {
			if(acc.hasProperty(_with,varName) && !isUnscopable(varName)) {
				if(withAccessorCache!=null) {
					VarAccessor cached = withAccessorCache.get(varName);
					if(cached!=null) {
						return cached;
					}
				}
				VarAccessor created = new VarAccessor() {
					@Override
					public String getKey() {
						return varName;
					}
					@Override
					public Object getValue() {
						// GetBindingValue (spec 9.1.1.2.6) re-checks HasProperty on its
						// own, independently of the HasBinding check that already ran to
						// even reach this VarAccessor - observably distinct from that
						// earlier check for a Proxy with-object, whose `has` trap can log/
						// count calls or return a different answer each time. S here is
						// the STRICTNESS OF THE REFERENCING CODE, not of this with
						// statement itself (a `with` body is always non-strict
						// syntactically, but a nested strict-mode function inside it can
						// still resolve a free identifier through this with environment -
						// test262's get-mutable-binding-binding-deleted-in-get-
						// unscopables-strict-mode.js) - JSRuntimeContext.get() is
						// whatever context is actually running AT THIS EXACT POINT
						// (mirrors ASTIdentifier.evaluateAssign()'s identical
						// `context.isStrictMode() && !e.stillExists()` check for the
						// write side, which already correctly uses the calling code's
						// own passed-in context rather than this with-context's own).
						if(!acc.hasProperty(_with,varName)) {
							if(JSRuntimeContext.get().isStrictMode()) {
								throw RuntimeUtil.referenceError("{0} is not defined", varName);
							}
							return RuntimeUtil.UNDEFINED;
						}
						Object v = acc.getProperty(_with,varName,RuntimeUtil.UNDEFINED);
						if(v instanceof Callable c) {
							return WithClosure.of(_with,c);
						}
						return v;
					}
					@Override
					public Object setValue(Object value) {
						// SetMutableBinding (spec 9.1.1.2.5): same "S is the referencing
						// code's own strictness" correction as getValue() above - a
						// binding that's gone by the time of the actual write (e.g. a
						// @@unscopables getter, or the RHS of the assignment itself,
						// deleting it as a side effect) must throw ReferenceError when
						// the assigning code is strict, per SetMutableBinding step 3,
						// rather than silently recreating the property via [[Set]].
						boolean stillExists = acc.hasProperty(_with,varName);
						if(!stillExists && JSRuntimeContext.get().isStrictMode()) {
							throw RuntimeUtil.referenceError("{0} is not defined", varName);
						}
						RuntimeUtil.setProperty(InterpretedWithRuntimeContext.this.getEnvironment(),_with,varName,value);
						return value;
					}
					@Override
					public boolean stillExists() {
						return acc.hasProperty(_with,varName);
					}
				};
				if(withAccessorCache==null) {
					withAccessorCache = new java.util.HashMap<>();
				}
				withAccessorCache.put(varName, created);
				return created;
			}
		}
		return super.resolveOwnIdentifierEntry(varName);
	}

	@Override
	public boolean deleteVariable(String varName) {
		if(_with!=null) {
			// HasBinding of an object environment is HasProperty: an inherited
			// property is a binding too (deleting it is then a no-op)
			boolean v = RuntimeUtil.hasProperty(InterpretedWithRuntimeContext.this.getEnvironment(),_with,varName);
			if(v && !isUnscopable(varName)) {
				return RuntimeUtil.deleteProperty(InterpretedWithRuntimeContext.this.getEnvironment(),_with,varName);
			}
		}
		return super.deleteVariable(varName);
	}

}
