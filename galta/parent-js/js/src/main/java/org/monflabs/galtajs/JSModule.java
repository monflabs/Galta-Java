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
package org.monflabs.galtajs;

import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 *
 */
public interface JSModule {

	public static final String DEFAULT_PROGRAM_NAME 	= "<program>";
	public static final String DEFAULT_EXPRESSION_NAME 	= "<expression>";

    public JSEnvironment getEnvironment();

	public JSModuleDescriptor getDescriptor();

	public Object getDefaultExport();
	public void setDefaultExport(Object defaultExport);

	// getDefaultExport()==null alone can't distinguish "no default export
	// was ever declared" from "the default export's own value is Java
	// null" (`export default null;`) - default fallback matches the OLD,
	// ambiguous check for any JSModule implementation that doesn't
	// override setDefaultExport() to track this separately.
	public default boolean hasDefaultExport() {
		return getDefaultExport()!=null;
	}

	public JSObject getNamedExports();
	public void addNamedExports(String key, Object value);

	// Like getNamedExports(), but never null - see AbstractModule's own
	// implementation/doc comment for why this is a SEPARATE method rather
	// than changing getNamedExports() itself (a real, checked invariant
	// relies on the latter staying null for a module with no named exports
	// at all). Default falls back to getNamedExports() (still possibly
	// null) for any implementer that hasn't opted in.
	public default JSObject ensureNamedExports() {
		return getNamedExports();
	}

	// `export * from '...'` merging - see AbstractModule's own doc comment
	// for the star-vs-star ambiguity rule. `source` is the exporting
	// module's OWN record (compared by identity, not `value` - two
	// re-exports of the same name are only NON-ambiguous when they trace
	// back to the exact same module, regardless of what value that
	// binding currently holds - e.g. two unrelated modules that both
	// happen to `export var both = null;` are still ambiguous). Defaults
	// to a plain add for any implementer that doesn't need the ambiguity
	// bookkeeping.
	public default void addStarReExport(String key, Object value, JSModule source) {
		addNamedExports(key, value);
	}

	// Named re-export bookkeeping for `export {x}`/`export {x as y} from
	// '...'` - see AbstractModule's own override/doc comment for the real
	// (live-accessor-enabling) implementation. Default no-op: a module
	// kind that doesn't override this simply doesn't get live re-export
	// behavior (getExportAccessor() falls back to its own default, a
	// value snapshot via getExport() - the caller (ASTExport) always
	// stores that snapshot via addNamedExports() too, unconditionally, so
	// this being a no-op never loses the export entirely).
	public default void addNamedReExport(String exportedName, JSModule source, String sourceName) {
	}

	// Module Namespace Exotic Object (spec 9.4.6) for `import * as ns`/
	// dynamic import()'s resolved value - spec requires this to be a
	// SINGLE, CACHED object per module (GetModuleNamespace's own
	// [[Namespace]] slot), reused across every reference (test262's own
	// identity tests rely on this). See AbstractModule's own override for
	// the real (cached) implementation; this default (a fresh, UNCACHED
	// object per call) is only a fallback for a module kind that doesn't
	// extend it.
	public default JSObject getModuleNamespaceObject() {
		return new org.monflabs.galtajs.rt.builtins.standard.module.ModuleNamespaceObject(getEnvironment(), this);
	}

	// An import of a name the module does not export is a SyntaxError at
	// link time (ResolveExport returns null: InitializeEnvironment step 7.d.i).
	public default Object getExport(String name) {
		JSObject namedExports = getNamedExports();
		if (namedExports == null) {
			throw org.monflabs.galtajs.rt.RuntimeUtil.syntaxError("Module {0} does not export named entries", getDescriptor().getName());
		}
		if (!namedExports.hasProperty(name)) {
			throw org.monflabs.galtajs.rt.RuntimeUtil.syntaxError("Module {0} does not export an entry {1}", getDescriptor().getName(), name);
		}
		return namedExports.getProperty(name);
	}

	// A LIVE binding for a named export, when this module implementation
	// can provide one (see JSInterpretedUnit's own override, which resolves
	// straight through to the exporting module's own top-level variable
	// cell) - importers can then alias their local binding directly to
	// this accessor (VariableMap.cache()) instead of copying a value
	// snapshot, so a later plain reassignment of the exported variable
	// stays visible through the import. Default falls back to a read-only
	// snapshot of getExport(name) - correct (if non-live) for any module
	// kind, or export form, that doesn't override this.
	public default VarAccessor getExportAccessor(String name) {
		return VarAccessor.ofStatic(name, getExport(name));
	}

	// Spec 16.2.1.6.2 GetExportedNames(exportStarSet) - the FULL set of
	// names this module exports, transitively following bare `export *
	// from` sources (unlike ASTProgram.getStaticExportedNames(), which
	// deliberately excludes those - see its own doc comment), with
	// exportStarSet as the cycle guard (a DIFFERENT set from
	// resolveExport()'s own resolveSet - this one is keyed by MODULE
	// alone, since GetExportedNames never resolves a specific name, just
	// enumerates "have I already visited this module's own star sources").
	// ModuleNamespaceObject's own [[HasProperty]]/[[OwnPropertyKeys]] need
	// this to be correct even for a PURE star cycle (two modules that only
	// ever `export * from` each other, no local export anywhere) - the
	// OLD dynamic getNamedExports()-snapshot-based approach never
	// populates anything for that shape at all. Default (for a module
	// kind with no re-export concept) just returns its own current
	// namedExports keys.
	public default java.util.Set<String> getExportedNames(java.util.Set<JSModule> exportStarSet) {
		JSObject named = getNamedExports();
		if(named==null) {
			return java.util.Collections.emptySet();
		}
		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
		for(java.util.Iterator<String> it = named.ownPropertyKeys(false); it.hasNext(); ) {
			names.add(it.next());
		}
		return names;
	}

	// A LIVE binding for THIS module's `export default`, but ONLY when
	// that default export is itself a genuine HoistableDeclaration
	// (`export default function fn(){}` and generator/async equivalents -
	// see ASTExport.isHoistableDefaultExport()) - unlike getExportAccessor()
	// above, there's no generic value-snapshot fallback to offer here: a
	// default export is never stored in namedExports (getExport("default")
	// would always throw "does not export named entries" regardless of
	// timing, since "default" is never added there at all), so a caller
	// unable to get a live accessor here must fall back to the ORDINARY
	// (non-hoisted) `module.getDefaultExport()` read at the import
	// statement's own source position instead - never to getExport().
	// Returns null when not applicable (the default export exists but
	// isn't a hoistable declaration, or hasn't started evaluating yet).
	public default VarAccessor getLiveDefaultExportAccessor() {
		return null;
	}

	// Spec 16.2.1.6.3 ResolveExport's cycle-guard key: a (module, exportName)
	// pair currently being resolved along one recursion path. Equality is
	// module IDENTITY (JSModule doesn't override equals()/hashCode(), so
	// this is Object identity - exactly right, matching spec's own Module
	// Record identity) plus exportName VALUE equality (String.equals()).
	public record ResolveKey(JSModule module, String exportName) {}

	// Spec ResolveExport's own return shape: a ResolvedBinding Record
	// {[[Module]], [[BindingName]]} - [[Module]]/[[BindingName]] are what
	// star-export ambiguity comparison needs (spec: two star resolutions
	// are ambiguous unless they're the SAME module AND SAME binding name),
	// `accessor` is GaltaJS's own live-VarAccessor addition so a caller can
	// actually read/alias the resolved binding without a second lookup.
	public record ResolvedBinding(JSModule module, String bindingName, VarAccessor accessor) {}

	// resolveSet-aware export resolution (spec ResolveExport) - unlike
	// getExportAccessor(name) (which THROWS when not found, the right
	// contract for a single top-level lookup), this returns null for "not
	// resolvable through this path" (genuinely absent OR broke a cycle),
	// so a caller walking multiple star-export sources can simply skip a
	// null and try the next one instead of catching an exception per
	// candidate. Default (for a module kind - native/JSON/etc - with no
	// re-export concept of its own, so no cycles possible) just wraps the
	// existing getExportAccessor(name) contract, swallowing its "not
	// found" exception into null.
	public default ResolvedBinding resolveExport(String name, java.util.Set<ResolveKey> resolveSet) {
		try {
			return new ResolvedBinding(this, name, getExportAccessor(name));
		} catch(RuntimeException ex) {
			return null;
		}
	}
}
