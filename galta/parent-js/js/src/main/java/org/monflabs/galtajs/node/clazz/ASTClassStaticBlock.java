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
package org.monflabs.galtajs.node.clazz;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.interpreter.InterpretedBlockRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledFunctionRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * Static block.
 *
 * Implements {@link IContextRootContainer} so a {@code var} (or a bare
 * function declaration) declared directly inside this block's own body binds
 * to THIS static block, not to the enclosing script/function - matching spec
 * (ClassStaticBlockDefinitionEvaluation: {@code OrdinaryFunctionCreate}, so
 * the block gets its own FunctionDeclarationInstantiation-equivalent
 * variable environment, isolated from the outer scope and from every OTHER
 * static block in the same class). Storage is delegated straight to
 * {@code block} (already an {@code ASTVarContainer} via {@code ASTBlock}
 * extends {@code ASTStatementList}) rather than duplicated here - this makes
 * {@code ASTClassStaticBlock} the node {@code findParentNodeByClass(
 * IContextRootContainer.class)} stops at (an ordinary {@code ASTBlock} never
 * implements it), while the actual {@code VariableDefContainer} bookkeeping
 * and transpiled codegen (a fresh {@code Object[]} array, emitted by {@code
 * ASTBlock.transpileJavaStatementNoBrace}'s existing, unmodified call to
 * {@code transpilerDeclareStatement()} on itself) stay exactly where they
 * already work correctly for any other block with declared variables.
 * Interpreted mode is unaffected - it already isolates a static block's own
 * {@code var}s at RUNTIME via {@code runStaticBlock()}'s
 * {@code InterpretedBlockRuntimeContext} override below, independent of this
 * static/AST-level container.
 */
public class ASTClassStaticBlock extends ASTClassMember implements IContextRootContainer {

	private ASTBlock block;

	public ASTClassStaticBlock(Token t, ASTBlock block) {
		super(t,"",true,false);
		this.block = assignParent(block);
	}

	public ASTBlock getBlock() {
		return block;
	}

	//
	// IContextRootContainer - delegates to `block`'s own ASTVarContainer
	// storage, see class doc above.
	//
	@Override
	public VariableDef getOwnVariable(String name) {
		return block.getOwnVariable(name);
	}
	@Override
	public VariableDef addVarDeclaration(String varName, VAR_TYPE varType, JSType jsType) {
		return block.addVarDeclaration(varName, varType, jsType);
	}
	@Override
	public int addFunctionDeclaration(ASTFunction function) {
		return block.addFunctionDeclaration(function);
	}
	// A class body (and everything lexically inside it, including a static
	// block) is always strict mode - never blended with any outer sloppy
	// context.
	@Override
	public boolean isGenuinelyStrictMode() {
		return true;
	}

	@Override
	public int getChildCount() {
		// The block plus the inherited children (decorators), as getChild() maps them
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return block; }
			default ->	{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.block = (ASTBlock)node; }
			default ->  { super._setChild(index-1,node); }
		}
	}

	@Override
	public void initClass(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
		// A static block has no key/name to compute, so nothing runs during
		// the class body's first (key-computation) pass - its body only runs
		// later, in source-order sequence with every static field's VALUE
		// initializer, via runStaticBlock() below (see that method's comment
		// and ASTClassDecl.evaluate()'s Initializer.initClass()).
	}
	// Runs this static block's body - called by ASTClassDecl in the SAME
	// deferred, source-ordered second pass that runs every static field's
	// value initializer (see ASTClassField.initClass()'s comment). Per spec
	// (ClassDefinitionEvaluation step 34), static fields and static blocks
	// share one ordered "staticElements" list and run interleaved in source
	// order - e.g. a static block reading an EARLIER static private field
	// (test262 static-init-scope-private.js: `static #test262 = 'private';
	// static { probe = C.#test262; }`) must see it already defined, and a
	// LATER static block/field must run strictly after an earlier one
	// (static-init-sequence.js). Previously ran inline during the first
	// pass, which happened to match source order only because static field
	// values ALSO used to run inline there - now that field values are
	// correctly deferred (to avoid corrupting a LATER element's still-to-
	// be-computed key), static blocks must be deferred to the same pass to
	// keep the two interleaved correctly.
	public void runStaticBlock(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
		// A ClassStaticBlockBody is OrdinaryFunctionCreate'd (spec), so it
		// gets its OWN VariableEnvironment for `var` declarations - unlike
		// an ordinary {} block (InterpretedBlockRuntimeContext's default
		// getVarDeclContext(), which delegates to the nearest enclosing
		// function/program scope), a `var` inside one static block must
		// stay isolated from every OTHER static block and the outer scope.
		InterpretedBlockRuntimeContext blockContext = new InterpretedBlockRuntimeContext(context) {
			@Override
			public Object getThis() {
				return clazz;
			}
			@Override
			public JSRuntimeContext getVarDeclContext() {
				return this;
			}
		};
		blockContext.run( () -> {
			block.evaluateValue(blockContext, new JSResult());
		});
	}

	@Override
	public void transpileInitClassStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
		// A static block has no key/name to compute, so nothing runs during
		// the class body's first (key-computation) pass - its body only
		// runs later, in source-order sequence with every static field's
		// VALUE initializer, via transpileInitStaticValueStatement below
		// (mirrors the interpreter's identical split - see runStaticBlock's
		// own comment).
	}
	// Runs this static block's body - called by ASTClassDecl in the SAME
	// deferred, source-ordered second pass that runs every static field's
	// value initializer (see ASTClassMember.transpileInitStaticValueStatement's
	// and ASTClassField.transpileInitStaticValueStatement's own comments).
	// Per spec (ClassDefinitionEvaluation step 34), static fields and
	// static blocks share one ordered "staticElements" list and run
	// interleaved in source order - exactly mirrors runStaticBlock()'s own
	// interpreted-mode equivalent (and its identical spec citation/test262
	// references). Previously ran inline in transpileInitClassStatement
	// above, in the FIRST pass - correct only by accident, back when static
	// field values also (wrongly) ran inline there; now that field values
	// are correctly deferred, this must move to the same later pass to
	// keep the two interleaved correctly.
	@Override
	public void transpileInitStaticValueStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
		// A static block is inlined directly into initClass() rather than
		// being its own separately-generated Java method (unlike an
		// ordinary method, which always gets its own fresh
		// TranspiledFunctionRuntimeContext) - without this wrapping,
		// super.prop/super()/new.target inside the block would incorrectly
		// resolve through whatever function/context happens to lexically
		// enclose the class declaration, instead of the class itself (spec
		// 15.7.11 ClassStaticBlockDefinitionEvaluation: HomeObject = the
		// class). See TranspiledFieldInitializerRuntimeContext's own doc.
		// The temp variable is needed because Java doesn't allow a local's
		// own initializer to reference the SAME name being declared (which
		// "_ctx = new TranspiledFieldInitializerRuntimeContext(_ctx,...)"
		// would be) - build the new context first under a fresh name, then
		// shadow "_ctx" with it in a nested block for the body statements.
		String tmpCtx = _jsContext.generateUniqueId("fictx");
		b.println("{0} {1} = new TranspiledFieldInitializerRuntimeContext({2},{3},{3});", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), tmpCtx, JSTranspiler.MAIN_CONTEXT, clazzVar);
		b.println("{");
		b.incIndent();
		b.println("{0} {1} = {2};", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT, tmpCtx);
		block.transpileJavaStatement(_jsContext, b);
		b.decIndent();
		b.println("}");
    }
	
	@Override
	public void decompile(JavaBuilder b) {
		b.append("static ");
		block.decompileStatement(b);
	}
}
