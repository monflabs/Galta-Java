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
package org.monflabs.galtajs.rt.builtins;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.iterators.Iterators;


/**
 * GlobalThis object.
 * 
 * This is a regular object to which we added the standard objects from the environment
 */
public class GlobalThis extends JSObjectImpl {
	
	public static final String GLOBAL_NAME = "globalThis";
	public static final String GLOBAL_ALIAS = "global";

	private StandardObjects standardObjects;    // Coming from the Realm

	// A top-level SCRIPT's own `var`/function declarations are transpiled as
	// a local Java array slot (see ASTVarContainer's own "global" branch),
	// NOT as a literal own property of this object - ordinary IDENTIFIER
	// resolution (a bare `v` read, including from a nested closure or a
	// `with`-fallback) already works via a completely separate mechanism
	// (JSTranspiledUnit.getIdentifierAccessor()/getIdentifierValue(),
	// driven by static lexical-scope info at transpile time), but an
	// explicit MEMBER-ACCESS read (`globalThis.v`, sloppy top-level `this.v`,
	// `Object.keys(globalThis)`) goes through THIS object's own property
	// methods instead, which had no knowledge of the script's own variable
	// array at all. Bound once, at script-startup time (see
	// TranspiledGlobalRuntimeContext.initGlobalVariables()'s own override),
	// to the SAME VariableMap that already backs identifier resolution - no
	// new storage, no duplicate bookkeeping. Null for interpreted mode,
	// module top levels (spec-correct: a module's own top-level bindings
	// are NOT globalThis properties), and any non-global context.
	private VariableMap scriptVariables;

	// Names successfully [[Delete]]d from REAL (native JSObjectImpl) storage
	// while an UNRELATED array-slot bridge for the same name still exists in
	// scriptVariables (an Annex-B block-hoisted function - or any hoisted
	// declaration - colliding with a pre-existing, genuinely CONFIGURABLE
	// real property: legacy hoisting must leave that property's descriptor
	// untouched, per B.3.3.3's CreateGlobalVarBinding no-op-if-exists rule -
	// TranspiledGlobalRuntimeContext.initGlobalVariables()'s own annexB
	// branch - but the array-slot bridge is STILL needed so the hoisted
	// declaration's real VALUE stays reachable from a separately-compiled
	// caller, e.g. a later $262.evalScript). Without this set,
	// getOwnPropertyDescriptor()'s scriptVariables fallback below would
	// resurrect a phantom DESC_FIXED_PROP entry for a name whose REAL
	// property was just legitimately deleted - making a genuinely
	// CONFIGURABLE property look permanently non-configurable/undeletable
	// after its first successful delete (test262 annexB/language/global-code/
	// *-existing-non-enumerable-global-init.js's own verifyProperty() calls
	// delete obj[name] to PROBE configurability, non-destructively, before
	// this set existed that probe alone made the property look stuck).
	// Lazily created; null for the overwhelming common case (no such
	// collision ever deleted).
	private java.util.Set<String> deletedRealProperties;

	public void bindScriptVariables(VariableMap scriptVariables) {
		this.scriptVariables = scriptVariables;
	}

	// Only a var/function-declared top-level name is a genuine globalThis
	// own-property per spec (CreateGlobalVarBinding/CreateGlobalFunctionBinding
	// put it on the global OBJECT; a lexical global declaration - let/const/
	// using, and a top-level `class` - instead lives in the Global
	// Environment Record's separate DECLARATIVE record, never as an object
	// property at all - see 9.1.1.4 GlobalEnvironmentRecord.HasBinding()'s
	// own [[ObjectRecord]] vs [[DeclarativeRecord]] split). scriptVariables
	// bridges the WHOLE top-level VariableMap (var/function AND lexical
	// alike - see that field's own doc for why: identifier resolution
	// within the script itself needs every entry, only THIS object's own
	// property-facing methods need the narrower subset), so each one must
	// re-check the found entry's own VAR_TYPE before treating it as a real
	// own property.
	private static boolean isScriptOwnProperty(VarAccessor a) {
		VAR_TYPE t = a.getType();
		return t!=VAR_TYPE.LET && t!=VAR_TYPE.CONST && t!=VAR_TYPE.USING;
	}

	// Null when scriptVariables has no entry for key, OR the entry exists
	// but is lexical (see isScriptOwnProperty()) - the single check every
	// property-facing method below needs.
	private VarAccessor findScriptOwnAccessor(String key) {
		if(scriptVariables==null) {
			return null;
		}
		VarAccessor a = scriptVariables.getAccessor(key);
		return (a!=null && isScriptOwnProperty(a)) ? a : null;
	}

	public GlobalThis(JSEnvironment env) {
		super(env);
		this.standardObjects = env.getStandardObjects();

		setOwnProperty(GLOBAL_NAME, this, PropertyDescriptor.DESC_PROP_GLOBALTHIS);
		if(env.supportGlobalAlias()) {
			setOwnProperty(GLOBAL_ALIAS, this, PropertyDescriptor.DESC_PROP_GLOBALTHIS);
		}
	}

	@Override
	public String getClassName() {
		return GLOBAL_NAME;
	}
	
	public final StandardObjects getStandardObjects() {
		return standardObjects;
	}	
	
	
	public VarAccessor getOwnVariableAccessor(String varName, boolean autoCreate) {
		boolean existedAsOwnProperty = hasOwnProperty(varName);
		if(autoCreate || existedAsOwnProperty) {
			return new VarAccessor() {
				@Override
				public String getKey() {
					return varName;
				}
				@Override
				public Object getValue() {
					return getOwnProperty(varName,RuntimeUtil.UNDEFINED);
				}
				@Override
				public Object setValue(Object value) {
					setOwnProperty(varName, value);
					// An assignment EXPRESSION (not just a standalone
					// statement) must evaluate to the assigned value - e.g.
					// `(y = 1) + y` (y previously undeclared, so this IS the
					// accessor that creates it) needs this VarAccessor's own
					// setValue() to return 1, not null, since transpiled
					// codegen embeds the setValue(...) call directly as the
					// left operand's own sub-expression (test262 language/
					// expressions/{addition,subtraction,...}/*_A2.4_T*.js:
					// the right operand read back correctly, but the whole
					// expression's own value was wrong because ToNumber(null)
					// treated it as JS `null` - i.e. 0 - not as "ignore me").
					return value;
				}
				@Override
				public boolean stillExists() {
					// Only re-check a binding that was already a real globalThis
					// property when resolved (its accessor's getter may have deleted
					// it as a side effect of the GetValue that just happened); one
					// created here purely via autoCreate (an implicit sloppy-mode
					// global, or a library-provided global like eval/isNaN that lives
					// outside globalThis's own storage) can't disappear this way.
					return !existedAsOwnProperty || hasOwnProperty(varName);
				}
			};
		}
		return null;
	}

	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(String member) {
		PropertyDescriptor desc = super.getOwnPropertyDescriptor(member);
		if(desc!=null) {
			return desc;
		}
		desc = standardObjects.getOwnPropertyDescriptor(member);
		if(desc!=null) {
			return desc;
		}
		if(findScriptOwnAccessor(member)!=null
				&& (deletedRealProperties==null || !deletedRealProperties.contains(member))) {
			// CreateGlobalVarBinding's own property shape: writable,
			// enumerable, NOT configurable (a top-level `var` can't be
			// deleted) - same constant TranspiledGlobalRuntimeContext's own
			// createVariable() already uses for the non-eval case. Suppressed
			// once this name's REAL property has been legitimately deleted -
			// see deletedRealProperties' own doc.
			return PropertyDescriptor.DESC_FIXED_PROP;
		}
		return null;
	}

	// JSObjectImpl.hasOwnProperty(String) checks only this object's own
	// native storage, bypassing getOwnPropertyDescriptor()'s fallback to
	// standardObjects above - so without this override, hasOwnProperty()
	// and getOwnPropertyDescriptor() disagree about builtin globals (e.g.
	// StandardLibrary/UnitTestLibrary functions), which getOwnVariableAccessor()
	// and other callers rely on being consistent.
	@Override
	public boolean hasOwnProperty(String member) {
		return getOwnPropertyDescriptor(member)!=null;
	}

	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors) {
		standardObjects.getOwnPropertyDescriptors(descriptors);
		return super.getOwnPropertyDescriptors(descriptors);
	}

	
	@Override
	public Object getOwnProperty(String key, Object defaultValue, Object receiver) {
		Object val = super.getOwnProperty(key,RuntimeUtil.NOT_AVAILABLE,receiver);
		if(val!=RuntimeUtil.NOT_AVAILABLE) {
			return val;
		}
		val = standardObjects.getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE);
		if(val!=RuntimeUtil.NOT_AVAILABLE) {
			return val;
		}
		VarAccessor a = findScriptOwnAccessor(key);
		if(a!=null) {
			return a.getValue();
		}
		return defaultValue;
	}

	@Override
	public boolean setOwnProperty(String key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		if(super.hasOwnProperty(key)) {
			return super.setOwnProperty(key,value,descriptor,check,receiver);
		} else if(standardObjects.hasOwnProperty(key)) {
			return standardObjects.setOwnProperty(key,value,descriptor,check,receiver);
		}
		VarAccessor a = findScriptOwnAccessor(key);
		if(a!=null) {
			a.setValue(value);
			return true;
		}
		return super.setOwnProperty(key,value,descriptor,check,receiver); // create a new one
	}

	@Override
	public boolean deleteProperty(String key, DESC_CHECK check) {
		if(super.hasOwnProperty(key)) {
			boolean deleted = super.deleteProperty(key,check);
			// See deletedRealProperties' own doc: only relevant when an
			// array-slot bridge for this SAME name also exists (the
			// overwhelming common case - no collision - never allocates the
			// set at all).
			if(deleted && findScriptOwnAccessor(key)!=null) {
				if(deletedRealProperties==null) {
					deletedRealProperties = new java.util.HashSet<>();
				}
				deletedRealProperties.add(key);
			}
			return deleted;
		} else if(findScriptOwnAccessor(key)!=null) {
			// A script `var`'s own property is non-configurable (see
			// getOwnPropertyDescriptor()'s DESC_FIXED_PROP above) - delete
			// must FAIL (return false), not fall through to standardObjects:
			// that path doesn't recognize this key as an own property
			// either, but [[Delete]] on a genuinely NONEXISTENT property
			// trivially SUCCEEDS (true) per spec - a plain `delete
			// this.someVarName` on a var-declared global was wrongly
			// returning `true` instead of the required `false` (test262
			// language/statements/variable/S12.2_A2.js: `delete(this
			// ["__variable"])` must be `false`). DESC_CHECK.CHECK-mode
			// callers throw the strict-mode TypeError themselves off this
			// `false`, matching spec's [[Delete]] on a non-configurable own
			// property.
			return false;
		} else {
			return standardObjects.deleteProperty(key,check);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		return Iterators.concat(
				super.ownPropertyEntries(strings,symbols,enumerableOnly),
				(Iterator)(strings ? standardObjects.ownPropertyEntries(strings,symbols,enumerableOnly) : null),
				// A top-level `var` is a genuine enumerable own property
				// (CreateGlobalVarBinding) - script var names are always
				// plain strings, never symbols, so only included for the
				// `strings` pass, same as standardObjects above. Filtered by
				// isScriptOwnProperty() first - a lexical (let/const/using)
				// global declaration is never enumerated here at all (see
				// that method's own doc).
				(Iterator)(strings && scriptVariables!=null
						? Iterators.map(Iterators.filter(scriptVariables.entries().iterator(), GlobalThis::isScriptOwnProperty), (VarAccessor a) -> JSAccessor.newEntry(a.getKey(), a.getValue()))
						: null)
			);
	}
}
