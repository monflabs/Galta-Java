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
package org.monflabs.galtajs.node.control;

import java.util.List;
import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.ModuleUtil;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSModuleContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringUtil;


/**
 * Java  export statement.
 */
public class ASTExport extends ASTImpExp {
	
	private ASTNode defaultExport;
	private ASTNode namedExport;

	public ASTExport(Token t) {
		super(t);
	}
	
	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return defaultExport; }
			case 1 ->		{ return namedExport; }
			default ->		{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.defaultExport = node; }
			case 1 ->	{ this.namedExport  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}
	
	public ASTNode getDefaultExport() {
		return defaultExport;
	}

	public void setDefaultExport(ASTNode defaultExport) {
		this.defaultExport = defaultExport;
		assignParent(defaultExport);
	}

	@Override
	protected void init(InitContext initContext) {
		// IsAnonymousFunctionDefinition (ExportDeclaration : export default
		// AssignmentExpression, step 3-5): an anonymous class's name is
		// inferred as "default" - mirrors ASTVariableDecl/ASTAssign's own
		// static setClassName() calls for `var x = class {}` (called from
		// init(), BEFORE the class's own init() runs) - NOT evaluate()'s
		// runtime RuntimeUtil.maybeSetFunctionName() call below, which only
		// renames the ALREADY-FULLY-EVALUATED class value, too late for a
		// class with static field initializers that read `this.name` (spec's
		// SetFunctionName runs as part of ClassDefinitionEvaluation, before
		// static initializers execute - test262 class-name-static-
		// initializer-default-export.js). A named declaration/expression
		// (getClassName() already non-empty) keeps its own name untouched.
		// Note: `export default class {}` gets isStatement()==true even
		// when anonymous (unlike a NAMED `export default class Foo{}`'s
		// separate outer-LET-binding registration, which is additionally
		// gated on a non-empty name) - setting getClassName() here also
		// makes ASTClassDecl.init() register BOTH an inner self-reference
		// binding AND an outer LET binding, both literally named "default".
		// Harmless: `default` is a reserved word, never a syntactically
		// valid identifier any real source could reference, so neither
		// binding is ever observable - verified via full regression sweeps
		// (see commit message).
		if(defaultExport instanceof ASTClassDecl cd && StringUtil.isEmpty(cd.getClassName())) {
			cd.setClassName("default");
		}
		super.init(initContext);
	}

	// True when this `export default` wraps a function/generator/async
	// declaration (named OR anonymous) - per spec, unlike `export default
	// class C{}`/`export default <expr>`, this specific form IS a genuine
	// HoistableDeclaration: evaluated (and its own binding initialized)
	// before any other module-body statement runs, exactly like an
	// ordinary top-level `function` declaration - see ASTStatementList.
	// hoistNodes()'s own extended check, which this feeds.
	// ASTFunctionDecl.createFunction() already unconditionally registers a
	// variable for THIS statement whenever isStatement() is true (true for
	// every export-default-function form, anonymous included) - under
	// getFunctionName(), which is the empty string for the anonymous case.
	// getLiveExportNames() maps "default" to that same empty-string local
	// name for the anonymous case (see its own doc comment) - "" is never
	// a legal JS identifier, so it can't collide with any real user-level
	// binding; it only needs to be internally consistent between the two.
	public boolean isHoistableDefaultExport() {
		return defaultExport instanceof ASTFunctionDecl;
	}

	// Mirrors isHoistableDefaultExport() above, for the NON-default
	// `export function fn(){}`/`export function* fn(){}` form: the inner
	// ASTFunctionDecl node itself implements HoistableNode, but the actual
	// top-level statement in the module's list is this OUTER ASTExport
	// wrapper (which doesn't) - without this, ASTStatementList.hoistNodes()
	// never reorders it to the front, so calling the exported function
	// before its own source position throws "Function does not exist"
	// instead of returning its already-hoisted value (test262
	// instn-local-bndng-export-fun.js/-export-gen.js).
	public boolean isHoistableNamedExport() {
		return namedExport instanceof ASTFunctionDecl fd && StringUtil.isNotEmpty(fd.getFunctionName());
	}

	public ASTNode getNamedExport() {
		return namedExport;
	}

	public void setNamedExport(ASTNode namedExport) {
		this.namedExport = namedExport;
		assignParent(namedExport);
	}

	// Spec InnerModuleEvaluation (16.2.1.6.6): a module's own [[RequestedModules]]
	// are ALL evaluated before ANY of the module's own [[ECMAScriptCode]]
	// runs - regardless of where an `export ... from '...'` statement is
	// textually positioned in source (it may appear AFTER ordinary
	// top-level code that depends on that dependency's side effects
	// having already run - test262 eval-rqstd-order.js's own
	// `assert.sameValue(fnGlobalObject().test262, ...)` on line 1,
	// checking a value only set by the LAST-declared `export ... from`
	// dependency). ASTImport.hoistBindings() already does this for plain
	// imports (see its own doc comment); `export ... from` had no
	// equivalent at all - its dependency was only evaluated when THIS
	// statement's own evaluate() below was reached, in normal source
	// order, same as any other statement. Called from ASTProgram's SAME
	// hoist loop, in the SAME relative order as import hoisting (so the
	// combined effect matches spec's [[RequestedModules]] declaration
	// order across BOTH forms) - resolveModule() is idempotent/cached, so
	// this statement's own LATER, normal-position evaluate() below still
	// runs its full export-processing logic unchanged, just against an
	// already-evaluated (cache-hit) source module.
	//
	// Safe only because ASTProgram's own hoist pass ALSO ensures this
	// module's own hoistable function declarations already have their
	// REAL values (not just a placeholder cell) before this runs - see
	// ASTFunctionDecl.hoistValue()'s own doc comment for the regression
	// (test262 verify-dfs.js) hit before that fix existed, when a
	// dependency eagerly triggered here could read back this module's
	// own not-yet-executed function as unavailable.
	public void hoistDependencyEvaluation(JSInterpretedRuntimeContext context) {
		if(getFrom()!=null) {
			resolveModule(context);
		}
	}

	// Transpiled-codegen mirror of hoistDependencyEvaluation() above - see
	// its own doc comment for the full spec rationale. Emits ONLY the
	// side-effect-only module-resolution call (matching resolveModule()'s
	// own dispatch between plain/attributed imports) - never touches
	// export-merging codegen at all, which still happens later, unchanged,
	// at this statement's own normal transpileJavaStatement() position; the
	// module cache makes this idempotent, matching resolveModule()'s own
	// interpreted-mode idempotency guarantee. Safe now that ASTProgram's
	// own hoist emission ALSO gives this module's own hoistable function
	// declarations their real values first (ASTFunctionDecl.
	// transpileHoistValue()) and mergeStarReExports() above consults live
	// resolution for a name a circular re-export hasn't snapshotted yet -
	// see both of their own doc comments for why this was previously
	// reverted without them.
	public void transpileHoistDependencyEvaluation(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(getFrom()==null) {
			return;
		}
		String type = getAttributes()==null ? null : getAttributes().get("type");
		if(type==null) {
			b.println("importModule({0},{1});", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()));
		} else {
			String attributesExpr = org.monflabs.util.StringFormat.format("java.util.Collections.singletonMap(\"type\",{0})", ASTLiteral.encodeString(type));
			b.println("importAttributedModule({0},{1},{2});", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()), attributesExpr);
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Must be evaluated within a module context, else export is not available
			if(context instanceof JSModuleContext moduleContext) {
		    	JSModule module = moduleContext.getScriptUnit();
		    	String moduleName = module.getDescriptor().getName();

				// `export * from '...'` (bare) / `export * as ns from '...'`
				// (aliased) - ExportNamespace() in the grammar sets
				// getNamespace() only for the aliased form; getFrom() is set
				// for BOTH. CRITICALLY, `getFrom()!=null` alone is NOT
				// enough to detect this case - a named re-export list WITH
				// a source (`export {a as b} from '...'`, ModuleList()'s own
				// grammar production) ALSO sets getFrom(), but must fall
				// through to the existing `items` handling below instead
				// (confirmed via a regression: `export {local1 as indirect}
				// from './same-file.js'` was wrongly intercepted here,
				// silently merging ALL of the source's exports instead of
				// just re-exporting the one named item). The star-export
				// grammar production (ExportNamespace()) never populates
				// `items` at all, so that's the real distinguishing test.
				if(getFrom()!=null && getItems().isEmpty()) {
					JSModule sourceModule = resolveModule(context);
					if(getNamespace()!=null) {
						// export * as ns from '...' - a single named export
						// bound to the source module's own namespace object.
						JSObject namespaceObj = RuntimeUtil.buildImportNamespaceObject(context.getEnvironment(), sourceModule);
						if("default".equals(getNamespace())) {
							// `export * as default from '...'` - "default"
							// is never a genuine entry in namedExports (see
							// JSModule's own getExport() doc comment); it's
							// tracked via setDefaultExport()/
							// hasDefaultExport() instead, which is what
							// BOTH a default IMPORT (ASTImport's separate
							// getLiveDefaultExportAccessor()/
							// hasDefaultExport() mechanism) and a further
							// re-export of `default` from THIS module
							// (resolveExport()'s own hasDefaultExport()
							// fallback) actually consult.
							module.setDefaultExport(namespaceObj);
						} else {
							module.addNamedExports(getNamespace(), namespaceObj);
						}
					} else {
						// export * from '...' - merge every one of the
						// source's OWN named exports into this module's
						// exports (never its default - see
						// AbstractModule.addStarReExport()'s own doc
						// comment for the star-vs-star ambiguity rule).
						mergeStarReExports(module, sourceModule);
						// Circular star re-export (A `export * from B`, B
						// `export * from A`): if sourceModule is STILL
						// executing its own body right now (this statement
						// reached via a NESTED import triggered from within
						// sourceModule's own not-yet-finished evaluation -
						// e.g. B's `export * from A` running from inside A's
						// own still-running `export * from B`), the merge
						// just above only saw sourceModule's namedExports AS
						// OF RIGHT NOW - it may be missing names sourceModule
						// hasn't declared yet. Re-run the SAME merge once
						// sourceModule's ENTIRE body finishes (see
						// JSInterpretedUnit.addSettleCallback()'s own doc
						// comment). addStarReExport() is idempotent for an
						// already-registered (name,source) pair (a plain
						// no-op - see its own doc comment), so re-running
						// this is always safe; if sourceModule had ALREADY
						// finished before this statement even ran, the
						// callback simply never fires (nothing was missing
						// to begin with).
						if(sourceModule instanceof org.monflabs.galtajs.modules.JSInterpretedUnit sourceUnit) {
							sourceUnit.addSettleCallback(() -> mergeStarReExports(module, sourceModule));
						}
					}
					return Signal.NONE;
				}

				if(defaultExport!=null) {
					Object value = defaultExport.evaluateValue(context,result);
					// A NAMED "export default function fn(){}"/"export default
					// class Foo{}" is a genuine DECLARATION (see
					// ExportStatement()'s own comment in JSParser.jj) - its
					// evaluate() leaves `result` empty/undefined per spec (a
					// declaration statement has no completion value) and instead
					// binds the function/class into its own name in `context`,
					// exactly like the plain "export function fn(){}" branch
					// below (whose namedExport.evaluateValue() return is
					// similarly discarded, relying on a live binding lookup
					// instead). The default export's VALUE is that SAME binding
					// (default is an alias, not a separate copy), so read it back
					// by name rather than trusting evaluateValue()'s (empty)
					// return - confirmed via test262's eval-gtbndng-indirect-
					// update-dflt.js family, which reassigns `fn` from within the
					// function body and expects `imported.default` to see it.
					if(defaultExport instanceof ASTFunctionDecl fd && fd.isStatement()) {
						// Anonymous form (`export default function(){}`/
						// `function*(){}`) - getFunctionName() is "" (see
						// isHoistableDefaultExport()'s own doc comment),
						// still a real hoisted variable slot under that
						// same empty-string key, just with no user-visible
						// name of its own - IsAnonymousFunctionDefinition
						// (spec step 3-5) still applies, so `.name` needs
						// the SAME "default" inference the non-hoisted
						// anonymous-expression branch below gives it.
						value = context.getVariableValue(fd.getFunctionName(), value);
						if(StringUtil.isEmpty(fd.getFunctionName())) {
							RuntimeUtil.maybeSetFunctionName("default", value);
						}
					} else if(defaultExport instanceof ASTClassDecl cd && cd.isStatement() && StringUtil.isNotEmpty(cd.getClassName())) {
						value = context.getVariableValue(cd.getClassName(), value);
					} else {
						// IsAnonymousFunctionDefinition (spec ExportDeclaration :
						// export default AssignmentExpression, step 3-5):
						// `export default (function(){})`/`export default
						// (class{})`/`export default function(){}` (no name)/
						// `export default class{}` (no name) all name-infer to
						// "default" - matches RuntimeUtil.maybeSetFunctionName()'s
						// own "only if it doesn't already have one" guard (a
						// NAMED function/class expression, e.g. `export default
						// (function f(){})`, keeps its own name "f", untouched -
						// this call is then simply a no-op for it). The named-
						// DECLARATION forms above already resolved `value` via
						// their own local binding read, bypassing this branch
						// entirely (their name was already set at creation time).
						RuntimeUtil.maybeSetFunctionName("default", value);
					}
					moduleContext.getScriptUnit().setDefaultExport(value);
				}
				if(namedExport!=null) {
					namedExport.evaluateValue(context, result);
				}
				
		    	// Named exports
		    	//  export { a as A, B, c as C... }
		    	//  export { a as A, B, c as C... } from '...'
		    	List<Item> items = getItems();
		    	if(items!=null && !items.isEmpty()) {
		    		// `export {name} from '...'` re-exports NAME'S VALUE IN THE
		    		// SOURCE module, never a local binding of this module's own
		    		// (this module may not even declare `name` at all) -
		    		// resolved once, shared by every item in this list.
		    		//
		    		// EXCEPT for a SELF-reference (`export {a} from
		    		// './this-same-file.js'`, an idiom test262's own module-
		    		// code_FIXTURE.js family relies on) - GaltaJS's module
		    		// system has no cache-before-completion (a module is only
		    		// registered once its ENTIRE body finishes running - see
		    		// KnownGaps.md's own module live-bindings entry), so
		    		// re-importing THIS module while it's still mid-load
		    		// recurses into loading it a second time from scratch,
		    		// which crashes. Reading the name from THIS module's own
		    		// (already-hoisted, already-partially-evaluated) local
		    		// scope instead sidesteps that entirely, and happens to be
		    		// observably correct for the self-reference case
		    		// specifically (the value's the same either way).
		    		boolean selfReference = getFrom()!=null && ModuleUtil.resolvePath(moduleName, getFrom()).equals(moduleName);
		    		JSModule namedFromSource = (getFrom()!=null && !selfReference) ? resolveModule(context) : null;
		    		for(Item item: items) {
		    			String name = item.getName();
		    			Object v;
		    			boolean deferred = false;
		    			if(namedFromSource!=null) {
		    				JSObject fromExports = namedFromSource.getNamedExports();
		    				if(fromExports==null || !fromExports.hasProperty(name)) {
		    					// Circular re-export (test262 instn-iee-*.js): this module's
		    					// own `export {A as B} from X` where X, in turn, imports
		    					// something back from THIS module - X's own matching export
		    					// statement may genuinely not have RUN yet, so its
		    					// namedExports snapshot doesn't have `name` YET even though
		    					// it's a real, statically resolvable export. Falling straight
		    					// to "Unknown export" here would wrongly reject a legitimate
		    					// cycle - defer to the LIVE re-export accessor instead (same
		    					// one registered via addNamedReExport() below,
		    					// AbstractModule.getExportAccessor()'s own recursive
		    					// resolution): if X can resolve `name` at all (even if its
		    					// VALUE isn't computed yet), skip the eager snapshot for this
		    					// alias and let the live path serve any future reader - only
		    					// a genuinely nonexistent export still throws.
		    					try {
		    						namedFromSource.getExportAccessor(name);
		    					} catch(RuntimeException ex) {
		    						// Spec 16.2.1.6.1 InitializeEnvironment step 9 (via
		    						// ResolveExport): an IndirectExportEntry that resolves
		    						// to null (genuinely missing) OR "ambiguous" must throw
		    						// a SyntaxError specifically - test262 instn-iee-err-
		    						// circular.js/instn-iee-err-ambiguous-import.js both
		    						// assert `error.name==="SyntaxError"`, not just "some
		    						// error".
		    						throw RuntimeUtil.syntaxError("Unknown export {0} in module {1}",name,getFrom());
		    					}
		    					v = null;
		    					deferred = true;
		    				} else {
		    					v = fromExports.getProperty(name);
		    				}
		    			} else {
		    				v = moduleContext.getVariableValue(name,RuntimeUtil.NOT_AVAILABLE);
		    				if(v==RuntimeUtil.NOT_AVAILABLE) {
		    					throw new JSException(null,"Unknown export {0} in module {1}",name,moduleName);
		    				}
		    			}
		    			String alias = item.getAlias();
		    			if(alias==null) {
		    				alias = name;
		    			}
		    			if(!deferred) {
		    				module.addNamedExports(alias, v);
		    			}
		    			// Live re-export accessor (see AbstractModule.
		    			// addNamedReExport()'s own doc comment) - only for a
		    			// genuine CROSS-module re-export; a self-reference
		    			// keeps behaving exactly as before (reading THIS
		    			// module's own local scope, snapshot only), and a
		    			// plain local `export {name}` (namedFromSource==null,
		    			// not a self-reference either) is already covered by
		    			// ASTProgram.getLiveExportNames()'s own tracking.
		    			if(namedFromSource!=null) {
		    				module.addNamedReExport(alias, namedFromSource, name);
		    			}
		    		}
		    	}
		    	
		    	// Declarations
		    	//   export const a=1;
		    	ASTNode decl = getNamedExport();
		    	if(decl!=null) {
					if(decl instanceof ASTVariableDecl vd) {
						List<String> vars = vd.getDeclaredVariables();
						for(String name: vars) {
			    			Object v = moduleContext.getVariableValue(name,RuntimeUtil.NOT_AVAILABLE);
			    			if(v==RuntimeUtil.NOT_AVAILABLE) {
			    				throw new JSException(null,"Unknown export {0} in module {1}",name,moduleName);
			    			}
			    			module.addNamedExports(name, v);
						}
			    	} else if(decl instanceof ASTFunctionDecl fd) {
			    		String name = fd.getFunctionName();
			    		if(StringUtil.isEmpty(name)) {
		    				throw new JSException(null,"Cannot export an anonymous function in module {0}",moduleName);
			    		}
			    		module.addNamedExports(name, moduleContext.getVariableValue(name,RuntimeUtil.UNDEFINED));
			    	} else if(decl instanceof ASTClassDecl cd) {
			    		String name = cd.getClassName();
			    		if(StringUtil.isEmpty(name)) {
		    				throw new JSException(null,"Cannot export an anonymous class in module {0}",moduleName);
			    		}
			    		module.addNamedExports(name, moduleContext.getVariableValue(name,RuntimeUtil.UNDEFINED));
			    	}
		    	}
				return Signal.NONE;
			} else {
				throw RuntimeUtil.syntaxError("export must be used from a module");
			}
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	// Shared by the bare `export * from '...'` branch above and its own
	// settle-callback retry (see that call site's own doc comment) - kept
	// as one method so both runs are guaranteed to do EXACTLY the same
	// thing.
	public static void mergeStarReExports(JSModule module, JSModule sourceModule) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		JSObject sourceExports = sourceModule.getNamedExports();
		if(sourceExports!=null) {
			for(Iterator<Map.Entry<String,Object>> it = sourceExports.ownPropertyEntries(false); it.hasNext(); ) {
				Map.Entry<String,Object> e = it.next();
				module.addStarReExport(e.getKey(), e.getValue(), sourceModule);
				seen.add(e.getKey());
			}
		}
		// A name sourceModule can already resolve LIVE (registerLiveExport()'s
		// hoisted local-export map, or a named-with-`from` re-export
		// registered via addNamedReExport()) but hasn't snapshotted into
		// namedExports yet - reached when sourceModule's own matching
		// `export ... from` statement took ASTExport's "existence confirmed
		// but deferred" catch path above (a circular indirect re-export whose
		// own source hadn't populated its snapshot yet at THAT time -
		// addNamedReExport() is still called unconditionally in that branch,
		// so the live map has the name even though namedExports doesn't).
		// Without this, a bare `export * from` of such a source silently
		// drops the name forever (test262 eval-rqstd-order.js's own indirect
		// re-export chain, surfaced once export-from dependency hoisting -
		// see ASTProgram's own early-trigger loop - makes this reachable
		// before the source's snapshot would otherwise have caught up).
		// getExportAccessor() resolves recursively and live, so this is
		// correct regardless of how deep the re-export chain goes.
		if(sourceModule instanceof org.monflabs.galtajs.modules.AbstractModule am) {
			for(String name: am.getLiveExportedNames()) {
				if(seen.contains(name) || "default".equals(name)) {
					continue;
				}
				try {
					Object value = RuntimeUtil.checkTDZ(sourceModule.getExportAccessor(name).getValue(), name);
					module.addStarReExport(name, value, sourceModule);
				} catch(RuntimeException ex) {
					// Not yet initialized (TDZ) or not actually resolvable -
					// skip; a later settle-callback re-run (see this
					// method's own call site) picks it up once available.
				}
			}
		}
	}


    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	String moduleName = jsContext.getModuleName();

		if(defaultExport!=null) {
			// IsAnonymousFunctionDefinition (spec ExportDeclaration : export
			// default AssignmentExpression, step 3-5): `export default
			// (function(){})`/`export default (class{})`/`export default
			// function(){}` (no name)/`export default class{}` (no name)
			// all name-infer to "default" - mirrors ASTExport.evaluate()'s
			// identical interpreted-mode call. maybeSetFunctionName() only
			// sets the name if the value doesn't already have one, so this
			// is a no-op for a NAMED function/class expression (e.g.
			// `export default (function f(){})` keeps "f") or a
			// non-function value - previously missing from transpiled
			// codegen entirely (test262 eval-export-dflt-expr-{fn,gen,
			// cls}-anon.js's own `f.name` assertion).
			if(defaultExport instanceof ASTFunctionDecl fd && fd.isStatement()) {
				// A HoistableDeclaration default export (named OR anonymous -
				// see isHoistableDefaultExport()'s own doc comment) genuinely
				// binds its OWN name too, not just "default" - `export
				// default function F(){} F.foo='';` must see F's real value
				// immediately (test262 export-default-function-declaration-
				// binding.js and generator/async siblings). The expression-
				// mode codegen below (asValue()/transpileJavaExpression())
				// constructs a function instance but never assigns it into
				// F's own variable slot - dispatch through
				// transpileJavaStatement() instead, which already does
				// exactly that (same fix shape as the `export let X;` one
				// above, for the SAME reason: expression mode is the wrong
				// dispatch for a declaration with slot-assignment side
				// effects). Then read the SAME slot back for the "default"
				// alias, mirroring ASTExport.evaluate()'s own interpreted-
				// mode read-back (`context.getVariableValue(fd.getFunctionName(),...)`)
				// instead of constructing a second, separate instance.
				fd.transpileJavaStatement(jsContext, b);
				VariableDef v = findVariable(fd.getFunctionName());
				String dfltVar = v.getJavaVariableValue();
				b.println("maybeSetFunctionName({0},{1});", ASTLiteral.encodeString("default"), dfltVar);
				b.println("setDefaultExport({0});", dfltVar);
			} else {
				String dfltVar = jsContext.generateUniqueId("_dflt");
				b.println("Object {0} = {1};", dfltVar, JSTranspiler.asValue(jsContext,defaultExport));
				b.println("maybeSetFunctionName({0},{1});", ASTLiteral.encodeString("default"), dfltVar);
				b.println("setDefaultExport({0});", dfltVar);
			}
		}
		if(namedExport!=null) {
			// `export let A;` (no initializer): asRawValue()/transpileJavaExpression()
			// deliberately returns just the bare slot reference for this case
			// (the shape a `for(let x; ...)` head needs) - valid as an
			// expression fragment, invalid alone as a Java statement
			// ("not a statement" compile error). ASTVariableDecl's own
			// transpileJavaStatement() already handles this correctly
			// (emits `= UNDEFINED;`), so dispatch through it instead for
			// this case specifically - not for ASTFunctionDecl/ASTClassDecl
			// below, whose current asRawValue()-based codegen is unverified
			// safe to change (see KnownGaps.md).
			if(namedExport instanceof org.monflabs.galtajs.node.variable.ASTVariableDecl
					|| namedExport instanceof ASTFunctionDecl) {
				// Same fix as the `export let X;`/`export default function`
				// cases above: expression mode (`asRawValue()`/
				// `transpileJavaExpression()`) is the wrong dispatch for a
				// declaration with slot-assignment side effects -
				// `ASTFunctionDecl.transpileJavaStatement()` already finds
				// its own variable and assigns `new FnClass(...)` into it
				// correctly; the old `asRawValue()` path constructed a
				// function instance and then DISCARDED it, never touching
				// `f`'s own slot at all (test262 instn-named-bndng-{fun,
				// gen}.js and siblings - a plain `export function f(){}`'s
				// own local binding was never actually assigned in
				// transpiled mode). `ASTClassDecl` is NOT included here -
				// its own asRawValue()-based codegen is a separate,
				// unverified case not touched by this fix.
				namedExport.transpileJavaStatement(jsContext, b);
			} else {
				b.println("{0};", JSTranspiler.asRawValue(jsContext,namedExport));
			}
		}
		
    	// `export * from '...'` (bare) / `export * as ns from '...'`
    	// (aliased) - getFrom()!=null with EMPTY items distinguishes this
    	// from a named re-export list WITH a source (`export {a as b} from
    	// '...'`, which also sets getFrom() but must fall through to the
    	// items handling below instead - see ASTExport.evaluate()'s own
    	// identical distinguishing comment). Previously entirely
    	// unimplemented in transpiled codegen (silently emitted NOTHING at
    	// all) - masked, before docs/GaltaJS/
    	// TranspiledModuleLiveBindingsDesignBrief.md's P1 (self-
    	// registration) existed, by a self-import of a star-re-exported
    	// name always resolving through an accidental duplicate, fully-
    	// interpreted copy of this same module instead (which DOES
    	// correctly process this statement, via ASTExport.evaluate()) -
    	// exposed as a real regression once self-imports started finding
    	// this real, transpiled instance (test262 ambiguous-export-
    	// bindings/*.js, export-star-as-dflt.js, export-expname-from-star-
    	// string.js). Mirrors evaluate()'s own logic, including the
    	// addSettleCallback() re-merge for a circular star re-export whose
    	// source is still mid-evaluation right now (see its own doc
    	// comment) - sourceModule always loads via JSInterpretedUnit (a
    	// star re-export's source is a module dependency, never
    	// ahead-of-time transpiled).
    	if(getFrom()!=null && getItems().isEmpty()) {
    		String fromVar = jsContext.generateUniqueId("_starMod");
    		b.println("JSModule {0} = importModule({1},{2});", fromVar, JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()));
    		if(getNamespace()!=null) {
    			String nsVar = fromVar+"_ns";
    			b.println("JSObject {0} = buildImportNamespaceObject({1},{2});", nsVar, JSTranspiler.MAIN_ENVIRONMENT, fromVar);
    			if("default".equals(getNamespace())) {
    				b.println("setDefaultExport({0});", nsVar);
    			} else {
    				b.println("addNamedExports({0},{1});", ASTLiteral.encodeString(getNamespace()), nsVar);
    			}
    		} else {
    			b.println("{0}.mergeStarReExports(this,{1});", org.monflabs.galtajs.node.control.ASTExport.class.getName(), fromVar);
    			String unitVar = fromVar+"_u";
    			b.println("if({0} instanceof {1} {2}) {", fromVar, org.monflabs.galtajs.modules.JSInterpretedUnit.class.getName(), unitVar);
    			b.incIndent();
    			b.println("{0}.addSettleCallback(() -> {1}.mergeStarReExports(this,{2}));", unitVar, org.monflabs.galtajs.node.control.ASTExport.class.getName(), fromVar);
    			b.decIndent();
    			b.println("}");
    		}
    		return;
    	}

    	// Named exports
    	//  export { a as A, B, c as C... }
    	List<Item> items = getItems();
    	if(items!=null && !items.isEmpty()) {
    		if(getFrom()!=null) {
    			// export { a as A, B } from '...' - a RE-EXPORT: each name is
    			// read from the SOURCE module's own exports, not a local
    			// variable. addNamedReExport() registers the LIVE delegation
    			// (AbstractModule.getExportAccessor()'s own namedReExportSources
    			// check, ALREADY implemented and used by every other module
    			// kind - see its own doc comment) so a later reassignment of
    			// the SOURCE's own exported variable (including via a
    			// self-/circular re-export back to this same module, per
    			// spec ResolveExport's recursive delegation) is observed here
    			// too - previously a one-time .getValue() snapshot (test262
    			// namespace/internals/get-str-update.js's own `ns.indirect`
    			// assertion), matching docs/GaltaJS/
    			// TranspiledModuleLiveBindingsDesignBrief.md's P2.
    			// addNamedExports() is ALSO still called, unconditionally, for
    			// the (rare) source kind with no live getExportAccessor()
    			// concept of its own (e.g. a synthetic JSON/text module) -
    			// AbstractModule's own re-export precedence check
    			// (namedReExportSources is consulted FIRST) means this
    			// snapshot is only ever the effective value for such a case,
    			// never overriding the live path.
    			String fromVar = jsContext.generateUniqueId("_fromMod");
    			b.println("JSModule {0} = importModule({1},{2});", fromVar, JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()));
    			for(Item item: items) {
    				String name = item.getName();
    				String alias = item.getAlias();
    				if(alias==null) {
    					alias = name;
    				}
    				b.println("addNamedReExport({0},{1},{2});",
    						JSTranspiler.asValue(jsContext,alias), fromVar, ASTLiteral.encodeString(name));
    				b.println("addNamedExports({0},{1}.getExportAccessor({2}).getValue());",
    						JSTranspiler.asValue(jsContext,alias), fromVar, ASTLiteral.encodeString(name));
    			}
    		} else {
    			for(Item item: items) {
    				String name = item.getName();
    				VariableDef v = jsContext.findVariable(name);
    				if(v==RuntimeUtil.NOT_AVAILABLE) {
    					throw new JSException(null,"Unknown export {0} in module {1}",name,moduleName);
    				}
    				String alias = item.getAlias();
    				if(alias==null) {
    					alias = name;
    				}
    				b.println("addNamedExports({0},{1});",JSTranspiler.asValue(jsContext,alias),JSTranspiler.asVar(jsContext,v));
    				// A re-exported source-phase import is an INDIRECT entry
    				// (registered by ASTProgram's hoisted export-entry pass),
    				// never a live local export - see ASTProgram.SOURCE_IMPORT_NAME.
    				org.monflabs.galtajs.node.ASTProgram.IndirectExportEntry imported =
    						findParentNodeByClass(org.monflabs.galtajs.node.ASTProgram.class).getImportedLocalNames().get(name);
    				if(imported==null || !org.monflabs.galtajs.node.ASTProgram.SOURCE_IMPORT_NAME.equals(imported.importName())) {
    					emitRegisterLiveExport(b,alias,v);
    				}
    			}
    		}
    	}

    	// Declarations
    	//   export const a=1;
    	ASTNode decl = getNamedExport();
    	if(decl!=null) {
			if(decl instanceof ASTVariableDecl vd) {
				List<String> vars = vd.getDeclaredVariables();
				for(String name: vars) {
	    			VariableDef v = jsContext.findVariable(name);
	    			if(v==RuntimeUtil.NOT_AVAILABLE) {
	    				throw new JSException(null,"Unknown export {0} in module {1}",name,moduleName);
	    			}
	    			b.println("addNamedExports({0},{1});",JSTranspiler.asValue(jsContext,name),JSTranspiler.asVar(jsContext,v));
	    			emitRegisterLiveExport(b,name,v);
				}
	    	} else if(decl instanceof ASTFunctionDecl fd) {
	    		String name = fd.getFunctionName();
	    		if(StringUtil.isEmpty(name)) {
    				throw new JSException(null,"Cannot export an anonymous function in module {0}",moduleName);
	    		}
    			VariableDef v = jsContext.findVariable(name);
    			if(v==RuntimeUtil.NOT_AVAILABLE) {
    				throw new JSException(null,"Unknown export {0} in module {1}",name,moduleName);
    			}
    			b.println("addNamedExports({0},{1});",JSTranspiler.asValue(jsContext,name),JSTranspiler.asVar(jsContext,v));
    			emitRegisterLiveExport(b,name,v);
	    	}
    	}
    }

    // See docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's P2 and
    // AbstractModule.registerLiveExport()'s own doc comment - registers a
    // LIVE accessor (a JSVarRef aliasing this module's own array+index)
    // alongside the existing addNamedExports() value snapshot, for every
    // LOCALLY-declared export (never a `from`-re-export, which has no local
    // slot of its own here - AbstractModule's own namedReExportSources/
    // starExportSources already handle re-export liveness by delegating to
    // the SOURCE module's own getExportAccessor(), unaffected by this).
    private void emitRegisterLiveExport(TranspilerJavaBuilder b, String exportedName, VariableDef v) {
    	b.println("registerLiveExport({0},JSVarRef.of({0},{1},{2},VAR_TYPE.{3}));",
    			ASTLiteral.encodeString(exportedName), v.getJavaVariableArray(), v.getJavaVariableIndex(), v.getVarType().name());
    }
}