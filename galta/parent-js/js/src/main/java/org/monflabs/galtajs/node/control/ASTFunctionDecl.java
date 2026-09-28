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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTStatementList;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.HoistableNode;
import org.monflabs.galtajs.node.TopNode;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionInterpreter;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;




/**
 * Function declaration.
 * 
 * It has to be register in the variable context when it is part of a statement, else it should simply be initialized on
 * demand, like anonymous functions.
 */
public class ASTFunctionDecl extends ASTFunction implements TopNode, HoistableNode {

	private boolean statement;
	private String sourceCode;
	private boolean generator;
	private boolean async;
	// True when this function-declaration statement is nested inside a block
	// (not directly in a function/program body) AND its enclosing function/
	// program is sloppy mode - Annex B.3.3 hoists it to that enclosing
	// scope as a var-like binding. Suppressed entirely in strict mode (the
	// declaration then stays block-scoped only). Set once in createFunction()
	// (parse time), read in evaluate() (possibly many times, once per call).
	private boolean hoistedToRoot;
	// True whenever this declaration is nested inside ANY block (not
	// directly at the top of a function/program body) - regardless of
	// whether Annex B ends up hoisting it to the root (hoistedToRoot can be
	// true here too) or it stays block-scoped only (strict mode, or a
	// hoist conflict). Read by ASTFunction.init()/BuiltinFunctionInterpreter
	// to decide whether this declaration's own name additionally needs a
	// function-local self-reference binding: a genuinely non-nested
	// declaration has exactly ONE external binding (the root one), so a
	// reference to its own name from inside its body can safely resolve
	// there via ordinary scope-chain lookup - but a block-nested one has a
	// SEPARATE, runtime-only lexical binding in its own block (created by
	// BlockDeclarationInstantiation, never represented as a static
	// VariableDef the scope-resolution optimizer's walk can see), which a
	// self-reassignment from inside the body must update instead of the
	// outer/root copy - not yet correctly resolvable via ordinary lookup,
	// so this case keeps the OLDER (self-copy) behavior rather than
	// resolving to the wrong (root) binding. Set once in createFunction()
	// (parse time).
	private boolean blockNested;
	// True for the one Annex B shape with NO scope of its own to fall back
	// to when the hoist is skipped: a bare (unbraced) FunctionDeclaration
	// directly as an if/else clause body (Annex B.3.2 - GaltaJS's Statement()
	// grammar already accepts this "for free", see JSParser.jj) whose name
	// conflicts with an existing non-overridable declaration at the root.
	// Unlike the block-nested case (which falls back to the real enclosing
	// block), ASTIf introduces no IContextBlockContainer of its own to fall
	// back to - so this declaration is registered NOWHERE and has no
	// observable effect at all when evaluated (matches test262: the ONLY
	// assertions made about this shape check that the OUTER binding is left
	// completely untouched, never that the inner declaration remains
	// separately callable by name).
	private boolean noBinding;

	// Set ONLY by hoistValue() below (never by evaluate()'s own normal
	// path) - true means this declaration's real function VALUE has
	// ALREADY been constructed+assigned once, so evaluate()'s own later,
	// normal-source-position pass over the SAME statement must skip
	// re-construction rather than overwrite the binding with a SECOND,
	// different function object instance (breaking identity for anyone
	// who captured a live reference to the first one in between - see
	// hoistValue()'s own doc comment for the concrete scenario this
	// exists for). Deliberately NOT a general "already ran once" guard:
	// an ordinary (non-hoisted-early) function declaration inside a loop
	// or repeatedly-called function body still needs evaluate() to run
	// - and reassign - every single time, exactly as before; this field
	// simply stays false for every one of those (hoistValue() is only
	// ever called for genuine module-top-level statements, each executed
	// at most once per module load).
	private boolean earlyHoisted;

	// Called ONLY from ASTProgram's own MODULE-level hoist pass (gated to
	// isModule()==true), for a genuine top-level `function fn(){}`/
	// `export function fn(){}`/`export default function fn(){}`
	// statement - never for one nested in a block/loop/function body.
	// Constructs this declaration's REAL function value NOW, matching
	// spec InstantiateFunctionObject (part of module/script environment
	// initialization, which completes for EVERY hoistable declaration
	// before any dependency is evaluated) - GaltaJS's own DEFAULT
	// hoisting only creates a PLACEHOLDER cell during the var/let/const/
	// function hoist loop, with the real value assigned later via
	// ASTStatementList.hoistNodes()'s physical statement-reordering
	// trick (this SAME statement, just moved earlier in the list, still
	// only runs through the ordinary evaluateNodes() loop) - too late
	// for a dependency eagerly triggered during THIS module's own hoist
	// pass (e.g. a self-/circular-import reading this function back
	// before evaluateNodes() ever reaches it) to observe a real value
	// (confirmed via test262 verify-dfs.js: a dependency's own top-level
	// call to this module's exported `check` function found nothing,
	// because eagerly triggering that dependency's evaluation - itself a
	// fix for eval-rqstd-order.js - ran before `check`'s own (reordered
	// but still evaluateNodes()-driven) statement had a chance to
	// execute).
	public void hoistValue(JSInterpretedRuntimeContext context) {
		if(isStatement() && !noBinding) {
			earlyHoisted = true;
			assignFunctionValue(context);
		}
	}

	// Transpiled-codegen mirror of earlyHoisted above - see
	// transpileHoistValue()'s own doc comment for why this is a SEPARATE
	// field rather than reusing earlyHoisted (interpreted/transpiled are
	// different execution modes of the same AST instance during dual-mode
	// test runs; sharing one field would let one mode's hoist pass
	// incorrectly suppress the other's own normal-position emission).
	private boolean earlyHoistedTranspiled;

	public ASTFunctionDecl(Token t, String functionName, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		super(t,functionName,parameters,nodes);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return statement ? STATEMENT_TYPE.STATEMENT : STATEMENT_TYPE.EXPRESSION;
	}
	
	@Override
	public String getSourceCode() {
		return sourceCode;
	}

    public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	@Override
	public boolean isStatement() {
		return statement;
	}

	@Override
	public boolean isBlockNested() {
		return blockNested;
	}

	public void setStatement(boolean statement) {
		this.statement = statement;
	}

	@Override
	public boolean isGenerator() {
		return generator;
	}

	public void setGenerator(boolean generator) {
		this.generator = generator;
	}

	@Override
	public boolean isAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}

	// If the function has to be
	@Override
	protected void init(InitContext initContext) {
    	if(isStatement()) {
    		createFunction();
    	}
    	super.init(initContext);
	}
    protected void createFunction() {
    	// Functions are hoisted, so they should be added to the root container and
    	// and not to the block container, that contains the definition -
    	// EXCEPT this Annex B hoist is sloppy-mode only (spec Annex B.3.3):
    	// in strict mode a block-scoped function declaration stays scoped to
    	// its own block, exactly like a let/const. When there's no enclosing
    	// block at all, IContextBlockContainer resolves to the same root
    	// container either way, so an ordinary (non-nested) function
    	// statement is unaffected regardless of strictness.
    	IContextRootContainer rootContainer = findParentNodeByClass(IContextRootContainer.class);
    	IContextBlockContainer nearestBlock = findParentNodeByClass(IContextBlockContainer.class);
    	blockNested = (nearestBlock!=rootContainer) || isBareIfClauseBody();
    	boolean enclosingGenuinelyStrict = rootContainer.isGenuinelyStrictMode();
    	IContextBlockContainer varContainer = enclosingGenuinelyStrict
    		? nearestBlock
    		: rootContainer;
    	// Annex B.3.3's hoist is skipped (not an error) - not just suppressed
    	// by strict mode - when hoisting would conflict with an existing
    	// non-overridable (let/const/class) declaration of the same name in
    	// ANY scope between this declaration's own block and the root
    	// (inclusive) - not just the root itself, since an intervening block
    	// (e.g. `{ let f; { function f(){} } }`) can carry the conflicting
    	// lexical declaration too. Spec: "if replacing the FunctionDeclaration
    	// with a VariableStatement would not produce any Early Errors, then
    	// [hoist]" - a would-be conflict just means "stay block-scoped only",
    	// never a thrown SyntaxError. Only applies when actually block-nested
    	// (nearestBlock!=rootContainer) - a conflict for a NON-nested
    	// declaration (no Annex B involved at all) is a genuine redeclaration
    	// error, left to addVarDeclaration() below.
    	boolean isAnnexBHoistCandidate = (nearestBlock!=rootContainer || isBareIfClauseBody());
    	if(varContainer==rootContainer && isAnnexBHoistCandidate) {
    		boolean conflicts = false;
    		// Also track whether the FALLBACK container itself (nearestBlock)
    		// is the one directly holding the conflicting declaration - e.g.
    		// `{ let f; if (true) function f(){} }`: ASTIf introduces no
    		// container of its own, so nearestBlock resolves to the SAME
    		// block that directly declares the conflicting `let f` - falling
    		// back to it would just re-conflict immediately. Only a conflict
    		// in some OUTER container (nearestBlock itself is clean) makes
    		// falling back to nearestBlock meaningful (e.g. the block-nested
    		// `{ let f; { function f(){} } }` case, where nearestBlock is the
    		// function's OWN dedicated inner block, separate from `let f`'s).
    		boolean nearestBlockConflicts = false;
    		for(ASTNode n=this; n!=null; n=n.getParent()) {
    			if(n instanceof IContextBlockContainer bc) {
    				ASTVarContainer.VariableDef existing = bc.getOwnVariable(getFunctionName());
    				boolean thisOneConflicts = existing!=null && existing.getVarType()!=VAR_TYPE.SYSTEM
    					&& (!existing.getVarType().canBeOverriden() || existing.isAnnexBHoistBlocked());
    				// B.3.3.1's function-code-specific extra condition: also
    				// skip the hoist when F is one of the enclosing function's
    				// OWN parameter names - a completely separate kind of
    				// binding from LET/CONST/CLASS, not caught by the
    				// VariableDef/canBeOverriden() check above (parameters are
    				// normally override-compatible with var/function, which is
    				// why plain `function f(x){ var x; }` is legal - this is
    				// Annex B's own additional restriction, root-only).
    				if(bc==rootContainer && bc instanceof ASTFunction fn) {
    					boolean[] isParam = {false};
    					fn.getParameters().forEachVarName((n2) -> { if(n2.equals(getFunctionName())) isParam[0]=true; });
    					thisOneConflicts |= isParam[0];
    					// FunctionDeclarationInstantiation step 22.f: "arguments" is
    					// appended to parameterNames whenever an arguments object is
    					// created (i.e. always, for any non-arrow function) - so a
    					// block-nested `function arguments(){}` must never copy up
    					// to the enclosing function scope, exactly like a same-named
    					// parameter wouldn't. The SYSTEM "arguments" VariableDef
    					// itself is deliberately excluded from `existing!=null &&
    					// getVarType()!=SYSTEM` above (so a plain `var arguments;` at
    					// function-root can coexist with it) - this is a separate,
    					// narrower rule that applies only to the Annex B hoist check.
    					if(!fn.isArrow() && getFunctionName().equals(Arguments.ARGUMENTS)) {
    						thisOneConflicts = true;
    					}
    				}
    				// A block-scoped FunctionDeclaration ALWAYS creates its own
    				// lexical binding in its immediate Block (ordinary ES6+ block
    				// scoping, independent of whether ITS OWN copy-up succeeds) -
    				// so hoisting `this` declaration's name past some OTHER,
    				// intervening block that directly contains a (different)
    				// same-named FunctionDeclaration would conflict with THAT
    				// block's own lexical binding, exactly as "replacing f with
    				// var f" early-errors against any LexicallyDeclaredName it
    				// passes through. `existing`/getOwnVariable() above can't see
    				// this: an intervening block's copy-up target is the ROOT
    				// container (not the intervening block itself), so nothing
    				// is ever registered as the intervening block's OWN variable.
    				// Excludes nearestBlock itself (sibling same-named function
    				// declarations directly in `this` declaration's own immediate
    				// block are explicitly permitted - the whole reason "last one
    				// wins" is normal, unconflicted behavior for that case) and
    				// rootContainer (an ordinary, non-block-scoped function decl
    				// there is the var/function binding itself, not a competing
    				// lexical one).
    				if(bc!=nearestBlock && bc!=rootContainer && bc instanceof ASTStatementList sl) {
    					for(ASTNode stmt: sl.getStatements()) {
    						if(stmt!=this && stmt instanceof ASTFunctionDecl fd && fd.getFunctionName().equals(getFunctionName())) {
    							thisOneConflicts = true;
    							break;
    						}
    					}
    				}
    				if(bc==nearestBlock) {
    					nearestBlockConflicts = thisOneConflicts;
    				}
    				if(thisOneConflicts) {
    					conflicts = true;
    					break;
    				}
    				if(bc==rootContainer) {
    					break;
    				}
    			}
    		}
    		if(conflicts) {
    			if(nearestBlock!=rootContainer && !nearestBlockConflicts) {
    				varContainer = nearestBlock;
    			} else {
    				// No real, conflict-free scope to fall back to - see
    				// noBinding's field comment.
    				noBinding = true;
    				checkStrictBindingName(enclosingGenuinelyStrict, getFunctionName(), this);
    				return;
    			}
    		}
    	}
    	hoistedToRoot = (varContainer == rootContainer);
    	// A function DECLARATION's own name is a BindingIdentifier contained in
    	// the ENCLOSING scope, not the function's own body - so its
    	// strict-mode restriction is checked against the enclosing scope's
    	// strictness (unlike a named function EXPRESSION's own name, checked
    	// in ASTFunction.init() against its own body's strictness instead).
    	checkStrictBindingName(enclosingGenuinelyStrict, getFunctionName(), this);
		ASTVarContainer.VariableDef vd = varContainer.addVarDeclaration(getFunctionName(), VAR_TYPE.FUNCTION, null);
		// See VariableDef.isAnnexBBlockHoisted()'s own comment - only true
		// when this declaration reached the root via Annex B.3.3's
		// sloppy-mode hoist (isAnnexBHoistCandidate - block-nested OR a bare
		// if-clause body), as opposed to being declared directly at the
		// root's own top level.
		if(hoistedToRoot && isAnnexBHoistCandidate) {
			vd.setAnnexBBlockHoisted(true);
		}
    }

    // See noBinding's field comment - detects a bare (unbraced)
    // FunctionDeclaration directly as an if/else clause's own body.
    private boolean isBareIfClauseBody() {
    	ASTNode parent = getParent();
    	return parent instanceof ASTIf;
    }

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(isStatement()) {
				if(noBinding) {
					// See noBinding's field comment - no scope exists to bind
					// into, so this declaration has no observable effect at all.
					result.setUndefined();
					return Signal.NONE;
				}
				if(earlyHoisted) {
					// See hoistValue()'s/earlyHoisted's own doc comments -
					// already constructed+assigned once, this normal-
					// source-position pass must not do it again.
					result.setUndefined();
					return Signal.NONE;
				}
				assignFunctionValue(context);
				result.setUndefined();
			} else {
				BuiltinFunction fct = new BuiltinFunctionInterpreter(context, this, getVariables(),getParamLength());
				result.setValue(fct);
			}
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	// Shared by evaluate()'s own normal-source-position path and
	// hoistValue()'s early-hoist path - see each one's own doc comment.
	private void assignFunctionValue(JSInterpretedRuntimeContext context) {
		String functionName = getFunctionName();
		BuiltinFunction fct = new BuiltinFunctionInterpreter(context, this, getVariables(),getParamLength());
		// Annex B hoist (sloppy mode, block-nested only): assign into
		// the enclosing function/global scope, skipping past any
		// intervening block (getVarDeclContext()'s usual var-style
		// skip). In strict mode (or when not block-nested at all)
		// the declaration stays local - assign directly into the
		// current (block-local, or already-root) context instead.
		JSRuntimeContext target = hoistedToRoot ? context.getVarDeclContext() : context;
		if(hoistedToRoot) {
			// Unlike the block-local case (whose slot is guaranteed to
			// already exist, pre-created by ASTBlock.evaluate()'s TDZ/
			// undefined pre-pass), the hoisted-to-root binding can have
			// been legitimately REMOVED since the initial hoist: it's
			// created deletable/configurable (direct-eval's
			// EvalDeclarationInstantiation, or an ordinary configurable
			// global), and test262's own propertyHelper.js routinely
			// `delete`s a property to probe configurability before this
			// same statement runs again. When that happens, the binding
			// must be re-created (createVariable()), not left to throw
			// ReferenceError like plain setVariable() would. But when the
			// binding IS still there, this must be a plain VALUE
			// assignment (setVariable(), spec: SetMutableBinding) - NOT
			// createVariable(), which for VAR_TYPE.FUNCTION unconditionally
			// redefines the property descriptor back to the default
			// (enumerable:true) shape, clobbering a pre-existing
			// non-enumerable descriptor that legacy hoisting is spec-
			// required to leave untouched (test262
			// annexB/language/*-existing-non-enumerable-global-init.js).
			if(target.getVariableEntry(functionName)!=null) {
				target.setVariable(functionName,fct);
			} else {
				target.createVariable(functionName, fct, VAR_TYPE.FUNCTION);
			}
		} else {
			target.setVariable(functionName,fct);
		}
	}

	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b) {
		// See noBinding's own field comment: an Annex B block-hoist that
		// was skipped (conflicting non-overridable declaration, with no
		// scope of its own to fall back to - the bare if/else-clause-body
		// shape) is registered NOWHERE and has no observable effect at all
		// when evaluated - mirrors evaluate()'s/hoistValue()'s identical
		// interpreted-mode guard (both skip calling assignFunctionValue()
		// entirely in this case). Previously unconditional here, so
		// getParent().findVariable(getFunctionName()) silently found and
		// overwrote whatever UNRELATED, pre-existing declaration of the
		// same name was actually in scope (the exact opposite of "no
		// effect") - test262 annexB/language/{function,global}-code/
		// *-skip-*.js.
		if(noBinding) {
			return;
		}
		if(earlyHoistedTranspiled) {
			// See earlyHoistedTranspiled's/transpileHoistValue()'s own doc
			// comments - already constructed+assigned once, this normal
			// (reordered-to-front, still-emitted-later) position pass must
			// not do it again.
			return;
		}
		doTranspileAssign(_jsContext, b);
	}

	// Shared by transpileJavaStatement()'s own normal-position path and
	// transpileHoistValue()'s early-hoist path below - see each one's own
	// doc comment.
	private void doTranspileAssign(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b) {
		// hoistedToRoot: go DIRECTLY to the root container's own variable
		// (IContextBlockContainer.getOwnVariable(), no scope walk) instead of
		// getParent().findVariable()'s generic, name-based walk UP the scope
		// chain - that walk stops at the FIRST matching name it finds, which
		// for a block-nested Annex-B hoist whose name collides with an
		// enclosing catch clause's OWN parameter (a collision B.3.5 SPECIFICALLY
		// permits, see isAnnexBHoistCandidate's own doc) incorrectly resolves
		// to the catch parameter's own slot instead of the true root-hoisted
		// one, several scopes further out (test262 annexB/language/
		// {function,global}-code/*-no-skip-try.js: `try{}catch(f){{function
		// f(){}}}` - the copy-up must reach the ENCLOSING FUNCTION's own `f`,
		// exactly like init()'s own hoistedToRoot computation already knows,
		// not the catch parameter it's nested inside). The non-hoistedToRoot
		// case (conflict-fallback to nearestBlock, or genuinely strict mode)
		// keeps the original walk - narrower, not overridden here.
		VariableDef vf = hoistedToRoot
				? findParentNodeByClass(IContextRootContainer.class).getOwnVariable(getFunctionName())
				: getParent().findVariable(getFunctionName()); // Don't get its own variable!
		if(vf!=null) {
			b.println("{0} = {1};", vf.getJavaVariableValue(), transpileJavaExpression(_jsContext));
		}
	}

	// Transpiled-codegen mirror of hoistValue() above - see its own doc
	// comment for the full spec rationale and the verify-dfs.js motivating
	// case. Called ONLY from ASTProgram's own module-level hoist emission
	// (mirrors the interpreted-mode call site exactly: isModule()-gated,
	// same 3 shapes - bare function, `export function`, `export default
	// function`), BEFORE the "Import hoisting" block emits any
	// importModule()-triggering code, so a dependency eagerly triggered
	// there (itself the eval-rqstd-order.js fix) can already observe this
	// module's own hoistable function/generator's REAL value if it reads
	// it back through a self-/circular import. Reuses the exact same
	// doTranspileAssign() codegen as the normal-position path (already
	// correct - see the `export function`/`export default function` fixes
	// elsewhere in this session) instead of duplicating it.
	public void transpileHoistValue(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(isStatement() && !noBinding) {
			earlyHoistedTranspiled = true;
			doTranspileAssign(jsContext, b);
		}
	}
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
    	if(isAsync()) {
    		b.append("async ");
    	}
    	if(isGenerator()) {
    		b.append("function* ");
    	} else {
    		b.append("function ");
    	}
		if(StringUtil.isNotEmpty(getFunctionName())) {
			b.append(getFunctionName());
		}
		b.append(decompileFunction());
		return b.toString();
	}
	public String decompileFunction() {
    	StringBuilder b = new StringBuilder();
		b.append("(");
		if(getParameters()!=null) {
			b.append(getParameters().decompileParameters());
		}
		b.append(") {\n");
		
		JavaBuilder jb = new JavaBuilder();
		jb.incIndent();
    	if(isForceStrictMode()) {
    		jb.println("\"use strict\";");
    	}
		decompileStatements(jb, getStatements());
		jb.decIndent();
		jb.append("}");
		
		b.append(jb.toString());
		return b.toString();
	}
}
