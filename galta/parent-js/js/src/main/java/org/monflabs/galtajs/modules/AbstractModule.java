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
package org.monflabs.galtajs.modules;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 */
public abstract class AbstractModule implements JSModule {
	
	private JSEnvironment env;
	private JSModuleDescriptor descriptor;
	
	private Object defaultExport;
	// Distinguishes "genuinely no default export was ever declared" from
	// "the default export's own VALUE is Java null" (`export default
	// null;`) - defaultExport alone can't tell these apart, and callers
	// (ASTImport's "does not have a default export" check) need to.
	private boolean hasDefaultExport;
	private JSObject namedExports;
	
    public AbstractModule(JSEnvironment env, JSModuleDescriptor descriptor) {
    	this.env = env;
    	this.descriptor = descriptor;
    }
    
    @Override
	public JSEnvironment getEnvironment() {
    	return env;
    }
    
    @Override
	public JSModuleDescriptor getDescriptor() {
    	return descriptor;
    }	
	
	@Override
	public Object getDefaultExport() {
		return defaultExport;
	}
	@Override
	public void setDefaultExport(Object defaultExport) {
		this.defaultExport = defaultExport;
		this.hasDefaultExport = true;
	}
	@Override
	public boolean hasDefaultExport() {
		return hasDefaultExport;
	}

	private org.monflabs.galtajs.rt.builtins.standard.module.ModuleNamespaceObject moduleNamespaceObject;
	@Override
	public JSObject getModuleNamespaceObject() {
		if(moduleNamespaceObject==null) {
			moduleNamespaceObject = new org.monflabs.galtajs.rt.builtins.standard.module.ModuleNamespaceObject(getEnvironment(), this);
		}
		return moduleNamespaceObject;
	}

	// `import defer * as ns from '...'` (import-defer proposal) - a
	// SEPARATE cache slot from moduleNamespaceObject above (not perfectly
	// spec-accurate: per spec a deferred and non-deferred import of the
	// SAME module should eventually converge on the exact same [[Namespace]]
	// object once evaluated, which this simplified two-slot cache doesn't
	// guarantee for a module reached BOTH ways - a known, narrower gap).
	// `deferredView` is a JSModule that triggers real evaluation (this
	// module's own initModule()) on first genuine use - see
	// InterpretedGlobalRuntimeContext.importDeferredNamespace()'s own doc
	// comment for the caller side. Cached here (on the REAL module, keyed
	// by module identity) rather than per-call, so repeated `import defer`
	// statements targeting the SAME module get the SAME namespace object
	// identity (test262 import-defer/deferred-namespace-object/identity.js).
	private JSObject deferredNamespaceObject;
	public JSObject getDeferredModuleNamespaceObject(JSModule deferredView) {
		if(deferredNamespaceObject==null) {
			deferredNamespaceObject = new org.monflabs.galtajs.rt.builtins.standard.module.ModuleNamespaceObject(getEnvironment(), deferredView, true);
		}
		return deferredNamespaceObject;
	}
	// import.meta (spec 16.2.1.7 ImportMeta) - a plain, null-prototype
	// object, one per module, created lazily and cached by identity so
	// repeated `import.meta` references within (or importing) the same
	// module observe the SAME object (spec: "the Module Record's
	// [[ImportMeta]] slot"). No implementation-defined own properties
	// are populated here - spec explicitly allows an empty object; test262
	// only relies on the null prototype (e.g. dynamic-import/assignment-
	// expression/import-meta.js: `import(import.meta)` must reject since a
	// null-prototype object has no toString/valueOf for ToString to use).
	private JSObject importMetaObject;
	public JSObject getImportMetaObject() {
		if(importMetaObject==null) {
			importMetaObject = JSObject.create(getEnvironment());
			importMetaObject.setPrototype(null);
		}
		return importMetaObject;
	}

	@Override
	public JSObject getNamedExports() {
		return namedExports;
	}

	// Unlike getNamedExports() (which stays null when this module has never
	// had ANY named export at all - a real, checked invariant, see
	// TranspilerModuleTest's own assertNull(m1.getNamedExports())), this
	// ALWAYS returns a real object, lazily creating one if needed. For
	// `import * as ns from '...'`'s STATIC namespace import (ASTImport.
	// java's interpreted evaluate() assigns this object reference DIRECTLY
	// as `ns`, not a copy) - a genuine self-/circular-import reached
	// mid-load (now possible without crashing - see the module cache-
	// before-run-body fix elsewhere) previously got a raw Java `null`
	// instead of a namespace object at all (`Reflect.set(ns, ...)`: "Target
	// must be an object. null"). Since this is the SAME underlying object
	// addNamedExports() mutates in place, obtaining a reference to it EARLY
	// (before any exports have run) still correctly observes exports added
	// LATER in the same module's execution.
	@Override
	public JSObject ensureNamedExports() {
		if(namedExports==null) {
			namedExports = JSObject.create(getEnvironment());
		}
		return namedExports;
	}
	public void setNamedExports(JSObject namedExports) {
		this.namedExports = namedExports;
	}
	
	@Override
	public void addNamedExports(String key, Object value) {
		if(namedExports==null) {
			namedExports = JSObject.create(getEnvironment());
		}
		namedExports.setOwnProperty(key,value);
	}

	// Live-binding registration for a LOCALLY-declared export (see
	// docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's P2) -
	// JSTranspiledUnit's own codegen (ASTExport.transpileJavaStatement())
	// calls this alongside addNamedExports() for every local named export,
	// passing a JSVarRef wrapping the exporting module's own array+index -
	// unlike addNamedExports()'s one-time value snapshot, getValue()/
	// setValue() on this accessor always indirect through the SAME array
	// object a later plain reassignment (`local1 = 333;`) also writes to
	// directly, so it stays live for the whole lifetime of this module
	// instance (test262 language/module-code/namespace/internals/
	// get-str-update.js). getExportAccessor() below checks this FIRST,
	// before the existing re-export delegation and the interface default's
	// value-snapshot fallback. JSInterpretedUnit never calls this (it has
	// its own, already-live resolveExport()/getExportAccessor() override
	// entirely independent of this map) - harmless no-op for that kind,
	// since the map stays null.
	private java.util.Map<String,VarAccessor> liveLocalExports;

	public void registerLiveExport(String name, VarAccessor accessor) {
		if(liveLocalExports==null) {
			liveLocalExports = new java.util.HashMap<>();
		}
		liveLocalExports.put(name, accessor);
	}

	// Exposes registerLiveExport()'s own map to a subclass's resolveExport()
	// override (JSTranspiledUnit's - see its own doc comment) without
	// duplicating the map itself.
	protected VarAccessor getLiveLocalExport(String name) {
		return liveLocalExports!=null ? liveLocalExports.get(name) : null;
	}

	// Static (name-only, no value/accessor) view of registerLiveExport()'s
	// own map - lets ModuleNamespaceObject.currentExportedNames() see every
	// locally-declared export name for a TRANSPILED module even before its
	// own `export` statement has run, mirroring what it already gets for an
	// INTERPRETED module via JSInterpretedUnit.getProgram().
	// getStaticExportedNames() (both are populated at hoist/link time, not
	// tied to execution having reached any particular statement - spec
	// GetExportedNames is a static operation). Empty for an
	// InterpretedUnit, which never calls registerLiveExport() at all (see
	// that method's own doc comment) - harmless, since ModuleNamespaceObject
	// already has its own JSInterpretedUnit-specific path for that kind.
	// Name-only (no accessor) static export names that don't fit
	// registerLiveExport()'s VarAccessor shape - currently just "default"
	// for a NON-hoistable `export default <expr>;`/`export default
	// class{}`, whose OWN slot isn't a plain named local variable the way
	// getLiveExportNames() (ASTProgram) requires. ASTProgram's own hoist
	// pass calls this whenever this module has ANY `export default` at all
	// (mirrors its own broader getStaticExportedNames(), which - unlike
	// getLiveExportNames() - includes every default form, hoistable or
	// not).
	private java.util.Set<String> additionalStaticExportNames;

	public void registerStaticExportName(String name) {
		if(additionalStaticExportNames==null) {
			additionalStaticExportNames = new java.util.HashSet<>();
		}
		additionalStaticExportNames.add(name);
	}

	public java.util.Set<String> getLiveExportedNames() {
		if(liveLocalExports==null && namedReExportSources==null && additionalStaticExportNames==null) {
			return java.util.Collections.emptySet();
		}
		java.util.Set<String> names = new java.util.LinkedHashSet<>();
		if(liveLocalExports!=null) {
			names.addAll(liveLocalExports.keySet());
		}
		if(namedReExportSources!=null) {
			// A self-referencing named re-export (addNamedReExport(name,
			// this, ...), hoisted early by ASTProgram - see its own doc
			// comment) is ALSO a statically-known name the moment this
			// module starts, same reasoning as liveLocalExports above.
			names.addAll(namedReExportSources.keySet());
		}
		if(additionalStaticExportNames!=null) {
			names.addAll(additionalStaticExportNames);
		}
		return names;
	}

	// Star-re-export bookkeeping for `export * from '...'` (ASTExport's bare
	// star-export branch) - distinct from addNamedExports() (used for local
	// declarations and explicit `export {name}` re-exports), which callers
	// keep calling directly for those cases. Per spec (ResolveExport /
	// GetModuleNamespace), a name reachable through TWO OR MORE different
	// `export *` sources is "ambiguous" - permanently OMITTED from this
	// module's own exports and namespace (not a thrown error at this stage;
	// only a direct `import {thatName}` of it is a SyntaxError - not
	// implemented here, a narrower remaining gap). Identity is compared by
	// SOURCE MODULE, not by the exported value - two unrelated modules that
	// both happen to `export var both = null;` are still ambiguous, while
	// two star-exports that trace back to the exact same underlying module
	// are NOT. "default" is never included - a bare `export *` never
	// re-exports another module's default export.
	private java.util.Map<String,JSModule> starExportSources;
	private java.util.Set<String> starExportAmbiguous;

	@Override
	public void addStarReExport(String name, Object value, JSModule source) {
		if("default".equals(name)) {
			return;
		}
		if(starExportAmbiguous!=null && starExportAmbiguous.contains(name)) {
			return;
		}
		if(starExportSources==null) {
			starExportSources = new java.util.LinkedHashMap<>();
		}
		if(starExportSources.containsKey(name)) {
			JSModule existingSource = starExportSources.get(name);
			if(existingSource!=source) {
				starExportSources.remove(name);
				if(starExportAmbiguous==null) {
					starExportAmbiguous = new java.util.HashSet<>();
				}
				starExportAmbiguous.add(name);
				if(namedExports!=null) {
					namedExports.deleteProperty(name);
				}
			}
			return;
		}
		// Per spec (ResolveExport), a local declaration or an explicit named
		// re-export (`export {x}`/`export {x as y} from '...'`) always takes
		// precedence over a same-named `export *` re-export - the star entry
		// is silently shadowed, never ambiguous, and must not clobber the
		// value already recorded for it. Those forms are the only other
		// callers of addNamedExports(), and always call it BEFORE any
		// starExportSources entry for the same name would exist here (this
		// method returns early above once starExportSources already has an
		// entry) - so namedExports already containing this key with no
		// starExportSources entry for it means a local/named-from export got
		// there first.
		if(namedExports!=null && namedExports.hasProperty(name)) {
			return;
		}
		starExportSources.put(name, source);
		addNamedExports(name, value);
	}

	// Named re-export bookkeeping for `export {x}`/`export {x as y} from
	// '...'` (ASTExport's named-items-with-`from` branch) - like
	// starExportSources above, but keyed by the EXPORTED (possibly
	// aliased) name, and remembering the SOURCE module's OWN name for it
	// too (an alias only renames on THIS module's side; the source is
	// still asked for its original name). Enables getExportAccessor()
	// below to recursively delegate to the source module's own live
	// accessor instead of only ever offering the value snapshot
	// addNamedExports() already stores alongside this (kept, unchanged,
	// for any caller/module-kind that only wants the snapshot).
	private record NamedReExport(JSModule source, String sourceName) {}
	private java.util.Map<String,NamedReExport> namedReExportSources;

	@Override
	public void addNamedReExport(String exportedName, JSModule source, String sourceName) {
		if(namedReExportSources==null) {
			namedReExportSources = new java.util.HashMap<>();
		}
		namedReExportSources.put(exportedName, new NamedReExport(source, sourceName));
	}

	// Live accessor resolution for re-exports (named-with-`from`, then
	// star) - checked BEFORE the interface default's value-snapshot
	// fallback. Recursion (source.getExportAccessor(...)) naturally
	// chains through a re-export-of-a-re-export. Wrapped in
	// VarAccessor.importBinding() for the SAME reason a direct import
	// binding is (see that factory's own doc comment): a re-exported
	// binding must still be read-only from any THIRD module that imports
	// it through here, even though the ORIGINAL source variable is
	// ordinarily mutable.
	@Override
	public VarAccessor getExportAccessor(String name) {
		if(liveLocalExports!=null) {
			VarAccessor a = liveLocalExports.get(name);
			if(a!=null) {
				return a;
			}
		}
		if(namedReExportSources!=null) {
			NamedReExport ref = namedReExportSources.get(name);
			if(ref!=null) {
				return VarAccessor.importBinding(name, ref.source().getExportAccessor(ref.sourceName()));
			}
		}
		if(starExportSources!=null) {
			JSModule source = starExportSources.get(name);
			if(source!=null) {
				return VarAccessor.importBinding(name, source.getExportAccessor(name));
			}
		}
		// "default" is never a genuine entry in namedExports (see
		// JSModule.getExport()'s own doc comment) - it's tracked via
		// setDefaultExport()/hasDefaultExport()/getDefaultExport()
		// instead. Needed so a NAMED item resolution of "default"
		// (`export {default}`/`import {default as x} from '...'`) works
		// for any AbstractModule subclass with a default export but no
		// namedExports entry for it - e.g. JSNativeModule's own synthetic
		// JSON/text modules (spec ParseJSONModule: default export only,
		// see RuntimeUtil.parseAttributedModuleContent()), which would
		// otherwise fall through to JSModule.super's namedExports-only
		// snapshot and throw "does not export an entry default" even
		// though hasDefaultExport() is true. Mirrors JSInterpretedUnit.
		// resolveExport()'s own identical fallback.
		if("default".equals(name) && hasDefaultExport()) {
			return VarAccessor.ofStatic("default", getDefaultExport());
		}
		return JSModule.super.getExportAccessor(name);
	}
}
