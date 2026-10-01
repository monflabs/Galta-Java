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

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.galtajs.rt.util.WeakIdentityMap;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * Java package/class import statement.
 */
public class ASTImport extends ASTImpExp {

	// `import defer * as ns from '...'` (import-defer proposal) - see
	// JSGlobalContext.importDeferredNamespace()'s own doc comment. Always
	// paired with a namespace import (getNamespace()!=null) - the grammar
	// (JSParser.jj's ImportStatement()) only ever sets this when "defer"
	// is immediately followed by "*"; every other combination is a
	// SyntaxError instead.
	private boolean deferred;
	public boolean isDeferred() {
		return deferred;
	}
	public void setDeferred(boolean deferred) {
		this.deferred = deferred;
	}

	// `import source x from '...'` (source-phase-imports proposal): the
	// binding (stored as the default import - the grammar allows exactly
	// one ImportedBinding, nothing else) holds the target's Module Source
	// Object (JSGlobalContext.importModuleSource()); the target is never
	// loaded, linked or evaluated. See ASTProgram.SOURCE_IMPORT_NAME for the
	// re-export bookkeeping.
	private boolean sourcePhase;
	public boolean isSourcePhase() {
		return sourcePhase;
	}
	public void setSourcePhase(boolean sourcePhase) {
		this.sourcePhase = sourcePhase;
	}

	// What hoistBindings() did for one module instance, read back by
	// evaluate() for the same instance. Kept per module instance (keyed by
	// its main context), never on this AST node, which every evaluation of
	// the module (another realm, a reload, ...) shares.
	private static final class HoistState {
		// The default import was already bound early (live) - evaluate()
		// skips its own (snapshot, non-hoisted) default-import handling
		// rather than re-processing (and clobbering) an already-live binding.
		boolean defaultHoisted;
		// The namespace binding was assigned early - evaluate() skips its own
		// (now-redundant) assignment. Unlike a default/named import, a
		// namespace import's target (never null - see
		// getModuleNamespaceObject()) is ALWAYS immediately resolvable
		// regardless of the source module's own execution progress, so this
		// is unconditionally set whenever a namespace binding exists at all.
		boolean namespaceHoisted;
		// Named items hoistBindings() couldn't resolve yet (its
		// getExportAccessor() call threw - e.g. a name reached only through a
		// bare `export * from '...'` in the SAME module, which has no
		// pre-existing binding cell to alias toward: star-re-exported names
		// are only populated by that statement's own, non-hoisted evaluation
		// - see AbstractModule.getExportAccessor()'s own re-export doc
		// comment). evaluate() retries these, at this import's own normal
		// source position, via the original (pre-live-binding) value-copy
		// path - exactly like it already does for a default import
		// targeting a non-hoistable default export.
		List<Item> unresolvedItems;
	}
	private final WeakIdentityMap<JSRuntimeContext,HoistState> hoistStates = new WeakIdentityMap<>();

	private HoistState getHoistState(JSRuntimeContext mainContext) {
		synchronized(hoistStates) {
			return hoistStates.get(mainContext);
		}
	}
	private HoistState createHoistState(JSRuntimeContext mainContext) {
		synchronized(hoistStates) {
			HoistState state = new HoistState();
			hoistStates.put(mainContext, state);
			return state;
		}
	}

	// Set once ASTProgram.transpileJavaStatement()'s own early-emission hoist
	// pass (see its own doc comment, and docs/GaltaJS/
	// TranspiledModuleLiveBindingsDesignBrief.md's P4) has already emitted
	// this import's codegen ahead of its own normal source position -
	// transpileJavaStatement()'s later, normal-position call becomes a
	// no-op, mirroring how HoistState's defaultHoisted/namespaceHoisted guard the
	// interpreted-mode evaluate() against redoing hoisted work.
	private boolean transpiledEarly;

	public ASTImport(Token t) {
		super(t);
	}

//	public boolean addVarDeclaration(String varName, VAR_TYPE varType, JSType jsType, ASTFunctionDecl function, Function<JSRuntimeContext,Object> initializer);
	@Override
	protected void init(InitContext initContext) {
		// An `import` declaration is only valid at module top level - never
		// in genuinely eval'd source (spec sec-scripts: eval code is always
		// a Script, and ImportDeclaration only exists in ModuleItemList).
		// Checked via isEval(), NOT !isModule() - GaltaJS's own test suite
		// calls env.createScript(text,name) (SCRIPT_ADDTOCACHE only, neither
		// SCRIPT_MODULE nor SCRIPT_EVAL) pervasively while still expecting
		// import/export syntax to work outside a genuine module, so
		// !isModule() is not a safe signal for "is this eval code" (see
		// KnownGaps.md's eval-code/import.js entry for the regression this
		// caused the first time around).
		ASTProgram program = findParentNodeByClass(ASTProgram.class);
		if(program!=null && program.isEval()) {
			throw RuntimeUtil.syntaxError("import declarations may not appear in eval code");
		}
		IContextBlockContainer varContainer = findParentNodeByClass(IContextBlockContainer.class);
		if (StringUtil.isNotEmpty(getDefaultImport())) {
			checkStrictBindingName(initContext, getDefaultImport(), this);
			varContainer.addVarDeclaration(getDefaultImport(), VAR_TYPE.CONST, null);
		}
		if (StringUtil.isNotEmpty(getNamespace())) {
			checkStrictBindingName(initContext, getNamespace(), this);
			varContainer.addVarDeclaration(getNamespace(), VAR_TYPE.CONST, null);
		}
		List<Item> items = getItems();
		if (!items.isEmpty()) {
			int sz = items.size();
			for (int i = 0; i < sz; i++) {
				Item it = items.get(i);
				String n = it.getAlias() == null ? it.getName() : it.getAlias();
				checkStrictBindingName(initContext, n, this);
				varContainer.addVarDeclaration(n, VAR_TYPE.CONST, null);
			}
		}
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if (deferred || sourcePhase) {
				// hoistBindings() already did everything a deferred or
				// source-phase import needs (always a single binding,
				// always resolved there - see its own doc comment) -
				// unlike an ordinary import, this statement's own source
				// position must NOT re-resolve the module (resolveModule()
				// below would evaluate it eagerly, defeating the entire
				// point of `import defer` / `import source`).
				return Signal.NONE;
			}
			// Named items and a hoistable default export's binding
			// (`export default function fn(){}` specifically - see
			// ASTExport.isHoistableDefaultExport()) are bound EARLY (see
			// hoistBindings(), called from ASTProgram's own hoist pass,
			// before ANY module-body statement runs) - not handled here
			// (defaultHoisted guards the default-import branch below).
			// Namespace imports, and a default import targeting a
			// NON-hoistable default export (`export default class{}`/
			// `export default <expr>`, whose VALUE is only ever produced
			// by that statement's own non-hoisted evaluation), are
			// deliberately NOT hoisted - confirmed via a regression:
			// eval-export-dflt-cls-named.js's self-referencing `import C
			// from './same-file.js'` started failing "does not have a
			// default export" when an earlier, cruder attempt hoisted
			// EVERY default import unconditionally, without regard to
			// whether the target's own default-export form was itself
			// hoisted.
			//
			// importModule() itself is ALWAYS called here, even for a
			// bindingless side-effect-only import (`import "./module.js";`)
			// or an items/default-only import (redundant but harmless -
			// already triggered by hoistBindings() in that case, so this is
			// just a cache hit) - a bare import's ENTIRE purpose is
			// triggering the target's evaluation for its side effects, so
			// skipping this call when there's no default/namespace BINDING
			// to establish was a real bug (confirmed via a genuine hang:
			// top-level-await/fulfillment-order.js's dependency graph relies
			// on a bare `import "./b-sentinel_FIXTURE.js";` actually running
			// (it resolves a Promise another module is awaiting) - silently
			// no-op'ing it left that promise permanently unresolved).
			JSModule module = resolveModule(context);
			InterpretedUnitRuntimeContext mainContext = (InterpretedUnitRuntimeContext) context.getMainContext();
			HoistState hoisted = getHoistState(mainContext);
			boolean defaultHoisted = hoisted!=null && hoisted.defaultHoisted;
			boolean namespaceHoisted = hoisted!=null && hoisted.namespaceHoisted;
			List<Item> unresolvedItems = hoisted!=null ? hoisted.unresolvedItems : null;
			if ((StringUtil.isEmpty(getDefaultImport()) || defaultHoisted) && (StringUtil.isEmpty(getNamespace()) || namespaceHoisted)
					&& (unresolvedItems == null || unresolvedItems.isEmpty())) {
				return Signal.NONE;
			}

			// Imported bindings are CONST, so ASTProgram's hoist pass already
			// pre-populated a TDZ placeholder for each of them - initialize it
			// in place (same as ASTVariableDeclConst.createVariable) rather
			// than re-declaring, which VariableMap.createVariable() would reject.
			if (StringUtil.isNotEmpty(getDefaultImport()) && !defaultHoisted) {
				if (!module.hasDefaultExport()) {
					// A genuine SELF-import (this import statement's own
					// enclosing module, currently executing, reaching back
					// to its OWN `export default <expr>`/`export default
					// class C{}` positioned LATER in source) - the default
					// export genuinely hasn't been produced yet, and there's
					// no later opportunity within THIS module's own single
					// top-to-bottom evaluate() pass to retry (unlike a named
					// item's unresolvedItems, which can retry later at this
					// SAME statement's own evaluate() call because that
					// mechanism bridges an EARLIER hoist-time attempt to
					// THIS one - here, this IS already the only normal-
					// position attempt). Defer via
					// JSInterpretedUnit.addDefaultExportCallback() instead -
					// fires the MOMENT the module's own `export default`
					// actually runs (not merely once its whole body
					// finishes - a self-import binding is spec-readable as
					// soon as the underlying export slot itself is, and
					// GaltaJS's own body may keep running well past that
					// point, e.g. test262 module-self-import-async-
					// resolution-ticks.js reads this SAME binding again
					// after its own `export default await ...` but before
					// the body ends). A genuinely different, still-mid-load
					// module (not self) keeps throwing immediately - a
					// narrower, deliberately conservative scope matching the
					// concrete self-import pattern test262 actually
					// exercises here.
					// Deliberately NOT mainContext.getScriptUnit() - that
					// reads JSGlobalContext's own mutable scriptUnit field,
					// which RuntimeUtil.importModule() above (or ANY nested
					// module load reached before this point) can silently
					// overwrite with a DIFFERENT unit and never restores -
					// unit.getExecutionContext()==context is a reliable,
					// set-once, per-unit identity check instead (see that
					// field's own doc comment).
					if (module instanceof org.monflabs.galtajs.modules.JSInterpretedUnit unit && unit.getExecutionContext() == context) {
						unit.addDefaultExportCallback(() ->
							mainContext.getVariableMap(true).set(getDefaultImport(), module.getDefaultExport()));
					} else {
						throw new JSException(null, "Module {0} does not have a default export", getFrom());
					}
				} else {
					mainContext.getVariableMap(true).set(getDefaultImport(), module.getDefaultExport());
				}
			}
			if (StringUtil.isNotEmpty(getNamespace()) && !namespaceHoisted) {
				// getModuleNamespaceObject() (never null, cached per module
				// - see its own doc comment) - a self-/circular-import
				// reached before the source module has exported anything
				// yet must still get a real Module Namespace Exotic Object,
				// not a raw `null`.
				mainContext.getVariableMap(true).set(getNamespace(), module.getModuleNamespaceObject());
			}
			// Retry any item hoistBindings() couldn't resolve yet (see
			// unresolvedItems' own doc comment) - by now (this import
			// statement's own normal source position), whatever it needed
			// (e.g. a sibling `export * from '...'` in this SAME module)
			// has had a chance to actually run, so the ORIGINAL
			// (pre-live-binding) value-copy path is expected to succeed.
			if (unresolvedItems != null) {
				for (Item it : unresolvedItems) {
					String n = it.getAlias() == null ? it.getName() : it.getAlias();
					Object v = module.getExport(it.getName());
					mainContext.getVariableMap(true).set(n, v);
				}
			}
			return Signal.NONE;
		} catch (Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	// Called from ASTProgram's own hoist pass (before ANY module-body
	// statement executes, including this import's own evaluate() above -
	// see that method's doc comment for why namespace imports, and a
	// default import targeting a non-hoistable default export, are
	// deliberately NOT handled here): for each named item, and for a
	// default import whose target's default export is itself a hoisted
	// function declaration, replaces the hoist pass's own CONST TDZ
	// placeholder with a LIVE, read-only alias to the source module's real
	// variable cell (JSModule.getExportAccessor()/getLiveDefaultExportAccessor()),
	// so a self-import's binding is already correct (reflecting whatever
	// the target's own hoisting has established so far) even before this
	// import statement's own source position is reached. No-op when there
	// are no named items AND no eligible default import.
	public void hoistBindings(JSInterpretedRuntimeContext context) {
		List<Item> items = getItems();
		boolean hasDefault = StringUtil.isNotEmpty(getDefaultImport());
		boolean hasNamespace = StringUtil.isNotEmpty(getNamespace());
		if (items.isEmpty() && !hasDefault && !hasNamespace) {
			// No binding to hoist (`import {} from '...'`/bare `import
			// '...'`), but per spec InnerModuleEvaluation this module's
			// OWN [[RequestedModules]] must ALL be evaluated before its
			// own top-level code runs, regardless of import FORM - a
			// bindingless import is still a real dependency (test262
			// eval-rqstd-order.js's own `import {} from`/bare `import`
			// entries). Mirrors ASTExport.hoistDependencyEvaluation()'s
			// identical concern on the export-from side (including the
			// SAME safety precondition: ASTFunctionDecl.hoistValue()
			// already ran for this module's own hoistable functions by
			// the time this executes).
			try {
				resolveModule(context);
			} catch(Throwable ex) {
				throw fillInStackTrace(ex);
			}
			return;
		}
		try {
			InterpretedUnitRuntimeContext mainContext = (InterpretedUnitRuntimeContext) context.getMainContext();
			if (sourcePhase) {
				// `import source x from '...'`: the binding is the target's
				// Module Source Object - obtained from its descriptor alone,
				// never by loading the module (resolveModule() would
				// evaluate it; spec: a source phase import does not link or
				// evaluate its target).
				// (evaluate() does nothing more for a source-phase import)
				mainContext.getVariableMap(true).set(getDefaultImport(), RuntimeUtil.importModuleSource(context, getFrom()));
				return;
			}
			if (deferred) {
				// Spec (import-defer proposal): a deferred import whose
				// target (transitively) contains top-level await is NOT
				// actually deferred - it's evaluated eagerly, right here at
				// link time, exactly like an ordinary import (test262
				// import-defer/errors/get-self-while-evaluating-async/
				// main.js's own comment: "`./dep_FIXTURE.js` is not
				// deferred, because it contains top-level await"). The
				// target stays genuinely deferred either way -
				// resolveModule()/RuntimeUtil.importModule() must NEVER be
				// called on IT directly in this branch (previously did,
				// eagerly evaluating the WHOLE target's own body as a side
				// effect, defeating the entire point of `import defer` for
				// any target that merely transitively TOUCHES an async
				// module without needing TLA itself - test262 import-defer-
				// transitive-async-module.js/sync-dependency-of-deferred-
				// async-module.js/flattening-order.js). RuntimeUtil.
				// importDeferredNamespaceSync() does the gather-then-link
				// sequence (shared with transpileJavaStatement()'s
				// generated-code equivalent below).
				// (evaluate() does nothing more for a deferred import)
				mainContext.getVariableMap(true).set(getNamespace(), RuntimeUtil.importDeferredNamespaceSync(context, getFrom(), getAttributes()));
				return;
			}
			JSModule module = resolveModule(context);
			HoistState state = createHoistState(mainContext);
			int sz = items.size();
			for (int i = 0; i < sz; i++) {
				Item it = items.get(i);
				String n = it.getAlias() == null ? it.getName() : it.getAlias();
				VarAccessor sourceAccessor;
				try {
					sourceAccessor = module.getExportAccessor(it.getName());
				} catch (RuntimeException notYetAvailable) {
					// Defer to evaluate() - see unresolvedItems' own doc
					// comment. A genuinely unknown export still ends up
					// reported (evaluate()'s own retry throws the SAME
					// way), just at this import's own source line instead
					// of during hoisting - a reasonable, arguably more
					// intuitive, place for that error to surface anyway.
					if (state.unresolvedItems == null) {
						state.unresolvedItems = new java.util.ArrayList<>();
					}
					state.unresolvedItems.add(it);
					continue;
				}
				VariableMap localMap = mainContext.getVariableMap(true);
				localMap.delete(n);
				localMap.cache(VarAccessor.importBinding(n, sourceAccessor));
			}
			if (hasDefault) {
				VarAccessor sourceAccessor = module.getLiveDefaultExportAccessor();
				if (sourceAccessor != null) {
					VariableMap localMap = mainContext.getVariableMap(true);
					localMap.delete(getDefaultImport());
					localMap.cache(VarAccessor.importBinding(getDefaultImport(), sourceAccessor));
					state.defaultHoisted = true;
				}
			}
			if (hasNamespace) {
				// Unlike a default/named import, the namespace object
				// (module.getModuleNamespaceObject(), never null, cached
				// per module) is always immediately available regardless
				// of the source module's own execution progress - no
				// unresolved/retry case needed. Plain in-place value init
				// (matching evaluate()'s own pre-existing non-hoisted
				// assignment), not a live VarAccessor wrapper -
				// getModuleNamespaceObject() returns the SAME object
				// identity across calls, so there's nothing for a live
				// indirection to add here.
				mainContext.getVariableMap(true).set(getNamespace(), module.getModuleNamespaceObject());
				state.namespaceHoisted = true;
			}
		} catch (Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	@Override
	public JSType getReturnedType() {
		return JSType.UNKNOWN;
	}

	// Called ONLY from ASTProgram's own early-hoist pass, BEFORE this
	// import's normal source position - see that call site's own doc
	// comment and docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's
	// P4. A namespace/default/deferred-namespace target is always safely
	// resolvable this early (namespace/deferred are unconditionally
	// available; default uses the same P3 deferred-callback a self-import
	// needs regardless of hoisting) - transpileNonItemBindings() handles
	// those unconditionally. Named items (`import {a,b} from '...'`) are
	// ALSO attempted early (mirroring interpreted mode's hoistBindings()'s
	// own try/catch-based unresolvedItems retry - see its doc comment) via
	// transpileEarlyItemBindings(), but wrapped in a runtime try/catch:
	// a self-/circular import's target export might genuinely not exist
	// yet this early (test262 instn-named-bndng-*.js: `getExportAccessor()`
	// throws "does not export an entry" before the source's own matching
	// `export` has run) - silently falling through to a no-op leaves the
	// binding TDZ/uninitialized, exactly as before this early attempt
	// existed, and transpileItemBindings() at this statement's own normal
	// source position (called unconditionally regardless of whether the
	// early attempt succeeded) resolves it correctly there instead, same as
	// always. For a genuine CROSS-module import (test262 instn-iee-bndng-
	// var.js: code reading the imported name before the `import` statement's
	// own line, importing from a different file - never mid-load itself),
	// the early attempt succeeds, matching spec (module linking fully
	// evaluates a dependency before the importing module's own top-level
	// code runs, regardless of where the `import` statement sits in source).
	public void transpileEarlyImport(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(transpiledEarly) {
			return;
		}
		transpiledEarly = true;
		transpileNonItemBindings(jsContext, b);
		transpileEarlyItemBindings(jsContext, b);
	}

	// See transpileEarlyImport()'s own doc comment. Structurally identical
	// to transpileItemBindings() below (same importModule()/
	// getExportAccessor() shape, harmless to run twice - see that method's
	// own doc comment on why a second, later importModule() call is a
	// side-effect-free cache hit) but wrapped in a single runtime try/catch
	// spanning every item in this clause: if ANY item fails to resolve this
	// early, none of this clause's variables get a value here (they all
	// still get one, correctly, from transpileItemBindings() at this
	// statement's own normal position) - simpler and just as correct as
	// per-item try/catch, since a self-import failing to resolve item N
	// almost always means items 1..N-1 from the SAME source aren't reliably
	// resolvable yet either (the source's OWN export statements run in ITS
	// source order, so partial availability is the exception, not later
	// runtime state worth preserving here).
	private void transpileEarlyItemBindings(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		List<Item> items = getItems();
		if(isDeferred() || items.isEmpty()) {
			return;
		}
		String type = getAttributes()==null ? null : getAttributes().get("type");
		String attributesExpr = type==null ? "null" : StringFormat.format("java.util.Collections.singletonMap(\"type\",{0})", ASTLiteral.encodeString(type));
		String modName = StringFormat.format("_mod{0}", jsContext.generateUniqueId());
		b.println("try {");
		b.incIndent();
		b.println("JSModule {0} = {1};",
				modName,
				type==null
					? StringFormat.format("importModule({0},{1})", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()))
					: StringFormat.format("importAttributedModule({0},{1},{2})", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()), attributesExpr));
		for(Item it: items) {
			String n = it.getAlias()==null ? it.getName() : it.getAlias();
			VariableDef v = findVariable(n);
			b.println("{0} = {1}.getExportAccessor({2}).getValue();", v.getJavaVariableValue(), modName, ASTLiteral.encodeString(it.getName()));
		}
		b.decIndent();
		b.println("} catch(RuntimeException ex) {");
		b.incIndent();
		b.println("// Not yet resolvable this early - transpileItemBindings() at this statement's own normal source position retries.");
		b.decIndent();
		b.println("}");
		for(Item it: items) {
			String n = it.getAlias()==null ? it.getName() : it.getAlias();
			jsContext.createVariable(findVariable(n));
		}
	}

	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(!transpiledEarly) {
			transpiledEarly = true;
			transpileNonItemBindings(jsContext, b);
		}
		transpileItemBindings(jsContext, b);
	}

	// Deferred-namespace, plain/attributed module fetch, default import, and
	// namespace import - see transpileEarlyImport()'s own doc comment for
	// why named items are handled separately, in transpileItemBindings()
	// below, never here.
	private void transpileNonItemBindings(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		// "type" is the only import attribute GaltaJS gives runtime meaning
		// to (see ASTImpExp.getAttributes()'s own doc comment - other keys
		// are parsed/stored but otherwise inert) - known at TRANSPILE time
		// (a static `with {...}` clause, not an expression to evaluate, per
		// isDeferred()'s own doc comment on why this differs from
		// ASTImportCall's dynamic-import codegen), so bake it into a
		// literal Map rather than emitting a runtime branch.
		String type = getAttributes()==null ? null : getAttributes().get("type");
		String attributesExpr = type==null ? "null" : StringFormat.format("java.util.Collections.singletonMap(\"type\",{0})", ASTLiteral.encodeString(type));

		if (isDeferred()) {
			// `import defer * as ns from '...'` - always paired with a
			// namespace import, never a default import or named items (see
			// isDeferred()'s own doc comment: the grammar only allows this
			// combination). Mirrors ASTImport.hoistBindings()'s identical
			// interpreted-mode branch AND RuntimeUtil.
			// importDeferredNamespaceSync()'s own gather-then-link sequence -
			// inlined here (rather than calling that helper) so each
			// gathered-and-started async dependency that's still
			// `EVALUATING` by the time it's started can be added to
			// `_pendingModuleDeps`, the SAME list ordinary (non-deferred)
			// imports feed just above. Without this, a transpiled ROOT
			// module's own later top-level code (test262 top-level-await/
			// import-defer/evaluation-top-level-await/flattening-order/
			// main.js's own `assert.compareArray` right after its last
			// import) could run before these gathered TLA pieces have
			// actually settled - `import defer` itself is never eagerly
			// awaited (that's the entire point of deferring), but its
			// gathered ASYNC pieces genuinely are part of the same
			// evaluation-order contract ordinary imports already
			// participate in.
			VariableDef v = findVariable(getNamespace());
			// An attributed (`with { type: ... }`) deferred import targets a
			// SYNTHETIC module - no requested modules, never asynchronous - so
			// emit no gather at all for it. Beyond being pointless work, the
			// gather inspects an uncached dependency by parsing its raw
			// content as JavaScript, which is wrong for a JSON/text/bytes
			// payload; see RuntimeUtil.importDeferredNamespaceSync(), whose
			// interpreted-mode branch skips it for the same reason.
			if(type==null) {
				String depsVar = StringFormat.format("_asyncDeps{0}", jsContext.generateUniqueId());
				String depVar = StringFormat.format("_asyncDep{0}", jsContext.generateUniqueId());
				b.println("java.util.List<{0}> {1} = gatherAndStartAsyncDependencies({2},{3});",
						JSInterpretedUnit.class.getName(), depsVar, JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()));
				b.println("for({0} {1} : {2}) {", JSInterpretedUnit.class.getName(), depVar, depsVar);
				b.incIndent();
				b.println("if({0}.getModuleStatus()=={1}.ModuleStatus.EVALUATING) { _pendingModuleDeps.add({0}); }", depVar, JSInterpretedUnit.class.getName());
				b.decIndent();
				b.println("}");
			}
			b.println("{0} = {1}.getGlobalContext().importDeferredNamespace({1}.getMainContext(),{2},{3});",
					v.getJavaVariableValue(),
					JSTranspiler.MAIN_CONTEXT,
					ASTLiteral.encodeString(getFrom()),
					attributesExpr);
			jsContext.createVariable(v);
			return;
		}

		if (isSourcePhase()) {
			// `import source x from '...'` - mirrors hoistBindings()'s
			// interpreted-mode branch: the Module Source Object, from the
			// descriptor alone, never importModule().
			VariableDef v = findVariable(getDefaultImport());
			b.println("{0} = {1}.getGlobalContext().importModuleSource({1}.getMainContext(),{2});",
					v.getJavaVariableValue(),
					JSTranspiler.MAIN_CONTEXT,
					ASTLiteral.encodeString(getFrom()));
			jsContext.createVariable(v);
			return;
		}

		String modName = StringFormat.format("_mod{0}", jsContext.generateUniqueId());

		// Mirrors ASTImpExp.resolveModule()'s identical dispatch (shared by
		// every OTHER importModule()-vs-importAttributedModule() call site) -
		// routes through importAttributedModule() whenever a "type"
		// attribute is present, matching spec's distinct-Module-Record-per-
		// attribute-set model.
		b.println("JSModule {0} = {1};",
				modName,
				type==null
					? StringFormat.format("importModule({0},{1})", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()))
					: StringFormat.format("importAttributedModule({0},{1},{2})", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()), attributesExpr));

		// A genuinely DIFFERENT module (never `this` - a self-import
		// resolves to a JSTranspiledUnit subclass instance, never a
		// JSInterpretedUnit, so it can't match here) that's still
		// mid-evaluation (an async dependency not yet settled) is recorded
		// into the enclosing ASTProgram's own `_pendingModuleDeps` list -
		// see that list's own doc comment (ASTProgram.transpileJavaStatement())
		// for why this can't just defer-and-return right here: a LATER
		// sibling import in this same module still needs its own
		// importModule() triggered regardless (test262 top-level-await/
		// async-module-does-not-block-sibling-modules.js). Applies to EVERY
		// import form (bare, default, namespace) since a bare import with
		// no binding at all can still leave a caller needing the dependency
		// fully settled before this module's own body continues (test262
		// top-level-await/dfs-invariant.js, pending-async-dep-from-cycle.js).
		b.println("if({0} instanceof {1} && (({1}){0}).getModuleStatus()=={1}.ModuleStatus.EVALUATING) {",
				modName, JSInterpretedUnit.class.getName());
		b.incIndent();
		b.println("_pendingModuleDeps.add(({0}){1});", JSInterpretedUnit.class.getName(), modName);
		b.decIndent();
		b.println("}");

		if (StringUtil.isNotEmpty(getDefaultImport())) {
			VariableDef v = findVariable(getDefaultImport());
			// A genuine SELF-import (modName resolving to THIS SAME module,
			// currently executing, reaching back to its OWN not-yet-run
			// `export default <expr>;`/`export default class C{}`) needs a
			// deferred callback instead of reading getDefaultExport()
			// synchronously and observing nothing - mirrors ASTImport.
			// evaluate()'s identical interpreted-mode branch (see its own
			// doc comment) and JSTranspiledUnit.addDefaultExportCallback()'s.
			// `modName==this` is the transpiled equivalent of that method's
			// `unit.getExecutionContext()==context` identity check - `this`
			// inside a module's own generated _runValue() IS the currently-
			// executing module instance itself. A genuinely different,
			// still-mid-load module (not self) keeps throwing immediately,
			// same deliberately narrow scope as interpreted mode.
			b.println("if({0}.hasDefaultExport()) {", modName);
			b.incIndent();
			b.println("{0} = {1}.getDefaultExport();", v.getJavaVariableValue(), modName);
			b.decIndent();
			b.println("} else if({0}==this) {", modName);
			b.incIndent();
			b.println("(({0}){1}).addDefaultExportCallback(() -> {2} = {1}.getDefaultExport());",
					org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit.class.getName(), modName, v.getJavaVariableValue());
			b.decIndent();
			// A genuinely DIFFERENT module that's still mid-evaluation (an
			// async dependency not yet settled, e.g. test262 top-level-await/
			// module-import-resolution.js) - not a self-import (handled just
			// above), not yet ready, but already recorded in
			// `_pendingModuleDeps` by the generic check just above (which
			// runs for every import form, not just default imports) - do
			// nothing here; the whole module's _runValue() is about to be
			// deferred and restarted from scratch once every pending
			// dependency settles (see that list's own doc comment), so this
			// variable's true value will be assigned correctly by the
			// `hasDefaultExport()` branch above on that later, real run.
			b.println("} else if({0} instanceof {1} && (({1}){0}).getModuleStatus()=={1}.ModuleStatus.EVALUATING) {",
					modName, JSInterpretedUnit.class.getName());
			b.println("} else {");
			b.incIndent();
			b.println("throw new JSException(null,{0});", ASTLiteral.encodeString("Module "+getFrom()+" does not have a default export"));
			b.decIndent();
			b.println("}");
			jsContext.createVariable(v);
		}
		if (StringUtil.isNotEmpty(getNamespace())) {
			VariableDef v = findVariable(getNamespace());
			// `new ImportNamespace(...)` referenced a Java class that
			// doesn't exist - a javac "cannot find symbol" compile error
			// for EVERY `import * as ns` (test262 language/module-code/
			// export-expname-binding-string.js). RuntimeUtil.
			// buildImportNamespaceObject(env,module) is the real, already-
			// used-elsewhere (ASTImportCall's dynamic import()) namespace-
			// object builder - and more spec-correct too, since it also
			// exposes the module's own "default" export under the
			// "default" key, unlike the old code's `getNamedExports()`
			// alone.
			b.println("{0} = buildImportNamespaceObject({1},{2});", v.getJavaVariableValue(), JSTranspiler.MAIN_ENVIRONMENT, modName);
			jsContext.createVariable(v);
		}
	}

	// Pure compile-time metadata registration for named items AND the
	// default import (if present) - marks each own VariableDef as a live
	// import binding (see VariableDef.isLiveImportBinding()'s own doc
	// comment) as EARLY as possible in the compile-time AST walk, called
	// from ASTProgram's own very first pre-pass, BEFORE any body statement
	// (including a hoisted function declared textually BEFORE this import,
	// but which references the imported name in its own body - variable
	// slot allocation itself already happened during the separate, earlier
	// init() pass, so findVariable() resolves correctly here regardless).
	// Emits NO Java code at all - ASTIdentifier's read-accessor consults
	// this marker purely at COMPILE TIME to decide which codegen shape to
	// emit; the actual runtime importModule()/getExportAccessor() calls
	// only ever come from transpileItemBindings()/transpileNonItemBindings()
	// below (at their own normal position, UNCHANGED - still needed for the
	// self-referencing addDefaultExportCallback() deferred-callback case,
	// which VariableDef.getLiveImportReadExpression()'s own "default"
	// branch falls back to the plain slot for instead of duplicating) and
	// the live-read expression ASTIdentifier itself emits (see
	// VariableDef.getLiveImportReadExpression()). Without this being a
	// SEPARATE, earlier pass, a reference compiled before those ran
	// (anything visited earlier in the walk) would silently fall back to
	// the old, non-live codegen instead. A namespace import (`import * as
	// ns`) is deliberately NOT included here - its object is already
	// dynamically computed per-property-access (buildImportNamespaceObject()/
	// ModuleNamespaceObject), never a value snapshot, so it has no
	// equivalent problem to fix.
	public void markLiveImportBindings() {
		String type = getAttributes()==null ? null : getAttributes().get("type");
		// A source-phase binding is a plain value (the Module Source
		// Object), not a live binding into the module's exports.
		if(!isDeferred() && !isSourcePhase() && StringUtil.isNotEmpty(getDefaultImport())) {
			VariableDef dv = findVariable(getDefaultImport());
			if(dv!=null) {
				dv.setLiveImportBinding(getFrom(), "default", type);
			}
		}
		List<Item> items = getItems();
		if(isDeferred() || items.isEmpty()) {
			return;
		}
		for(Item it: items) {
			String n = it.getAlias() == null ? it.getName() : it.getAlias();
			VariableDef v = findVariable(n);
			if(v!=null) {
				v.setLiveImportBinding(getFrom(), it.getName(), type);
			}
		}
	}

	// Named items (`import {a,b} from '...'`) - ALWAYS emitted at this
	// import's own normal source position, whether or not
	// transpileNonItemBindings() already ran early for the same statement -
	// see transpileEarlyImport()'s own doc comment for why these are never
	// hoisted. Re-fetches the module fresh (importModule() is cached by the
	// module resolver - a harmless, side-effect-free re-lookup, never a
	// second load/evaluation) since the early call's own `modName` Java
	// local (if any) is a different method-local variable, out of scope
	// here.
	private void transpileItemBindings(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		List<Item> items = getItems();
		if(isDeferred() || items.isEmpty()) {
			return;
		}
		String type = getAttributes()==null ? null : getAttributes().get("type");
		String attributesExpr = type==null ? "null" : StringFormat.format("java.util.Collections.singletonMap(\"type\",{0})", ASTLiteral.encodeString(type));
		String modName = StringFormat.format("_mod{0}", jsContext.generateUniqueId());
		b.println("JSModule {0} = {1};",
				modName,
				type==null
					? StringFormat.format("importModule({0},{1})", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()))
					: StringFormat.format("importAttributedModule({0},{1},{2})", JSTranspiler.MAIN_CONTEXT, ASTLiteral.encodeString(getFrom()), attributesExpr));
		int sz = items.size();
		for (int i = 0; i < sz; i++) {
			Item it = items.get(i);
			String n = it.getAlias() == null ? it.getName() : it.getAlias();
			VariableDef v = findVariable(n);
			// getExportAccessor(), not plain getExport() - the latter
			// only ever looks at namedExports, but a literal "default"
			// item (`import {default as x} from '...'`) must resolve
			// through the module's own default-export slot instead
			// (AbstractModule.getExportAccessor()'s own "default"
			// special case) - mirrors ASTImport.hoistBindings()'s
			// identical interpreted-mode call (test262 json-idempotency.js:
			// a JSON synthetic module's default value was otherwise
			// unreachable this way, throwing "does not export an entry
			// default"). This snapshot write is now a harmless, UNUSED
			// fallback for anything that reads this slot directly via
			// getJavaVariableValue() (e.g. JSVarRef-based live-export
			// registration, not applicable to a plain import binding - see
			// VariableDef.isLiveImportBinding()'s own doc comment, set by
			// markLiveImportBindings() above) - actual reads of `n` go
			// through ASTIdentifier's own read-accessor instead, which
			// re-resolves live, since a named import must observe a later
			// reassignment of the exported variable in the SOURCE module
			// (test262 eval-gtbndng-indirect-update*.js/instn-named-iee-
			// cycle.js - a one-time snapshot never did).
			b.println("{0} = {1}.getExportAccessor({2}).getValue();", v.getJavaVariableValue(), modName, ASTLiteral.encodeString(it.getName()));
			jsContext.createVariable(v);
		}
	}
    
    @Override
	public void decompileStatement(JavaBuilder b) {
		List<Item> items = getItems();
		boolean from = false;
    	b.append("import ");
    	if (sourcePhase) {
    		b.append("source ");
    	} else if (deferred) {
    		b.append("defer ");
    	}
		if (StringUtil.isNotEmpty(getDefaultImport())) {
			from = true;
	    	b.append(getDefaultImport());
		}
		if (StringUtil.isNotEmpty(getNamespace())) {
			from = true;
			if (StringUtil.isNotEmpty(getDefaultImport())) {
		    	b.append(", ");
			}
	    	b.append("* as {0}", getNamespace());
		} else if(!items.isEmpty()) {
			from = true;
			if (StringUtil.isNotEmpty(getDefaultImport())) {
		    	b.append(", ");
			}
	    	b.append("{");
	    	for(int i=0; i<items.size(); i++) {
	    		Item it = items.get(i);
	    		if(i>0) {
	    	    	b.append(",");
	    		}
	    		
    	    	b.append(it.getName());
    	    	if(StringUtil.isNotEmpty(it.getAlias())) {
        	    	b.append(" as ");
        	    	b.append(it.getAlias());
        	    }
    	    	
	    	}
	    	b.append("}");
		}
		if(from) {
			b.append(" from ");
		}
    	b.append(ASTLiteral.encodeString(getFrom()));
    	if(getAttributes()!=null && !getAttributes().isEmpty()) {
    		b.append(" with { ");
    		boolean first = true;
    		for(java.util.Map.Entry<String,String> e: getAttributes().entrySet()) {
    			if(!first) {
    				b.append(", ");
    			}
    			first = false;
    			b.append(ASTLiteral.encodeString(e.getKey()));
    			b.append(": ");
    			b.append(ASTLiteral.encodeString(e.getValue()));
    		}
    		b.append(" }");
    	}
    	b.append(";");
	}
}