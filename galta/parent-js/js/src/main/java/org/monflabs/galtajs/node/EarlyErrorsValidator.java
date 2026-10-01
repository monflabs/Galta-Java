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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTCase;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTForIn;
import org.monflabs.galtajs.node.control.ASTForOf;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.ASTSwitchCaseBlock;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.node.clazz.ASTClassField;
import org.monflabs.galtajs.node.clazz.ASTClassStaticBlock;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;

/**
 * Context-dependent early errors that the parser and the nodes' own init()
 * cannot see locally, checked by one AST walk right after init (the same
 * pattern as {@link PrivateNameValidator}):
 * <ul>
 * <li>a SuperCall ({@code super()}) is only valid in the constructor of a
 * class that has a heritage, including arrow functions nested in it;</li>
 * <li>a SuperProperty ({@code super.x}) is only valid in a method (object
 * or class method, accessor, constructor), a class field initializer or a
 * class static block, including arrow functions nested in them;</li>
 * <li>a class field initializer or a class static block cannot reference
 * {@code arguments} (outside of a nested non-arrow function);</li>
 * <li>in a block, a case block or a for statement with a lexical head, a
 * lexically declared name cannot be declared twice (except for two plain
 * function declarations in sloppy code, Annex B.3.2.4) nor be a var
 * declared name of the block.</li>
 * </ul>
 * Code outside of any function (a script or an eval) is left to
 * ASTProgram.checkEvalCallerRestrictions(), which knows the eval caller's
 * context.
 */
public class EarlyErrorsValidator {

	private EarlyErrorsValidator() {
	}

	public static void check(ASTNode root) {
		check(root, false);
	}

	// importExportInScripts: JSConfiguration.supportImportExportInScripts()
	public static void check(ASTNode root, boolean importExportInScripts) {
		boolean module = root instanceof ASTProgram p && p.isModule();
		// import/export declarations are module items (GaltaJS extension:
		// also allowed at the top level of a script)
		if(root instanceof ASTProgram program && !module && !importExportInScripts) {
			int n = program.getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = ASTNode.skipTransparent(program.getChild(i));
				if(child instanceof org.monflabs.galtajs.node.control.ASTImpExp) {
					throw new JSParseException(null, child, "Cannot use import/export declarations outside a module");
				}
			}
		}
		if(module) {
			ModuleEarlyErrors.check((ASTProgram)root);
		}
		if(root instanceof ASTProgram program) {
			for(ASTNode s: program.getYieldLabelledStatements()) {
				if(isStrictAt(s)) {
					throw new JSParseException(null, s, "'yield' is not a valid label in strict mode code");
				}
			}
		}
		// Script and module code (not eval code, which ASTProgram.
		// checkEvalCallerRestrictions() checks against its caller) has no
		// new.target and no super
		boolean topLevel = root instanceof ASTProgram p && !p.isEval() && !p.isCommonJS();
		walk(root, !topLevel, !topLevel, false, module, module, !topLevel);
	}

	private static void walk(ASTNode node, boolean superCall, boolean superProperty, boolean noArguments, boolean noAwait, boolean module, boolean newTarget) {
		if(node==null) {
			return;
		}
		// (a function expression's own name is in the function's scope)
		if(noAwait && ("await".equals(node instanceof ASTIdentifier id ? id.getId()
				: node instanceof ASTFunction fn && fn.isStatement() ? fn.getFunctionName()
				: node instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass cls ? cls.getClassName() : null))) {
			throw new JSParseException(null, node, "'await' is not a valid identifier in a module or a class static block");
		}
		// A class name is a BindingIdentifier in strict code
		if(node instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass cls && cls.getClassName()!=null
				&& (ASTNode.isStrictFutureReservedWord(cls.getClassName()) || "let".equals(cls.getClassName())
					|| "yield".equals(cls.getClassName()) || "eval".equals(cls.getClassName()) || "arguments".equals(cls.getClassName()))) {
			throw new JSParseException(null, node, "'{0}' is not a valid class name", cls.getClassName());
		}
		if(node instanceof ASTSuperCtor && !superCall) {
			throw new JSParseException(null, node, "'super' keyword unexpected here: super() is only valid in a derived class constructor");
		}
		if(node instanceof ASTSuperMember && !superProperty) {
			throw new JSParseException(null, node, "'super' keyword unexpected here: super properties are only valid in methods");
		}
		if(noArguments && node instanceof ASTIdentifier id && Arguments.ARGUMENTS.equals(id.getId())) {
			throw new JSParseException(null, node, "'arguments' is not allowed in class field initializer or static initialization block");
		}
		if(node instanceof org.monflabs.galtajs.node.clazz.ASTClassGetter || node instanceof org.monflabs.galtajs.node.clazz.ASTClassSetter) {
			checkAccessorArity(node);
		}
		if(node instanceof ASTFunction arrow && arrow.isArrow() && noAwait && !module) {
			// In a class static block, an arrow function's parameters still
			// cannot use "await" as an identifier, its body can
			int n = node.getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = node.getChild(i);
				walk(child, superCall, superProperty, noArguments, child==arrow.getParameters(), module, newTarget);
			}
			return;
		}
		if(node instanceof ASTFunction fn && !fn.isArrow()) {
			superCall = fn.isDerivedClassConstructor();
			newTarget = true;
			superProperty = fn.isMethod() || fn.isClassConstructor();
			noArguments = false;
			noAwait = module;
		} else if(node instanceof ASTClassStaticBlock) {
			superCall = false;
			superProperty = true;
			noArguments = true;
			noAwait = true;
			newTarget = true;
		} else if(node instanceof ASTClassField field) {
			// ClassElement : FieldDefinition - neither "constructor" nor a
			// static "prototype" is a valid (non-computed) field name
			String name = field.getNameNode()==null ? field.getName() : null;
			if(!field.isPrivate() && ("constructor".equals(name) || (field.isStatic() && "prototype".equals(name)))) {
				throw new JSParseException(null, node, "Classes may not have a field named '{0}'", name);
			}
			// Only the initializer runs in the field's own context: a computed
			// key is evaluated in the scope enclosing the class
			int n = node.getChildCount();
			for(int i=0; i<n; i++) {
				ASTNode child = node.getChild(i);
				if(child==field.getBody()) {
					walk(child, false, true, true, noAwait, module, true);
				} else {
					walk(child, superCall, superProperty, noArguments, noAwait, module, newTarget);
				}
			}
			return;
		}
		if(node instanceof ASTBlock block) {
			checkBlockDeclarations(block, block.getStatements());
		} else if(node instanceof ASTSwitchCaseBlock caseBlock) {
			java.util.List<ASTNode> all = new java.util.ArrayList<>();
			for(ASTNode c: caseBlock.getCases()) {
				if(c instanceof ASTCase cs && cs.getStatements()!=null) {
					all.addAll(java.util.Arrays.asList(cs.getStatements()));
				}
			}
			// A using declaration is not allowed directly in a case clause
			for(ASTNode st: all) {
				if(ASTNode.skipTransparent(st) instanceof ASTVariableDecl decl && decl.getVarType()==VAR_TYPE.USING) {
					throw new JSParseException(null, st, "A using declaration is not allowed directly in a case clause");
				}
			}
			checkBlockDeclarations(caseBlock, all.toArray(new ASTNode[0]));
		} else if(node instanceof ASTFor f && ASTNode.skipTransparent(f.getInitNode()) instanceof ASTVariableDecl decl && decl.getVarType()!=VAR_TYPE.VAR) {
			checkBlockDeclarations(f, new ASTNode[] { decl, f.getBodyNode() });
		} else if(node instanceof ASTForIn f && ASTNode.skipTransparent(f.getVarDecl()) instanceof ASTVariableDecl decl && decl.getVarType()!=VAR_TYPE.VAR) {
			checkBlockDeclarations(f, new ASTNode[] { decl, f.getBodyNode() });
		} else if(node instanceof ASTForOf f && ASTNode.skipTransparent(f.getVarDecl()) instanceof ASTVariableDecl decl && decl.getVarType()!=VAR_TYPE.VAR) {
			checkBlockDeclarations(f, new ASTNode[] { decl, f.getBodyNode() });
		}
		// import/export declarations only appear at the top level of a module
		// (GaltaJS also tolerates them at the top level of a script)
		if(node instanceof org.monflabs.galtajs.node.control.ASTImpExp && !(ASTNode.skipTransparentParent(node) instanceof ASTProgram)) {
			throw new JSParseException(null, node, "import and export declarations may only appear at the top level of a module");
		}
		// A const declaration needs an initializer, except as a for-in/of head;
		// a for-in/of head declaration has none (Annex B.3.5 allows it for a
		// sloppy "for (var x = e in o)" only)
		if(node instanceof ASTVariableDecl decl) {
			ASTNode parent = ASTNode.skipTransparentParent(node);
			boolean forInOfHead = (parent instanceof ASTForIn fi && ASTNode.skipTransparent(fi.getVarDecl())==node)
					|| (parent instanceof ASTForOf fo && ASTNode.skipTransparent(fo.getVarDecl())==node);
			for(ASTVariableDecl.Entry e: decl.getEntries()) {
				if(forInOfHead) {
					if(e.getInitNode()!=null && !(parent instanceof ASTForIn && decl.getVarType()==VAR_TYPE.VAR
							&& e.getVarDecl() instanceof ASTIdentifier && !isStrictAt(node))) {
						throw new JSParseException(null, node, "for-in/for-of loop variable declaration may not have an initializer");
					}
				} else if(decl.getVarType()==VAR_TYPE.CONST && e.getInitNode()==null) {
					throw new JSParseException(null, node, "Missing initializer in const declaration");
				}
			}
		}
		// A function whose body is strict cannot be named eval or arguments
		if(node instanceof ASTFunctionDecl named && !named.isMethod() && named.isGenuinelyStrictMode()
				&& ("eval".equals(named.getFunctionName()) || "arguments".equals(named.getFunctionName()))) {
			throw new JSParseException(null, node, "'{0}' is not allowed as a function name in strict mode", named.getFunctionName());
		}
		if(node instanceof org.monflabs.galtajs.node.literal.ASTObjectLiteral obj) {
			obj.checkShorthands();
			obj.checkEarlyErrors();
		}
		// "let" is never a lexically declared name
		if(node instanceof ASTVariableDecl lexical && lexical.getVarType()!=VAR_TYPE.VAR) {
			for(ASTVariableDecl.Entry e: lexical.getEntries()) {
				e.forEachVarName(name -> {
					if("let".equals(name)) {
						throw new JSParseException(null, lexical, "let is disallowed as a lexically bound name");
					}
				});
			}
		}
		// A catch parameter pattern binds each name once
		if(node instanceof org.monflabs.galtajs.node.control.ASTCatch c && c.getBindingNode() instanceof org.monflabs.galtajs.node.literal.ASTContainerLiteral pattern) {
			Set<String> names = new HashSet<>();
			pattern.forEachVarName(name -> {
				if(!names.add(name)) {
					throw new JSParseException(null, c, "Duplicate catch parameter '{0}'", name);
				}
			});
		}
		// A catch parameter cannot also be lexically declared (let, const,
		// class, function) directly in the catch block
		if(node instanceof org.monflabs.galtajs.node.control.ASTCatch c && c.getBodyNode()!=null && c.getBodyNode().getStatements()!=null) {
			Set<String> params = new HashSet<>();
			if(c.getBindingNode() instanceof org.monflabs.galtajs.node.literal.ASTContainerLiteral pattern) {
				pattern.forEachVarName(params::add);
			} else if(c.getIdentifier()!=null) {
				params.add(c.getIdentifier());
			}
			for(ASTNode st: c.getBodyNode().getStatements()) {
				ASTNode s = ASTNode.skipTransparent(st);
				java.util.List<String> names = new java.util.ArrayList<>();
				if(s instanceof ASTVariableDecl decl && decl.getVarType()!=VAR_TYPE.VAR) {
					for(ASTVariableDecl.Entry e: decl.getEntries()) {
						e.forEachVarName(names::add);
					}
				} else if(s instanceof ASTClassDecl cls && cls.isStatement() && cls.getClassName()!=null) {
					names.add(cls.getClassName());
				} else if(s instanceof ASTFunctionDecl fn && fn.isStatement() && fn.getFunctionName()!=null) {
					names.add(fn.getFunctionName());
				}
				for(String name: names) {
					if(params.contains(name)) {
						throw new JSParseException(null, s, "Identifier '{0}' has already been declared", name);
					}
				}
			}
		}
		// The right operand of "in" is a ShiftExpression, never an arrow function
		if(node instanceof org.monflabs.galtajs.node.binaryop.ASTIn in
				&& in.getRightNode() instanceof ASTFunction rhs && rhs.isArrow()) {
			throw new JSParseException(null, node, "Unexpected arrow function as the right operand of 'in'");
		}
		if(node instanceof ASTNewMember && !newTarget) {
			throw new JSParseException(null, node, "new.target expression is not allowed here");
		}
		if(node instanceof org.monflabs.galtajs.node.binaryop.ASTPower pow && isUnaryExpression(pow.getLeftNode())) {
			throw new JSParseException(null, node, "Unary operator used immediately before exponentiation expression, parentheses are required");
		}
		if(node instanceof org.monflabs.galtajs.node.binaryop.ASTNullCoalescing nc
				&& (isAndOr(nc.getLeftNode()) || isAndOr(nc.getRightNode()))
				|| isAndOr(node) && (((org.monflabs.galtajs.node.binaryop.ASTBinaryOp)node).getLeftNode() instanceof org.monflabs.galtajs.node.binaryop.ASTNullCoalescing
					|| ((org.monflabs.galtajs.node.binaryop.ASTBinaryOp)node).getRightNode() instanceof org.monflabs.galtajs.node.binaryop.ASTNullCoalescing)) {
			throw new JSParseException(null, node, "?? cannot be mixed with && or || without parentheses");
		}
		if(node instanceof org.monflabs.galtajs.node.unaryop.ASTDelete strictDel && skipParens(strictDel.getNode()) instanceof ASTIdentifier && isStrictAt(node)) {
			throw new JSParseException(null, node, "Delete of an unqualified identifier in strict mode");
		}
		if(node instanceof org.monflabs.galtajs.node.unaryop.ASTDelete del) {
			ASTNode target = del.getNode();
			while(target instanceof org.monflabs.galtajs.node.unaryop.ASTParen p) {
				target = p.getNode();
			}
			if(target instanceof ASTMember m && m.getMemberName()!=null && m.getMemberName().startsWith("#")) {
				throw new JSParseException(null, node, "Private fields cannot be deleted");
			}
		}
		if(node instanceof ASTMember m && m.getNode() instanceof ASTSuperMember && m.getMemberName()!=null && m.getMemberName().startsWith("#")) {
			throw new JSParseException(null, node, "Unexpected private name after super");
		}
		if(node instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass cls) {
			checkClassElements(cls);
		}
		// Strict mode reserved words as an IdentifierReference or a
		// BindingIdentifier (declarations are already checked by
		// checkStrictBindingName(), except "yield"/"let"/"static")
		if(node instanceof ASTIdentifier id && isStrictReservedWord(id.getId()) && isStrictAt(node)) {
			throw new JSParseException(null, node, "Unexpected strict mode reserved word '{0}'", id.getId());
		}
		if(node instanceof ASTFunction fn) {
			checkFunctionYieldAwait(fn);
		}
		// A class static block is not a function body: no return statement
		if(node instanceof org.monflabs.galtajs.node.control.ASTReturn) {
			for(INode p=node.getParent(); p!=null && !(p instanceof ASTFunction); p=p.getParent()) {
				if(p instanceof ASTClassStaticBlock) {
					throw new JSParseException(null, node, "A return statement is not allowed in a class static block");
				}
			}
		}
		if(node.getStatementPosition()!=0) {
			checkStatementPosition(node, node.getStatementPosition());
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			walk(node.getChild(i), superCall, superProperty, noArguments, noAwait, module, newTarget);
		}
	}

	// The head of a for(;;) statement is an Expression[~In] (or declarations
	// whose initializers are [~In]): an "in" operator is only allowed where a
	// production resets the parameter - in parentheses, brackets, arguments,
	// a function or the middle operand of a conditional
	public static void checkNoIn(ASTNode node) {
		node = ASTNode.skipTransparent(node);
		if(node==null) {
			return;
		}
		if(node instanceof org.monflabs.galtajs.node.binaryop.ASTIn) {
			throw new JSParseException(null, node, "Unexpected 'in' in the head of a for statement");
		}
		if(node instanceof ASTVariableDecl decl) {
			for(ASTVariableDecl.Entry e: decl.getEntries()) {
				checkNoIn(e.getInitNode());
			}
		} else if(node instanceof org.monflabs.galtajs.node.ternaryop.ASTTernaryOp t) {
			// The condition and the else branch (ASTTernaryTest stores it as
			// op2, the then branch as op3), not the then branch
			checkNoIn(t.getOp1Node());
			checkNoIn(t.getOp2Node());
		} else if(node instanceof org.monflabs.galtajs.node.binaryop.ASTBinaryOp b) {
			checkNoIn(b.getLeftNode());
			checkNoIn(b.getRightNode());
		} else if(node instanceof org.monflabs.galtajs.node.assignop.ASTAbstractAssign a) {
			checkNoIn(a.getRightNode());
		} else if(node instanceof ASTExpression
				|| node instanceof org.monflabs.galtajs.node.control.ASTYield
				|| node instanceof org.monflabs.galtajs.node.control.ASTYieldStar
				|| (node instanceof org.monflabs.galtajs.node.unaryop.ASTUnaryOp && !(node instanceof org.monflabs.galtajs.node.unaryop.ASTParen)
					&& !(node instanceof ASTImportCall))) {
			int n = node.getChildCount();
			for(int i=0; i<n; i++) {
				checkNoIn(node.getChild(i));
			}
		}
	}

	// yield/await rules on a function's name and an arrow's parameters
	private static void checkFunctionYieldAwait(ASTFunction fn) {
		String name = fn.isMethod() || fn.isArrow() ? null : fn.getFunctionName();
		if(name!=null) {
			// A generator/async function expression's own name is in its own
			// [Yield]/[Await] context
			if(!fn.isStatement() && (("yield".equals(name) && fn.isGenerator()) || ("await".equals(name) && fn.isAsync()))) {
				throw new JSParseException(null, fn, "'{0}' is not a valid name for this function expression", name);
			}
			// A function declaration's name is in the context of the
			// enclosing code: "await" inside an async function
			if(fn.isStatement() && "await".equals(name) && enclosingFunctionIsAsync(fn)) {
				throw new JSParseException(null, fn, "'await' is not a valid function name in an async function");
			}
			if(fn.isStatement() && "yield".equals(name) && enclosingFunction(fn) instanceof ASTFunction outer && outer.isGenerator()) {
				throw new JSParseException(null, fn, "'yield' is not a valid function name in a generator");
			}
			if(("yield".equals(name) || "let".equals(name) || "static".equals(name))
					&& (fn.isGenuinelyStrictMode() || (fn.getParent() instanceof ASTNode parent && isStrictAt(parent)))) {
				throw new JSParseException(null, fn, "'{0}' is not a valid function name in strict mode code", name);
			}
		}
		if(fn.isArrow() && fn.getParameters()!=null) {
			// ArrowParameters cannot contain a YieldExpression or an AwaitExpression
			ASTNode found = findInParameters(fn.getParameters(), false);
			if(found!=null) {
				throw new JSParseException(null, found, "Arrow function parameters cannot contain a yield or await expression");
			}
			// The parameters of an async arrow function are in an [Await]
			// context, nested arrow functions included
			if(fn.isAsync()) {
				found = findInParameters(fn.getParameters(), true);
				if(found!=null) {
					throw new JSParseException(null, found, "'await' is not a valid identifier in async arrow function parameters");
				}
			}
		}
	}

	// awaitIdentifier false: the first yield/await expression, not entering
	// nested functions; true: the first "await" identifier, entering nested
	// arrow functions but not other functions
	private static ASTNode findInParameters(ASTNode node, boolean awaitIdentifier) {
		if(node==null) {
			return null;
		}
		if(node instanceof ASTFunction f && (!awaitIdentifier || !f.isArrow())) {
			return null;
		}
		if(awaitIdentifier ? node instanceof ASTIdentifier id && "await".equals(id.getId())
				: node instanceof org.monflabs.galtajs.node.control.ASTYield || node instanceof org.monflabs.galtajs.node.control.ASTYieldStar
					|| node instanceof org.monflabs.galtajs.node.unaryop.ASTAwait) {
			return node;
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			ASTNode r = findInParameters(node.getChild(i), awaitIdentifier);
			if(r!=null) {
				return r;
			}
		}
		return null;
	}

	private static ASTFunction enclosingFunction(ASTNode node) {
		for(INode p=node.getParent(); p!=null; p=p.getParent()) {
			if(p instanceof ASTFunction f) {
				return f;
			}
		}
		return null;
	}

	// An arrow function's body inherits the [Await] context of the enclosing code
	private static boolean enclosingFunctionIsAsync(ASTNode node) {
		for(INode p=node.getParent(); p!=null; p=p.getParent()) {
			if(p instanceof ASTFunction f && (f.isAsync() || !f.isArrow())) {
				return f.isAsync();
			}
			if(p instanceof ASTClassStaticBlock) {
				return false;
			}
		}
		return false;
	}

	private static ASTNode skipParens(ASTNode n) {
		while(n instanceof org.monflabs.galtajs.node.unaryop.ASTParen p) {
			n = p.getNode();
		}
		return n;
	}

	private static boolean isAndOr(ASTNode n) {
		return n instanceof org.monflabs.galtajs.node.binaryop.ASTAnd || n instanceof org.monflabs.galtajs.node.binaryop.ASTOr;
	}

	// ExponentiationExpression: the base is an UpdateExpression, never a
	// UnaryExpression like -x, !x, typeof x (parentheses are required)
	private static boolean isUnaryExpression(ASTNode n) {
		return n instanceof org.monflabs.galtajs.node.unaryop.ASTMinus || n instanceof org.monflabs.galtajs.node.unaryop.ASTPlus
				|| n instanceof org.monflabs.galtajs.node.unaryop.ASTNot || n instanceof org.monflabs.galtajs.node.unaryop.ASTBitNot
				|| n instanceof org.monflabs.galtajs.node.unaryop.ASTTypeof || n instanceof org.monflabs.galtajs.node.unaryop.ASTVoid
				|| n instanceof org.monflabs.galtajs.node.unaryop.ASTDelete || n instanceof org.monflabs.galtajs.node.unaryop.ASTAwait;
	}

	private static boolean isStrictReservedWord(String name) {
		return "yield".equals(name) || "let".equals(name) || ASTNode.isStrictFutureReservedWord(name);
	}

	// Whether the code at node is strict: class bodies always are, otherwise
	// its nearest function or program decides.
	static boolean isStrictAt(ASTNode node) {
		INode child = null;
		for(INode n=node; n!=null; child=n, n=n.getParent()) {
			// A class is strict code, except for its decorators
			if(n instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass cls
					&& (cls.getDecorators()==null || !java.util.Arrays.asList(cls.getDecorators()).contains(child))) {
				return true;
			}
			if(n instanceof IContextRootContainer root) {
				return root.isGenuinelyStrictMode();
			}
		}
		return false;
	}

	// ClassBody early errors on "constructor": #constructor is never a valid
	// private name, the constructor is a plain method (no accessor,
	// generator or async) and a class has at most one.
	private static void checkClassElements(org.monflabs.galtajs.node.clazz.ASTBaseClass cls) {
		ASTNode[] elements = cls.getElements();
		if(elements==null) {
			return;
		}
		int constructors = 0;
		for(ASTNode e: elements) {
			if(!(e instanceof org.monflabs.galtajs.node.clazz.ASTClassMember m) || e instanceof ASTClassStaticBlock
					|| m.getNameNode()!=null) {
				continue;
			}
			if(m.isStatic() && !m.isPrivate() && "prototype".equals(m.getName())) {
				throw new JSParseException(null, e, "Classes may not have a static property named 'prototype'");
			}
			if(!"constructor".equals(m.getName())) {
				continue;
			}
			if(m.isPrivate()) {
				throw new JSParseException(null, e, "Classes may not have a private element named '#constructor'");
			}
			if(m.isStatic()) {
				continue;
			}
			if(e instanceof org.monflabs.galtajs.node.clazz.ASTClassGetter || e instanceof org.monflabs.galtajs.node.clazz.ASTClassSetter) {
				throw new JSParseException(null, e, "Class constructor may not be an accessor");
			}
			if(e instanceof org.monflabs.galtajs.node.clazz.ASTClassMethod cm) {
				if(cm.isAutoGenerated()) {
					continue;
				}
				ASTFunction fn = cm.getFunctionDecl();
				if(fn!=null && (fn.isGenerator() || fn.isAsync())) {
					throw new JSParseException(null, e, "Class constructor may not be a generator or an async method");
				}
				if(++constructors>1) {
					throw new JSParseException(null, e, "A class may only have one constructor");
				}
			}
		}
	}

	// A getter has no parameter, a setter exactly one (not a rest parameter)
	private static void checkAccessorArity(ASTNode accessor) {
		boolean getter = accessor instanceof org.monflabs.galtajs.node.clazz.ASTClassGetter;
		ASTFunction fn = getter ? ((org.monflabs.galtajs.node.clazz.ASTClassGetter)accessor).getFunctionDecl()
				: ((org.monflabs.galtajs.node.clazz.ASTClassSetter)accessor).getFunctionDecl();
		checkAccessorParameters(accessor, fn, getter);
	}

	// A getter has no parameter, a setter exactly one (not a rest parameter)
	public static void checkAccessorParameters(ASTNode accessor, ASTFunction fn, boolean getter) {
		if(fn!=null && fn.getParameters()!=null) {
			org.monflabs.galtajs.node.literal.ASTArrayLiteral params = fn.getParameters();
			int count = params.getChildCount();
			boolean rest = count>0 && params.getChild(count-1) instanceof org.monflabs.galtajs.node.literal.ASTArrayLiteral.InitializerSpread;
			if(getter ? count!=0 : (count!=1 || rest)) {
				throw new JSParseException(null, accessor, getter ? "Getter must not have any formal parameters" : "Setter must have exactly one formal parameter");
			}
		}
	}

	// The Statement of a loop, with, if or labelled statement is never a
	// Declaration. Annex B.3.2/B.3.4 let sloppy code use a plain function
	// declaration as a labelled statement or as an if clause (not both).
	private static void checkStatementPosition(ASTNode s, int position) {
		boolean declaration;
		if(s instanceof ASTVariableDecl decl) {
			declaration = decl.getVarType()!=VAR_TYPE.VAR;
		} else if(s instanceof ASTClassDecl cls) {
			declaration = cls.isStatement();
		} else if(s instanceof ASTFunctionDecl fn && fn.isStatement()) {
			IContextRootContainer root = s.findParentNodeByClass(IContextRootContainer.class);
			boolean strict = root!=null && root.isGenuinelyStrictMode();
			boolean annexB = !strict && !fn.isGenerator() && !fn.isAsync()
					&& (position & ASTNode.POSITION_BODY)==0
					&& position!=(ASTNode.POSITION_IF_CLAUSE|ASTNode.POSITION_LABELLED);
			declaration = !annexB;
		} else {
			declaration = false;
		}
		if(declaration) {
			throw new JSParseException(null, s, "Lexical declaration cannot appear in a single-statement context");
		}
	}

	private static void checkBlockDeclarations(ASTNode block, ASTNode[] statements) {
		if(statements==null) {
			return;
		}
		IContextRootContainer root = block.findParentNodeByClass(IContextRootContainer.class);
		boolean strict = root!=null && root.isGenuinelyStrictMode();
		// name -> true when every declaration so far is a plain function declaration
		Map<String,Boolean> lexical = new HashMap<>();
		for(ASTNode st: statements) {
			ASTNode s = ASTNode.skipTransparent(st);
			if(s instanceof ASTVariableDecl decl && decl.getVarType()!=VAR_TYPE.VAR) {
				for(ASTVariableDecl.Entry e: decl.getEntries()) {
					e.forEachVarName(name -> declareLexical(lexical, name, false, strict, s));
				}
			} else if(s instanceof ASTClassDecl cls && cls.isStatement() && cls.getClassName()!=null) {
				declareLexical(lexical, cls.getClassName(), false, strict, s);
			} else if(s instanceof ASTFunctionDecl fn && fn.isStatement() && fn.getFunctionName()!=null) {
				declareLexical(lexical, fn.getFunctionName(), !fn.isGenerator() && !fn.isAsync(), strict, s);
			}
		}
		if(lexical.isEmpty()) {
			return;
		}
		Set<String> varNames = new HashSet<>();
		for(ASTNode st: statements) {
			collectVarNames(st, varNames);
		}
		for(String name: varNames) {
			if(lexical.containsKey(name)) {
				throw new JSParseException(null, block, "Identifier '{0}' has already been declared", name);
			}
		}
	}

	private static void declareLexical(Map<String,Boolean> lexical, String name, boolean plainFunction, boolean strict, ASTNode node) {
		Boolean previous = lexical.get(name);
		if(previous!=null && !(previous && plainFunction && !strict)) {
			throw new JSParseException(null, node, "Identifier '{0}' has already been declared", name);
		}
		lexical.put(name, plainFunction);
	}

	// VarDeclaredNames: the var declarations of a statement, not entering functions or classes
	static void collectVarNames(ASTNode node, Set<String> names) {
		if(node==null || node instanceof ASTFunction || node instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass) {
			return;
		}
		if(node instanceof ASTVariableDecl decl && decl.getVarType()==VAR_TYPE.VAR) {
			for(ASTVariableDecl.Entry e: decl.getEntries()) {
				e.forEachVarName(names::add);
			}
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			collectVarNames(node.getChild(i), names);
		}
	}
}
