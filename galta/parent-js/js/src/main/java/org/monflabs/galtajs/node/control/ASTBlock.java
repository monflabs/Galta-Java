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
import java.util.function.Consumer;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTStatementList;
import org.monflabs.galtajs.node.debug.ASTDebugger;
import org.monflabs.galtajs.node.debug.DebuggableNode;
import org.monflabs.galtajs.node.debug.ASTDebugHook;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedBlockRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap.Block;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockSplitContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;


/**
 * block {} statement.
 */
public class ASTBlock extends ASTStatementList implements ILabeledNode, DebuggableNode {

	private String label;
	
	public ASTBlock(Token t, List<ASTNode> statements) {
		super(t);
		if(statements!=null) {
			setStatements(statements.toArray(new ASTNode[statements.size()]));
		}
	}
	
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return !hasDeclaredVariables() && areChildrenConstant(context);
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public void setLabel(String label) {
		this.label = label;
	}
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			ASTNode[] statements = getStatements();
			if(statements.length>0) {
				if(hasDeclaredVariables()) {
					JSInterpretedRuntimeContext blockContext = new InterpretedBlockRuntimeContext(context);
					// Every let/const/using in this block exists (in the Temporal
					// Dead Zone) from block-entry, even before its own declaration
					// statement runs - pre-populate a TDZ placeholder for each so
					// an earlier read/write/typeof throws instead of silently
					// falling through to an outer same-named binding.
					boolean hasUsingDeclarations = false;
					for(VariableDef v: getVariables()) {
						VAR_TYPE t = v.getVarType();
						if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
							blockContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
							if(t==VAR_TYPE.USING) {
								hasUsingDeclarations = true;
							}
						} else if(t==VAR_TYPE.FUNCTION) {
							// A block-scoped function declaration that stayed
							// local (Annex B hoist suppressed - strict mode,
							// or a switch/for-loop CaseBlock-style container -
							// see ASTFunctionDecl.createFunction()) needs a
							// local slot to assign into; UNDEFINED until its
							// own statement runs, matching the same
							// not-eagerly-hoisted-with-value behavior ordinary
							// top-level function hoisting already has
							// (BuiltinFunctionInterpreter.bindParametersAndVars).
							blockContext.createVariable(v.getName(), RuntimeUtil.UNDEFINED, t);
						}
					}
					if(!hasUsingDeclarations) {
						return blockContext.with( () -> {
							return evaluateBlock(blockContext, result);
						});
					}
					// RS: Block evaluation - DisposeResources(blockEnv.[[DisposeCapability]],
					// blockValue) runs on EVERY exit path (normal completion, break/
					// continue/return via Signal, or an exception) - only paid for
					// when this block actually declares a using/await-using resource.
					return blockContext.with( () -> {
						Signal s = null;
						Throwable pending = null;
						try {
							s = evaluateBlock(blockContext, result);
						} catch(Throwable t) {
							pending = t;
						}
						Throwable toThrow = DisposeResourcesUtil.dispose(blockContext, pending);
						if(toThrow!=null) {
							if(toThrow instanceof RuntimeException re) {
								throw re;
							}
							if(toThrow instanceof Error e) {
								throw e;
							}
							throw new RuntimeException(toThrow);
						}
						return s;
					});
				} else {
					return evaluateBlock(context, result);
				}
			}
			// Empty block (zero statements): per spec, an empty Block has EMPTY
			// completion, not "undefined" - `result` must be left untouched so an
			// earlier statement's completion value survives (e.g.
			// eval('{length: 3000}{}') must complete with 3000, not have this
			// trailing empty block stomp it back to undefined).

			// result contains the last statement evaluation
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	@SuppressWarnings("incomplete-switch")
	private Signal evaluateBlock(JSInterpretedRuntimeContext blockContext, JSResult result) {
		ASTNode[] statements = getStatements();
loop:	for(int i=0; i<statements.length; i++) {
			Signal s = statements[i].evaluate(blockContext,result);
			if(s!=Signal.NONE) {
				if(label!=null) {
					switch(s.getType()) {
						case CONTINUE -> {
							String l = s.getLabel(); 
		            		if(!StringUtil.equals(label,l)) {
		            			// Continue up the next level
		            			return s;
		            		}
	            			throw RuntimeUtil.syntaxError("Syntactic error");
						}
						case BREAK -> {
							String l = s.getLabel(); 
		            		if(!StringUtil.equals(label,l)) {
		            			// Continue up the next level
		            			return s;
		            		}
			                // Break the main loop - result already carries the last
			                // completion value seen from the block (UpdateEmpty semantics).
			                break loop;
						}
					}
				}
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
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(StringUtil.isNotEmpty(label)) {
			b.println("{0}:", label);
		}
		b.println("{");
		b.incIndent();
		transpileJavaStatementNoBrace(jsContext,b);
		b.decIndent();
		b.println("}");
    }

	public void transpileJavaStatementNoBrace(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		transpileJavaStatementNoBrace(jsContext,b,null);
	}
	public void transpileJavaStatementNoBrace(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, Consumer<TranspilerJavaBuilder> initVariables) {
    	JSTranspilerMap map = jsContext.getTranspilerMap();
		map.pushBlock(b.getCurrentLine());

		JSTranspilerGeneratorContext blockContext = new TranspilerGeneratorBlockContext(jsContext);
		transpilerDeclareStatement(blockContext,b,0);

		if(initVariables!=null) {
			initVariables.accept(b);
		}

		ASTNode[] statements = getStatements();
		if(hasUsingDeclarations(this)) {
			transpileStatementsWithDisposal(jsContext, blockContext, b, this, statements);
		} else {
			transpileBlockStatements(blockContext, b, this, statements);
		}

		map.popBlock(b.getCurrentLine());
    }

	// Mirrors evaluate()'s own `hasUsingDeclarations` check above. Shared with
	// ASTFunction (a function's own body is NOT an ASTBlock instance - see
	// ASTRootStatementList - but is an ASTVarContainer too, so a using/
	// await-using declared directly in a function body, with no intervening
	// nested Block, needs this same check run against the function itself).
	public static boolean hasUsingDeclarations(org.monflabs.galtajs.node.ASTVarContainer container) {
		if(!container.hasDeclaredVariables()) {
			return false;
		}
		for(VariableDef v: container.getVariables()) {
			if(v.getVarType()==VAR_TYPE.USING) {
				return true;
			}
		}
		return false;
	}

	// Wraps `statements` so every using/await-using resource registered
	// against it (via RuntimeUtil.registerDisposableResource(), called from
	// ASTVariableDeclUsing.transpileJavaStatement() once it finds this list
	// through blockContext.getDisposablesListVar()) is disposed on EVERY exit
	// path - normal completion, break/continue/return, or an exception -
	// mirroring DisposeResources' spec algorithm (see
	// DisposeResourcesUtil.dispose()). A plain Java `finally` is the right
	// primitive here (unlike the interpreter's own Signal-based evaluate(),
	// transpiled control flow uses REAL return/break/continue statements,
	// which skip any code that isn't inside a finally) - the inner
	// try/catch's sole purpose is capturing the in-flight exception (if any)
	// into `pendingVar` so DisposeResourcesUtil.dispose() can chain a
	// SuppressedError exactly like the interpreted path; return/break/continue
	// or a clean fall-through never touch that catch at all (pendingVar stays
	// null), and the finally block still runs for them regardless.
	public static void transpileStatementsWithDisposal(JSTranspilerGeneratorContext jsContext, JSTranspilerGeneratorContext blockContext, TranspilerJavaBuilder b, ASTNode container, ASTNode[] statements) {
		transpileWithDisposal(jsContext, blockContext, b, () -> transpileBlockStatements(blockContext, b, container, statements));
	}

	// Generic version of the above: `body` emits whatever needs to run
	// disposed-on-every-exit-path, not necessarily a flat AST statement list -
	// e.g. ASTFor's own for(;;){...} loop structure (init clause already
	// transpiled separately, before this wrapper starts - see ASTFor's own
	// call site), which transpileBlockStatements() has no notion of.
	public static void transpileWithDisposal(JSTranspilerGeneratorContext jsContext, JSTranspilerGeneratorContext blockContext, TranspilerJavaBuilder b, Runnable body) {
		String listVar = jsContext.generateUniqueId("__disposables");
		String pendingVar = jsContext.generateUniqueId("__pending");
		blockContext.setDisposablesListVar(listVar);
		b.println("List<DisposableResource> {0} = new ArrayList<>();", listVar);
		b.println("Throwable {0} = null;", pendingVar);
		b.println("try {");
		b.incIndent();
		b.println("try {");
		b.incIndent();
		body.run();
		b.decIndent();
		b.println("} catch(Throwable __t) {");
		b.incIndent();
		b.println("{0} = __t;", pendingVar);
		b.println("throw __t;");
		b.decIndent();
		b.println("}");
		b.decIndent();
		b.println("} finally {");
		b.incIndent();
		b.println("Throwable __toThrow = DisposeResourcesUtil.dispose({0},{1},{2});", JSTranspiler.MAIN_CONTEXT, listVar, pendingVar);
		b.println("if(__toThrow!=null) { RuntimeUtil.rethrowUnchecked(__toThrow); }");
		b.decIndent();
		b.println("}");
	}
	
	// Emits a guarded call to JSTranspiledUnit.debugStatement() for one
	// top-level statement-list entry, when JSTranspilerOptions.isDebuggable()
	// - false (the default) emits nothing at all, byte-identical to today's
	// codegen. Unlike ASTDebugHook's interpreted-mode DebuggableNode
	// check (which skips a compound statement like ASTFor/ASTIf/ASTWhile
	// because its test/update/condition sub-expressions get their OWN wraps
	// elsewhere), transpiled mode does NOT instrument sub-expressions at all -
	// every element of `statements` is instrumented here unconditionally, or
	// that statement's line would never be reachable by a breakpoint/step at
	// all. See JSTranspilerOptions.debuggable's own doc for the full design.
	private static void emitDebugHook(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, ASTNode node) {
		if(jsContext.getOptions().isDebuggable()) {
			b.println("debugStatement({0}, {1}, {2}, {3});", JSTranspiler.MAIN_CONTEXT, node.getBeginLine(), node.getBeginCol(), isDebuggerStatement(node));
		}
	}

	// node is the literal `debugger;` statement, whether or not it went
	// through the parser's own ASTDebugHook wrap (Statement()'s trailing
	// wrap applies unconditionally, but this stays correct even if some
	// future call site passes the raw, unwrapped node).
	private static boolean isDebuggerStatement(ASTNode node) {
		ASTNode actual = node instanceof ASTDebugHook ih ? ih.getNode() : node;
		return actual instanceof ASTDebugger;
	}

	public static void transpileBlockStatements(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, ASTNode container, ASTNode[] statements) {
    	JSTranspilerMap map = jsContext.getTranspilerMap();
		Block mapBlock = map.getCurrentBlock();
		
		if(statements.length>0) {
			// Split to avoid classes that are to large
			int count = statements.length;
			
	        TranspilerCodeSplitter splitter = jsContext.getOptions().getCodeSplitter();

			if(splitter!=null && splitter.shouldSplitBlock(container, statements)) {
				for(int pos=0; pos<statements.length; ) {
					// Calculate the number of statements to aggregate
					int statementCount = 0;
					int max = Math.min(count-pos, splitter.blockSplitMax());
					for(int j=0; j<max ; j++) {
						ASTNode node = statements[pos+j];
						if(!splitter.isNodeBlockSplitable(container, node)) {
							break;
						}
						statementCount++;
					}
					
					// Generate the aggregated statements
					if(statementCount>=splitter.blockSplitMin()) {
						b.println("(new Runnable() { // Begin Block split");
						b.incIndent();
						b.println("@Override");
						b.println("public void run() {");
						b.incIndent();

						TranspilerGeneratorBlockSplitContext bCtx = new TranspilerGeneratorBlockSplitContext(jsContext);

						for(int j=0; j<statementCount; j++) {
							ASTNode node = statements[pos+j];
							b.debugLocation(node);
							mapBlock.add(b, node);
							emitDebugHook(bCtx, b, node);
							node.transpileJavaStatement(bCtx, b);
						}
						
						b.decIndent();
						b.println("}");
						b.decIndent();
						
						JSTranspiler.createConstantPool(bCtx,b);
						
						b.println("}).run(); // End Block split");
					} else {
						// Not enough statements to put them in a separate method
						for(int j=0; j<statementCount; j++) {
							ASTNode node = statements[pos+j];
							b.debugLocation(node);
							mapBlock.add(b, node);
							emitDebugHook(jsContext, b, node);
							node.transpileJavaStatement(jsContext, b);
						}
					}
					pos += statementCount;
					
					for( ; pos<statements.length && !splitter.isNodeBlockSplitable(container,statements[pos]); pos++) {
						ASTNode node = statements[pos];
						b.debugLocation(node);
						mapBlock.add(b, node);
						emitDebugHook(jsContext, b, node);
						node.transpileJavaStatement(jsContext, b);
					}
				}
			} else {
				for(int i=0; i<count; i++) {
					ASTNode node = statements[i];
					b.debugLocation(node);
					mapBlock.add(b, node);
					emitDebugHook(jsContext, b, node);
					node.transpileJavaStatement(jsContext, b);
					if(i<count-1 && alwaysLeavesBlock(node)) {
						// Whatever textually follows `node` in this SAME
						// statement list is genuinely unreachable (see
						// alwaysLeavesBlock() below) - stop emitting further
						// Java statements. javac rejects unreachable code as
						// a compile error ("unreachable statement"), and
						// real JS execution would never reach it either, so
						// skipping it changes nothing observable. Var
						// hoisting is unaffected: transpilerDeclareStatement()
						// above already declared every var in this block
						// regardless of which statements get emitted here.
						break;
					}
				}
			}
		}
	}

	// Conservative "does control ever fall through past `node` to whatever
	// lexically follows it in THIS statement list" check. Used only to avoid
	// emitting genuinely-unreachable Java code - see test262
	// language/statements/do-while/S12.6.1_A4_T3.js: a labeled do-while's
	// body unconditionally `break`s an OUTER label, so the do-while
	// statement itself never completes normally, making whatever follows it
	// in this block dead in both JS and (unlike JS) rejected by javac.
	private static boolean alwaysLeavesBlock(ASTNode node) {
		node = ASTNode.skipTransparent(node);
		if(node instanceof ASTReturn || node instanceof ASTThrow) {
			return true;
		}
		if(node instanceof ASTBreak || node instanceof ASTContinue) {
			// Whatever it targets, a break/continue statement sitting
			// directly in this list unconditionally leaves it right here.
			return true;
		}
		if(node instanceof ASTBlock block && StringUtil.isEmpty(block.getLabel())) {
			// Mirrors ASTFor.endsWithUnconditionalLoopExit()'s recursion:
			// only a plain (unlabeled) nested block, only its own directly-
			// visible last statement.
			ASTNode[] inner = block.getStatements();
			if(inner==null || inner.length==0) {
				return false;
			}
			return alwaysLeavesBlock(inner[inner.length-1]);
		}
		if(node instanceof ASTDoWhile dw) {
			return bodyAlwaysExitsLoop(dw.getBodyNode(), dw.getLabel());
		}
		return false;
	}

	// True when `body` (a do-while loop's own body) always performs, on its
	// last reachable top-level statement, a return/throw - or a break/
	// continue whose label is present and does NOT match `loopLabel`. Per
	// JS's static label-scoping rules a label can only bind to a lexically
	// ENCLOSING labeled statement, so a break/continue whose label doesn't
	// match this loop's own must resolve to some construct OUTSIDE it -
	// meaning the loop can never complete/continue normally, and always
	// jumps past itself instead. An unlabeled (or self-labeled) break/
	// continue instead targets this loop itself - the loop then completes
	// or continues NORMALLY, falling through to whatever follows it - so
	// that case deliberately returns false (this is the opposite question
	// from ASTFor.endsWithUnconditionalLoopExit(), which treats an
	// unlabeled break as an exit; here it is not one). Same conservative
	// scope as that helper: does not descend into nested loops/switches/
	// conditionals, or a nested block that carries its own label (a break/
	// continue targeting THAT label would be caught there, not propagate
	// out to this loop).
	private static boolean bodyAlwaysExitsLoop(ASTNode node, String loopLabel) {
		node = ASTNode.skipTransparent(node);
		if(node instanceof ASTReturn || node instanceof ASTThrow) {
			return true;
		}
		if(node instanceof ASTBreak || node instanceof ASTContinue) {
			String l = node.getNodeString();
			if(StringUtil.isEmpty(l) || StringUtil.equals(l, loopLabel)) {
				return false;
			}
			return true;
		}
		if(node instanceof ASTBlock block && StringUtil.isEmpty(block.getLabel())) {
			ASTNode[] inner = block.getStatements();
			if(inner==null || inner.length==0) {
				return false;
			}
			return bodyAlwaysExitsLoop(inner[inner.length-1], loopLabel);
		}
		return false;
	}

	
    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(StringUtil.isNotEmpty(label)) {
        	b.append("{0}: ", label).nl();
    	}
    	b.append("{").nl();
    	b.incIndent();
		ASTNode[] statements = getStatements();
    	decompileStatements(b, statements);
    	b.decIndent();
    	b.append("}").nl();
    }

}