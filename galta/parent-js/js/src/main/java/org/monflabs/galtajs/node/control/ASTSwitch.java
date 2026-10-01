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
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedBlockRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;


/**
 * switch statement.
 *
 * NOT an {@link IContextBlockContainer} - see {@link ASTSwitchCaseBlock}'s
 * own doc for why. The discriminant expression (`exprNode`) resolves free
 * variables/closures through whatever genuinely encloses this whole switch
 * statement; every case selector/body resolves through the synthetic
 * `caseBlock` wrapper instead, which is the CaseBlock's own single shared
 * container.
 */
public class ASTSwitch extends ASTNode implements ILabeledNode {

	private String label;
	private ASTNode exprNode;
	private ASTSwitchCaseBlock caseBlock;

	public ASTSwitch(Token t, ASTNode exprNode, List<ASTNode> cases) {
		super(t);
		this.exprNode = assignParent(exprNode);
		this.caseBlock = assignParent(new ASTSwitchCaseBlock(t, cases));
	}

	public ASTNode[] getCases() {
		return caseBlock.getCases();
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
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return exprNode; }
			case 1 ->	{ return caseBlock; }
			default ->	{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.exprNode = node; }
			case 1 ->	{ this.caseBlock = (ASTSwitchCaseBlock)node; }
			default ->  { super._setChild(index-2,node); }
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// SwitchStatement Evaluation: the discriminant Expression evaluates
			// in the OUTER environment, BEFORE the CaseBlock's own lexical
			// environment is even created (step order: exprRef/switchValue,
			// THEN NewDeclarativeEnvironment + BlockDeclarationInstantiation) -
			// so it must NOT see a case-declared let/const.
	    	Object switchValue = exprNode.evaluateValue(context,result);

			// The CaseBlock's lexical environment (for any let/const declared in
			// ANY case) is created once, before evaluating even the first case's
			// SELECTOR expression - not just before the matched case's own
			// statements run (a selector like `case (x = 1, 1):` can already
			// observe/mutate a case-declared `let x`). So when this switch has
			// its own declarations, the whole selector-matching-and-execution
			// pass below runs inside one shared block context, mirroring
			// ASTBlock/ASTWith.
			if(caseBlock.hasDeclaredVariables()) {
				JSInterpretedRuntimeContext switchContext = new InterpretedBlockRuntimeContext(context);
				// Every let/const declared in ANY case exists (in the Temporal
				// Dead Zone) from the CaseBlock's environment creation, same as
				// any other block-entry - see ASTBlock's identical pre-population.
				for(VariableDef v: caseBlock.getVariables()) {
					VAR_TYPE t = v.getVarType();
					if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
						switchContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
					} else if(t==VAR_TYPE.FUNCTION) {
						// A case-body function declaration in strict mode stays
						// CaseBlock-scoped (Annex B hoist suppressed) - see
						// ASTBlock's identical branch for the full rationale.
						switchContext.createVariable(v.getName(), RuntimeUtil.UNDEFINED, t);
					}
				}
				return switchContext.with( () -> {
					return evaluateSwitch(switchContext, switchValue, result);
				});
			}
			return evaluateSwitch(context, switchValue, result);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	@SuppressWarnings("incomplete-switch")
	private Signal evaluateSwitch(JSInterpretedRuntimeContext context, Object switchValue, JSResult result) {
		ASTNode[] cases = caseBlock.getCases();
        // Find the node that matches the value
        int matchIndex=-1;
        int count = cases.length;
        for(int i=0;i<count;i++) {
            ASTNode caseExpr= ((ASTCase)cases[i]).getExprNode();
            if(caseExpr!=null) {
                Object caseValue = caseExpr.evaluateValue(context,result);
                if(RuntimeUtil.eqStrict(context.getEnvironment(),switchValue, caseValue)) {
                    matchIndex = i;
                    break;
                }
            } else {
            	// Use the default
                matchIndex = i;
            }
        }

        // And execute all the statements from that node
		result.setUndefined();
        if( matchIndex>=0 ) {
loop:       for( int i=matchIndex; i<count; i++ ) {
                ASTNode caseNode=cases[i];
                Signal s = caseNode.evaluate(context,result);
                if(s!=Signal.NONE) {
					switch(s.getType()) {
						case RETURN -> {
							return s;
						}
						case CONTINUE -> {
	            			// Continue up the next level - continue doesn't work with switch
	            			return s;
						}
						case BREAK -> {
							String l = s.getLabel();
			            	if(!StringUtil.isEmpty(l)) {
			            		if(!StringUtil.equals(label,l)) {
			            			// Continue up the next level
			            			return s;
			            		}
			            	}
			                // Break the main loop
			                break loop;
						}
					}
                }
            }
        }

		// result contains the last case evaluation
        return Signal.NONE;
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	int unid = jsContext.generateUniqueId();

		b.println("{", label);
    	b.incIndent();

		// Case selectors and case bodies both run under one shared block
		// context (mirroring ASTBlock.transpileJavaStatementNoBrace) so a
		// let/const declared in any case is visible to every selector,
		// matching the interpreter's own switchContext wrapping above.
		//
		// `caseBlock` (an ASTSwitchCaseBlock, see its own doc) - not
		// `exprNode` (the discriminant) - is the CaseBlock's own
		// IContextBlockContainer: a function literal declared directly in the
		// discriminant (e.g. `switch (probeExpr = function(){...}, v) {`) is
		// NOT reachable from `caseBlock` at all (exprNode is a sibling child
		// of ASTSwitch, not a descendant of caseBlock) - it hoists to
		// whatever container genuinely encloses this whole switch statement,
		// exactly like any other closure written outside a case. That outer
		// container already declares ALL of its own nested function classes
		// (via its own transpilerDeclareStatement() call) before transpiling
		// any of its own statements - including this switch statement itself
		// - so the discriminant's closure class is always already visible by
		// the time its `new F..._f0(...)` instantiation is emitted below,
		// with no reordering needed for it here.
		//
		// A function literal in a case SELECTOR or BODY, however, hoists to
		// `caseBlock` (the nearest IContextBlockContainer reachable from
		// anywhere in `cases[]`) - and `caseBlock`'s own declaration MUST
		// still happen before anything in THIS switch statement's own
		// generated code references it (the case-matching if/else chain
		// below, and the switch(index) dispatch's case bodies) - Java local
		// classes are only visible from their point of declaration onward.
		// Declaring first is safe: it only emits class definitions (and the
		// CaseBlock's own let/const array), it doesn't evaluate/execute
		// anything, so runtime evaluation order (discriminant still before
		// the CaseBlock's own declarations, per spec) is unaffected - only
		// the generated Java TEXT order matters here (mirrors
		// ASTBlock.transpileJavaStatementNoBrace's identical declare-then-
		// execute shape).
		JSTranspilerGeneratorContext switchContext = new TranspilerGeneratorBlockContext(jsContext);
		caseBlock.transpilerDeclareStatement(switchContext,b,0);

		// Discriminant Expression evaluates in the OUTER context - same
		// "before the CaseBlock's own lexical environment exists" runtime
		// ordering as the interpreter's evaluate() above - and, unlike the
		// case-block declarations above, uses `jsContext` (the OUTER
		// context), not `switchContext`, since the discriminant is not part
		// of the CaseBlock at all.
		b.println("Object value{0} = {1};", unid, JSTranspiler.asValue(jsContext, exprNode));
		b.println("int index{0} = -1;", unid);

		boolean first = true;
		ASTNode[] cases = caseBlock.getCases();
        int count = cases.length;
        for(int i=0;i<count;i++) {
			ASTCase node = (ASTCase)cases[i];
			if(node.getExprNode()!=null) {
	        	if(!first) {
	            	b.println("else ");
	        	} else {
	        		first = false;
	        	}
				b.debugLocation(node);
	        	// CaseBlock case-matching (spec 12.11 Runtime Semantics) uses
	        	// Strict Equality Comparison (===), not the Abstract (loose,
	        	// coercing) Equality Comparison (==) eq() implements - was
	        	// wrongly using eq(), so e.g. `switch(true){case 1: ...}`
	        	// matched (true==1) when it must not (true!==1). Mirrors
	        	// ASTSwitch's own interpreted evaluate(), which already uses
	        	// eqStrict.
	        	b.println("if(eqStrict(value{0},{1})) {", unid, JSTranspiler.asValue(switchContext, node.getExprNode()));
	        	b.incIndent();
	        	b.println("index{0} = {1};", unid, i);
	        	b.decIndent();
	        	b.println("}");
			}
        }

		if(StringUtil.isNotEmpty(label)) {
			b.println("{0}:", ILabeledNode.javaLabel(label));
		}

		b.println("switch(index{0}) {",unid);
		b.incIndent();
		for(int i=0; i<count; i++) {
			ASTCase node = (ASTCase)cases[i];
	    	if(node.getExprNode()!=null) {
	    		b.println("case {0}:", i);
	    	} else {
	    		b.println("default:");
	    	}
	    	node.transpileJavaStatement(switchContext, b);
		}
		b.decIndent();
		b.println("}");

		b.decIndent();
		b.println("}");
    }
    
    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(StringUtil.isNotEmpty(label)) {
        	b.append("{0}: ", label).nl();
    	}
    	b.append("switch(");
       	b.append(exprNode.decompileExpression());
    	b.append(") {\n");
    	
    	b.incIndent();
		ASTNode[] cases = caseBlock.getCases();
        int count = cases.length;
        for(int i=0;i<count;i++) {
			ASTCase node = (ASTCase)cases[i];
	    	if(node.getExprNode()!=null) {
		    	b.append("case ");
		       	b.append(node.getExprNode().decompileExpression());
		    	b.append(": {\n");
	    	} else {
		    	b.append("default: {\n");
	    	}
	    	b.incIndent();
	    	decompileStatements(b, node.getStatements());
	    	b.decIndent();
	    	b.append("}\n");
        }
    	b.decIndent();

    	b.append("}");
	}

}