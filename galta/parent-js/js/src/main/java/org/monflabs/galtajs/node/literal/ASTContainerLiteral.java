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
package org.monflabs.galtajs.node.literal;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.node.control.IVarDeclarator;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.util.StringUtil;


/**
 * Base for Array/Object Literal Node.
 */
public abstract class ASTContainerLiteral extends ASTNode implements IVarDeclarator {

	public ASTContainerLiteral(Token t) {
		super(t);
	}

	// Set when a "," follows a spread/rest element: legal in a literal
	// ("[...a, b]"), a SyntaxError once the literal is a destructuring
	// pattern or a parameter list, where the rest element must come last
	// and cannot have a trailing comma ("[...a,] = x", "function f(...a,){}").
	private boolean commaAfterSpread;

	/** Called by the parser for each "," between two entries. */
	public void markComma() {
		if(lastIsSpread()) {
			commaAfterSpread = true;
		}
	}

	public boolean hasCommaAfterSpread() {
		return commaAfterSpread;
	}

	protected abstract boolean lastIsSpread();

	/**
	 * Early errors of this literal used as a destructuring pattern: a
	 * BindingPattern ({@code binding}: declarations, parameters, catch) only
	 * binds identifiers, an AssignmentPattern assigns to simple targets, and
	 * in both the rest element comes last, without an initializer.
	 */
	public abstract void checkPattern(boolean binding, boolean strict);

	// One pattern element (without its default value): a nested pattern or a leaf target.
	protected static void checkPatternTarget(ASTNode target, boolean binding, boolean strict) {
		ASTNode n = target;
		while(n instanceof org.monflabs.galtajs.node.debug.ASTDebugHook h) {
			n = h.getNode();
		}
		if(n instanceof ASTContainerLiteral lit) {
			lit.checkPattern(binding, strict);
		} else if(binding) {
			if(!(n instanceof ASTIdentifier)) {
				throw new JSParseException(null, target, "Invalid destructuring binding target");
			}
		} else {
			checkAssignmentTarget(n, AssignmentUse.NESTED, strict);
		}
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
    public abstract void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType);
	// isAssignmentContext distinguishes an AssignmentPattern (plain "[a] = x"/
	// bare for-of/for-in loop variable) from a BindingPattern (declaration,
	// function/catch parameter): an empty ArrayAssignmentPattern ("[] = x")
	// must still call GetIterator+IteratorClose per spec even though it binds
	// nothing, while an empty ArrayBindingPattern ("const [] = x") is a true
	// no-op that never touches the iterator at all - see ASTArrayLiteral's
	// override. ObjectAssignmentPattern's empty case needs no such
	// distinction (RequireObjectCoercible only, no iterator involved either
	// way), so ASTObjectLiteral's override just ignores the flag.
	public abstract void assign(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, Object value, JSResult result, boolean isAssignmentContext);
	// A variableFactory that ALSO performs spec's ResolveBinding (9.4.2) at
	// the exact point PropertyBindingInitialization/KeyedBindingInitialization
	// requires it - BEFORE GetV reads the source property, not after (test262
	// destructuring/binding/keyed-destructuring-property-reference-target-
	// evaluation-order-with-bindings.js). Only a `var` declaration's
	// variableFactory needs this: `var`'s own ResolveBinding walks the
	// running execution context's ambient scope chain, which a `with`
	// object can shadow/intercept (observable via a Proxy `has` trap) -
	// `let`/`const` and every other destructuring context (function params,
	// catch clause, for-in/of, plain assignment patterns) always resolve
	// directly against an already-known target environment/binding, never
	// walking an ambient `with` chain, so their plain BiConsumer
	// variableFactory is unchanged and correctly does nothing extra here.
	// See ASTVariableDecl's own var-declaration call site.
	public interface WithScopeResolvingFactory extends BiConsumer<String,Object> {
		void resolveBinding(String name);
	}
	public abstract void forEachVarName(Consumer<String> callback);
	// jsContext/b: the CURRENT (possibly split/nested-block) generator context
	// and its builder, needed when a computed property key (ASTObjectLiteral
	// only - arrays have no computed keys) must be evaluated into a temp var
	// before it can be used as a path segment - jsContext must be passed
	// explicitly rather than recovered via b.getContext(), since a builder
	// created higher up a split/nested scope chain doesn't necessarily share
	// the current call's actual variable-resolution context. Both are null
	// only from the optimizer's ASTVariableDecl.updateOptimizedContext
	// pre-pass, which never reaches a computed-key leaf anyway.
	public abstract void destructParameters(VariableFactory varFactory, Object[] path, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue);

	// NamedEvaluation for a destructuring default value (IsAnonymousFunctionDefinition):
	// "const [f = function(){}] = []" / "const {g = class{}} = {}" must infer
	// the anonymous function/class expression's own .name from the binding
	// target it defaults into - same mechanism ASTVariableDecl.Entry.init()
	// already uses for a plain "const f = function(){}" declaration, just
	// applied at the point a destructuring pattern's own default-value
	// Initializer is built instead. Setting the name statically on the AST
	// node here (once, at tree-init time) rather than after each evaluation
	// works because the function/class object is only ever created when this
	// Initializer's own expression node is evaluated (i.e. exactly when the
	// default fires), and every evaluation of that same node reads its name
	// back off the AST node.
	public static void inferAnonymousName(ASTNode exprNode, String name) {
		// skipTransparent() unwraps a ParenthesizedExpression wrapper, which
		// HasName/IsFunctionDefinition propagate through per spec - see ASTAssign.
		exprNode = skipTransparent(exprNode);
		if(exprNode instanceof ASTFunction fd && StringUtil.isEmpty(fd.getFunctionName())) {
			fd.setFunctionName(name);
		} else if(exprNode instanceof ASTClassDecl cd && StringUtil.isEmpty(cd.getClassName())) {
			cd.setClassName(name);
		}
	}

	public static Object[] addPath(Object[] path, Object value) {
		if(path!=null) {
			Object[] pp = new Object[path.length+1];
			System.arraycopy(path,0,pp,0,path.length);
			pp[path.length] = value;
			return pp;
		} else {
			return new Object[] {value};
		}
	}
}