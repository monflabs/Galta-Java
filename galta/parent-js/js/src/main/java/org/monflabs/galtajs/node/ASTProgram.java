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
package org.monflabs.galtajs.node;

import java.util.WeakHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.ModuleUtil;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.GlobalThis;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerException;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * JsonScrGaltaJSipt Program.
 */
public class ASTProgram extends ASTRootStatementList implements TopNode {
	
	private String text;
	private boolean debugInfo;
	private boolean commonJS;
	private boolean isModule;
	// True only for genuine eval'd source (JSEnvironment.SCRIPT_EVAL/
	// createEvalScript()) - never for an ordinary script or a module, even
	// though both also have isModule()==false, so a check needing "is this
	// eval code specifically" must use this field, not !isModule(). Set
	// before the init() walk below runs, same timing as isModule.
	private boolean isEval;
	private boolean asyncExecution;
	// True when a direct eval's CALLER is genuinely strict (see __init's
	// `forceStrict` param) - distinct from isForceStrictMode(), which only
	// ever reflects this program's OWN directive prologue. A freshly-parsed
	// eval program has no AST ancestor to inherit strictness from (it's its
	// own separate root), so any inherited caller-strictness has to be
	// threaded in explicitly and remembered here; MainContext (built in
	// __init below) already combines the two correctly for the InitContext-
	// threaded isGenuinelyStrict() path, but code that queries the root
	// container directly (ASTFunctionDecl.createFunction()'s ancestor walk,
	// JSInterpretedUnit.isForceStrictMode()) needs the same combined answer
	// available here too.
	private boolean callerForcedStrict;

	public ASTProgram(String text, List<ASTNode> nodes, boolean debugInfo) {
		super(null,nodes);
		this.text = text;
		this.debugInfo = debugInfo;
	}

	public String getText() {
		return text;
	}

	public boolean isAsyncExecution() {
		return asyncExecution;
	}

	// A program has no enclosing scope to inherit strictness from EXCEPT a
	// direct eval's caller (callerForcedStrict, see field comment).
	@Override
	public boolean isGenuinelyStrictMode() {
		return isForceStrictMode() || callerForcedStrict;
	}

	public void setAsyncExecution(boolean asyncExecution) {
		this.asyncExecution = asyncExecution;
	}

	// True only for a genuine ES module body (never for a script, direct/
	// indirect eval, or CommonJS-wrapped file - see JSEnvironment.
	// SCRIPT_MODULE/compileProgram()). Set before the init() walk below runs
	// (so it's visible to ASTFunctionDecl/ASTVarContainer's declaration-
	// collection pass), used by validateModuleDeclarations() to apply the
	// module-specific "duplicate LexicallyDeclaredNames" early error that
	// scripts don't have.
	public boolean isModule() {
		return isModule;
	}

	// See isEval field's own doc comment.
	public boolean isEval() {
		return isEval;
	}

	// Lazily-computed map (exported name -> local variable name) of every
	// name this module exports via a LOCAL declaration/reference (`export
	// var/let/const x`, `export function x(){}`, `export class X{}`,
	// `export {x}`/`export {x as y}` with no `from`) - i.e. names for
	// which "the exported binding IS this module's own top-level variable
	// named <local name>" holds, the precondition JSInterpretedUnit.
	// getExportAccessor() needs before it's safe to expose a live accessor
	// by looking up the LOCAL name in this module's own variable scope (a
	// non-exported local, or a genuine re-export from another module, must
	// NOT be reachable this way - only entries actually present here are).
	// Deliberately a STATIC (AST-level) map, not derived from the dynamic
	// namedExports object - so it's already complete before ANY
	// module-body statement (including an early-hoisted named import) has
	// run, see ASTImport.hoistBindings()'s own doc comment. Aliased
	// exports (`export {x as y}`) ARE included (key "y", value "x") -
	// confirmed needed via instn-named-id-name.js's own self-import of an
	// all-aliased export list, which otherwise fell through to the
	// snapshot-based getExport() fallback, which in turn needs
	// namedExports to already be populated by the (non-hoisted) `export`
	// statement having already run - exactly the ordering problem this
	// whole mechanism exists to avoid.
	private java.util.Map<String,String> liveExportNames;

	public java.util.Map<String,String> getLiveExportNames() {
		if(liveExportNames==null) {
			java.util.Map<String,String> names = new java.util.HashMap<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp)) {
					continue;
				}
				if(exp.getFrom()!=null) {
					// Re-export (`export {x} from '...'`/`export * from
					// '...'`) - not backed by a variable in THIS module.
					continue;
				}
				for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: exp.getItems()) {
					String exportedName = item.getAlias()!=null ? item.getAlias() : item.getName();
					names.put(exportedName, item.getName());
				}
				ASTNode decl = exp.getNamedExport();
				if(decl instanceof org.monflabs.galtajs.node.variable.ASTVariableDecl vd) {
					for(String name: vd.getDeclaredVariables()) {
						names.put(name, name);
					}
				} else if(decl instanceof org.monflabs.galtajs.node.control.ASTFunctionDecl fd
						&& org.monflabs.util.StringUtil.isNotEmpty(fd.getFunctionName())) {
					names.put(fd.getFunctionName(), fd.getFunctionName());
				} else if(decl instanceof org.monflabs.galtajs.node.clazz.ASTClassDecl cd
						&& org.monflabs.util.StringUtil.isNotEmpty(cd.getClassName())) {
					names.put(cd.getClassName(), cd.getClassName());
				}
				// `export default function fn(){}` (named function/
				// generator/async form only - see ASTExport.
				// isHoistableDefaultExport()'s own doc comment for why
				// `export default class{}`/`export default <expr>` are
				// excluded here, same as they're excluded from
				// ASTStatementList's hoisting reorder) - "default" maps to
				// the function's own local name, exactly like any other
				// named declaration export above.
				if(exp.isHoistableDefaultExport()) {
					String fnName = ((org.monflabs.galtajs.node.control.ASTFunctionDecl)exp.getDefaultExport()).getFunctionName();
					names.put("default", fnName);
				}
			}
			liveExportNames = names;
		}
		return liveExportNames;
	}

	// `export {x as y} from '...'` when the `from` target is THIS SAME
	// module (a self-reference - test262's own module-code_FIXTURE.js
	// idiom, e.g. namespace/internals/get-str-found-uninit.js's `indirect`)
	// - getLiveExportNames() deliberately skips ANY re-export (its own
	// value is never backed by a local variable in the general,
	// cross-module case), but a SELF-referencing one genuinely IS: `y` is
	// just an alias for THIS module's own local `x`. ASTExport.evaluate()'s
	// own self-reference handling only kicks in once that statement
	// actually RUNS (reading moduleContext's local scope directly, no live
	// mapping registered - see its own doc comment for why) - this gives
	// JSInterpretedUnit.getExportAccessor() (and so the Module Namespace
	// Exotic Object, and any self-importer) a way to resolve `y` to `x`'s
	// own live/TDZ-tracked variable cell EARLY too, exactly like a direct
	// export already does. Returns null (not found / not a self-reference)
	// rather than throwing - callers fall back to their own default path.
	public String getSelfReexportLocalName(String exportedName, String ownModuleName) {
		int n = getChildCount();
		for(int i=0; i<n; i++) {
			ASTNode child = skipTransparent(getChild(i));
			if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) || exp.getFrom()==null) {
				continue;
			}
			if(!ModuleUtil.resolvePath(ownModuleName, exp.getFrom()).equals(ownModuleName)) {
				continue;
			}
			for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: exp.getItems()) {
				String alias = item.getAlias()!=null ? item.getAlias() : item.getName();
				if(alias.equals(exportedName)) {
					return item.getName();
				}
			}
		}
		return null;
	}

	private java.util.Set<String> staticExportedNames;

	// Broader than getLiveExportNames() (which only maps names backed by a
	// LOCAL variable slot, for live-binding purposes, and deliberately skips
	// re-exports and non-hoistable defaults): this is the FULL set of names
	// this module SYNTACTICALLY exports, known purely from its own AST,
	// independent of execution order - used by the Module Namespace Exotic
	// Object (`import * as ns`) for [[HasProperty]]/[[OwnPropertyKeys]],
	// which must be correct even BEFORE any module-body statement runs
	// (test262 namespace/internals/has-property-str-found-uninit.js: `'x'
	// in ns` is true even while `x`'s own binding is still in the TDZ).
	// Deliberately does NOT include a bare `export * from '...'` re-export's
	// names - those depend on the SOURCE module's own exports, which isn't
	// knowable from this AST alone (see ModuleNamespaceObject's own doc
	// comment for how that gap is handled instead, best-effort/dynamically).
	public java.util.Set<String> getStaticExportedNames() {
		if(staticExportedNames==null) {
			java.util.Set<String> names = new java.util.LinkedHashSet<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp)) {
					continue;
				}
				for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: exp.getItems()) {
					names.add(item.getAlias()!=null ? item.getAlias() : item.getName());
				}
				if(exp.getFrom()!=null) {
					if(exp.getNamespace()!=null) {
						// `export * as ns from '...'`
						names.add(exp.getNamespace());
					}
					// A bare `export * from '...'` (getNamespace()==null,
					// getItems() empty) contributes no STATICALLY knowable
					// name here - see this method's own doc comment.
					continue;
				}
				ASTNode decl = exp.getNamedExport();
				if(decl instanceof org.monflabs.galtajs.node.variable.ASTVariableDecl vd) {
					names.addAll(vd.getDeclaredVariables());
				} else if(decl instanceof org.monflabs.galtajs.node.control.ASTFunctionDecl fd
						&& org.monflabs.util.StringUtil.isNotEmpty(fd.getFunctionName())) {
					names.add(fd.getFunctionName());
				} else if(decl instanceof org.monflabs.galtajs.node.clazz.ASTClassDecl cd
						&& org.monflabs.util.StringUtil.isNotEmpty(cd.getClassName())) {
					names.add(cd.getClassName());
				}
				if(exp.getDefaultExport()!=null) {
					// Any `export default ...` at all (hoistable or not,
					// unlike getLiveExportNames()'s narrower check) - the
					// NAME "default" is exported syntactically regardless.
					names.add("default");
				}
			}
			staticExportedNames = names;
		}
		return staticExportedNames;
	}

	// Static (AST-level) export-entry lists mirroring the spec module
	// record's IndirectExportEntries/StarExportEntries (ParseModule step
	// 10) - unlike getLiveExportNames() (which only covers LOCAL
	// declarations) or the dynamic, execution-order-populated
	// namedReExportSources/starExportSources maps in AbstractModule, these
	// are known purely from THIS module's own AST, independent of whether
	// any statement (including the exporting `export ... from` itself) has
	// run yet. This is what makes JSModule.resolveExport()'s resolveSet-
	// based walk (spec 16.2.1.6.3 ResolveExport) possible: it can recurse
	// into a source module's own static entries even while that source
	// module is still mid-load/mid-evaluation, exactly like the spec
	// algorithm (which never depends on execution order at all - only on
	// the STATIC module record built during instantiation).
	public record IndirectExportEntry(String exportName, String moduleRequest, String importName) {}
	public record StarExportEntry(String moduleRequest) {}

	// Sentinel importName for an `export * as ns from '...'` indirect entry
	// (spec Table 59: ImportName=~all~, LocalName=null) - resolving one of
	// these doesn't recurse into the source module looking for a NAME (it
	// has none), it resolves directly to the SOURCE MODULE's own namespace
	// (spec ResolveExport step 6.iii: "Return ResolvedBinding{[[Module]]:
	// importedModule, [[BindingName]]: namespace}"). Never a legal JS
	// identifier, so it can't collide with a real importName.
	public static final String NAMESPACE_IMPORT_NAME = "*namespace*";
	// Spec ResolveExport's ~source~ [[BindingName]]: `import source x from
	// '...'; export {x};` is an indirect entry whose resolution is the
	// TARGET module's Module Source Object, obtained from its descriptor
	// alone (the target is never loaded). Same never-a-legal-identifier
	// rationale as NAMESPACE_IMPORT_NAME.
	public static final String SOURCE_IMPORT_NAME = "*source*";

	private java.util.List<IndirectExportEntry> indirectExportEntries;
	private java.util.List<StarExportEntry> starExportEntries;
	private java.util.Map<String,IndirectExportEntry> importedLocalNames;

	// Maps a LOCAL name introduced by one of THIS module's own `import
	// {x}`/`import {a as x} from '...'` declarations (NOT `import x
	// from`/`import * as x from`, neither of which has a single named
	// [[ImportName]] to trace back to) to the (moduleRequest, importName)
	// it came from. Per spec ParseModule step 10.1.ii-iii, a bare `export
	// {x}` (no `from`) whose `x` is ITSELF one of these imported names is
	// NOT a local export entry at all - it's rewritten into an INDIRECT
	// entry tracing back to the ORIGINAL import's own source. This
	// matters for JSModule.resolveExport()'s star-ambiguity comparison
	// (spec ResolveExport step 8d), which needs the TRUE underlying
	// [[Module]]/[[BindingName]] - this module's own local variable cell
	// for an import is only ever a live ALIAS (right VALUE, wrong
	// identity for that comparison). getLiveExportNames() deliberately
	// keeps treating such names as "local" (unchanged, for its existing
	// callers/callers' doc-commented guarantees) - this is a separate,
	// additional lookup consulted only by resolveExport()'s own local-vs-
	// indirect disambiguation.
	public java.util.Map<String,IndirectExportEntry> getImportedLocalNames() {
		if(importedLocalNames==null) {
			java.util.Map<String,IndirectExportEntry> names = new java.util.HashMap<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				if(!(child instanceof org.monflabs.galtajs.node.control.ASTImport imp)) {
					continue;
				}
				for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: imp.getItems()) {
					String localName = item.getAlias()!=null ? item.getAlias() : item.getName();
					names.put(localName, new IndirectExportEntry(null, imp.getFrom(), item.getName()));
				}
				// `import * as x from '...'` re-exported bare (`export
				// {x}`) - same spec treatment as `export * as ns from`
				// (ParseModule step 10.1.ii.2/3.a): an indirect entry with
				// the namespace sentinel importName, not a local one.
				// EXCLUDES `import defer * as x from '...'` - resolving
				// that indirect entry means resolveModuleForRequest()
				// eagerly loads/evaluates the source module (needed to
				// build ITS namespace object for the ambiguity-identity
				// check), which would defeat the entire point of a
				// deferred import: its own local variable already holds
				// the correct (lazy) deferred namespace object, so
				// treating it as an ordinary LOCAL export instead - read
				// directly, no eager resolution - is both simpler and
				// correct for the value (test262 import-defer/deferred-
				// namespace-object/reexport-deferred-ns-evaluation.js).
				if(imp.getNamespace()!=null && !imp.isDeferred()) {
					names.put(imp.getNamespace(), new IndirectExportEntry(null, imp.getFrom(), NAMESPACE_IMPORT_NAME));
				}
				// `import source x from '...'` re-exported bare - see
				// SOURCE_IMPORT_NAME's own doc comment.
				if(imp.isSourcePhase() && imp.getDefaultImport()!=null) {
					names.put(imp.getDefaultImport(), new IndirectExportEntry(null, imp.getFrom(), SOURCE_IMPORT_NAME));
				}
			}
			importedLocalNames = names;
		}
		return importedLocalNames;
	}

	public java.util.List<IndirectExportEntry> getIndirectExportEntries() {
		if(indirectExportEntries==null) {
			java.util.List<IndirectExportEntry> entries = new java.util.ArrayList<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) || exp.getFrom()==null) {
					continue;
				}
				// `export {x}`/`export {x as y} from '...'`
				for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: exp.getItems()) {
					String exportedName = item.getAlias()!=null ? item.getAlias() : item.getName();
					entries.add(new IndirectExportEntry(exportedName, exp.getFrom(), item.getName()));
				}
				// `export * as ns from '...'` - see NAMESPACE_IMPORT_NAME's
				// own doc comment.
				if(exp.getNamespace()!=null) {
					entries.add(new IndirectExportEntry(exp.getNamespace(), exp.getFrom(), NAMESPACE_IMPORT_NAME));
				}
			}
			indirectExportEntries = entries;
		}
		return indirectExportEntries;
	}

	public java.util.List<StarExportEntry> getStarExportEntries() {
		if(starExportEntries==null) {
			java.util.List<StarExportEntry> entries = new java.util.ArrayList<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) || exp.getFrom()==null) {
					continue;
				}
				// A bare `export * from '...'` - no items, no namespace alias.
				if(exp.getItems().isEmpty() && exp.getNamespace()==null) {
					entries.add(new StarExportEntry(exp.getFrom()));
				}
			}
			starExportEntries = entries;
		}
		return starExportEntries;
	}

	// Every distinct module specifier this program's own top-level
	// import/export-from statements reference (both forms - a plain
	// `import "./x.js"`, `import {x} from`, `import * as x from`, AND any
	// `export ... from` shape) - unlike getIndirectExportEntries()/
	// getStarExportEntries() (which only cover EXPORT forms, for
	// resolveExport()'s own needs), this also covers plain IMPORT
	// statements, since it exists for a DIFFERENT purpose: `import defer`
	// (spec) needs to eagerly verify its target's own DIRECT dependencies
	// all resolve to a real module (though not evaluate them) at defer-
	// time, not lazily wait until first access - see
	// InterpretedGlobalRuntimeContext.importDeferredNamespace()'s own doc
	// comment. Narrower than full spec instantiation (only ONE level
	// deep, not transitive through the whole graph - a known, documented
	// limitation, see KnownGaps.md).
	private java.util.Set<String> allModuleRequests;
	public java.util.Set<String> getAllModuleRequests() {
		if(allModuleRequests==null) {
			java.util.LinkedHashSet<String> requests = new java.util.LinkedHashSet<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				if(child instanceof org.monflabs.galtajs.node.control.ASTImport imp) {
					// A source-phase import never loads its target, so it
					// is not an evaluation dependency (readyForSyncExecution()
					// would otherwise import - i.e. evaluate - it).
					if(imp.getFrom()!=null && !imp.isSourcePhase()) {
						requests.add(imp.getFrom());
					}
				} else if(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) {
					if(exp.getFrom()!=null) {
						requests.add(exp.getFrom());
					}
				}
			}
			allModuleRequests = requests;
		}
		return allModuleRequests;
	}

	// An import/export-from carrying a `with { type: '...' }` attribute
	// (see ASTImpExp.resolveModule()'s own doc comment) resolves to a
	// DIFFERENT (synthetic, non-JS) module record than a plain import of
	// the SAME specifier would, cached under a different key - calling the
	// plain, unattributed importModule() for it would parse the raw
	// source as JavaScript instead, which is simply wrong for it (test262
	// import-attributes/text-javascript.js: attempting to parse a `type:
	// 'text'` fixture's raw text content as JS). These are synthetic
	// module records with no [[RequestedModules]] of their own and no
	// async-dependency concerns, so getModuleEvaluationOrder() excludes
	// them entirely - the actual, correctly-attributed load still happens
	// later, exactly as before that dependency walk existed, when the
	// owning import/export-from statement's own hoistBindings()/evaluate()
	// reaches it.
	private static boolean isAttributed(org.monflabs.galtajs.node.control.ASTImpExp impExp) {
		java.util.Map<String,String> attributes = impExp.getAttributes();
		return attributes!=null && attributes.get("type")!=null;
	}

	// The specifiers of every `import defer * as ns from '...'` statement in
	// One entry per distinct (specifier, phase) pair, in SOURCE order -
	// spec's own "Static Semantics: ModuleRequests" only dedupes an
	// [[RequestedModules]] entry against an EARLIER one with the SAME
	// specifier AND the SAME phase; a `import defer * as ns from "./x.js"`
	// and a later plain `import "./x.js"` are TWO SEPARATE request records
	// (test262 module-imported-defer-and-eager.js: "the module is
	// evaluated in the order where it's imported as non-deferred" - the
	// deferred reference's own gather contributes nothing to
	// evaluationList when the target has no HasTLA anywhere, so the
	// EAGER reference's own, LATER position is what actually determines
	// where the module lands, not the deferred reference's earlier one -
	// an earlier version of this method deduped by specifier alone,
	// keeping only the FIRST occurrence's position, which is wrong for
	// exactly this case). Used by JSInterpretedUnit.linkModule() to build
	// ONE combined, interleaved evaluationList walk (see that method's own
	// doc comment for why two separate passes - eager then deferred - is
	// also wrong, independently of the ordering issue here: test262
	// flattening-order.js).
	public record ModuleRequestItem(String specifier, boolean deferred) {}
	private java.util.List<ModuleRequestItem> moduleEvaluationOrder;
	public java.util.List<ModuleRequestItem> getModuleEvaluationOrder() {
		if(moduleEvaluationOrder==null) {
			java.util.List<ModuleRequestItem> items = new java.util.ArrayList<>();
			java.util.Set<String> processedKeys = new java.util.HashSet<>();
			int n = getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = skipTransparent(getChild(i));
				String from = null;
				boolean deferred = false;
				if(child instanceof org.monflabs.galtajs.node.control.ASTImport imp) {
					from = imp.getFrom();
					if(from==null || isAttributed(imp)) {
						continue;
					}
					deferred = imp.isDeferred();
				} else if(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) {
					from = exp.getFrom();
					if(from==null || isAttributed(exp)) {
						continue;
					}
					// export-from can never carry `defer` - only an import
					// declaration's own grammar allows that phase.
				} else {
					continue;
				}
				if(!processedKeys.add((deferred?"D:":"E:")+from)) {
					continue;
				}
				items.add(new ModuleRequestItem(from, deferred));
			}
			moduleEvaluationOrder = items;
		}
		return moduleEvaluationOrder;
	}

	// We have to make a method public as this is called by JS environment
	// This is not to be used outside of this context
	public void __init(JSEnvironment env, boolean commonJS) {
		__init(env,commonJS,false);
	}

	// forceStrict: the calling context of a direct eval is genuinely strict,
	// so this eval'd program must parse (and be genuinely strict, per
	// InitContext.isGenuinelyStrict()) as strict too, regardless of its own
	// directive prologue. Never true for an ordinary (non-eval) script.
	public void __init(JSEnvironment env, boolean commonJS, boolean forceStrict) {
		__init(env,commonJS,forceStrict,true,true,true);
	}

	// callerHasNewTarget/callerIsMethod/callerIsDerivedCtor: caller-derived facts
	// (PerformEval's inFunc/inMethod/inDerivedCtor) - see
	// StandardLibrary.findNearestNonArrowFunction() (a RUNTIME JSFunctionContext
	// walk, not a static AST walk - needed so it still resolves correctly through
	// nested eval-within-eval, where each eval'd text is its own freshly-parsed,
	// AST-disconnected program) for how these are computed at the eval call site.
	// All-true is the permissive default (an ordinary, non-eval script/module
	// program never runs checkEvalCallerRestrictions() for real, since every
	// check below is gated on its own fact being false).
	public void __init(JSEnvironment env, boolean commonJS, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor) {
		__init(env,commonJS,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,false);
	}

	// callerInParameterExpressionScope: see StandardLibrary.isCallerInParameterExpressionScope() -
	// true only for a direct eval whose call site sits inside the nearest
	// enclosing function's own default parameter-value expression. False
	// (permissive - no check) for every ordinary script/module program.
	public void __init(JSEnvironment env, boolean commonJS, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope) {
		__init(env,commonJS,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,false);
	}

	// callerInFieldInitializer: see StandardLibrary.isCallerInFieldInitializer() -
	// true only for a direct eval whose call site sits inside a class
	// field's own Initializer expression. False (permissive - no check) for
	// every ordinary script/module program.
	//
	// No caller-private-names argument here: every caller of THIS overload is
	// either an ordinary (non-eval) compile or one of the convenience eval
	// overloads above that never learned any caller-enclosing private names -
	// both cases must still enforce AllPrivateNamesValid with an empty
	// starting set (see PrivateNameValidator's class doc), never skip it, so
	// this delegates to the real bottom overload with Collections.emptySet().
	public void __init(JSEnvironment env, boolean commonJS, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer) {
		__init(env,commonJS,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,java.util.Collections.emptySet());
	}

	// callerPrivateNames: the set of private names ("#name") visible from any
	// class lexically enclosing a direct eval's CALL SITE, collected by
	// walking the caller's own runtime PrivateEnvironment chain (see
	// JSRuntimeContext.collectEnclosingPrivateNames(), StandardLibrary's eval
	// case) - spec's EvalDeclarationInstantiation step 6-8 ("privateIdentifiers"
	// collected from privateEnv). null means "skip the check entirely" (used
	// only for a direct eval from a TRANSPILED caller - see
	// PrivateNameValidator's class doc); every other caller passes a real,
	// possibly-empty Set.
	public void __init(JSEnvironment env, boolean commonJS, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer, java.util.Set<String> callerPrivateNames) {
		__init(env,commonJS,false,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,callerPrivateNames);
	}

	// isModule: true only when this program is being compiled as a genuine
	// ES module body (JSEnvironment.SCRIPT_MODULE) - see isModule()'s own
	// doc comment. Every other overload above defaults this to false
	// (ordinary script/eval compile), preserving their existing behavior
	// exactly; only JSEnvironment.compileProgram() ever calls this overload
	// with a real value.
	public void __init(JSEnvironment env, boolean commonJS, boolean isModule, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer, java.util.Set<String> callerPrivateNames) {
		__init(env,commonJS,isModule,false,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,callerPrivateNames);
	}

	// isEval: true only when this program is being compiled as genuine
	// eval'd source (JSEnvironment.SCRIPT_EVAL) - see isEval()'s own doc
	// comment for what this gates. Every overload above defaults this to
	// false (ordinary script/module compile), preserving their existing
	// behavior exactly; only JSEnvironment.compileProgram() ever calls this
	// overload with a real value.
	public void __init(JSEnvironment env, boolean commonJS, boolean isModule, boolean isEval, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer, java.util.Set<String> callerPrivateNames) {
		this.commonJS = commonJS;
		this.isModule = isModule;
		this.isEval = isEval;
		this.callerForcedStrict = forceStrict;
		// A module is INTRINSICALLY strict (spec: every module body is
		// always strict, with no directive needed) - unlike forceStrict
		// (only ever true for a direct eval whose CALLER happens to be
		// strict), this must feed the INIT-TIME context's own strictness
		// too, not just the module's own top-level RUNTIME context
		// (InterpretedModuleRuntimeContext.isStrictMode() already
		// unconditionally returns true, but that's a SEPARATE, later,
		// runtime-only override - it doesn't retroactively fix a NESTED
		// function EXPRESSION's own COMPILED strictMode flag, computed
		// HERE, at init time, from its enclosing MainContext). Without
		// this, a function defined at module top level with no "use
		// strict" of its own (e.g. a callback: `function() { delete
		// ns.x; }`) compiled as non-strict, silently swallowing an
		// expected TypeError (test262 namespace/internals/delete-
		// exported-*.js, set.js: strict-mode-only rejections never fired).
		this.init(new MainContext(env,isForceStrictMode() || forceStrict || isModule));
		checkEvalCallerRestrictions(callerHasNewTarget,callerIsMethod,callerIsDerivedCtor);
		checkParameterExpressionArgumentsRestriction(callerInParameterExpressionScope);
		checkFieldInitializerArgumentsRestriction(callerInFieldInitializer);
		PrivateNameValidator.check(this, callerPrivateNames);
		EarlyErrorsValidator.check(this);
	}

	// A direct eval whose call site is inside a function's default parameter-
	// value expression evaluates in a scope that per spec never has its own
	// "arguments" binding (that lives in the function BODY's environment,
	// which parameter expressions don't share) - so declaring a var named
	// "arguments" there via eval is always a SyntaxError, UNCONDITIONALLY,
	// regardless of any other "arguments" binding existing elsewhere (a
	// same-scope preceding/following parameter literally named "arguments",
	// or a body-level var/let/function declaration) - confirmed empirically
	// via test262's language/eval-code/direct/*-declare-arguments*.js
	// generated test matrix (every variant throws the same way, independent
	// of what else is named "arguments"). Checked against this eval'd
	// program's own hoisted var-scoped names (getVariables(), already
	// populated by init() above) rather than the calling context.
	private void checkParameterExpressionArgumentsRestriction(boolean callerInParameterExpressionScope) {
		if(!callerInParameterExpressionScope) {
			return;
		}
		var variables = getVariables();
		if(variables==null) {
			return;
		}
		for(VariableDef v: variables) {
			VAR_TYPE t = v.getVarType();
			if((t==VAR_TYPE.VAR || t==VAR_TYPE.AUTO || t==VAR_TYPE.FUNCTION) && "arguments".equals(v.getName())) {
				throw new JSParseException(null,this,"'arguments' is not allowed to be declared in this eval");
			}
		}
	}

	// Additional Early Error Rules for Eval Inside Initializer (a class field
	// Initializer is never a function body, so it has no "arguments" binding
	// of its own at all - unlike the parameter-expression case above, this
	// is a REFERENCE restriction, not just a declaration one: "It is a
	// Syntax Error if ContainsArguments of StatementList is true" - ANY use
	// of the identifier "arguments" anywhere in the eval'd text is rejected,
	// not just a `var arguments`/`function arguments(){}` declaration.
	private void checkFieldInitializerArgumentsRestriction(boolean callerInFieldInitializer) {
		if(!callerInFieldInitializer) {
			return;
		}
		ASTNode offender = findArgumentsReference(this,false);
		if(offender!=null) {
			throw new JSParseException(null,offender,"'arguments' is not allowed in class field initializer");
		}
	}
	// Same opaque-at-nested-non-arrow-function-boundary walk as
	// findEvalCallerRestrictionViolation() below - a nested non-arrow
	// function (including a class method/constructor) establishes its own
	// genuine "arguments" binding, so a reference inside one is unrelated to
	// the field initializer's own restriction.
	private static ASTNode findArgumentsReference(ASTNode node, boolean insideNestedNonArrow) {
		if(node==null) {
			return null;
		}
		if(!insideNestedNonArrow && node instanceof ASTIdentifier id && "arguments".equals(id.getId())) {
			return node;
		}
		boolean childInsideNestedNonArrow = insideNestedNonArrow;
		if(node instanceof ASTFunction fn && !fn.isArrow()) {
			childInsideNestedNonArrow = true;
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			ASTNode result = findArgumentsReference(node.getChild(i),childInsideNestedNonArrow);
			if(result!=null) {
				return result;
			}
		}
		return null;
	}

	// PerformEval, non-eval-specific early errors:
	//   - "If inFunc is false and body Contains NewTarget, throw a SyntaxError."
	//   - "If inMethod is false and body Contains SuperProperty, throw a SyntaxError."
	//   - "If inDerivedCtor is false and body Contains SuperCall, throw a SyntaxError."
	// A single top-down scan checks all three at once. Nested non-arrow functions
	// (and, transitively, class bodies via their methods/constructor, which are
	// themselves non-arrow ASTFunctionMethod nodes) are opaque - they establish
	// their OWN new.target/super context, so a `new.target`/`super` used inside
	// one is unrelated to the CALLER's context and must never be flagged here;
	// arrow functions are transparent (same rule "Contains" static semantics uses,
	// mirrored by ASTFunction.containsHazard()/RuntimeUtil.getSuper()/superCtor()'s
	// runtime arrow-skipping walks). All three facts default to permissive
	// (true/true/true) for an ordinary, non-eval program, making this a no-op.
	private void checkEvalCallerRestrictions(boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor) {
		if(callerHasNewTarget && callerIsMethod && callerIsDerivedCtor) {
			return;
		}
		ASTNode offender = findEvalCallerRestrictionViolation(this,false,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor);
		if(offender!=null) {
			if(offender instanceof ASTNewMember) {
				throw new JSParseException(null,offender,"new.target expression is not allowed here");
			}
			if(offender instanceof ASTSuperMember) {
				throw new JSParseException(null,offender,"'super' keyword is only valid inside a method");
			}
			throw new JSParseException(null,offender,"'super' keyword is only valid inside a constructor");
		}
	}
	private static ASTNode findEvalCallerRestrictionViolation(ASTNode node, boolean insideNestedNonArrow, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor) {
		if(node==null) {
			return null;
		}
		if(!insideNestedNonArrow) {
			if(node instanceof ASTNewMember && !callerHasNewTarget) {
				return node;
			}
			if(node instanceof ASTSuperMember && !callerIsMethod) {
				return node;
			}
			if(node instanceof ASTSuperCtor && !callerIsDerivedCtor) {
				return node;
			}
		}
		boolean childInsideNestedNonArrow = insideNestedNonArrow;
		if(node instanceof ASTFunction fn && !fn.isArrow()) {
			childInsideNestedNonArrow = true;
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			ASTNode result = findEvalCallerRestrictionViolation(node.getChild(i),childInsideNestedNonArrow,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor);
			if(result!=null) {
				return result;
			}
		}
		return null;
	}
	
	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);
		
		// If this is a common JS module, the we need to create some variables
		if(commonJS) {
			addVarDeclaration(ModuleUtil.MODULE, VAR_TYPE.PREDECLARED, null);
			addVarDeclaration(ModuleUtil.EXPORTS, VAR_TYPE.PREDECLARED, null);
		}
	}
	
	@Override
	public String getSourceCode() {
		return text;
	}
	
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return areChildrenConstant(context);
	}

	public boolean isDebugInfo() {
		return debugInfo;
	}

	public boolean isCommonJS() {
		return commonJS;
	}
	
	// Extracted from evaluate() so JSInterpretedUnit can run this EARLIER,
	// before its own dependency-graph link phase (JSInterpretedUnit.
	// linkModule()) - a circular back-reference into THIS module's own
	// not-yet-declared bindings (e.g. a fixture's `export {A as B} from
	// './this-file.js'` reading THIS module's own `export const A`) needs
	// this module's own LET/CONST/VAR/FUNCTION cells to already exist -
	// exactly like a genuine spec Link() pass would guarantee, before
	// anything even STARTS resolving this module's own [[RequestedModules]] -
	// not just once THIS module's own evaluate() eventually reaches this
	// same code (too late for that self-/circular-reference). Idempotent
	// (tracked per context in hoistedContexts) - evaluate() below
	// still calls this unconditionally (needed for the non-module script/
	// eval case, which never goes through JSInterpretedUnit's own early
	// call at all), so this only actually hoists once regardless of which
	// caller reaches it first. hasUsingDeclarationsCache hands off this
	// method's own local finding to evaluate()'s later disposal-wrapping
	// decision, since the two are no longer the same call.
	// The AST is shared by every execution of the script (and possibly by several
	// threads), so "already hoisted" is tracked per execution context, not as a
	// flag on the node: a second run of the same program must hoist again.
	private final Map<JSInterpretedRuntimeContext,Boolean> hoistedContexts = Collections.synchronizedMap(new WeakHashMap<>());
	private volatile boolean hasUsingDeclarationsCache;
	public void hoistDeclarations(JSInterpretedRuntimeContext context) {
		if(hoistedContexts.putIfAbsent(context, Boolean.TRUE)!=null) {
			return;
		}
		try {
			// Set inside the TDZ pre-population loop below (mirrors
			// ASTBlock.evaluate()'s own local of the same name/purpose) -
			// gates whether this program's own body evaluation needs to be
			// wrapped with DisposeResourcesUtil.dispose() at the end (see
			// the evaluateNodes() call site further down).
			boolean hasUsingDeclarations = false;
			if(isCommonJS()) {
				JSObject exports = JSObject.create(context.getEnvironment());
				JSObject module = JSObject.of(context.getEnvironment(),ModuleUtil.EXPORTS,exports);
				context.createVariable(ModuleUtil.MODULE, module, VAR_TYPE.SYSTEM);
				context.createVariable(ModuleUtil.EXPORTS, exports, VAR_TYPE.SYSTEM);
			}
			// Pre-declare the 'var'and functions
			// 1- we should hoist the functions
			// 2- we should hoist the vars
			var variables = getVariables();
			if(variables!=null) {
				JSRuntimeContext varDeclContext = context.getVarDeclContext();
				if(varDeclContext instanceof InterpretedGlobalRuntimeContext gctx) {
					validateGlobalDeclarations(context, gctx.getGlobalThis(), variables);
				} else if(varDeclContext instanceof org.monflabs.galtajs.rt.JSEvalRuntimeContext evalCtx) {
					// A direct eval's own var/function declarations only reach
					// the real global object for names with no pre-existing
					// local/parent-shadowing binding (see
					// JSEvalRuntimeContext.resolveGlobalDeclarationTarget()) -
					// in practice, for the test262 scenarios this covers, this
					// is uniform across the whole eval'd program (either all
					// hoisted names reach the same global, or none do), so
					// resolving via the FIRST hoisted name is representative;
					// if it resolves to a real global context, validate the
					// whole set against it exactly like a plain script would.
					for(VariableDef v: variables) {
						if(v.getVarType().isHoisted()) {
							org.monflabs.galtajs.rt.JSGlobalContext target = evalCtx.resolveGlobalDeclarationTarget(v.getName());
							if(target!=null) {
								validateGlobalDeclarations(context, target.getGlobalThis(), variables);
							}
							break;
						}
					}
				}
				for(VariableDef v: variables) {
					VAR_TYPE t = v.getVarType();
					// In case of a function, don't override with undefined
					if(t.isHoisted()) {
						// CreateGlobalFunctionBinding (unlike CreateGlobalVarBinding) is
						// spec'd to run for EVERY hoisted function name unconditionally,
						// even when a same-named property already exists - it's the one
						// that redefines a pre-existing CONFIGURABLE property back to the
						// standard writable/enumerable/[[Configurable]]=D descriptor (see
						// InterpretedGlobalRuntimeContext.createVariable()'s VAR_TYPE.FUNCTION
						// branch, which already implements that nuance correctly but was
						// never being reached for an already-existing entry). Plain var
						// hoisting (CreateGlobalVarBinding) only ever defines when absent,
						// so it keeps the skip-if-exists guard (test262
						// language/eval-code/{direct,indirect}/var-env-func-init-global-
						// update-configurable.js) - and so does an Annex-B block-hoisted
						// function (v.isAnnexBBlockHoisted()): its own synchronization step
						// must NOT touch an existing property's descriptor at all (only
						// ASTFunctionDecl.evaluate() conditionally updates its VALUE at
						// block-exit time) - unconditionally redefining here regressed
						// test262 annexB/language/{eval-code,global-code}/*-existing-*-
						// global-init.js (confirmed via a full test262 sweep after first
						// trying the unconditional version).
						// EvalDeclarationInstantiation (spec 18.2.1.3) step 5: a
						// SLOPPY direct eval's var/function name colliding with an
						// enclosing LET/CONST binding must throw SyntaxError. This
						// must walk the FULL ancestor chain (getVariableEntry(),
						// not getLocalVariableEntry() - the collision can be in
						// any enclosing scope, not just varDeclContext's own) and
						// run independently of the "should I create a local
						// binding" decision below (a nested function's own local
						// var-creation must not be skipped just because an
						// enclosing scope happens to have a DIFFERENT binding of
						// the same name - see the getLocalVariableEntry() call
						// below for that case) - distinguish "found a var" (fine,
						// no collision) from "found a let/const" (must throw)
						// instead of treating both the same. isGenuinelyStrictMode()
						// here is THIS eval program's OWN genuine strictness (its
						// own directive, OR'd with the calling context's, via
						// callerForcedStrict - see that field/method's own doc
						// comment) - a STRICT eval never hoists outward at all (its
						// declarations land in its own fresh environment instead),
						// so no collision is even possible there; only check when
						// genuinely sloppy. Must use isGenuinelyStrictMode(), not the
						// runtime-field-based isStrictMode() - the latter doesn't
						// correctly reflect a top-level-SCRIPT caller's strictness,
						// only a function caller's.
						if(varDeclContext instanceof org.monflabs.galtajs.rt.JSEvalRuntimeContext && !isGenuinelyStrictMode()) {
							VarAccessor existing = varDeclContext.getVariableEntry(v.getName());
							// Annex B.3.5 carve-out (see VarAccessor.isCatchParameter()'s
							// own doc): a collision with a Catch clause's OWN parameter
							// must NOT throw, unlike an ordinary LET/CONST collision -
							// test262 annexB/language/eval-code/direct/
							// var-env-lower-lex-catch-non-strict.js.
							if(existing!=null && (existing.getType()==VAR_TYPE.LET || existing.getType()==VAR_TYPE.CONST)
									&& !existing.isCatchParameter()) {
								throw RuntimeUtil.syntaxError("Identifier '{0}' has already been declared", v.getName());
							}
							// Sub-case B of the same family, for a call site inside the
							// nearest enclosing function's own default parameter-value
							// expression specifically (StandardLibrary.
							// isCallerInAnyParameterExpressionScope() - broader than the
							// isCallerInParameterExpressionScope() used above for the
							// "arguments" restriction: no arrow exemption, since an
							// arrow's own OTHER parameter can genuinely collide too): the
							// collision is with a PARAMETER, not a let/const (parameters.
							// declareVariables(this, VAR_TYPE.PREDECLARED) in
							// ASTFunction.init() tags every parameter this way). For an
							// INTERPRETED caller this branch is never actually reached in
							// that scenario - the analogous runtime-context-chain lookup
							// this same eval program's varDeclContext.getVariableEntry()
							// performs genuinely returns null while a parameter's own
							// default value is still being bound (see
							// InterpretedFunctionRuntimeContext.hasParameterExpressions()'s
							// own doc), landing in the OTHER branch above instead, where
							// StandardLibrary.BaseEvalContext.createVariable() detects the
							// exact same collision via a live InterpretedFunctionRuntimeContext
							// object - there's no such separate parameter-frame object for
							// a TRANSPILED caller (TranspiledFunctionRuntimeContext is one
							// merged context for both params and body) so its bundled
							// VarAccessor[] (what getVariableEntry() resolves through here)
							// already reports the parameter as found, taking THIS branch
							// instead - hence this check is needed here, not there, for
							// that caller. isCallerInAnyParameterExpressionScope() is read
							// directly off evalCtx (set once by StandardLibrary right
							// before executeForEval(), from the same static-AST fact
							// either caller mode computes) rather than threaded through
							// __init like forceStrict/callerInFieldInitializer - it's only
							// ever needed here, at declaration time, never at parse time.
							else if(existing!=null && existing.getType()==VAR_TYPE.PREDECLARED
									&& ((org.monflabs.galtajs.rt.JSEvalRuntimeContext)varDeclContext).isCallerInAnyParameterExpressionScope()) {
								throw RuntimeUtil.syntaxError("Identifier '{0}' has already been declared", v.getName());
							}
						}
						// "Should a local binding be (re)created" is answered
						// against varDeclContext's OWN scope only
						// (getLocalVariableEntry(), never the full ancestor
						// walk) - an unrelated same-named var in an ENCLOSING
						// scope (e.g. a direct eval nested inside a function
						// that also has an outer same-named var) must not
						// suppress creating this scope's own genuinely separate
						// binding (test262 language/expressions/assignment/
						// S11.13.1_A6_T1.js: `eval("var x;")` inside a function
						// whose own enclosing scope also declares `var x` must
						// still create a NEW local `x`, shadowing the outer one,
						// not silently no-op against it). For a direct eval
						// specifically, this means hoisting can now call
						// createVariable() TWICE for the same name (once here,
						// once more when the var declaration STATEMENT itself
						// executes and assigns the real value) where it
						// previously often called it zero times for this case -
						// StandardLibrary.BaseEvalContext.createVariable()'s own
						// parameter-frame collision check has a matching fix
						// (excluding VAR_TYPE.VAR from its "already declared"
						// throw) to treat that second call as finding its own
						// earlier-hoisted entry, not a foreign collision.
						if((t==VAR_TYPE.FUNCTION && !v.isAnnexBBlockHoisted()) || varDeclContext.getLocalVariableEntry(v.getName())==null) {
							// B.3.3.3's hoist-time synchronization step for an Annex-B
							// block-hoisted function is CreateGlobalVarBinding, not
							// CreateGlobalFunctionBinding: pass VAR_TYPE.VAR instead of
							// FUNCTION so every createVariable() in the chain
							// (StandardLibrary.BaseEvalContext, Interpreted/
							// TranspiledGlobalRuntimeContext, VariableMap) takes its
							// no-op-if-already-exists path instead of FUNCTION's
							// unconditional redefine, which was clobbering an existing
							// global property's value to undefined (and, if configurable,
							// force-resetting enumerable to true) - test262 annexB/
							// language/eval-code/{direct,indirect}/global-*-eval-global-
							// existing-{global,non-enumerable-global}-init.js. The
							// function's real value is still assigned correctly later,
							// when the FunctionDeclaration statement itself executes
							// (ASTFunctionDecl.assignFunctionValue()). Only swap the type
							// here - the surrounding "should createVariable() even be
							// called" gate above must stay EXACTLY as it was: for a
							// function-SCOPE (non-global) Annex-B hoist colliding with an
							// existing same-named PARAMETER, getLocalVariableEntry() above
							// already correctly finds it (including, for a TRANSPILED
							// caller, via TranspiledEvalContext's own bundle-aware
							// override) and skips calling createVariable() entirely - a
							// first attempt at this fix removed that gate for the whole
							// annexB-hoisted branch, which routed straight into
							// BaseEvalContext.createVariable()'s OWN unconditional
							// var.setValue(value) on its "found locally" branch (unlike
							// its value!=NOT_AVAILABLE-gated "found via parent" branch),
							// clobbering the parameter's value and regressing test262
							// annexB/language/eval-code/direct/func-*-eval-func-no-skip-
							// param.js under transpiled mode specifically (only
							// observable once a real transpiled-mode test262 sweep
							// existed at all - see the 2026-08-23 harness fix).
							VAR_TYPE createType = (t==VAR_TYPE.FUNCTION && v.isAnnexBBlockHoisted()) ? VAR_TYPE.VAR : t;
							// isModule(): THIS program's own var/function hoisting
							// (not a nested $262.evalScript SCRIPT sharing the same
							// varDeclContext - that one's isModule() is false, and
							// must still hoist to globalThis normally) - see
							// createModuleLocalVariable()'s own doc comment.
							if(isModule() && varDeclContext instanceof InterpretedGlobalRuntimeContext igc) {
								igc.createModuleLocalVariable(v.getName(), RuntimeUtil.NOT_AVAILABLE, createType);
							} else {
								varDeclContext.createVariable(v.getName(), RuntimeUtil.NOT_AVAILABLE, createType);
							}
						}
					} else if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
						// Program-level let/const/using always bind in the
						// current lexical context (never routed through a
						// `with`'s var-decl target) - pre-populate the
						// Temporal Dead Zone placeholder here, same as
						// ASTBlock does (ASTBlock's own equivalent loop
						// already includes VAR_TYPE.USING; this one didn't,
						// an incomplete port - test262 language/statements/
						// using/syntax/using.js: `using z = null;` directly
						// at a module's own top level left `z` completely
						// unresolvable, "Unknown identifier z", since it was
						// never even created here).
						// Checked against the LOCAL map only (not
						// getVariableEntry(), which also resolves globals -
						// a script-level let/const with the same name as an
						// existing global property must still get its own
						// fresh lexical binding, shadowing the global).
						if(context.getVariableMap(true).getEntry(v.getName())==null) {
							context.createVariable(v.getName(), RuntimeUtil.TDZ, t);
						}
						if(t==VAR_TYPE.USING) {
							hasUsingDeclarations = true;
						}
					}
				}
			}

			// Module-level function declarations get their REAL VALUE
			// (not just a placeholder cell) constructed NOW - see
			// ASTFunctionDecl.hoistValue()'s own doc comment for why this
			// is needed (spec InstantiateFunctionObject binds every
			// hoistable declaration's real value during instantiation,
			// before ANY dependency is evaluated - GaltaJS's own default
			// hoisting only creates a placeholder here, with the real
			// value assigned later via ASTStatementList.hoistNodes()'s
			// physical-reordering trick, still driven by the ordinary
			// evaluateNodes() loop below - too late for the eager
			// dependency-evaluation hoisting just below this block to
			// safely read back). Gated to isModule(): this is specifically
			// about matching module instantiation semantics; a script's
			// existing (unhoisted-early) function-value timing is
			// unchanged. Covers a bare `function fn(){}` (a direct
			// ASTFunctionDecl child), `export function fn(){}` (wrapped in
			// ASTExport.getNamedExport()), and `export default function
			// fn(){}` (wrapped in ASTExport.getDefaultExport()) - every
			// OTHER export form (class, default non-function-expression,
			// etc.) is untouched, matching isHoistableNamedExport()/
			// isHoistableDefaultExport()'s own existing narrower scope.
			if(isModule()) {
				int n = getChildCount();
				for(int i=0; i<n; i++) {
					ASTNode child = skipTransparent(getChild(i));
					if(child instanceof org.monflabs.galtajs.node.control.ASTFunctionDecl fd) {
						fd.hoistValue(context);
					} else if(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) {
						if(exp.isHoistableNamedExport()) {
							((org.monflabs.galtajs.node.control.ASTFunctionDecl)exp.getNamedExport()).hoistValue(context);
						} else if(exp.isHoistableDefaultExport()) {
							((org.monflabs.galtajs.node.control.ASTFunctionDecl)exp.getDefaultExport()).hoistValue(context);
						}
					}
				}
			}
			hasUsingDeclarationsCache = hasUsingDeclarations;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			hoistDeclarations(context);
			boolean hasUsingDeclarations = hasUsingDeclarationsCache;

			// Named import bindings (`import {x}`/`import {x as y}`) are
			// resolved here - AFTER the var/let/const/function hoist loop
			// above (so a SELF-import's own target variable already has
			// its real cell, even if not yet initialized - see
			// JSInterpretedUnit.getExportAccessor()'s own doc), but BEFORE
			// evaluateNodes() runs any of this module's own statements in
			// source order (needed since an import can appear ANYWHERE in
			// a module body, not just at the top, yet its binding must
			// already be live for code that textually precedes it - see
			// ASTImport.hoistBindings()'s own doc comment for why
			// default/namespace imports are deliberately excluded from
			// this early pass).
			//
			// Also hoists `export ... from '...'` dependency EVALUATION
			// (not any binding - export-from introduces no local variable)
			// in the SAME source-order pass, alongside plain imports - see
			// ASTExport.hoistDependencyEvaluation()'s own doc comment for
			// why: per spec InnerModuleEvaluation, a module's own
			// [[RequestedModules]] are ALL evaluated before its own
			// top-level code runs, regardless of import/export-from FORM
			// or source position.
			//
			// NOT gated on isModule(): import/export syntax is accepted
			// (and must still work) in several script-compile paths that
			// never set that flag - GaltaJS's own internal ImportTest/
			// ImportExportTest/ImportFilesTest suites all compile
			// import-containing code without it (confirmed via a
			// regression: gating this on isModule() left their imports
			// permanently stuck in the hoist pass's own TDZ placeholder,
			// "Cannot access 'a' before initialization", since the ONLY
			// code that used to bind them - the `items` handling, now
			// moved out of evaluate() and into hoistBindings() below -
			// never ran at all). A plain script with zero ASTImport
			// children (the overwhelmingly common case) pays only this
			// cheap child-scan, same as the equivalent already-unconditional
			// HoistableNode scan in ASTStatementList.hoistNodes().
			{
				int n = getChildCount();
				for(int i=0; i<n; i++) {
					ASTNode child = skipTransparent(getChild(i));
					if(child instanceof org.monflabs.galtajs.node.control.ASTImport imp) {
						imp.hoistBindings(context);
					} else if(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) {
						exp.hoistDependencyEvaluation(context);
					}
				}
			}

			result.setUndefined();
			Signal s;
			// Every top-level using/await-using resource must be disposed
			// (RS: DisposeResources) once this program's own body finishes
			// evaluating - on EVERY exit path (normal completion, an
			// exception, or a RETURN/BREAK/CONTINUE signal below), mirroring
			// ASTBlock.evaluate()'s identical try/dispose wrapping around
			// evaluateBlock() - only paid for when this program actually
			// declares a using/await-using resource at its own top level
			// (test262 language/statements/using/initializer-disposed-at-
			// end-of-module.js and siblings).
			if(!hasUsingDeclarations) {
				s = evaluateNodes(context,result);
			} else {
				Throwable pending = null;
				try {
					s = evaluateNodes(context,result);
				} catch(Throwable t) {
					s = null;
					pending = t;
				}
				Throwable toThrow = DisposeResourcesUtil.dispose(context, pending);
				if(toThrow!=null) {
					if(toThrow instanceof RuntimeException re) {
						throw re;
					}
					if(toThrow instanceof Error e) {
						throw e;
					}
					throw new RuntimeException(toThrow);
				}
			}
			if(s!=null && s!=Signal.NONE) {
				switch(s.getType()) {
					case RETURN -> {
						// in result...
					}
					case BREAK -> {
						result.setUndefined();
						throw RuntimeUtil.syntaxError("Unsyntactic break");
					}
					case CONTINUE -> {
						result.setUndefined();
						throw RuntimeUtil.syntaxError("Unsyntactic continue");
					}
				}
			}
			// eval("{}")'s completion value must be undefined per spec (an
			// empty Block), even though GaltaJS's deliberate "bare object
			// literal at top level" extension (see MainSourceElements() in
			// JSParser.jj) parses it as an ObjectLiteral EXPRESSION so a
			// PLAIN SCRIPT typing "{}" gets the object back - that
			// extension's own behavior for a non-eval caller is
			// unconditionally preserved (ObjectAsExpressionTest.
			// testObjectLiteratl), and so is a genuinely user-written empty
			// object literal even when eval'd (`eval("({})")`) -
			// isBareTopLevelEmptyPromotion() is set ONLY by that ONE
			// specific parser grammar alternative, never for an ordinary
			// ObjectLiteral() parse, so this can't misfire on either.
			if(isEval() && getChildCount()==1) {
				ASTNode sole = skipTransparent(getChild(0));
				if(sole instanceof org.monflabs.galtajs.node.literal.ASTObjectLiteral lit
						&& lit.isBareTopLevelEmptyPromotion()) {
					result.setUndefined();
				}
			}
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	// GlobalDeclarationInstantiation/EvalDeclarationInstantiation (script/
	// eval top-level hoisting into the global object) is all-or-nothing: if
	// ANY declaration collides with an existing incompatible binding, the
	// WHOLE operation must throw BEFORE any of the OTHER declarations are
	// created - not just the offending one. The existing per-name creation
	// loop below (InterpretedGlobalRuntimeContext.createVariable() for var/
	// function, the TDZ-placeholder branch for let/const) validates and
	// creates in the same step, one name at a time, so a "definable"
	// declaration earlier in source order would already be created before a
	// later "non-definable" one is rejected. This is a pure, read-only
	// validation pre-pass run over the SAME already-fully-collected
	// `variables` list before any binding is touched, mirroring the CURRENT
	// spec text's GlobalDeclarationInstantiation steps, in order (fetched
	// from https://tc39.es/ecma262/multipage/global-object.html - as of this
	// writing, GlobalDeclarationInstantiation has NO separate
	// "HasVarDeclaration" check at all; an older draft did, and some
	// long-standing test262 tests' own info: excerpts (e.g. script-decl-lex-
	// var.js, copyright 2016) still quote that older wording, but the
	// operative, current algorithm folds it entirely into
	// HasRestrictedGlobalProperty - see the NOTE below):
	//   3. lexicalNames (let/const/class): SyntaxError if
	//      HasLexicalDeclaration(name), else SyntaxError if
	//      HasRestrictedGlobalProperty(name) [NOTE in spec: "Global var and
	//      function bindings (except those that are introduced by non-strict
	//      direct eval) are non-configurable and are therefore restricted
	//      global properties" - i.e. HasRestrictedGlobalProperty is now the
	//      ONLY check needed to also reject a collision with an existing var/
	//      function declaration, precisely BECAUSE such a declaration is
	//      always non-configurable, except when introduced by a non-strict
	//      direct eval, which deliberately must NOT collide (test262
	//      language/global-code/script-decl-lex-var-declared-via-eval.js) -
	//      confirmed against InterpretedGlobalRuntimeContext.createVariable(),
	//      which already gives an eval-introduced var/function a configurable
	//      descriptor (isEvalExecution()) and a genuine script-level one a
	//      non-configurable one, exactly matching this invariant].
	//   4. variableNames (includes function names - both are
	//      VarDeclaredNames): SyntaxError if HasLexicalDeclaration(name).
	//   Later (CanDeclareGlobalFunction/CanDeclareGlobalVar): TypeError if a
	//      function/var can't be defined against an existing non-configurable
	//      property (functions checked first - a function name always wins
	//      over a same-named plain var, per spec).
	// HasLexicalDeclaration is answered by the LOCAL variable map
	// (context.getVariableMap(true)) - the same live map a script sharing
	// this realm via $262.evalScript keeps reusing (see
	// JSInterpretedUnit.executeWithContext(), which re-runs a freshly-parsed
	// program against the CALLER's own already-live context rather than a
	// fresh one), so a let/const/class declared by an earlier script is
	// still visible there. HasRestrictedGlobalProperty is answered directly
	// off the existing property's [[Configurable]] attribute - deliberately
	// NOT globalThis.hasOwnProperty() (too broad: see the FILTER comment
	// history in Test262BaseTest.java for the regression a first, reverted
	// attempt using hasOwnProperty() as a HasVarDeclaration proxy caused -
	// it misfired on ordinary host/Java-interop globals and builtins, which
	// are real but CONFIGURABLE properties, never var/function-declared).
	private static void validateGlobalDeclarations(JSRuntimeContext context, GlobalThis globalThis, Iterable<VariableDef> variables) {
		java.util.LinkedHashSet<String> functionNames = new java.util.LinkedHashSet<>();
		java.util.LinkedHashSet<String> varNames = new java.util.LinkedHashSet<>();
		java.util.LinkedHashSet<String> lexNames = new java.util.LinkedHashSet<>();
		for(VariableDef v: variables) {
			VAR_TYPE t = v.getVarType();
			if(t==VAR_TYPE.FUNCTION) {
				functionNames.add(v.getName());
			} else if(t==VAR_TYPE.VAR || t==VAR_TYPE.AUTO) {
				varNames.add(v.getName());
			} else if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
				lexNames.add(v.getName());
			}
		}
		validateGlobalDeclarationSets(context, globalThis, functionNames, varNames, lexNames);
	}
	// TRANSPILED-caller entry point: same validation, but the transpiled
	// runtime never has AST-coupled VariableDef instances available (they're
	// only ever constructed via ASTVarContainer.VariableDefContainer, an AST
	// object) - it only has the parallel varNames/varTypes String[] pair
	// ASTVarContainer.types()/names() already emit into generated code (see
	// the "global" codegen branch's initGlobalVariables(...) call). Builds
	// the same three name sets from those arrays instead, then shares the
	// exact same validation core - see TranspiledGlobalRuntimeContext.
	// initGlobalVariables() for the call site and, critically, WHY it must
	// run before that method's own variable-map population/GlobalThis
	// binding (self-collision false positives otherwise).
	public static void validateGlobalDeclarations(JSRuntimeContext context, GlobalThis globalThis, String[] varNames, String[] varTypes) {
		if(varNames==null) {
			return;
		}
		java.util.LinkedHashSet<String> functionNames = new java.util.LinkedHashSet<>();
		java.util.LinkedHashSet<String> vNames = new java.util.LinkedHashSet<>();
		java.util.LinkedHashSet<String> lexNames = new java.util.LinkedHashSet<>();
		int l = varNames.length;
		for(int i=0; i<l; i++) {
			VAR_TYPE t = (varTypes!=null && varTypes[i]!=null) ? VAR_TYPE.valueOf(varTypes[i]) : VAR_TYPE.AUTO;
			if(t==VAR_TYPE.FUNCTION) {
				functionNames.add(varNames[i]);
			} else if(t==VAR_TYPE.VAR || t==VAR_TYPE.AUTO) {
				vNames.add(varNames[i]);
			} else if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
				lexNames.add(varNames[i]);
			}
		}
		validateGlobalDeclarationSets(context, globalThis, functionNames, vNames, lexNames);
	}
	private static void validateGlobalDeclarationSets(JSRuntimeContext context, GlobalThis globalThis, java.util.Set<String> functionNames, java.util.Set<String> varNames, java.util.Set<String> lexNames) {
		for(String name: lexNames) {
			if(hasLexicalDeclaration(context, name)) {
				throw RuntimeUtil.syntaxError("Identifier ''{0}'' has already been declared", name);
			}
			PropertyDescriptor existing = globalThis.getOwnPropertyDescriptor(name);
			if(existing!=null && !existing.isConfigurable()) {
				throw RuntimeUtil.syntaxError("Identifier ''{0}'' has already been declared", name);
			}
		}
		for(String name: functionNames) {
			if(hasLexicalDeclaration(context, name)) {
				throw RuntimeUtil.syntaxError("Identifier ''{0}'' has already been declared", name);
			}
		}
		for(String name: varNames) {
			if(functionNames.contains(name)) {
				continue;
			}
			if(hasLexicalDeclaration(context, name)) {
				throw RuntimeUtil.syntaxError("Identifier ''{0}'' has already been declared", name);
			}
		}
		for(String name: functionNames) {
			if(!canDeclareGlobalFunction(globalThis, name)) {
				throw RuntimeUtil.typeError("Cannot declare global function ''{0}''", name);
			}
		}
		for(String name: varNames) {
			if(functionNames.contains(name)) {
				continue;
			}
			if(!canDeclareGlobalVar(globalThis, name)) {
				throw RuntimeUtil.typeError("Cannot declare global variable ''{0}''", name);
			}
		}
	}
	// HasLexicalDeclaration(name): true when the LOCAL variable map (the
	// global script's own declarative bindings, never resolved through a
	// parent/global-property fallback - see the let/const creation branch
	// above) already holds a genuinely lexical entry for this name. USING is
	// included alongside LET/CONST since `using`/`await using` are lexically
	// scoped, TDZ'd bindings for this purpose too; PREDECLARED/SYSTEM entries
	// (e.g. a CommonJS module's own "module"/"exports") are deliberately
	// excluded - they are not LexicallyDeclaredNames.
	private static boolean hasLexicalDeclaration(JSRuntimeContext context, String name) {
		VarAccessor existing = context.getVariableMap(true).getEntry(name);
		if(existing==null) {
			return false;
		}
		VAR_TYPE t = existing.getType();
		return t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING;
	}
	private static boolean canDeclareGlobalFunction(JSObject globalThis, String name) {
		PropertyDescriptor existing = globalThis.getOwnPropertyDescriptor(name);
		if(existing==null) {
			return globalThis.isExtensible();
		}
		if(existing.isConfigurable()) {
			return true;
		}
		return !existing.isAccessor() && existing.isWritable() && existing.isEnumerable();
	}
	private static boolean canDeclareGlobalVar(JSObject globalThis, String name) {
		if(globalThis.hasOwnProperty(name)) {
			return true;
		}
		return globalThis.isExtensible();
	}
	protected Signal evaluateNodes(JSInterpretedRuntimeContext context, JSResult result) {
		ASTNode[] statements = getStatements();
		int count = statements.length;
		for(int i=0; i<count; i++) {
			Signal s = statements[i].evaluate(context,result);
			if(s!=Signal.NONE) {
				return s;
			}
		}
		return Signal.NONE;
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
	
    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	JSTranspilerMap map = jsContext.getTranspilerMap();
		map.pushBlock(1);

		ASTNode[] statements = getStatements();
    	if(statements.length==0) {
    		return "null";
    	}
    	if(statements.length==1) {
    		return JSTranspiler.asValue(jsContext, statements[0]);
    	}
		map.popBlock(1);

    	throw new JSTranspilerException(null, this, "Cannot compile as an expression");
    }
	
	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	JSTranspilerMap map = jsContext.getTranspilerMap();
		map.pushBlock(b.getCurrentLine());
		
    	// Mark named-import bindings as LIVE (see VariableDef.
    	// isLiveImportBinding()'s/ASTImport.markLiveImportBindings()'s own
    	// doc comments) as the very FIRST thing, before absolutely anything
    	// else below is compiled - pure compile-time metadata, no Java code
    	// emitted. Must run before EVEN transpilerDeclareStatement() and the
    	// hoistable-function pass below, since a hoisted function declared
    	// textually BEFORE its module's own import statement can still
    	// reference the imported name in its own body, which gets compiled
    	// as part of THIS same top-level walk - if the marker weren't set
    	// until the import's own (later) position, that earlier-compiled
    	// reference would silently fall back to non-live codegen.
    	{
    		int n = getChildCount();
    		for(int i=0; i<n; i++) {
    			ASTNode child = skipTransparent(getChild(i));
    			if(child instanceof org.monflabs.galtajs.node.control.ASTImport imp) {
    				imp.markLiveImportBindings();
    			}
    		}
    	}

    	// Static (AST-shape) module-request list, baked at transpile time
    	// from getAllModuleRequests() (a pure AST-structure computation, see
    	// its own doc comment - safe to call here since it only reads this
    	// program's own already-parsed children, no runtime state needed) -
    	// mirrors JSInterpretedUnit.readyForSyncExecution()'s identical use
    	// of the SAME method on a retained AST; JSTranspiledUnit keeps no AST
    	// at runtime, so this needs to be precomputed into a literal argument
    	// list instead. See JSTranspiledUnit.readyForSyncExecution()'s own
    	// doc comment for what this feeds.
    	if(isModule() && !getAllModuleRequests().isEmpty()) {
    		StringBuilder reqArgs = new StringBuilder();
    		boolean first = true;
    		for(String req: getAllModuleRequests()) {
    			if(!first) {
    				reqArgs.append(',');
    			}
    			first = false;
    			reqArgs.append(org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(req));
    		}
    		b.println("registerModuleRequests({0});", reqArgs.toString());
    	}

    	// Pre-declare all vars and functions
    	transpilerDeclareStatement(jsContext,b,0);

    	// Live-export registration, hoisted to happen BEFORE anything else
    	// (including the import hoisting just below, which may need to
    	// resolve a self-import of one of these names) - see
    	// AbstractModule.registerLiveExport()'s own doc comment and
    	// docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's P2.
    	// getLiveExportNames() is the SAME static (AST-level, precomputed)
    	// map JSInterpretedUnit.resolveExport() already relies on for the
    	// identical reason: a module's own var/let/const/function/class
    	// declarations are ALL hoisted (their slot/cell exists, possibly
    	// still TDZ, from the moment this method starts) regardless of
    	// which line their OWN `export` statement is on - registering the
    	// live accessor only once that statement's own codegen runs (as
    	// ASTExport.transpileJavaStatement() alone previously did) left a
    	// self-import reaching this name BEFORE its `export` statement's own
    	// source position (a common test262 idiom: the self-import is often
    	// the very FIRST statement) unable to resolve it at all (test262
    	// language/module-code/instn-named-bndng-*.js: "does not export an
    	// entry"). ASTExport's own registerLiveExport() call, at its normal
    	// source position, still runs too - a harmless re-registration of
    	// the same accessor, kept for defense in depth.
    	for(java.util.Map.Entry<String,String> e: getLiveExportNames().entrySet()) {
    		String exportedName = e.getKey();
    		String localName = e.getValue();
    		IndirectExportEntry sourceImport = getImportedLocalNames().get(localName);
    		if(sourceImport!=null && SOURCE_IMPORT_NAME.equals(sourceImport.importName())) {
    			// `import source x from '...'; export {x};` - an indirect
    			// entry (see SOURCE_IMPORT_NAME), so that two modules
    			// re-exporting the SAME source-phase target compare as
    			// non-ambiguous in JSTranspiledUnit.resolveExport() (a local
    			// live export would carry each module's own identity).
    			// ASTExport.transpileJavaStatement() skips it the same way.
    			b.println("registerIndirectExport({0},{1},{2});",
    					org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(exportedName),
    					org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(sourceImport.moduleRequest()),
    					org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(SOURCE_IMPORT_NAME));
    			continue;
    		}
    		VariableDef v = findVariable(localName);
    		if(v==null) {
    			continue;
    		}
    		b.println("registerLiveExport({0},JSVarRef.of({0},{1},{2},VAR_TYPE.{3}));",
    				org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(exportedName), v.getJavaVariableArray(), v.getJavaVariableIndex(), v.getVarType().name());
    	}
    	if(getStaticExportedNames().contains("default")) {
    		// See AbstractModule.registerStaticExportName()'s own doc
    		// comment - getLiveExportNames() only covers a HOISTABLE default
    		// (a real local variable slot); a non-hoistable one (`export
    		// default <expr>;`/`export default class{}`) still exports the
    		// NAME "default" syntactically, needed for the namespace
    		// object's own TDZ-safe [[HasProperty]] (test262 namespace/
    		// internals/has-property-str-found-uninit.js's own `'default' in
    		// ns` check, reached before this module's own `export default`
    		// statement has run).
    		b.println("registerStaticExportName({0});", org.monflabs.galtajs.node.literal.ASTLiteral.encodeString("default"));
    	}

    	// Self-referencing named re-export (`export {x as y} from
    	// './this-same-file.js'` - test262's own module-code_FIXTURE.js
    	// idiom) - NOT covered by getLiveExportNames() above (which
    	// deliberately skips every `from` re-export, local or not - see its
    	// own doc comment), but a SELF-referencing one genuinely needs the
    	// same hoist-time registration: `y` is just an alias for `x`'s own
    	// export, resolvable via addNamedReExport()'s existing live
    	// delegation (AbstractModule.getExportAccessor()) the moment this
    	// starts, with no need to wait for any `importModule()` call at all
    	// since the source IS `this`. Mirrors getSelfReexportLocalName()'s
    	// own AST walk (used by interpreted mode for the identical concern)
    	// but emits `this` directly as the source, and ALL items at once
    	// (getSelfReexportLocalName() is a single-name lookup, called
    	// per-name on demand instead - not reusable as-is here).
    	{
    		String ownModuleName = jsContext.getModuleName();
    		if(ownModuleName!=null) {
    			int n = getChildCount();
    			for(int i=0; i<n; i++) {
    				ASTNode child = skipTransparent(getChild(i));
    				if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) || exp.getFrom()==null || exp.getItems().isEmpty()) {
    					continue;
    				}
    				if(!ModuleUtil.resolvePath(ownModuleName, exp.getFrom()).equals(ownModuleName)) {
    					continue;
    				}
    				for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: exp.getItems()) {
    					String alias = item.getAlias()!=null ? item.getAlias() : item.getName();
    					b.println("addNamedReExport({0},this,{1});",
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(alias),
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(item.getName()));
    				}
    			}
    		}
    	}

    	// Indirect/star static export-entry metadata, hoisted for
    	// JSTranspiledUnit.resolveExport()'s own execution-order-independent
    	// spec algorithm (see its own doc comment for the full rationale -
    	// test262 instn-*-iee-cycle.js and ambiguous-export-bindings/*.js).
    	// Pure data registration (no importModule() side effect) - covers
    	// EVERY `export ... from` form (named-from items, self or
    	// cross-module alike - a self-reference simply resolves back to
    	// `this` via the module cache at actual resolution time, same as
    	// block 3 above, so no self-check is needed here - and both star
    	// forms), regardless of whether this statement's own normal-position
    	// evaluate() has run yet.
    	{
    		int n = getChildCount();
    		for(int i=0; i<n; i++) {
    			ASTNode child = skipTransparent(getChild(i));
    			if(!(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) || exp.getFrom()==null) {
    				continue;
    			}
    			String from = exp.getFrom();
    			if(exp.getItems().isEmpty()) {
    				if(exp.getNamespace()!=null) {
    					b.println("registerIndirectExport({0},{1},{2});",
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(exp.getNamespace()),
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(from),
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(NAMESPACE_IMPORT_NAME));
    				} else {
    					b.println("registerStarExport({0});", org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(from));
    				}
    			} else {
    				for(org.monflabs.galtajs.node.control.ASTImpExp.Item item: exp.getItems()) {
    					String alias = item.getAlias()!=null ? item.getAlias() : item.getName();
    					b.println("registerIndirectExport({0},{1},{2});",
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(alias),
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(from),
    							org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(item.getName()));
    				}
    			}
    		}
    	}

    	// Module-level function/generator declarations get their REAL VALUE
    	// emitted NOW, before the import-hoisting block just below can
    	// trigger a dependency's evaluation - mirrors hoistDeclarations()'s
    	// identical interpreted-mode loop (see ASTFunctionDecl.hoistValue()'s
    	// own doc comment for the full spec rationale and the verify-dfs.js
    	// motivating case: a circular/self-import reading this module's own
    	// exported function back, from inside a dependency eagerly triggered
    	// by the block below, must see the REAL value, not the placeholder
    	// this method's own normal (reordered-to-front but still-emitted-
    	// later) statement position would otherwise still be waiting to
    	// assign). Gated to isModule(), same 3 shapes as interpreted mode:
    	// bare `function fn(){}`, `export function fn(){}`, `export default
    	// function fn(){}` - every other export form (class, non-hoistable
    	// default) untouched, matching isHoistableNamedExport()/
    	// isHoistableDefaultExport()'s own existing narrower scope.
    	if(isModule()) {
    		int n = getChildCount();
    		for(int i=0; i<n; i++) {
    			ASTNode child = skipTransparent(getChild(i));
    			if(child instanceof org.monflabs.galtajs.node.control.ASTFunctionDecl fd) {
    				fd.transpileHoistValue(jsContext, b);
    			} else if(child instanceof org.monflabs.galtajs.node.control.ASTExport exp) {
    				if(exp.isHoistableNamedExport()) {
    					((org.monflabs.galtajs.node.control.ASTFunctionDecl)exp.getNamedExport()).transpileHoistValue(jsContext, b);
    				} else if(exp.isHoistableDefaultExport()) {
    					((org.monflabs.galtajs.node.control.ASTFunctionDecl)exp.getDefaultExport()).transpileHoistValue(jsContext, b);
    				}
    			}
    		}
    	}

    	// Import hoisting - mirrors hoistDeclarations()'s identical
    	// interpreted-mode loop just above evaluate() (see its own doc
    	// comment: NOT gated on isModule(), same reasoning) and
    	// ASTBlock.transpileBlockStatements()'s own mapBlock.add() pattern
    	// (required to avoid regressing JSTranspilerMap's source-map line
    	// tracking for this early-emitted code - see
    	// docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's P4 for
    	// the full history of why this exact combination is needed). A
    	// module's default/namespace `import` bindings must be visible/usable
    	// before ANY of its own top-level code runs, regardless of the
    	// import statement's own source position (test262 language/
    	// module-code/instn-iee-bndng-*.js) - calls ASTImport.
    	// transpileEarlyImport() (NOT transpileJavaStatement() - see its own
    	// doc comment for why named items are deliberately excluded here and
    	// always handled later, at the import's own normal source position).
    	//
    	// Also hoists `export ... from '...'` dependency EVALUATION (not any
    	// binding - export-from introduces no local variable) in the SAME
    	// source-order pass, alongside plain imports - see ASTExport.
    	// transpileHoistDependencyEvaluation()'s own doc comment for why: per
    	// spec InnerModuleEvaluation, a module's own [[RequestedModules]] are
    	// ALL evaluated before its own top-level code runs, regardless of
    	// import/export-from form or source position (test262
    	// eval-rqstd-order.js). Safe only because the hoistable-function
    	// early-value loop just above already ran first - see its own doc
    	// comment for the regression (test262 verify-dfs.js) this ordering
    	// prevents.
    	{
    		// _pendingModuleDeps collects every dependency this module's OWN
    		// imports found still mid-evaluation (a genuinely different,
    		// async, not-yet-settled module - see ASTImport.
    		// transpileNonItemBindings()'s own doc comment on why this can't
    		// just `return;` from inside a single import's codegen: doing so
    		// would skip triggering the STILL-TO-COME sibling imports' own
    		// importModule() calls, which must always run regardless of an
    		// earlier sibling's pending status (test262 top-level-await/
    		// async-module-does-not-block-sibling-modules.js). Deferring is
    		// therefore decided ONCE, here, only after every import in this
    		// loop has had its own importModule() triggered.
    		b.println("java.util.List<{0}> _pendingModuleDeps = new java.util.ArrayList<>();", JSInterpretedUnit.class.getName());
    		int n = getChildCount();
    		for(int i=0; i<n; i++) {
    			ASTNode child = skipTransparent(getChild(i));
    			if(child instanceof org.monflabs.galtajs.node.control.ASTImport imp) {
    				b.debugLocation(child);
    				map.getCurrentBlock().add(b, child);
    				imp.transpileEarlyImport(jsContext, b);
    			} else if(child instanceof org.monflabs.galtajs.node.control.ASTExport exp && exp.getFrom()!=null) {
    				b.debugLocation(child);
    				map.getCurrentBlock().add(b, child);
    				exp.transpileHoistDependencyEvaluation(jsContext, b);
    			}
    		}
    		// See _pendingModuleDeps's own doc comment just above - restarts
    		// this module's WHOLE _runValue() from scratch (via
    		// JSTranspiledUnit.deferModuleUntilSettled()) once every
    		// still-pending dependency collected above has settled, instead
    		// of running this module's own body against not-yet-ready state
    		// (test262 top-level-await/dfs-invariant.js,
    		// pending-async-dep-from-cycle.js).
    		b.println("if(!_pendingModuleDeps.isEmpty()) { deferModuleUntilSettled({0},_pendingModuleDeps); return; }", JSTranspiler.MAIN_CONTEXT);
    	}

		if(isCommonJS()) {
			VariableDef exp = findVariable(ModuleUtil.EXPORTS);
			VariableDef mod = findVariable(ModuleUtil.MODULE);
			b.println("{0} = createObject();", exp.getJavaVariableValue());
			b.println("{0} = createObject(\"exports\",{1});", mod.getJavaVariableValue(), exp.getJavaVariableValue());
		}

		// Every top-level using/await-using resource must be disposed once
		// this module's own body finishes evaluating, on every exit path -
		// mirrors the identical interpreted-mode fix (ASTProgram.evaluate()'s
		// own hasUsingDeclarations-gated DisposeResourcesUtil.dispose() wrap)
		// and ASTFunction's own transpiled-codegen handling of the SAME
		// "root container, not an ASTBlock instance" concern (a function
		// body isn't an ASTBlock either - see ASTRootStatementList - so its
		// own using/await-using disposal boundary is set up directly against
		// its own context, not a nested child one; a module's top level is
		// the exact same shape). Previously ALWAYS transpileBlockStatements()
		// unconditionally, with no disposal-boundary support at module scope
		// at all - ASTVariableDeclUsing.transpileJavaStatement() would throw
		// "no enclosing disposal-boundary support" for any using/await-using
		// declared directly at a module's own top level (test262 language/
		// statements/{using,await-using}/initializer-*-disposed-at-end-of-
		// module.js and siblings).
		if(ASTBlock.hasUsingDeclarations(this)) {
			ASTBlock.transpileStatementsWithDisposal(jsContext, jsContext, b, this, getStatements());
		} else {
			ASTBlock.transpileBlockStatements(jsContext,b,this,getStatements());
		}

		map.popBlock(b.getCurrentLine());
    }

	
    /////////////////////////////////////////////////////////////////////////////
    // Decompiler
    /////////////////////////////////////////////////////////////////////////////

    public String decompile() {
    	return decompile(new JavaBuilder());
    }
    public String decompile(JavaBuilder b ) {
    	decompileStatement(b);
    	return b.toString();
    }

    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(isForceStrictMode()) {
    		b.println("\"use strict\";");
    	}
		ASTNode[] statements = getStatements();
    	decompileStatements(b, statements);
	}
}