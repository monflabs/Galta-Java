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
package org.monflabs.galtajs.rt.builtins.standard.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.iterators.Iterators;

/**
 * Module Namespace Exotic Object (spec 9.4.6) - the object `import * as ns`
 * (and dynamic `import()`'s resolved value) exposes.
 *
 * Diverges from an ordinary object in several ways that can't be achieved by
 * just pre-populating a plain {@link JSObject}'s storage: [[GetPrototypeOf]]
 * always null, [[SetPrototypeOf]]/[[IsExtensible]]/[[PreventExtensions]]
 * fixed, [[HasProperty]] checks STATIC export-name membership only (no value
 * resolution, so it's TDZ-safe even for an uninitialized binding), while
 * [[GetOwnProperty]]/[[Get]] both resolve the LIVE binding value and can
 * throw ReferenceError for an uninitialized one, and [[DefineOwnProperty]]
 * only ever accepts a no-op-equivalent redefinition (even a WRITABLE export
 * still rejects a different value - unlike ordinary [[DefineOwnProperty]]).
 *
 * The set of exported names is NOT frozen at construction: {@link
 * #currentExportedNames()} recomputes it on every call, combining {@link
 * org.monflabs.galtajs.node.ASTProgram#getStaticExportedNames()} (local
 * declarations + named re-export aliases - always complete, known purely
 * from the module's own AST, independent of execution order - this is what
 * makes [[HasProperty]] correct even BEFORE the module body has run) with
 * {@code module.getNamedExports()}'s CURRENT keys (best-effort - picks up
 * bare `export * from '...'` names as they get merged, but - see
 * KnownGaps.md's own circular-star-re-export entries - may still be
 * incomplete for a genuinely circular star cycle reached before it settles).
 */
public class ModuleNamespaceObject extends NativeObject {

	private final JSModule module;
	private final boolean deferred;

	public ModuleNamespaceObject(JSEnvironment env, JSModule module) {
		this(env, module, false);
	}

	// deferred: true only for `import defer * as ns from '...'`'s own
	// namespace object (see AbstractModule.getDeferredModuleNamespaceObject()) -
	// spec 9.4.6 ModuleNamespaceCreate's own toStringTag computation
	// ("Deferred Module" vs "Module") is fixed at CONSTRUCTION time and
	// never changes afterward (test262 import-defer/deferred-namespace-
	// object/to-string-tag.js checks it before the module has ever been
	// evaluated at all - triggering evaluation later doesn't turn this
	// namespace into a non-deferred one, it's the SAME object throughout).
	public ModuleNamespaceObject(JSEnvironment env, JSModule module, boolean deferred) {
		super(env);
		this.module = module;
		this.deferred = deferred;
		// Fixed, ordinary, non-configurable/non-enumerable/non-writable own
		// property - via super.setOwnProperty (bypassing this class's OWN
		// override below, which only ever accepts a KNOWN export name).
		// Ordinary (inherited, non-overridden) [[Delete]]/[[DefineOwnProperty]]
		// behavior for a Symbol key already matches spec 9.4.6.6/9.4.6.8's
		// own "Type(P) is Symbol -> ordinary behavior" carve-out.
		super.setOwnProperty(Symbol.TO_STRING_TAG, deferred ? "Deferred Module" : "Module", PropertyDescriptor.of(false,false,false), DESC_CHECK.NONE, this);
		// isExtensible() is FINAL (ObjectPropertiesMap), backed by an
		// internal flag - preventExtensions() (NOT final, but its inherited
		// default already sets that flag and returns true, exactly
		// matching spec 9.4.6.10 [[PreventExtensions]] - no override
		// needed) is how it's set permanently, once, here.
		preventExtensions();
	}

	// spec IsSymbolLikeNamespaceKey: a Symbol key is ALREADY handled
	// (Symbol-keyed overloads of hasProperty/getOwnProperty/etc. are never
	// overridden in this class at all, so they fall straight to ordinary
	// NativeObject behavior without ever reaching here) - the only STRING
	// key this applies to is "then", and only for a DEFERRED namespace:
	// treating it as symbol-like (ordinary property behavior, no exports-
	// list lookup) regardless of whether "then" happens to be a real
	// export name prevents a deferred namespace from ever being
	// accidentally treated as a thenable (e.g. by `await`/Promise
	// resolution) AND, as a side effect, means probing "then" never
	// triggers the underlying module's evaluation (test262 import-defer/
	// evaluation-triggers/ignore-*-then-*.js).
	private boolean isSymbolLikeNamespaceKey(String key) {
		return deferred && "then".equals(key);
	}

	private Set<String> currentExportedNames() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		if(module instanceof JSInterpretedUnit unit) {
			names.addAll(unit.getProgram().getStaticExportedNames());
		} else if(module instanceof org.monflabs.galtajs.modules.AbstractModule am) {
			// Transpiled equivalent of the interpreted-mode static-names
			// source above - see AbstractModule.getLiveExportedNames()'s
			// own doc comment. Without this, a self-import's namespace
			// object never sees a locally-declared export name until its
			// own `export` statement has actually run, breaking `in`/
			// Reflect.has()'s TDZ-safe "found but uninitialized" semantics
			// (test262 namespace/internals/has-property-str-found-uninit.js).
			names.addAll(am.getLiveExportedNames());
		}
		JSObject named = module.getNamedExports();
		if(named!=null) {
			for(Iterator<String> it = named.ownPropertyKeys(false); it.hasNext(); ) {
				names.add(it.next());
			}
		}
		// Transitive star-export names (spec GetExportedNames, 16.2.1.6.2)
		// not already covered above - each must be individually confirmed
		// resolvable AND unambiguous via resolveExport() before being
		// exposed (spec GetModuleNamespace step 3.c: ResolveExport per
		// name, discard null/ambiguous) - GetExportedNames itself doesn't
		// do that filtering, it's a SEPARATE algorithm/step that only
		// enumerates candidate names.
		for(String n: module.getExportedNames(new java.util.HashSet<>())) {
			if(!names.contains(n) && module.resolveExport(n, new java.util.HashSet<>())!=null) {
				names.add(n);
			}
		}
		if(names.contains("default") || module.hasDefaultExport()) {
			names.add("default");
		}
		return names;
	}

	// Resolves the CURRENT live value for a known export name - throws
	// ReferenceError (matching a let/const TDZ read) when the binding
	// hasn't been initialized yet. "default" is special-cased since
	// JSModule.getExportAccessor("default") only works for a HOISTABLE
	// default (task #256/#260) - a non-hoistable one (`export default
	// <expr>`/`export default class C{}`) is tracked separately via
	// getDefaultExport()/hasDefaultExport(), which this mirrors into the
	// same TDZ-style ReferenceError for consistency with every other
	// export (test262 namespace/internals/get-str-found-uninit.js:
	// `ns.default` before `export default null;` runs must throw exactly
	// like `ns.localUninit1` does).
	private Object resolveExportValue(String name) {
		if("default".equals(name)) {
			// module.getExportAccessor("default") covers every form that
			// can produce a LIVE accessor: a hoistable `export default
			// function fn(){}` (correctly reflects a later reassignment
			// of fn - test262 dynamic-import/usage/eval-gtbndng-indirect-
			// update-dflt.js family) AND, via ASTProgram.
			// getLiveExportNames()'s generic local-export mapping, an
			// ORDINARY named export whose alias happens to be "default"
			// (`export {x as default}` - NOT `export default` syntax at
			// all, so module.hasDefaultExport() below is permanently
			// false for it; test262 dynamic-import/namespace/*-nested-
			// namespace-dflt-indirect.js). Only a genuine, non-hoistable
			// `export default <expr>` has no live accessor to give here -
			// falls through to the hasDefaultExport()-gated static
			// snapshot (and its own "not yet initialized" TDZ guard) for
			// that one remaining case.
			try {
				return RuntimeUtil.checkTDZ(module.getExportAccessor("default").getValue(), name);
			} catch(RuntimeException ex) {
				// Falls through below - expected whenever no live
				// accessor exists yet (see above).
			}
			if(!module.hasDefaultExport()) {
				throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
			}
			return module.getDefaultExport();
		}
		VarAccessor accessor = module.getExportAccessor(name);
		return RuntimeUtil.checkTDZ(accessor.getValue(), name);
	}

	// Object.freeze's own implementation (BuiltinObjectConstructor) calls
	// this DIRECTLY, bypassing the per-property setOwnProperty/
	// [[DefineOwnProperty]] path entirely (a fast internal flag toggle,
	// correct for an ORDINARY object) - so freeze() needs its own override
	// here to correctly fail whenever there's at least one export (SetIntegrityLevel's
	// own per-key DefinePropertyOrThrow(writable:false) would always be
	// rejected by this class's [[DefineOwnProperty]] above). An empty
	// export set (nothing to lock down) is a trivial no-op success.
	@Override
	public void freeze() {
		if(!currentExportedNames().isEmpty()) {
			throw RuntimeUtil.typeError("Cannot freeze module namespace object");
		}
		super.freeze();
	}

	// Object.isFrozen/isSealed (RuntimeUtil.testIntegrityLevel) walk THIS
	// map, not the underlying storage - JSObjectImpl's own default instead
	// iterates the RAW internal property map directly, which for this
	// class holds only Symbol.toStringTag (every export is COMPUTED, never
	// actually stored via super.setOwnProperty) - without this override,
	// testIntegrityLevel saw zero string entries (vacuously "all
	// configurable:false"), wrongly concluding the namespace WAS frozen.
	// getOwnPropertyDescriptor(key)'s own TDZ check applies here too
	// (matches spec: [[OwnPropertyKeys]] then per-key [[GetOwnProperty]],
	// which can throw).
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors) {
		for(String key: currentExportedNames()) {
			descriptors.setOwnProperty(key, getOwnPropertyDescriptor(key));
		}
		return super.getOwnPropertyDescriptors(descriptors);
	}

	@Override
	public Object getPrototype() {
		return null;
	}

	@Override
	public boolean hasProperty(String key) {
		if(isSymbolLikeNamespaceKey(key)) {
			return super.hasProperty(key);
		}
		return currentExportedNames().contains(key) || super.hasProperty(key);
	}

	// The default JSObject.hasProperty(long) doesn't reliably reach the
	// override above (test262 export-expname-binding-index.js: `0 in ns`
	// for an export named "0" via `export { a as "0" }` - the "arbitrary
	// module namespace names" feature - returned false without this).
	// Explicit delegation via the same String-key path get/set/delete
	// already resolve through.
	@Override
	public boolean hasProperty(long index) {
		return hasProperty(RuntimeUtil.memberIndex(index));
	}

	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(String key) {
		if(isSymbolLikeNamespaceKey(key)) {
			return super.getOwnPropertyDescriptor(key);
		}
		if(currentExportedNames().contains(key)) {
			resolveExportValue(key); // side-effect-only: propagates TDZ
			return PropertyDescriptor.of(true,false,true);
		}
		return super.getOwnPropertyDescriptor(key);
	}

	@Override
	public Object getOwnProperty(String key, Object defaultValue, Object receiver) {
		if(isSymbolLikeNamespaceKey(key)) {
			return super.getOwnProperty(key, defaultValue, receiver);
		}
		if(currentExportedNames().contains(key)) {
			return resolveExportValue(key);
		}
		return super.getOwnProperty(key, defaultValue, receiver);
	}

	// Spec 9.4.6.9 [[Set]] is a SEPARATE, OVERRIDING algorithm for a Module
	// Namespace Exotic Object - unlike ordinary OrdinarySet (JSObject's own
	// default implementation this replaces, which always calls
	// [[GetOwnProperty]] FIRST to decide how to proceed), it unconditionally
	// returns false with no exports-list lookup at all. Without this
	// override, a plain `ns.anyKey = v` reached getOwnPropertyDescriptor()
	// (via OrdinarySet) before ever reaching setOwnProperty() below - for a
	// DEFERRED namespace, that lookup alone incorrectly triggered the
	// underlying module's evaluation (test262 import-defer/evaluation-
	// triggers/ignore-set-string-*.js), even though the assignment itself
	// was always going to fail regardless of whether `key` names a real
	// export.
	@Override
	public boolean setProperty(String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(RuntimeUtil.isStrictCheck(check)) {
			throw RuntimeUtil.typeError("Cannot assign to read only property {0}", member);
		}
		return false;
	}

	// [[Set]] (desc==null, a plain write) always fails; [[DefineOwnProperty]]
	// (desc!=null) only ever accepts a no-op-equivalent redefinition - see
	// this class's own doc comment.
	@Override
	public boolean setOwnProperty(String key, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(desc==null) {
			// Plain [[Set]] (spec 9.4.6.9) always fails - whether `key` IS
			// a known export (an existing but non-writable-from-outside
			// binding) or not (nothing to add - the namespace is non-
			// extensible) makes no difference to [[Set]] itself, only to
			// WHY it fails; either way, unlike [[DefineOwnProperty]] below
			// (whose rejection is surfaced by Object.defineProperty's OWN
			// caller unconditionally throwing, and must stay silent for
			// Reflect.defineProperty), a plain assignment's throw-or-not
			// depends on the CALLER's strictness (mirrors the ordinary
			// non-writable-property/non-extensible-object convention) - a
			// module's own top-level code is always strict, so
			// `ns.anyKey = v` reaches this (test262 set.js: even a
			// NON-exported key like `local2`, never declared `export`,
			// must still throw here, not just return false).
			if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot assign to read only property {0}", key);
			}
			return false;
		}
		if(isSymbolLikeNamespaceKey(key) || !currentExportedNames().contains(key)) {
			return false;
		}
		if(desc.isAccessor() || !desc.isWritable() || !desc.isEnumerable() || desc.isConfigurable()) {
			return false;
		}
		if(value!=RuntimeUtil.NOT_AVAILABLE) {
			Object current = resolveExportValue(key);
			if(!RuntimeUtil.eqSameValue(getEnvironment(), current, value)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean deleteProperty(String key, DESC_CHECK check) {
		if(!isSymbolLikeNamespaceKey(key) && currentExportedNames().contains(key)) {
			// Mirrors CustomLinkedMap.remove()'s own convention for a
			// rejected delete: strict-mode/explicit-STRICT callers get a
			// thrown TypeError (a module's own top-level code is always
			// strict, so `delete ns.exportedName` reaches this), others
			// just get `false` back.
			if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Property {0} cannot be removed", key);
			}
			return false;
		}
		return true;
	}

	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		// Spec 9.4.6.11 [[OwnPropertyKeys]] ALWAYS computes
		// GetModuleExportsList first, regardless of whether the caller
		// ultimately wants string keys, symbol keys, or both -
		// Object.getOwnPropertySymbols(ns) still triggers a deferred
		// module's evaluation even though it only reads the (separate,
		// always-empty-here) symbol entries afterward (test262 import-defer/
		// evaluation-triggers/trigger-ownPropertyKeys-symbols.js). The
		// `strings` flag only controls whether the computed set is
		// actually USED for output below - the side-effecting call itself
		// must always happen.
		currentExportedNames();
		Iterator<Map.Entry<Object,Object>> stringEntries;
		if(strings) {
			List<String> sorted = new ArrayList<>(currentExportedNames());
			// Matches spec 9.4.6.11's own "as if sorted via %Array.prototype.sort%
			// with undefined comparefn" - plain code-unit string comparison,
			// NOT the array-index-first ordering ordinary objects use.
			Collections.sort(sorted);
			if(enumerableOnly) {
				// enumerableOnly==true means this call is doing
				// EnumerableOwnPropertyNames (Object.keys/values/entries,
				// for-in's EnumerateObjectProperties) - per spec, THAT
				// algorithm calls [[GetOwnProperty]] for EVERY key (to
				// check [[Enumerable]], always true here) BEFORE deciding
				// whether to include it, so it can throw for TDZ even
				// though only the KEY (never the value) is ultimately
				// needed by Object.keys specifically (test262 enumerate-
				// binding-uninit.js/object-keys-binding-uninit.js). A bare
				// [[OwnPropertyKeys]] caller (Reflect.ownKeys,
				// enumerableOnly==false) does NOT do this - own-property-
				// keys-sort.js's `Reflect.ownKeys(ns)` must list keys
				// without resolving/TDZ-checking any of them.
				for(String key: sorted) {
					getOwnPropertyDescriptor(key);
				}
			}
			stringEntries = Iterators.map(sorted.iterator(), (k) -> (Map.Entry<Object,Object>)new Map.Entry<Object,Object>() {
				@Override
				public Object getKey() { return k; }
				// Lazy - only resolved (and so only TDZ-checked) if the
				// caller actually reads the value, matching the ordinary
				// accessor-entry pattern used elsewhere (e.g. ProxyAccessor's
				// own ownPropertyEntries).
				@Override
				public Object getValue() { return getOwnProperty(k, RuntimeUtil.UNDEFINED, ModuleNamespaceObject.this); }
				@Override
				public Object setValue(Object v) { throw new UnsupportedOperationException(); }
			});
		} else {
			stringEntries = Iterators.empty();
		}
		Iterator<Map.Entry<Object,Object>> symbolEntries = symbols ? super.ownPropertyEntries(false,true,enumerableOnly) : Iterators.empty();
		return Iterators.concat(stringEntries, symbolEntries);
	}
}
