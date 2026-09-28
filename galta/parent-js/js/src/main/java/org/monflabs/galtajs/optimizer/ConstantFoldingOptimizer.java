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
package org.monflabs.galtajs.optimizer;

import java.io.PrintStream;

import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.NodeFactory;
import org.monflabs.galtajs.node.assignop.ASTAbstractAssign;
import org.monflabs.galtajs.node.binaryop.ASTBinaryOp;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.control.IWithContextNode;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.ternaryop.ASTTernaryOp;
import org.monflabs.galtajs.node.unaryop.ASTAbstractIncDec;
import org.monflabs.galtajs.node.unaryop.ASTDelete;
import org.monflabs.galtajs.node.unaryop.ASTUnaryOp;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;

/**
 * Constant folding optimizer.
 * 
 * We execute the node is the exact order the engine would do. There are 2 reasons:
 *   - a mix of data types can lead to different results, like s+1+2 is not s+3 is s is a string
 *   - rounding between numerical types can generate different results if the operations are executed in a different order.
 */
public class ConstantFoldingOptimizer extends NodeOptimizer {
	
	public ConstantFoldingOptimizer() {
	}
	
	@Override
	public void optimize(JSOptimizerContext _context, ASTNode node) {
		JSOptimizerContext context = node.createOptimizedContext(_context);

		int count = node.getChildCount();
next:	for(int i=0; i<count; i++) {
			ASTNode c = node.getChild(i);
			if(c!=null) {
				if(!shouldOptimize(c)) {
					continue;
				}
				
				// optimize the children
				optimize(context,c);

				// Phase 1e: fold the NaN/Infinity globals to literals when no
				// enclosing scope shadows them. Mirrors the transpiler-side
				// substitution in ASTIdentifier.getIdentifierReadAccessor().
				// `undefined` is intentionally left out: ASTLiteral has no
				// encoding for the UNDEFINED sentinel, so the transpiler pass
				// would fall over on a folded literal.
				if(c instanceof ASTIdentifier ident) {
					Object folded = foldGlobal(context, ident, ident.getId());
					if(folded!=null) {
						ASTLiteral lit = NodeFactory.DEFAULT.createLiteral(null, folded);
						lit.copyPositionFrom(c);
						node.setChild(i, lit);
						PrintStream ps = context.getTraceStream();
						if(ps!=null) {
							ps.println(StringFormat.format("*** Constant folding optimization: {0} => {1}",ident.getId(),ASTLiteral.encodeJavaLiteral(folded)));
						}
						continue next;
					}
				}

				if(c.isConstant(context)) {
					try { 
						Object r = evaluateNode(context, c);
						// Replace primitives
						if(r instanceof CharSequence || r instanceof Number || r instanceof Boolean) {
							ASTLiteral lit = NodeFactory.DEFAULT.createLiteral(null, r);
							lit.copyPositionFrom(c);
							node.setChild(i, lit);
							PrintStream ps = context.getTraceStream();
							if(ps!=null) {
								ps.println(StringFormat.format("*** Constant folding optimization, calculated value={0}",ASTLiteral.encodeJavaLiteral(r)));
							}
							continue next;
						}
					} catch(Exception ex) {
						// Ignore errors, keep the code as is
						Console.log("Issue in ConstantFolding Optimizer - Keep existing code, {0}:{1}",ex.getClass(),ex.getMessage());
						//ex.printStackTrace();
					}
				}
				
				// Update the context in case of a variable declaration
				// This has to be done after the optimization of the node itself, to cascade the optimizations
				c.updateOptimizedContext(context);
			}
		}
		afterChildrenOptimized(context, node);
	}

	// Extension point: runs once this node's own children have all been
	// recursively constant-folded (bottom-up, same as the rest of this
	// method). No-op here - overridden by ConstantFoldingAndUnreachableCodeOptimizer
	// to also run dead-code elimination in the SAME traversal instead of as
	// a separate full pass, once this node's own subtree (all that
	// UnreachableCodeRemovalOptimizer's own per-node check ever needs) is
	// already folded.
	protected void afterChildrenOptimized(JSOptimizerContext context, ASTNode node) {
	}

	private boolean shouldOptimize(ASTNode node) {
		if(node instanceof ASTLiteral) {
			// Already a constant
			return false;
		}
		if(node instanceof ASTIdentifier) {
			// Should be part of an exoression evaluation
			// Examples that fail:
			//    const x=3; o={x:4}; o={x}
			//    const a=3; { const a=4; }
			//    const a=3; delete a;
			//    const a=3; a++; (a's OWN reference needed to attempt - and
			//      fail with a runtime TypeError - the write; substituting its
			//      literal value here turns `a++` into e.g. `3++`, which is a
			//      totally different, meaningless "not assignable" SyntaxError
			//      instead. See test262 const-invalid-assignment-next-
			//      expression-for.js: a for-loop's `const i` update expression
			//      `i++` must reach ASTAbstractIncDec's runtime CONST check,
			//      never get folded away first.)
			// We put here the use cases we know. We might miss some, but the risk is just a lack of optimization
			ASTNode parent = ASTNode.skipTransparent(node.getParent());
			if(parent instanceof ASTUnaryOp) {
				if(parent instanceof ASTDelete || parent instanceof ASTAbstractIncDec) {
					return false;
				}
				return true;
			}
			if(parent instanceof ASTBinaryOp) {
				return true;
			}
			if(parent instanceof ASTTernaryOp) {
				return true;
			}
			if(parent instanceof ASTCall p && node!=p.getNode()) { // Call parameters
				return true;
			}
			if(parent instanceof ASTArrayMember p && node!=p.getNode()) { // Only the content
				return true;
			}
			if(parent instanceof ASTAbstractAssign p && node==p.getRightNode()) { // Only the right side
				return true;
			}
			if(parent instanceof ASTVariableDecl.Entry p && node==p.getInitNode()) { // Only the right side
				return true;
			}
			return false;
		}
		return true;
	}
	
	// Return the primitive value for a NaN/Infinity reference that is not
	// shadowed by any enclosing lexical scope, else null. `resolveSymbolVar`
	// only picks up CONST bindings, so walk the context chain directly and
	// bail out on any binding regardless of type.
	//
	// Also bail if the identifier is inside a `with` block: `with(o){NaN}`
	// must resolve against `o`'s own "NaN" property (if any) at runtime,
	// which folding to a literal at optimize-time would permanently bypass
	// (confirmed via test262 language/statements/with - an object's own
	// "NaN"/"Infinity" property must shadow the global).
	private Object foldGlobal(JSOptimizerContext context, ASTNode node, String name) {
		if(!"NaN".equals(name) && !"Infinity".equals(name)) {
			return null;
		}
		for(ASTNode n=node.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof IWithContextNode) {
				return null;
			}
		}
		for(JSOptimizerContext ctx=context; ctx!=null; ctx=ctx.getParent()) {
			if(ctx.getVariables().containsKey(name)) {
				return null;
			}
		}
		return "NaN".equals(name) ? Double.NaN : Double.POSITIVE_INFINITY;
	}

	private Object evaluateNode(JSOptimizerContext context, ASTNode node) {
		JSInterpretedRuntimeContext jsContext = new OptimizerEvaluationRuntimeContext(context);
		return jsContext.with( () -> {
			return node.evaluateValue(jsContext,new JSResult());
		});
	}
}