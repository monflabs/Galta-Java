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
package org.monflabs.galtajs.node.variable;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.clazz.ASTClassStaticBlock;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTForIn;
import org.monflabs.galtajs.node.control.ASTForOf;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.DisposableResource;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerException;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;


/**
 * `using`/`await using` declaration (explicit resource management).
 *
 *   using v = ...
 *   await using v = ...
 */
public class ASTVariableDeclUsing extends ASTVariableDecl {

	private boolean isAwait;

	public ASTVariableDeclUsing(Token t, boolean isAwait) {
		super(t);
		this.isAwait = isAwait;
	}

	public boolean isAwait() {
		return isAwait;
	}

	@Override
	public VAR_TYPE getVarType() {
		return VAR_TYPE.USING;
	}

	@Override
	protected JSRuntimeContext getDeclContext(JSRuntimeContext context) {
		return context;
	}

	@Override
	protected void init(InitContext initContext) {
		// `await using` is only legal where a plain `await` expression would
		// be - same check ASTAwait.init() performs, replicated here since
		// there's no shared expression node to reuse (this is a declaration,
		// not an expression).
		if(isAwait) {
			IContextRootContainer n = findParentNodeByClass(IContextRootContainer.class);
			if(n instanceof ASTProgram p) {
				p.setAsyncExecution(true);
			} else if(n instanceof ASTFunctionDecl f) {
				if(!f.isAsync()) {
					throw RuntimeUtil.syntaxError("await using must be used in a generator function");
				}
			}
		}
		// Unlike let/const, using/await-using require a plain BindingIdentifier
		// (no destructuring - AddDisposableResource has no meaning for a
		// pattern) on every binding. A for-of loop head ("for (using x of
		// items)") is the one place an Initializer is instead FORBIDDEN - the
		// loop variable's value comes from each iteration, exactly like
		// let/const there - everywhere else (a plain statement) a using
		// declaration with nothing to dispose is itself a SyntaxError, unlike
		// `let x;`.
		boolean isForOfHead = getParent() instanceof ASTForOf;
		for(Entry e: getEntries()) {
			if(!(e.getVarDecl() instanceof ASTIdentifier)) {
				throw new JSParseException(null, this, "Using declaration may not use a binding pattern");
			}
			if(isForOfHead) {
				if(e.getInitNode()!=null) {
					throw new JSParseException(null, this, "for-of using declaration may not have an initializer");
				}
			} else if(e.getInitNode()==null) {
				throw new JSParseException(null, this, "Missing initializer in using declaration");
			}
		}
		checkNotAtTopLevelOfScript();
		super.init(initContext);
	}

	// Early error: "It is a Syntax Error if the goal symbol is Script and
	// UsingDeclaration is not contained, either directly or indirectly,
	// within a Block, ForStatement, ForInOfStatement, FunctionBody,
	// GeneratorBody, AsyncGeneratorBody, AsyncFunctionBody,
	// ClassStaticBlockBody, or ClassBody." Gated on the goal symbol being
	// SCRIPT specifically - a MODULE's own top-level body is a SEPARATE
	// goal symbol, explicitly NOT restricted by this rule (test262
	// language/statements/using/syntax/using.js: `using z = null;` directly
	// at a MODULE's own top level, `flags: [module]`, must parse fine).
	// A direct eval also parses using the Script goal, so this still
	// rejects `eval('using x = null;')` too - both share the same
	// ASTProgram node type, no eval-specific check needed. Walk up the
	// parent chain for the NEAREST qualifying container: a block-like one
	// (Block/for-loop/class static block) always satisfies the rule
	// regardless of what encloses it; a function body satisfies it too
	// (IContextRootContainer, checked after ruling out ASTProgram since
	// ASTProgram is itself one); reaching ASTProgram first means this
	// declaration sits directly in the top-level body with no qualifying
	// container in between - an error only if that top level is a genuine
	// Script, not a Module.
	private void checkNotAtTopLevelOfScript() {
		for(ASTNode n=getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTBlock || n instanceof ASTFor || n instanceof ASTForIn
					|| n instanceof ASTForOf || n instanceof ASTClassStaticBlock) {
				return;
			}
			if(n instanceof ASTProgram program) {
				if(!program.isModule()) {
					throw new JSParseException(null, this, "using declaration not allowed at the top level of a script");
				}
				return;
			}
			if(n instanceof IContextRootContainer) {
				return;
			}
		}
	}

	@Override
	protected void createVariable(JSRuntimeContext declContext, String name, Object value) {
		// The binding already exists (as a TDZ placeholder installed at
		// container-entry, exactly like let/const - see ASTBlock) - initialize
		// it in place rather than re-declaring. AddDisposableResource: for a
		// SYNC `using`, a null/undefined value is bound but registers no
		// disposal at all (not even a no-op entry - spec's own early-return-
		// unused is gated on "hint is sync-dispose" specifically); for
		// `await using` it STILL records a no-op entry (DisposeMethod
		// undefined) - see DisposeResourcesUtil.dispose()'s own doc for why
		// that matters (needsAwait/DisposeResources step 4a). Any other
		// non-object value is a TypeError; an object value must resolve a
		// callable [Symbol.dispose] (or [Symbol.asyncDispose], falling back
		// to [Symbol.dispose], for `await using`) - resolved and captured
		// HERE, once, not re-looked-up at actual disposal time.
		Object v = value!=RuntimeUtil.NOT_AVAILABLE ? value : RuntimeUtil.UNDEFINED;
		if(!RuntimeUtil.isNullOrUndefined(v)) {
			JSEnvironment env = declContext.getEnvironment();
			if(!RuntimeUtil.isObject(env, v)) {
				throw RuntimeUtil.typeError("Cannot dispose non-object value, {0}", RuntimeUtil.objectTypeName(env,v));
			}
			// Moved to DisposeResourcesUtil.resolveDisposeMethod() - shared
			// with DisposableStack/AsyncDisposableStack's own use() method.
			Callable disposeMethod = DisposeResourcesUtil.resolveDisposeMethod(env, v, isAwait);
			declContext.registerDisposableResource(new DisposableResource(v, disposeMethod, isAwait));
		} else if(isAwait) {
			declContext.registerDisposableResource(new DisposableResource(v, null, true));
		}
		declContext.getVariableMap(true).set(name, v);
	}

	// The base ASTVariableDecl.decompileExpression()'s keyword switch only
	// covers CONST/LET/VAR (getVarType() alone can't distinguish `using`
	// from `await using`, both VAR_TYPE.USING) - override here so the
	// decompiled+re-parsed test mode round-trips the declaration keyword
	// instead of silently dropping it (which would decompile to a bare
	// `x = value`, re-parsed as a plain assignment with no disposal at all).
	@Override
	public String decompileExpression() {
		StringBuilder b = new StringBuilder();
		b.append(isAwait ? "await using " : "using ");
		int i = 0;
		for(Entry e: getEntries()) {
			if(i++>0) {
				b.append(", ");
			}
			b.append(e.decompileExpression());
		}
		return b.toString();
	}

	// The inherited ASTVariableDecl.transpileJavaStatement() only emits the
	// plain `variable = initValue;` assignment - it knows nothing about
	// AddDisposableResource, so this overrides it to also register the
	// resource against the nearest enclosing disposal boundary's own list
	// (set up by ASTBlock.transpileStatementsWithDisposal() /
	// ASTFunction's equivalent - found here via
	// jsContext.getDisposablesListVar(), which delegates up the codegen
	// context chain). AddDisposableResource's own null/undefined-skip,
	// TypeError, and GetDisposeMethod logic all live in the shared
	// RuntimeUtil.registerDisposableResource(), reused by both this and the
	// interpreter's createVariable() below.
	// Only the plain declaration-statement form is handled here - a `for
	// (using x of iterable)` loop-head `using` still goes through the
	// inherited ASTVariableDecl.transpileJavaAssignment() (used by for-of),
	// which has no disposal-boundary awareness at all yet; not attempted
	// this round.
	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		String listVar = jsContext.getDisposablesListVar();
		if(listVar==null) {
			throw new JSTranspilerException(null, this, "using/await-using declaration has no enclosing disposal-boundary support in transpiled mode (only Block/function-body positions are supported so far)");
		}
		for(Entry e: getEntries()) {
			ASTIdentifier id = (ASTIdentifier)e.getVarDecl();
			VariableDef variable = findVariable(id.getId());
			if(!variable.getVarType().isDeclaredGlobally()) {
				variable.setTranspilerDeclared(true);
			}
			String tmpVar = jsContext.generateUniqueId("tmp");
			b.println("Object {0} = {1};", tmpVar, JSTranspiler.asValue(jsContext,e.getInitNode()));
			b.println("{0} = {1};", variable.getJavaVariableValue(), tmpVar);
			b.println("RuntimeUtil.registerDisposableResource({0},{1},{2},{3});", JSTranspiler.MAIN_CONTEXT, listVar, tmpVar, isAwait);
		}
	}

	// The `for (using x of iterable)`/`for (await using x of iterable)`
	// loop-head form - unlike the plain declaration-statement form above,
	// this never reaches transpileJavaStatement() at all; ASTForOf's own
	// per-iteration codegen calls transpileJavaAssignment() (inherited from
	// ASTVariableDecl) instead, via ASTFor_.transpileForAssignmentStatement().
	// Overridden here for the same reason: the base implementation only
	// assigns the value, with no notion of AddDisposableResource. Only ever
	// called with a single plain-identifier entry (no destructuring, no
	// Initializer) - init()'s own checkNotAtTopLevelOfScript()/isForOfHead
	// checks, and the ForBinding grammar itself, both rule out anything
	// else. Registers against whatever disposal-boundary list ASTForOf's
	// per-ITERATION ASTBlock.transpileWithDisposal() wrapper set up on
	// `jsContext` (the same forContext instance) just before calling this -
	// see that call site's own doc for why the boundary is per-iteration
	// here, unlike ASTFor's once-at-loop-exit init-clause using.
	@Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
		String listVar = jsContext.getDisposablesListVar();
		if(listVar==null) {
			throw new JSTranspilerException(null, this, "using/await-using for-of loop head has no enclosing disposal-boundary support in transpiled mode");
		}
		TranspilerJavaBuilder b = jsContext.createJavaBuilder();
		for(Entry e: getEntries()) {
			ASTIdentifier id = (ASTIdentifier)e.getVarDecl();
			VariableDef variable = findVariable(id.getId());
			if(!variable.getVarType().isDeclaredGlobally()) {
				variable.setTranspilerDeclared(true);
			}
			b.println("{0} = {1};", variable.getJavaVariableValue(), rightValue);
			b.println("RuntimeUtil.registerDisposableResource({0},{1},{2},{3});", JSTranspiler.MAIN_CONTEXT, listVar, variable.getJavaVariableValue(), isAwait);
		}
		return b.toString();
	}
}
