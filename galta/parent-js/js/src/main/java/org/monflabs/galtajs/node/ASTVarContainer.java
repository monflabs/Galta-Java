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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionTranspiler;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.HeadClosureSnapshotHolder;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledFunctionRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorConstantPoolContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorMainContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Base class for a node containing variable declarations.
 */
public abstract class ASTVarContainer extends ASTNode implements IContextBlockContainer {
	
	public class VariableDefContainer implements Iterable<VariableDef> {
		private boolean global;
		private String transpilerId;
		private LinkedHashMap<String, VariableDef> variables = new LinkedHashMap<String, ASTVarContainer.VariableDef>();
		VariableDefContainer(boolean global) {
			this.global = global;
		}
		public boolean isGlobal( ) {
			return global;
		}
		public int size() {
			return variables.size();
		}
		public VariableDef addVariable(String varName) {
			VariableDef v = new VariableDef(this, varName, variables.size());
			variables.put(varName,v);
			return v;
		}
		public String getJavaVariable() {
			return transpilerId;
		}
		// Lets ASTFor temporarily redirect every variable reference generated
		// for this container's body (an ASTIdentifier deep inside it
		// resolves its Java array name via getJavaVariable() at the moment
		// it's transpiled) to a fresh per-iteration copy of the array,
		// without needing to touch how identifiers themselves resolve.
		public void setJavaVariable(String id) {
			this.transpilerId = id;
		}
		public VariableDef get(String varName) {
			return variables.get(varName);
		}
		@Override
		public Iterator<VariableDef> iterator() {
			return variables.values().iterator();
		}
		public String[] names() {
			return variables.keySet().toArray(new String[variables.size()]);
		}
		// Parallel to names() (same LinkedHashMap iteration order) - each
		// entry's real VAR_TYPE.name(), for JSTranspiledRuntimeContext.
		// initGlobalVariables()'s own var/function-vs-lexical distinction.
		public String[] types() {
			String[] result = new String[variables.size()];
			int i=0;
			for(VariableDef v: variables.values()) {
				result[i++] = v.getVarType().name();
			}
			return result;
		}
		// Parallel to names()/types() - each entry's isAnnexBBlockHoisted(),
		// for JSTranspiledRuntimeContext.initGlobalVariables()'s own
		// CreateGlobalVarBinding-vs-CreateGlobalFunctionBinding distinction
		// (see that method's FUNCTION branch doc): unlike an ordinary
		// top-level function declaration, an Annex-B block-hoisted one's
		// hoist-time synchronization step must never touch an already-
		// existing global property's descriptor - test262 annexB/language/
		// global-code/*-existing-non-enumerable-global-init.js.
		public boolean[] annexBBlockHoisted() {
			boolean[] result = new boolean[variables.size()];
			int i=0;
			for(VariableDef v: variables.values()) {
				result[i++] = v.isAnnexBBlockHoisted();
			}
			return result;
		}
	}

	public static class VariableDef {
		
		private String name;
		private VAR_TYPE varType;
		private JSType jsType;
		private boolean transpilerDeclared;
		private boolean used;
		// True only for a DESTRUCTURING catch clause parameter's own bound
		// names (see ASTCatch.init()) - per spec B.3.5, unlike a plain
		// `catch(e)` BindingIdentifier (which Annex B.3.3's function-hoist
		// may freely override, hence the ordinary VAR_TYPE.PREDECLARED/
		// canBeOverriden() path), a DESTRUCTURING catch parameter's bound
		// names conflicting with a hoisted FunctionDeclaration is ALWAYS an
		// early error - this flag lets ASTFunctionDecl's Annex B conflict
		// check treat this ONE case as non-overridable without changing
		// VAR_TYPE.PREDECLARED's normal (correct, overridable) semantics
		// for every other use.
		private boolean annexBHoistBlocked;
		// True only for a FunctionDeclaration that reached this (root)
		// container via Annex B.3.3's sloppy-mode hoist (ASTFunctionDecl.
		// createFunction()'s hoistedToRoot + isAnnexBHoistCandidate - either
		// genuinely block-nested, or the bare-if-clause-body shape, which has
		// no block of its own), as opposed to a function declared directly
		// at this container's own top level. Distinguishes the two cases for
		// ASTProgram's hoisting pre-pass: a genuine top-level function
		// declaration's CreateGlobalFunctionBinding must unconditionally
		// redefine a pre-existing configurable global property's descriptor,
		// but Annex B's block-hoisted synchronization must NOT touch an
		// existing property's descriptor at all (only conditionally
		// initialize/update its value at block-exit time, handled separately
		// in ASTFunctionDecl.evaluate()) - test262
		// annexB/language/{eval-code,global-code}/*-existing-*-global-init.js
		// regressed when this distinction wasn't made.
		private boolean annexBBlockHoisted;

		// Only meaningful for VAR_TYPE.FUNCTION_SELF: whether the OWNING
		// function expression is itself strict-mode (its own
		// isGenuinelyStrictMode(), captured at declaration time since that's
		// statically fixed - never re-derived per-call). Assignment to a
		// named function expression's own self-reference binding is
		// spec-immutable but enforced at ASSIGNMENT time, not statically: a
		// silent no-op in sloppy code, a TypeError in strict code - see
		// ASTIdentifier.transpileJavaAssignment()'s EQUALS case and
		// ASTIdentifier.evaluateAssign()'s interpreted-mode equivalent.
		private boolean functionSelfStrict;

		// True when this slot is statically guaranteed to be OUT of its
		// Temporal Dead Zone at every read - so ASTIdentifier can skip the
		// checkTDZ(...) guard it would otherwise emit purely from the
		// VAR_TYPE. Currently only set for a simple-parameter-list parameter
		// (VAR_TYPE.PREDECLARED): such a parameter is unconditionally bound
		// from its argument in the function prologue, before any body
		// statement runs, and a simple list has no default/destructuring
		// expressions that could observe an earlier param mid-binding (the
		// same invariant that lets transpileParameterBindingPrologue skip the
		// param TDZ pre-fill for a simple list). let/const/using bindings do
		// NOT get this blanket flag - proving those definitely-assigned needs
		// per-read flow analysis (see declSite / ASTIdentifier's linear-flow
		// TDZ check), not a single per-variable boolean.
		private boolean tdzExempt;

		// The BindingIdentifier node that DECLARES this binding (set only for
		// a single-identifier let/const/using declaration, via
		// ASTIdentifier.declareVariables). Lets a later read prove - purely
		// from AST structure - that it's textually after, and on a straight-
		// line path from, this declaration's initializer, so its checkTDZ
		// guard is provably dead (see ASTIdentifier.isTdzSafeLinearRead). Null
		// for destructuring patterns, parameters, var/function, etc. - those
		// keep their normal type-based guard.
		private ASTNode declSite;

		// Set ONLY by ASTImport.transpileItemBindings() for a named import
		// (`import {x} from '...'`) - the module specifier this binding's
		// value comes from. When non-null, ASTIdentifier's read-accessor
		// codegen emits a LIVE re-resolution (importModule(...).
		// getExportAccessor(importExportName).getValue()) instead of this
		// slot's own getJavaVariableValue() - a named import is a live
		// binding per spec (a later reassignment of the exported variable in
		// the SOURCE module must be observed here), but the array slot this
		// class otherwise always represents is a ONE-TIME snapshot, written
		// once at import-hoist time and never updated again. See
		// ASTImport.transpileItemBindings()'s own doc comment for why the
		// slot itself is still allocated/written (kept as a harmless,
		// unused fallback - JSVarRef/live-export-registration and any other
		// array-slot-based machinery that isn't ASTIdentifier's own read
		// path still sees the ORIGINAL, non-live value, an accepted, narrow
		// limitation - a plain `import {x}` binding is never itself
		// re-exported through the array-slot path, see ASTExport's own
		// named-`from`-export handling, which resolves straight through the
		// source module instead of reading this slot at all).
		private String importModuleRequest;
		private String importExportName;
		private String importAttributesType;

		public boolean isLiveImportBinding() {
			return importModuleRequest!=null;
		}
		public String getImportModuleRequest() {
			return importModuleRequest;
		}
		public String getImportExportName() {
			return importExportName;
		}
		public String getImportAttributesType() {
			return importAttributesType;
		}
		public void setLiveImportBinding(String moduleRequest, String exportName, String attributesType) {
			this.importModuleRequest = moduleRequest;
			this.importExportName = exportName;
			this.importAttributesType = attributesType;
		}

		// The live re-resolution expression ASTIdentifier's own read-accessor
		// codegen substitutes for getJavaVariableValue() when
		// isLiveImportBinding() is true - see that field's own doc comment.
		// Self-contained (no shared local-variable state, unlike
		// ASTImport.transpileItemBindings()'s own `_mod{N}` local, which is
		// out of scope from any OTHER method/nested-function-class) so it's
		// safe to emit at ANY read site, however deeply nested in closures -
		// mirrors ASTImportMeta.transpileJavaExpression()'s identical
		// "reference _ctx directly, no cached local" pattern.
		// importModule()/importAttributedModule() are cache-hit-cheap after
		// the first real load (see ASTImpExp.resolveModule()'s own doc
		// comment), so re-resolving the module on every read is correct, not
		// just tolerable.
		public String getLiveImportReadExpression() {
			String moduleExpr = importAttributesType==null
					? org.monflabs.util.StringFormat.format("importModule({0},{1})", JSTranspiler.MAIN_CONTEXT, org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(importModuleRequest))
					: org.monflabs.util.StringFormat.format("importAttributedModule({0},{1},java.util.Collections.singletonMap(\"type\",{2}))",
							JSTranspiler.MAIN_CONTEXT, org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(importModuleRequest), org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(importAttributesType));
			if("default".equals(importExportName)) {
				// Unlike a plain named local export (always slot/accessor-
				// backed via registerLiveExport()'s JSVarRef, so
				// getExportAccessor() correctly reflects TDZ even before the
				// module is "ready"), a default export is tracked as a plain
				// boolean+value pair (hasDefaultExport()/getDefaultExport())
				// with no such backing until hasDefaultExport() is genuinely
				// true. Before that, calling getExportAccessor("default")
				// would throw ("does not export named entries") instead of
				// reflecting the pending value - so fall back to the PLAIN
				// ARRAY SLOT instead, which ASTImport.
				// transpileNonItemBindings()'s own addDefaultExportCallback()
				// keeps correctly updated for exactly this case (a circular
				// self-import reading its own not-yet-run `export default` -
				// test262 instn-named-bndng-dflt-*.js, regressed and reverted
				// once already by calling getExportAccessor("default")
				// unconditionally here - see KnownGaps.md's own account).
				String plainSlot = container.getJavaVariable()+"["+varIndex+"]"+"/*"+name+"*/";
				return org.monflabs.util.StringFormat.format("({0}.hasDefaultExport() ? checkTDZ({1}.getExportAccessor(\"default\").getValue(),{2}) : {3})",
						moduleExpr, moduleExpr, org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(name), plainSlot);
			}
			return org.monflabs.util.StringFormat.format("checkTDZ({0}.getExportAccessor({1}).getValue(),{2})",
					moduleExpr, org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(importExportName), org.monflabs.galtajs.node.literal.ASTLiteral.encodeString(name));
		}

		// Container references
		private VariableDefContainer container;
		private int varIndex;
		
		private VariableDef(VariableDefContainer container, String varName, int varIndex) {
			this.container = container;
			this.name = varName;
			this.varIndex = varIndex;
		}
		
		@Override
		public String toString() {
			return name + ", " + varType;
		}
		public VariableDefContainer getContainer( ) {
			return container;
		}
		public void update(VAR_TYPE varType, JSType jsType) {
			this.varType = varType;
			this.jsType = jsType;
		}
		public boolean isTranspilerDeclared() {
			return transpilerDeclared;
		}
		public void setTranspilerDeclared(boolean transpilerDeclared) {
			this.transpilerDeclared = transpilerDeclared;
		}
		// True once any ASTIdentifier read/write anywhere in the enclosing
		// function body resolves to this VariableDef (see ASTIdentifier.init).
		// The transpiler uses this to elide initArg(...) emissions for
		// parameters that are never referenced.
		public boolean isUsed() {
			return used;
		}
		public void setUsed(boolean used) {
			this.used = used;
		}
		// See tdzExempt's own field comment.
		public boolean isTdzExempt() {
			return tdzExempt;
		}
		public void setTdzExempt(boolean tdzExempt) {
			this.tdzExempt = tdzExempt;
		}
		// See declSite's own field comment.
		public ASTNode getDeclSite() {
			return declSite;
		}
		public void setDeclSite(ASTNode declSite) {
			this.declSite = declSite;
		}
		public String getName() {
			return name;
		}
		public String getJavaVariableArray() {
			return container.transpilerId;
		}
		public int getJavaVariableIndex() {
			return varIndex;
		}
		public void setJavaVariableIndex(int index) {
			this.varIndex = index;
		}
		public String getJavaVariable() {
			return container.getJavaVariable();
		}
		public String getJavaVariableValue() {
			return container.getJavaVariable()+"["+varIndex+"]"+"/*"+name+"*/";
		}
		
		public VAR_TYPE getVarType() {
			return varType;
		}
		public boolean isAnnexBHoistBlocked() {
			return annexBHoistBlocked;
		}
		public void setAnnexBHoistBlocked(boolean annexBHoistBlocked) {
			this.annexBHoistBlocked = annexBHoistBlocked;
		}
		public boolean isAnnexBBlockHoisted() {
			return annexBBlockHoisted;
		}
		public void setAnnexBBlockHoisted(boolean annexBBlockHoisted) {
			this.annexBBlockHoisted = annexBBlockHoisted;
		}
		public JSType getJsType() {
			return jsType;
		}
		public boolean isFunctionSelfStrict() {
			return functionSelfStrict;
		}
		public void setFunctionSelfStrict(boolean functionSelfStrict) {
			this.functionSelfStrict = functionSelfStrict;
		}
	}


	private List<ASTFunction> functions;
	private String fctContainerClassName; 
	
	private VariableDefContainer variables;

	public ASTVarContainer(Token t) {
		super(t);
	}
	
	@Override
	public String getNodeString() {
		if(variables!=null) {
			StringBuilder b = new StringBuilder();
			for(VariableDef v: variables) {
				if(b.length()>0) {
					b.append(", ");
				}
				b.append(v.getVarType().toString().toLowerCase());
				b.append(" ");
				b.append(v.getName());
			}
			return b.toString();
		}
		return "";
	}
	
	
	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);
	}


	public VariableDefContainer getVariables() {
		return variables;
	}
	
	public boolean hasDeclaredVariables() {
		return variables!=null;
	}

	public boolean hasFunctionDeclarations() {
		return functions!=null;
	}

	// Overridden by ASTFor/ASTForOf/ASTForIn (the only containers whose own
	// variable array can be mutated, via the per-iteration copy-back
	// trailer, AFTER a closure hoisted DIRECTLY to it - not to its body -
	// has already been declared/instantiated - see needsPerIterationBinding()
	// in each of those classes for the full mechanism). A closure hoisted
	// to such a container still generates a body that reads the container's
	// array by its ORIGINAL name (baked in once, see
	// transpilerDeclareFunctionClasses below), so to stay correct across a
	// LATER mutation of that same array, its generated class instead takes
	// a defensive COPY of the array - made fresh at ITS OWN instantiation
	// site, see ASTFunction.transpileJavaExpression() - as an extra
	// constructor argument, stored in a field of the SAME name as the
	// container's own array variable. That field then SHADOWS the captured
	// outer array for every already-generated identifier read inside the
	// closure's body (plain Java scoping - an instance field shadows a
	// captured enclosing-scope local of the same name), with no change to
	// identifier codegen itself.
	public boolean needsHeadClosureSnapshot() {
		return false;
	}

	public List<ASTFunction> getFunctionDeclarations() {
		return functions;
	}

	@Override
	public VariableDef getOwnVariable(String name) {
		if(variables!=null) {
			return variables.get(name);
		}
		return null;
	}

	@Override
	public VariableDef addVarDeclaration(String varName, VAR_TYPE varType, JSType jsType) {
		return _addVarDeclaration(varName, varType, jsType);
	}
	
	private static boolean isVarOrFunction(VAR_TYPE t) {
		return t==VAR_TYPE.VAR || t==VAR_TYPE.AUTO || t==VAR_TYPE.FUNCTION;
	}

	protected VariableDef _addVarDeclaration(String varName, VAR_TYPE varType, JSType jsType) {
		if(variables==null) {
			// Must be in order!
			variables = new VariableDefContainer(this instanceof ASTProgram);
		}
		// Check if the variable is already defined in scope
		// Should be an option
		VariableDef v = variables.get(varName);
		if(v!=null) {
			// A SYSTEM binding (the function's own implicit "arguments" object,
			// or CommonJS's "module"/"exports") is a placeholder, not a real
			// user declaration - any actual let/const/function/class/var
			// declaration of the same name in the same scope always cleanly
			// supersedes it, never collides. Matches spec: the "arguments"
			// object is simply never created when the function body has its
			// own top-level declaration of that name (confirmed via test262's
			// language/eval-code/direct/*-fn-body-cntns-arguments-lex-bind-*.js,
			// which requires `function f(p = ...) { let arguments; }` to parse
			// validly). A later SECOND redeclaration of the same name is still
			// correctly rejected, since v's type is no longer SYSTEM once this
			// first one has updated it below.
			//
			// A FUNCTION_SELF binding (a named function EXPRESSION's own
			// self-reference, `var f = function myself() {...}`) is likewise
			// exempted for the same underlying reason, despite living in the
			// SAME VariableDefContainer here: per spec it's actually a
			// DIFFERENT (outer) environment from the function body's own
			// var/let/const declarations, so a body-level declaration of the
			// same name never really collides with it - it simply shadows.
			// GaltaJS doesn't model that as a genuinely separate environment,
			// but this exemption reproduces the same observable outcome for
			// the static redeclaration check specifically (confirmed via
			// test262 expressions/call/scope-lex-open.js: `var f = function
			// f() { let f = 1; }` must parse and let the inner `let`
			// shadow, not throw "already defined"). NOTE: this does NOT
			// fully fix the sibling scope-var-open.js case - a `var`-typed
			// override still needs its VALUE reset to undefined at runtime
			// (a distinct, deeper gap in bindParametersAndVars' hoisting
			// pass, which currently treats the pre-existing self-reference
			// entry as "already initialized" and skips it - left as-is).
			// Module-only tightening: per spec, a MODULE's top-level function
			// declarations are LexicallyDeclaredNames (unlike a script, where
			// they're VarDeclaredNames) - "It is a Syntax Error if the
			// LexicallyDeclaredNames of ModuleItemList contains any duplicate
			// entries, or if any element also occurs in VarDeclaredNames."
			// VAR_TYPE.FUNCTION.canBeOverriden()/canOverride() are
			// deliberately `true` (correct for a SCRIPT, where redeclaring a
			// function - or a function colliding with a plain `var` - is a
			// benign override, not an error), so the general check just
			// below would silently let this through everywhere, including a
			// module's own top level. Gated to `this instanceof ASTProgram`
			// (top-level only - a function/block NESTED inside a module body
			// still uses ordinary, non-module scoping rules) and to a real
			// FUNCTION-involving collision specifically (`var x; var x;` -
			// two plain vars - stays legal everywhere, including modules;
			// only a FUNCTION declaration colliding with another FUNCTION or
			// a VAR/AUTO is newly rejected here).
			if(this instanceof ASTProgram p && p.isModule()
					&& (varType==VAR_TYPE.FUNCTION || v.getVarType()==VAR_TYPE.FUNCTION)
					&& isVarOrFunction(varType) && isVarOrFunction(v.getVarType())) {
				throw new JSParseException(null,this,"Identifier '{0}' has already been declared", varName);
			}
			if(v.getVarType()!=VAR_TYPE.SYSTEM && v.getVarType()!=VAR_TYPE.FUNCTION_SELF
					&& (!varType.canBeOverriden() || !v.getVarType().canBeOverriden())) {
				throw new JSParseException(null,this,"Variable or function {0} is already defined in scope", varName);
			}
			// We cannpt set a value for PREDECLARED (override with undefined)
			if(v.varType!=VAR_TYPE.PREDECLARED) {
				v.update(varType, jsType);
			}
		} else {
			v = variables.addVariable(varName);
			v.update(varType, jsType);
		}
		return v;
	}
	
	@Override
	public int addFunctionDeclaration(ASTFunction function) {
		if(functions==null) {
			functions = new ArrayList<ASTFunction>();
		}
		functions.add(function);
		return functions.size()-1;
	}

	
	//
	// Optimizer
	//
	
	@Override
	public JSOptimizerContext createOptimizedContext(JSOptimizerContext context) {
		JSOptimizerContext varContext = new JSOptimizerContext.ChildOptimizerContext(context,this);
		if(variables!=null) {
			for(VariableDef v: variables) {
				varContext.getVariables().put(v.getName(),new JSOptimizerContext.ContextVariable(v));
			}
		}
		return varContext;
	}


	//
	// Transpiler helpers
	//
	// Emits ONLY this container's own array allocation ("final Object[]
	// x=new Object[N];"), assigning transpilerId if needed - the part of
	// transpilerDeclareStatement() that a loop can safely hoist to BEFORE
	// entering the loop when it's proven this container's own declared
	// variables are never captured across iterations (see ASTFor's own
	// hoisting logic for the safety argument), so the array is allocated
	// once and reused for every iteration instead of once per iteration.
	// A subsequent transpilerDeclareStatement() call on the SAME container
	// (as ASTBlock.transpileJavaStatementNoBrace normally makes, once per
	// iteration when reached inside the loop) sees transpilerId already
	// set and skips re-declaring the array, while still redoing everything
	// else that genuinely needs to happen on every entry (createVariable
	// bookkeeping, initVars/TDZ-seeding, function-class declarations) - a
	// fresh block-scope "instance" per iteration still needs its slots
	// reset to TDZ/undefined every time, per spec; only the underlying
	// Java array object identity is what's safe to keep across iterations
	// here, exactly as nothing observes the difference when no closure
	// exists to notice it.
	public void transpilerDeclareArrayOnly(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(variables!=null && !variables.variables.isEmpty() && variables.transpilerId==null) {
			variables.transpilerId = jsContext.generateUniqueId("p_");
			b.println("final Object[] {0}=new Object[{1}];", variables.getJavaVariable(), variables.size());
		}
	}

	public void transpilerDeclareStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, int doNotInitialize) {
		transpilerDeclareStatement(jsContext, b, doNotInitialize, null);
	}

	// functionFilter, when non-null, restricts EMISSION of this container's
	// own nested function classes (see transpilerDeclareFunctionClasses) to
	// only those matching it - lets ASTFunction's generator-split codegen
	// declare a SUBSET of `functions` (the ones needed for eager parameter
	// defaults) here, while the rest stay declared where their own
	// (unmoved) body-statement instantiation lives - see
	// ASTFunction.transpileFunctionBody's splitForGenerator/
	// canSplitFunctionClassesForGenerator. Every other caller passes null
	// (declare everything, the original behavior).
	public void transpilerDeclareStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, int doNotInitialize, Predicate<ASTFunction> functionFilter) {
		// All variables tyoes should be declared as they can be accessed from the code in functions
		if(variables!=null && !variables.variables.isEmpty()) {
			// A MODULE's own top-level var/function declarations are NEVER
			// global object properties (unlike a plain script's - spec
			// GlobalDeclarationInstantiation only runs for script/eval code,
			// never module code, which gets its own separate
			// InitializeEnvironment instead) - `jsContext instanceof
			// TranspilerGeneratorMainContext` alone doesn't distinguish "this
			// is the outermost declare-statement call for a SCRIPT" from
			// "...for a MODULE" (both compile through the same MainContext),
			// so a module's own top-level `var x` was wrongly getting
			// synchronized onto the real global object via
			// initGlobalVariables() below - test262 language/module-code/
			// instn-local-bndng-var.js and siblings assert this does NOT
			// happen (`Object.getOwnPropertyDescriptor(globalObj, 'x')` stays
			// undefined even after the module's own `var x` runs).
			boolean global = jsContext instanceof TranspilerGeneratorMainContext
					&& !(this instanceof ASTProgram program && program.isModule());
			// A loop that's proven safe to reuse this container's array across
			// iterations (see ASTFor's own hoisting logic) calls
			// transpilerDeclareArrayOnly() BEFORE entering the loop, which
			// already assigns transpilerId and emits the array itself - skip
			// redoing either here, since this call is then reached once per
			// iteration purely to re-run the per-entry bookkeeping below
			// (createVariable/initVars/TDZ-seed/function-classes), which still
			// needs to happen every time this container's own block executes.
			boolean alreadyDeclared = variables.transpilerId!=null;
			if(!alreadyDeclared) {
				variables.transpilerId = jsContext.generateUniqueId("p_");
			}

			for(VariableDef v: variables) {
				VAR_TYPE t = v.getVarType();
				jsContext.createVariable(v,t.isHoisted());
			}
			if(!alreadyDeclared) {
				b.println("final Object[] {0}=new Object[{1}];", variables.getJavaVariable(), variables.size());
			}
			// JSVar.initVars UNDEFINED-fills the slot range [doNotInitialize,
			// size) - but every LET/CONST/USING slot in that range is
			// immediately overwritten with TDZ by the loop below, making its
			// fill a dead store. When EVERY init-range slot is one of those
			// (the common case for a function body whose only declarations are
			// let/const, e.g. `function f(a,b,c){ const d=... }`), the whole
			// initVars call is dead - skip it. A single non-TDZ slot (VAR/
			// FUNCTION/AUTO) in the range genuinely needs the UNDEFINED fill,
			// so keep the call then.
			int varCount = variables.size()-doNotInitialize; // Argument & this & params
			if(varCount>0) {
				boolean allTdzInInitRange = true;
				for(VariableDef v: variables) {
					if(v.getJavaVariableIndex()<doNotInitialize) {
						continue;
					}
					VAR_TYPE t = v.getVarType();
					if(!(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING)) {
						allTdzInInitRange = false;
						break;
					}
				}
				if(!allTdzInInitRange) {
					b.println("JSVar.initVars({0}, {1});", variables.getJavaVariable(),doNotInitialize);
				}
			}
			// A let/const/using slot is in the Temporal Dead Zone from this
			// container's creation until its own declaration statement runs
			// (see ASTVariableDecl.transpileJavaStatement, which overwrites
			// this with the real value/UNDEFINED) - override JSVar.initVars'
			// blanket UNDEFINED fill for exactly those slots.
			for(VariableDef v: variables) {
				VAR_TYPE t = v.getVarType();
				if((t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) && needsTdzSeed(v)) {
					b.println("{0}[{1}]/*{2}*/ = TDZ;", variables.getJavaVariable(), v.getJavaVariableIndex(), v.getName());
				}
			}

			if(global) {
				String[] varNames = variables.names();
				String cst = jsContext.getConstantPool().createConstant(varNames);
				String typesCst = jsContext.getConstantPool().createConstant(variables.types());
				String annexBCst = jsContext.getConstantPool().createConstant(variables.annexBBlockHoisted());
				b.println("{0}.initGlobalVariables({1}, {2}/*var names*/, {3}/*var types*/, {4}/*annexB block-hoisted*/);", JSTranspiler.MAIN_CONTEXT, variables.getJavaVariable(), cst, typesCst, annexBCst);
			}
		}

		if(functions!=null) {
			transpilerDeclareFunctionClasses(jsContext, b, functionFilter);
		}
	}

	// Whether a let/const/using slot needs its `= TDZ` seed store at container
	// creation. The seed makes the slot hold the TDZ sentinel (so a read before
	// its own declaration statement throws) until that declaration runs. It's
	// dead only when NO code can observe the slot before its declaration
	// assigns it - which the general case can't prove cheaply (shadowing, early
	// closures, with/eval), so the base container always keeps it. ASTFor
	// overrides this for its own C-style loop variables, where the dedicated
	// per-loop array and always-assigning init clause make the seed provably
	// dead unless the init clause itself could read the variable.
	protected boolean needsTdzSeed(VariableDef v) {
		return true;
	}

	// Extracted from transpilerDeclareStatement so ASTFunction's generator
	// split can emit a FILTERED subset of this container's own nested
	// function classes from two different Java methods (initGeneratorParams
	// vs callVoid) - see that method's own doc. filter==null preserves the
	// original, unfiltered behavior (every other caller).
	//
	// The filtered subset feature only supports the "one class per function"
	// codegen mode below (functionsSize<=maxFunctionSingleClass) - the
	// dispatcher mode (shared switch over ALL functions) can't be split by
	// subset without a larger restructure, so a non-null filter there is a
	// caller bug (see ASTFunction.canSplitFunctionClassesForGenerator, which
	// guards against ever reaching this with both a non-null filter AND
	// dispatcher-mode function counts).
	public void transpilerDeclareFunctionClasses(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, Predicate<ASTFunction> functionFilter) {
		if(functions==null) {
			return;
		}
        TranspilerCodeSplitter splitter = jsContext.getOptions().getCodeSplitter();
		String mainClassName = getFctContainerClassName(jsContext);
		int functionsSize = functions.size();

		// Per-function class emission when the count is small enough:
		// each function gets its own class extending BuiltinFunctionTranspiler
		// directly, so each retains the direct-arg callVoidN fast path
		// (ASTFunction gates that on functionName=="callVoid"). Beyond
		// MAX_SINGLE_FCT_OPTIMIZATION we fall back to the shared switch
		// dispatcher to keep class-count / metaspace bounded.
        int maxFunctionSingleClass = splitter!=null ? splitter.maxFunctionsBeforeDispatcher() : TranspilerCodeSplitter.DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER;
		if(functionsSize <= maxFunctionSingleClass) {
			// See needsHeadClosureSnapshot()'s own doc. Only wired up for this
			// (single-class-per-function) branch - the dispatcher branch below
			// is for containers with many functions, which a tiny loop head
			// never hits in practice; left unchanged rather than speculatively
			// extended.
			boolean snapshotCapture = needsHeadClosureSnapshot();
			String snapshotVar = snapshotCapture ? getVariables().getJavaVariable() : null;
			for(int i=0; i<functionsSize; i++) {
				ASTFunction fct = functions.get(i);
				if(functionFilter!=null && !functionFilter.test(fct)) {
					continue;
				}
				// Each per-function class gets its own constant pool so
				// each class only carries the constants its body actually
				// references - unlike the dispatcher branch where one
				// shared pool amortises across many functions.
				// getNestedFunctionParentContext lets ASTFunction route a
				// nested function declared INSIDE its own parameter list
				// through a filtered parent context, so it can't see the
				// enclosing function's own body-declared names - see that
				// method's own doc. Every other container (no such
				// concept) returns jsContext unchanged.
				TranspilerGeneratorConstantPoolContext fctPoolContext = new TranspilerGeneratorConstantPoolContext(getNestedFunctionParentContext(jsContext,fct));
				String className = getFunctionEmittedClassName(jsContext, mainClassName, functionsSize, i);
				// See ASTFor's "deferred head closure snapshot" doc (and
				// HeadClosureSnapshotHolder's own doc): a closure ASTFor has
				// flagged via setDeferredHeadSnapshotTempVar() needs its
				// snapshot field settable AFTER construction, instead of
				// (like every other head closure) fixed forever by the
				// constructor - so it additionally implements
				// HeadClosureSnapshotHolder and its field is non-final.
				// Deliberately checked per-function, not once for the whole
				// container: an ordinary head closure sharing this same
				// container (e.g. one also present in the test clause) is
				// completely unaffected.
				boolean deferred = snapshotCapture && fct.getDeferredHeadSnapshotTempVar()!=null;
				b.println(deferred
						? "class {0} extends {1} implements {2} {"
						: "class {0} extends {1} {",
						className, BuiltinFunctionTranspiler.class.getSimpleName(), HeadClosureSnapshotHolder.class.getName());
				b.incIndent();
					if(snapshotCapture) {
						b.println(deferred ? "private Object[] {0};" : "private final Object[] {0};", snapshotVar);
					}
					b.println("{0}({1} parentCtx, int flags, String name, int length, int index, int srcStart, int srcEnd{2}) {",
							className, JSTranspiledRuntimeContext.class.getSimpleName(),
							snapshotCapture ? StringFormat.format(", Object[] {0}", snapshotVar) : "");
					b.incIndent();
						b.println("super(parentCtx, flags, name, length, index, srcStart, srcEnd);");
						if(snapshotCapture) {
							b.println("this.{0} = {0};", snapshotVar);
						}
					b.decIndent();
					b.println("}");
					if(deferred) {
						b.println("@Override public void setHeadClosureSnapshot(Object[] snapshot) { this.{0} = snapshot; }", snapshotVar);
					}

					// Phase C: elide per-call fctContext allocation + ScopedValue push
					// when the function body doesn't observe/mutate fctContext state.
					if(fct.isTranspiledContextElidable()) {
						b.println("@Override protected boolean isContextElidable() { return true; }");
					}
					// The @Override annotation for the emitted callVoid[N] method(s)
					// is printed inside transpileFunctionExpression - the direct-arg
					// path may emit BOTH callVoidN (arity-N direct-arg) and
					// callVoid(Object[]), each with its own annotation.
					b.append(fct.transpileFunctionExpression(fctPoolContext,className,"callVoid"));

					JSTranspiler.createConstantPool(fctPoolContext,b);
				b.decIndent();
				b.println("}");
			}
		} else {
			if(functionFilter!=null) {
				throw new IllegalStateException("Filtered function-class emission isn't supported in dispatcher mode ("+functionsSize+" functions)");
			}
			TranspilerGeneratorConstantPoolContext allFctContext = new TranspilerGeneratorConstantPoolContext(jsContext);

	        int maxFunctions = splitter!=null ? splitter.maxFunctionsPerDispatcher() : TranspilerCodeSplitter.DEFAULT_MAX_FUNCTIONS_PER_DISPATCHER;

			int fidx = 0;
			for(int fi=0; fi<functionsSize; fi+=maxFunctions, fidx++) {
				int functionsCount = Math.min(maxFunctions, functionsSize-fi);
				boolean isMainDispatcher = fi+maxFunctions>=functionsSize;
				String className = isMainDispatcher ? mainClassName : StringFormat.format("{0}_{1}", mainClassName, fidx);
				String extend = fi==0 ? BuiltinFunctionTranspiler.class.getSimpleName() : StringFormat.format("{0}_{1}", mainClassName, fidx-1);
				b.println("class {0} extends {1} {", className, extend);
				b.incIndent();

					b.println("{0}({1} parentCtx, int flags, String name, int length, int index, int srcStart, int srcEnd) {",className,JSTranspiledRuntimeContext.class.getSimpleName());
					b.incIndent();
						b.println("super(parentCtx, flags, name, length, index, srcStart, srcEnd);");
					b.decIndent();
					b.println("}");

					b.println("@Override");
					b.println("public Object callVoid({0} {1}, Object {2}, Object[] {3}) {", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT,JSTranspiler.THIS_VAR,JSTranspiler.FUNCTION_ARGUMENTS);
					b.incIndent();
						b.println("return switch(index) {");
						b.incIndent();
						for(int i=0; i<functionsCount; i++) {
							b.println("case {0} -> f_{0}({1},{2},{3});", fi+i, JSTranspiler.MAIN_CONTEXT,JSTranspiler.THIS_VAR,JSTranspiler.FUNCTION_ARGUMENTS);
						}
						b.println("default -> super.callVoid({0},{1},{2});", JSTranspiler.MAIN_CONTEXT,JSTranspiler.THIS_VAR,JSTranspiler.FUNCTION_ARGUMENTS);
						b.decIndent();
						b.println("};");
					b.decIndent();
					b.println("}");

					// Sibling generator functions in this chunk that need eager
					// parameter binding (see ASTFunction.needsInitGeneratorParamsSplit)
					// each emit their own indexed initGeneratorParams_f_N helper
					// instead of a fixed-name override (would collide - they're
					// all methods of this ONE shared dispatcher class). Only this
					// class's OWN chunk range is switched on here; an index
					// outside it falls to super.initGeneratorParams(...), exactly
					// like the callVoid dispatcher above - a chunk with no such
					// siblings simply doesn't override the method at all, and
					// plain Java inheritance reaches the nearest ancestor chunk
					// that does (or the no-op base), so skipping empty chunks is
					// safe.
					List<Integer> generatorSplitIndices = null;
					for(int i=0; i<functionsCount; i++) {
						if(functions.get(fi+i).needsInitGeneratorParamsSplit(allFctContext)) {
							if(generatorSplitIndices==null) {
								generatorSplitIndices = new ArrayList<>();
							}
							generatorSplitIndices.add(fi+i);
						}
					}
					if(generatorSplitIndices!=null) {
						b.println();
						b.println("@Override");
						b.println("protected void initGeneratorParams({0} {1}, Object {2}, Object[] {3}) {", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT, JSTranspiler.THIS_VAR, JSTranspiler.FUNCTION_ARGUMENTS);
						b.incIndent();
							b.println("switch(index) {");
							b.incIndent();
							for(int idx : generatorSplitIndices) {
								b.println("case {0} -> initGeneratorParams_f_{0}({1},{2},{3});", idx, JSTranspiler.MAIN_CONTEXT,JSTranspiler.THIS_VAR,JSTranspiler.FUNCTION_ARGUMENTS);
							}
							b.println("default -> super.initGeneratorParams({0},{1},{2});", JSTranspiler.MAIN_CONTEXT,JSTranspiler.THIS_VAR,JSTranspiler.FUNCTION_ARGUMENTS);
							b.decIndent();
							b.println("}");
						b.decIndent();
						b.println("}");
					}

					// Now generate each method
					for(int i=0; i<functionsCount; i++) {
						ASTFunction fct = functions.get(fi+i);
						b.println();
						String functionName = StringFormat.format("f_{0}",fi+i);
						b.append(fct.transpileFunctionExpression(allFctContext,className,functionName));
					}

					JSTranspiler.createConstantPool(allFctContext,b);

				b.decIndent();
				b.println("}");
			}
		}
	}

	// Hook consulted for EVERY nested function declaration's own codegen
	// parent context, right before its class+body gets emitted (see the
	// single-class-per-function loop above). The default wraps for any
	// enclosing NAMED class (see wrapForEnclosingClasses) - ASTFunction
	// additionally layers its own "parameter list vs. body" wrap on top,
	// see its own override and TranspilerParameterScopeContext's own doc.
	protected JSTranspilerGeneratorContext getNestedFunctionParentContext(JSTranspilerGeneratorContext jsContext, ASTFunction fct) {
		return wrapForEnclosingClasses(jsContext, fct);
	}

	// TRANSPILER ONLY - see KnownGaps.md "a class's own name isn't bound in
	// its own separate, immutable inner scope" and ASTClassDecl's
	// `selfBindingVarDef`/`getSelfBindingVarDef()` own docs.
	//
	// A class element's function (constructor/method/getter/setter - a
	// field initializer or static block's own TOP-LEVEL statements are
	// different, see below) is never itself an IContextBlockContainer's
	// direct child in the sense addFunctionDeclaration() cares about -
	// ASTClassDecl doesn't implement IContextBlockContainer at all (a class
	// body doesn't introduce a new var-scoping container the way a block or
	// function does), so `fct`'s own addFunctionDeclaration() call (see
	// ASTFunction.init()) walks straight past every enclosing ASTClassDecl
	// to whatever REAL container encloses them - meaning `fct`'s own class+
	// body gets emitted by THAT enclosing container's transpilerDeclare
	// FunctionClasses, using THAT container's own (persistent, built once,
	// well before the class declaration statement is even reached) codegen
	// context - which has no way to know about a class declared LATER in
	// its own body at all. A field initializer or static block's own top-
	// level statements don't have this problem (they're transpiled inline,
	// via ASTClassDecl.transpileJavaExpression's own already-wrapped
	// `jsContext` directly - see ASTClassField.transpileInitInstanceStatement/
	// ASTClassStaticBlock.transpileInitStaticValueStatement) - but a nested
	// function declared WITHIN one of those (e.g. a field initializer that's
	// itself a closure, `x = () => C`) has the exact same problem as a
	// method, for the exact same reason.
	//
	// Walk up from `fct` (NOT including `this`, the container fct is being
	// declared into) collecting every enclosing NAMED class along the way,
	// nearest-first; wrap `jsContext`, outermost first, so the nearest
	// enclosing class's own self-binding is checked FIRST by the resulting
	// context's getOwnVariable() walk (matters only if two enclosing classes
	// happen to share the same name, vanishingly rare). No-op (returns
	// jsContext unchanged) when `fct` isn't nested inside any named class
	// between itself and `this` - the overwhelmingly common case.
	protected final JSTranspilerGeneratorContext wrapForEnclosingClasses(JSTranspilerGeneratorContext jsContext, ASTFunction fct) {
		List<org.monflabs.galtajs.node.clazz.ASTClassDecl> classes = null;
		for(ASTNode n=fct; n!=null && n!=this; n=n.getParent()) {
			if(n instanceof org.monflabs.galtajs.node.clazz.ASTClassDecl cd && cd.getSelfBindingVarDef()!=null) {
				if(classes==null) {
					classes = new ArrayList<>();
				}
				classes.add(cd);
			}
		}
		if(classes==null) {
			return jsContext;
		}
		JSTranspilerGeneratorContext ctx = jsContext;
		for(int i=classes.size()-1; i>=0; i--) {
			org.monflabs.galtajs.node.clazz.ASTClassDecl cd = classes.get(i);
			ctx = new org.monflabs.galtajs.transpiler.context.TranspilerClassSelfNameContext(ctx, cd.getClassName(), cd.getSelfBindingVarDef());
		}
		return ctx;
	}

	// Class to instantiate for function `indexInRoot`. Two shapes:
	//   - per-function-class mode (functionsSize <= MAX_SINGLE_FCT_OPTIMIZATION):
	//     single function reuses the container class name (preserves the
	//     original single-function name); multiple functions each get their
	//     own {main}_f{i} class so they can keep the direct-arg fast path.
	//   - dispatcher mode (functionsSize > MAX_SINGLE_FCT_OPTIMIZATION): all
	//     functions instantiate the outermost (main) container class - the
	//     switch inside routes by index. The intermediate _1, _2, ... chunks
	//     are internal to the inheritance chain, never instantiated directly.
	public String getFunctionEmittedClassName(JSTranspilerGeneratorContext jsContext, String mainClassName, int functionsSize, int indexInRoot) {
        TranspilerCodeSplitter splitter = jsContext.getOptions().getCodeSplitter();
        int maxFunctionSingleClass = splitter!=null ? splitter.maxFunctionsBeforeDispatcher() : TranspilerCodeSplitter.DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER;
		if(functionsSize <= maxFunctionSingleClass) {
			if(functionsSize==1) {
				return mainClassName;
			}
			return StringFormat.format("{0}_f{1}", mainClassName, indexInRoot);
		}
		return mainClassName;
	}
	public String getFunctionEmittedClassName(JSTranspilerGeneratorContext jsContext, int indexInRoot) {
		return getFunctionEmittedClassName(jsContext,getFctContainerClassName(jsContext), functions.size(), indexInRoot);
	}
	public String getFctContainerClassName(JSTranspilerGeneratorContext jsContext) {
		if(fctContainerClassName==null) {
			fctContainerClassName = jsContext.generateUniqueId("F");
		}
		return fctContainerClassName;
	}
}